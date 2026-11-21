// Package dmstream serves a file of any rclone remote (including encrypted
// "crypt" remotes) over a loopback-only HTTP server with range support.
// A media player can then start playing straight away and seek freely,
// without downloading and decrypting the whole file first.
//
// Speed notes:
//   - the Fs is taken from rclone's own cache, so a new stream does not log in again
//   - the remote object is looked up in the background as soon as the stream is prepared
//   - data is fetched ahead of the player in several parallel ranged requests
//     (small chunks first, so playback starts fast), instead of one slow connection
//   - downloaded pieces are kept per FILE (shared by thumbnail and player streams): memory for the
//     start/end of the file, an encrypted session-only disk cache for the rest (see "Shared file cache")
package dmstream

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/rclone/rclone/fs"
	"github.com/rclone/rclone/fs/cache"
)

const (
	// how many chunks may be downloading or waiting in memory per open request
	aheadChunks = 6
	// how many of them download at the same time
	parallelFetch = 3
	// biggest chunk; the first ones are smaller so playback starts quickly
	maxChunk = 4 << 20
	minChunk = 256 << 10
	// downloads running at once across ALL open requests (thumbnails + player)
	globalFetch = 8
)

var fetchSem = make(chan struct{}, globalFetch)

// bgFetch is how many background reads (thumbnails, warm-up, previews; URL has "?bg=1") may run
// at the same time. They also count against globalFetch, so the player always keeps free
// slots: before, many thumbnail extractors on big encrypted videos could fill every slot and
// the player waited "forever" for its first chunk.
const bgFetch = 2

// A single ranged download (open + read of one chunk) that takes longer than this is abandoned and retried,
// so a hanging cloud request can never leave the player on an endless spinner.
const attemptTimeout = 45 * time.Second

var (
	diagMu    sync.Mutex
	diagLines []string
)

// diag keeps the last few stream events (slow or failed requests) so the app can show
// the real reason when playback does not start.
func diag(format string, a ...interface{}) {
	line := time.Now().Format("15:04:05") + " " + fmt.Sprintf(format, a...)
	diagMu.Lock()
	diagLines = append(diagLines, line)
	if len(diagLines) > 12 {
		diagLines = diagLines[len(diagLines)-12:]
	}
	diagMu.Unlock()
}

// LastDiagnostics returns the most recent stream events, one per line (newest last).
func LastDiagnostics() string {
	diagMu.Lock()
	defer diagMu.Unlock()
	return strings.Join(diagLines, "\n")
}

var bgSem = make(chan struct{}, bgFetch)

// Shared file cache.
//
// Players and thumbnail extractors read the same parts of a file again and again: the
// container header and the MP4 "moov" index (start and end of the file) when a
// thumbnail is made and again when playback starts, a seek back, a replay, or a player
// that reopens its connection. Every one of those reads used to be a new cloud round
// trip (plus decryption on an encrypted remote).
//
// Pieces that were downloaded are therefore kept, keyed by the FILE (remote + path +
// size) and not by the stream token, so a thumbnail request and a later play request
// share them. Two tiers, both private to this app run:
//   - memory: the first and last edgeBytes of each file (small, instant)
//   - disk: pieces of whole small/medium files and of the beginning of big ones,
//     AES-256-GCM encrypted with a random key that only lives in memory. The key is
//     gone when the app stops, the folder is wiped on the next start, so nothing
//     readable survives and nothing needs a password. Decrypted video never touches
//     the disk in clear.
//
// Writing to disk never slows playback: it runs in the background and is skipped
// when two writes are already running.
var (
	// bytes at the start and end of a file that are kept in memory
	edgeBytes int64 = 4 << 20
	// memory for all kept edge pieces, across all files; oldest files are dropped first
	memBudget int64 = 64 << 20
	// disk space for all kept pieces
	diskBudget int64 = 512 << 20
	// files up to this size are cached on disk completely
	diskWholeFileMax int64 = 512 << 20
	// of bigger files only this much of the beginning is cached on disk (a long film
	// watched once should not be written to flash a second time)
	diskHeadMax int64 = 64 << 20
)

type seg struct {
	start int64
	data  []byte
}

// dseg is one encrypted piece on disk.
type dseg struct {
	start int64
	n     int64
	name  string
	fc    *fileCache
}

type fileCache struct {
	key  string
	mem  []seg
	disk []*dseg
}

var (
	cacheMu   sync.Mutex // guards everything below and every fileCache field
	caches    = map[string]*fileCache{}
	memUsed   int64
	memOrder  []*fileCache
	diskUsed  int64
	diskOrder []*dseg

	diskOnce sync.Once
	diskDir  string
	diskAEAD cipher.AEAD
	diskSem  = make(chan struct{}, 2)
)

// setupDisk wipes leftovers of an earlier run and makes the folder and a fresh random key.
// It returns nil when anything fails; the disk tier then simply stays off.
func setupDisk() (string, cipher.AEAD) {
	dir := filepath.Join(os.TempDir(), "dmstream-cache")
	_ = os.RemoveAll(dir)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", nil
	}
	key := make([]byte, 32)
	if _, err := rand.Read(key); err != nil {
		return "", nil
	}
	blk, err := aes.NewCipher(key)
	if err != nil {
		return "", nil
	}
	gcm, err := cipher.NewGCM(blk)
	if err != nil {
		return "", nil
	}
	return dir, gcm
}

// initDisk prepares the encrypted disk tier once, before the server starts.
func initDisk() {
	diskOnce.Do(func() { diskDir, diskAEAD = setupDisk() })
}

// cacheFor returns the cache shared by all streams of the same file.
func cacheFor(key string) *fileCache {
	cacheMu.Lock()
	defer cacheMu.Unlock()
	fc := caches[key]
	if fc == nil {
		fc = &fileCache{key: key}
		caches[key] = fc
	}
	return fc
}

// segAt finds a kept piece that contains pos. cacheMu must be held.
// Exactly one of the results is set: memory piece or disk piece.
func segAt(fc *fileCache, pos int64) (*seg, *dseg) {
	for i := range fc.mem {
		s := &fc.mem[i]
		if pos >= s.start && pos < s.start+int64(len(s.data)) {
			return s, nil
		}
	}
	for _, d := range fc.disk {
		if pos >= d.start && pos < d.start+d.n {
			return nil, d
		}
	}
	return nil, nil
}

// coveredLocked reports whether [start, end) is completely inside kept pieces. cacheMu must be held.
func coveredLocked(fc *fileCache, start, end int64) bool {
	pos := start
	for pos < end {
		m, d := segAt(fc, pos)
		switch {
		case m != nil:
			pos = m.start + int64(len(m.data))
		case d != nil:
			pos = d.start + d.n
		default:
			return false
		}
	}
	return true
}

// lookup returns bytes [start, start+want) if they are completely in the cache, else nil.
// The result must be treated as read-only.
func lookup(fc *fileCache, start int64, want int) []byte {
	if fc == nil || want <= 0 {
		return nil
	}
	end := start + int64(want)
	cacheMu.Lock()
	// fast path: one memory piece covers everything, no copy
	for _, s := range fc.mem {
		if start >= s.start && end <= s.start+int64(len(s.data)) {
			off := start - s.start
			b := s.data[off : off+int64(want)]
			cacheMu.Unlock()
			return b
		}
	}
	ok := coveredLocked(fc, start, end)
	cacheMu.Unlock()
	if !ok {
		return nil
	}
	// stitch pieces (memory and disk) together
	out := make([]byte, want)
	pos := start
	for pos < end {
		n := copyAt(fc, pos, out[pos-start:])
		if n <= 0 {
			return nil // a piece vanished meanwhile (evicted): treat as a miss
		}
		pos += int64(n)
	}
	return out
}

// copyAt copies from the piece containing pos into dst and returns how many bytes it copied.
func copyAt(fc *fileCache, pos int64, dst []byte) int {
	cacheMu.Lock()
	m, d := segAt(fc, pos)
	if m != nil {
		n := copy(dst, m.data[pos-m.start:])
		cacheMu.Unlock()
		return n
	}
	if d == nil {
		cacheMu.Unlock()
		return 0
	}
	name, dstart := d.name, d.start
	cacheMu.Unlock()

	plain, err := readDisk(name)
	if err != nil {
		return 0
	}
	return copy(dst, plain[pos-dstart:])
}

func readDisk(name string) ([]byte, error) {
	if diskAEAD == nil {
		return nil, errors.New("no disk cache")
	}
	sealed, err := os.ReadFile(filepath.Join(diskDir, name))
	if err != nil {
		return nil, err
	}
	ns := diskAEAD.NonceSize()
	if len(sealed) < ns {
		return nil, errors.New("short cache file")
	}
	return diskAEAD.Open(nil, sealed[:ns], sealed[ns:], nil)
}

// store keeps a downloaded piece: the edges of the file in memory, other parts on disk when eligible.
func store(fc *fileCache, start int64, data []byte, size int64) {
	if fc == nil || len(data) == 0 {
		return
	}
	end := start + int64(len(data))
	cacheMu.Lock()
	if coveredLocked(fc, start, end) {
		cacheMu.Unlock()
		return
	}
	if end <= edgeBytes || start >= size-edgeBytes {
		if len(fc.mem) == 0 {
			memOrder = append(memOrder, fc)
		}
		fc.mem = append(fc.mem, seg{start: start, data: data})
		memUsed += int64(len(data))
		for memUsed > memBudget && len(memOrder) > 0 {
			v := memOrder[0]
			memOrder = memOrder[1:]
			for _, s := range v.mem {
				memUsed -= int64(len(s.data))
			}
			v.mem = nil
			pruneLocked(v)
		}
		cacheMu.Unlock()
		return
	}
	cacheMu.Unlock()
	if size <= diskWholeFileMax || start < diskHeadMax {
		diskPut(fc, start, data)
	}
}

// diskPut encrypts and writes a piece in the background. It is skipped (not waited for) when busy.
func diskPut(fc *fileCache, start int64, data []byte) {
	if diskAEAD == nil {
		return
	}
	select {
	case diskSem <- struct{}{}:
	default:
		return
	}
	go func() {
		defer func() { <-diskSem }()
		nonce := make([]byte, diskAEAD.NonceSize())
		if _, err := rand.Read(nonce); err != nil {
			return
		}
		sealed := diskAEAD.Seal(nonce, nonce, data, nil) // nonce || ciphertext
		id := make([]byte, 8)
		if _, err := rand.Read(id); err != nil {
			return
		}
		name := hex.EncodeToString(id)
		path := filepath.Join(diskDir, name)
		_ = os.MkdirAll(diskDir, 0o700) // the system may have cleaned the cache folder
		if err := os.WriteFile(path, sealed, 0o600); err != nil {
			_ = os.Remove(path)
			return
		}
		var gone []string
		cacheMu.Lock()
		d := &dseg{start: start, n: int64(len(data)), name: name, fc: fc}
		fc.disk = append(fc.disk, d)
		diskOrder = append(diskOrder, d)
		diskUsed += d.n
		for diskUsed > diskBudget && len(diskOrder) > 0 {
			v := diskOrder[0]
			diskOrder = diskOrder[1:]
			diskUsed -= v.n
			v.fc.removeDisk(v)
			gone = append(gone, v.name)
			pruneLocked(v.fc)
		}
		cacheMu.Unlock()
		for _, n := range gone {
			_ = os.Remove(filepath.Join(diskDir, n))
		}
	}()
}

func (fc *fileCache) removeDisk(d *dseg) {
	for i, x := range fc.disk {
		if x == d {
			fc.disk = append(fc.disk[:i], fc.disk[i+1:]...)
			return
		}
	}
}

// pruneLocked forgets a file's record when nothing is kept for it any more. cacheMu must be held.
func pruneLocked(fc *fileCache) {
	if len(fc.mem) == 0 && len(fc.disk) == 0 {
		delete(caches, fc.key)
	}
}

// chunkSizeFor grows 256K, 512K, 1M, 2M, 4M, 4M...
func chunkSizeFor(seq int) int64 {
	if seq > 4 {
		return maxChunk
	}
	sz := int64(minChunk) << uint(seq)
	if sz > maxChunk {
		sz = maxChunk
	}
	return sz
}

// hlsFs is implemented by remotes that can give a ready-made HLS playlist (Terabox).
type hlsFs interface {
	StreamPlaylist(ctx context.Context, remote string, quality string) (string, error)
}

type item struct {
	playlist string // set for HLS items
	f        fs.Fs
	remote   string
	mime     string
	created  time.Time

	mu  sync.Mutex
	obj fs.Object

	// identifies the file across streams (remote + path); the size is added once the object is known
	fkey string
}

func (it *item) object(ctx context.Context) (fs.Object, error) {
	it.mu.Lock()
	defer it.mu.Unlock()
	if it.obj != nil {
		return it.obj, nil
	}
	o, err := it.f.NewObject(ctx, it.remote)
	if err != nil {
		return nil, err
	}
	it.obj = o
	return o, nil
}

var (
	mu      sync.Mutex
	started bool
	port    int
	items   = map[string]*item{}
)

// StartStreamServer starts the loopback server (once) and returns its port.
func StartStreamServer() (int, error) {
	mu.Lock()
	defer mu.Unlock()
	if started {
		return port, nil
	}
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, err
	}
	initDisk()
	srv := &http.Server{
		Handler:           http.HandlerFunc(handle),
		ReadHeaderTimeout: 15 * time.Second,
	}
	go func() { _ = srv.Serve(ln) }()
	port = ln.Addr().(*net.TCPAddr).Port
	started = true
	return port, nil
}

// PrepareHls asks the remote for its own HLS playlist of a video and returns the token of
// the local playlist URL. It fails when the remote cannot do this; then the caller plays the original file.
func PrepareHls(fsSpec string, remote string, quality string) (string, error) {
	f, err := cache.Get(context.Background(), fsSpec)
	if err != nil && !errors.Is(err, fs.ErrorIsFile) {
		return "", err
	}
	h, ok := f.(hlsFs)
	if !ok {
		return "", errors.New("this remote has no stream playlist")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 40*time.Second)
	defer cancel()
	body, err := h.StreamPlaylist(ctx, strings.Trim(remote, "/"), quality)
	if err != nil {
		return "", err
	}
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	token := hex.EncodeToString(raw)
	mu.Lock()
	items[token] = &item{playlist: body, created: time.Now()}
	mu.Unlock()
	return token, nil
}

// PrepareStream registers a file and returns the secret token for its URL.
// fsSpec is an rclone "fs" such as "dmabc:" and remote the path inside it.
func PrepareStream(fsSpec string, remote string, mime string) (string, error) {
	f, err := cache.Get(context.Background(), fsSpec)
	if err != nil && !errors.Is(err, fs.ErrorIsFile) {
		return "", err
	}
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	token := hex.EncodeToString(raw)

	it := &item{f: f, remote: strings.Trim(remote, "/"), mime: mime, created: time.Now()}
	it.fkey = fsSpec + "\x00" + it.remote

	mu.Lock()
	for k, v := range items {
		if time.Since(v.created) > 6*time.Hour {
			delete(items, k)
		}
	}
	items[token] = it
	mu.Unlock()

	// look the file up now, while the player is still getting ready
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
		defer cancel()
		_, _ = it.object(ctx)
	}()
	return token, nil
}

func handle(w http.ResponseWriter, r *http.Request) {
	const prefix = "/s/"
	const hlsPrefix = "/h/"
	if strings.HasPrefix(r.URL.Path, hlsPrefix) {
		mu.Lock()
		hit := items[strings.TrimPrefix(r.URL.Path, hlsPrefix)]
		mu.Unlock()
		if hit == nil || hit.playlist == "" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		w.Header().Set("Cache-Control", "no-store")
		_, _ = io.WriteString(w, hit.playlist)
		return
	}
	if !strings.HasPrefix(r.URL.Path, prefix) {
		http.NotFound(w, r)
		return
	}
	mu.Lock()
	it := items[strings.TrimPrefix(r.URL.Path, prefix)]
	mu.Unlock()
	if it == nil || it.f == nil {
		http.NotFound(w, r)
		return
	}
	octx, ocancel := context.WithTimeout(r.Context(), 60*time.Second)
	t0 := time.Now()
	obj, err := it.object(octx)
	ocancel()
	if d := time.Since(t0); err != nil || d > 3*time.Second {
		diag("find %q took %s err=%v", it.remote, d.Round(time.Millisecond), err)
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	// Always set a type: otherwise ServeContent reads 512 bytes to guess it and
	// then seeks back, which costs one extra request to the cloud.
	mime := it.mime
	if mime == "" {
		mime = "application/octet-stream"
	}
	w.Header().Set("Content-Type", mime)
	// No ModTime call (a possible extra round trip); zero time means no Last-Modified header.
	rd := &aheadReader{parent: r.Context(), obj: obj, size: obj.Size(), fc: cacheFor(it.fkey + "\x00" + strconv.FormatInt(obj.Size(), 10)), bg: r.URL.Query().Get("bg") == "1", serial: wrapsAnotherFs(it.f)}
	defer rd.close()
	http.ServeContent(w, r, "", time.Time{}, rd)
}

// chunk is one ranged download that fills buf.
type chunk struct {
	start int64
	want  int
	buf   []byte
	n     int
	err   error
	done  chan struct{}
}

// aheadReader lets http.ServeContent read any byte range of an rclone object.
// It downloads the next few chunks in parallel while the player is still
// consuming the current one. Nothing starts until data is really read, so
// the Seek calls ServeContent makes first cost nothing.
type aheadReader struct {
	parent context.Context
	obj    fs.Object
	fc     *fileCache // shared piece cache of this file; may be nil
	bg     bool       // background read (thumbnail, warm-up): limited to bgFetch parallel downloads
	serial bool       // wrapped remote (crypt...): one sequential stream per seek instead of many ranged chunks

	rc       io.ReadCloser      // sequential mode: the open stream (decrypted bytes from pos on)
	rcCancel context.CancelFunc // stops that stream
	rcTimer  *time.Timer        // fires when the stream delivers nothing for attemptTimeout
	size     int64
	pos      int64

	ctx    context.Context
	cancel context.CancelFunc
	sem    chan struct{}
	chunks []*chunk
	next   int64
	seq    int
}

func (o *aheadReader) start() {
	o.ctx, o.cancel = context.WithCancel(o.parent)
	o.sem = make(chan struct{}, parallelFetch)
	o.chunks = nil
	o.next = o.pos
	o.seq = 0
}

func (o *aheadReader) fill() {
	for len(o.chunks) < aheadChunks && o.next < o.size {
		sz := chunkSizeFor(o.seq)
		if o.next+sz > o.size {
			sz = o.size - o.next
		}
		c := &chunk{start: o.next, want: int(sz), done: make(chan struct{})}
		o.chunks = append(o.chunks, c)
		o.next += sz
		o.seq++
		go o.download(c, o.ctx, o.sem)
	}
}

func (o *aheadReader) download(c *chunk, ctx context.Context, sem chan struct{}) {
	defer close(c.done)
	// already read before (start/end of the file)? then no cloud request at all
	if o.fc != nil {
		if b := lookup(o.fc, c.start, c.want); b != nil {
			c.buf, c.n = b, c.want
			return
		}
	}
	select {
	case sem <- struct{}{}:
	case <-ctx.Done():
		c.err = ctx.Err()
		return
	}
	defer func() { <-sem }()
	if o.bg {
		select {
		case bgSem <- struct{}{}:
		case <-ctx.Done():
			c.err = ctx.Err()
			return
		}
		defer func() { <-bgSem }()
	}
	select {
	case fetchSem <- struct{}{}:
	case <-ctx.Done():
		c.err = ctx.Err()
		return
	}
	defer func() { <-fetchSem }()

	// memory is taken only when the download really starts
	want := c.want
	c.buf = make([]byte, want)
	var last error
	for attempt := 0; attempt < 3 && c.n < want; attempt++ {
		if err := ctx.Err(); err != nil {
			c.err = err
			return
		}
		if attempt > 0 {
			select {
			case <-time.After(time.Duration(attempt) * 400 * time.Millisecond):
			case <-ctx.Done():
				c.err = ctx.Err()
				return
			}
		}
		actx, acancel := context.WithTimeout(ctx, attemptTimeout)
		t0 := time.Now()
		rc, err := o.obj.Open(actx, &fs.RangeOption{Start: c.start + int64(c.n), End: c.start + int64(want) - 1})
		if err != nil {
			acancel()
			last = err
			if ctx.Err() == nil {
				diag("open bytes %d+%d failed after %s: %v", c.start+int64(c.n), want-c.n, time.Since(t0).Round(time.Millisecond), err)
			}
			continue
		}
		n, err := io.ReadFull(rc, c.buf[c.n:])
		_ = rc.Close()
		acancel()
		if d := time.Since(t0); ctx.Err() == nil && (d > 5*time.Second || (err != nil && c.n+n < want)) {
			diag("read bytes %d+%d: got %d in %s err=%v", c.start+int64(c.n), want-c.n, n, d.Round(time.Millisecond), err)
		}
		c.n += n
		if c.n >= want {
			store(o.fc, c.start, c.buf[:want], o.size)
			return
		}
		last = err
	}
	if c.n < want {
		if last == nil {
			last = io.ErrUnexpectedEOF
		}
		c.err = last
	}
}

// wrapsAnotherFs reports whether f is an overlay such as crypt. For these every ranged request costs
// extra cloud calls (crypt first reads the 32-byte file header, then the range), so many small parallel
// chunks multiply the cost and can stall behind the cloud's rate limit. One sequential stream per seek is
// what rclone itself does for such remotes.
func wrapsAnotherFs(f fs.Fs) bool {
	if f == nil {
		return false
	}
	ft := f.Features()
	return ft != nil && ft.UnWrap != nil
}

// readSeq serves Read with a single open stream starting at pos.
func (o *aheadReader) readSeq(p []byte) (int, error) {
	if o.pos >= o.size {
		return 0, io.EOF
	}
	if o.rc == nil {
		ctx, cancel := context.WithCancel(o.parent)
		timer := time.AfterFunc(attemptTimeout, cancel)
		t0 := time.Now()
		rc, err := o.obj.Open(ctx, &fs.RangeOption{Start: o.pos, End: -1})
		if err != nil {
			timer.Stop()
			cancel()
			if o.parent.Err() == nil {
				diag("open stream at %d failed after %s: %v", o.pos, time.Since(t0).Round(time.Millisecond), err)
			}
			return 0, err
		}
		if d := time.Since(t0); d > 3*time.Second {
			diag("open stream at %d took %s", o.pos, d.Round(time.Millisecond))
		}
		o.rc, o.rcCancel, o.rcTimer = rc, cancel, timer
	}
	n, err := o.rc.Read(p)
	o.rcTimer.Reset(attemptTimeout)
	o.pos += int64(n)
	if err == nil || n > 0 {
		// data first; a pending error shows up again on the next Read
		if err == io.EOF && o.pos < o.size {
			err = io.ErrUnexpectedEOF
		}
		if err != nil && err != io.EOF {
			o.closeStream()
			if o.parent.Err() == nil {
				diag("stream broke at %d: %v", o.pos, err)
			}
			return n, nil
		}
		return n, nil
	}
	o.closeStream()
	if err == io.EOF {
		if o.pos < o.size {
			err = io.ErrUnexpectedEOF
		} else {
			return 0, io.EOF
		}
	}
	if o.parent.Err() == nil {
		diag("stream failed at %d: %v", o.pos, err)
	}
	return 0, err
}

func (o *aheadReader) closeStream() {
	if o.rcTimer != nil {
		o.rcTimer.Stop()
	}
	if o.rcCancel != nil {
		o.rcCancel()
	}
	if o.rc != nil {
		_ = o.rc.Close()
	}
	o.rc, o.rcCancel, o.rcTimer = nil, nil, nil
}

func (o *aheadReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if o.serial {
		return o.readSeq(p)
	}
	for {
		if o.pos >= o.size {
			return 0, io.EOF
		}
		if o.ctx == nil {
			o.start()
		}
		o.fill()
		if len(o.chunks) == 0 {
			return 0, io.EOF
		}
		c := o.chunks[0]
		select {
		case <-c.done:
		case <-o.ctx.Done():
			return 0, o.ctx.Err()
		}
		off := int(o.pos - c.start)
		if off < 0 || off >= c.n {
			if c.err != nil {
				return 0, c.err
			}
			// chunk fully consumed (or out of step): drop it and move on
			o.chunks = o.chunks[1:]
			continue
		}
		n := copy(p, c.buf[off:c.n])
		o.pos += int64(n)
		if off+n >= c.n {
			if c.err != nil && c.n < c.want {
				// short chunk: keep it, so the next Read reports the failure after its data was delivered
				o.chunks = o.chunks[:1]
				return n, nil
			}
			o.chunks = o.chunks[1:]
		}
		return n, nil
	}
}

func (o *aheadReader) Seek(offset int64, whence int) (int64, error) {
	var next int64
	switch whence {
	case io.SeekStart:
		next = offset
	case io.SeekCurrent:
		next = o.pos + offset
	case io.SeekEnd:
		next = o.size + offset
	default:
		return 0, fmt.Errorf("dmstream: invalid whence %d", whence)
	}
	if next < 0 {
		return 0, errors.New("dmstream: negative position")
	}
	if next != o.pos {
		o.close()
		o.pos = next
	}
	return next, nil
}

// close stops all downloads and forgets the buffered data; the next Read starts afresh at pos.
func (o *aheadReader) close() {
	o.closeStream()
	if o.cancel != nil {
		o.cancel()
	}
	o.cancel = nil
	o.ctx = nil
	o.sem = nil
	o.chunks = nil
}
