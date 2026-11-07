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
package dmstream

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
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
	remote  string
	mime    string
	created time.Time

	mu  sync.Mutex
	obj fs.Object
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

	mu.Lock()
	for k, v := range items {
		if time.Since(v.created) > 6*time.Hour {
			delete(items, k)
		}
	}
	items[token] = it
	mu.Unlock()

	// look the file up now, while the player is still getting ready
	go func() { _, _ = it.object(context.Background()) }()
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
	obj, err := it.object(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
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
	rd := &aheadReader{parent: r.Context(), obj: obj, size: obj.Size()}
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
	size   int64
	pos    int64

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
	select {
	case sem <- struct{}{}:
	case <-ctx.Done():
		c.err = ctx.Err()
		return
	}
	defer func() { <-sem }()
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
		rc, err := o.obj.Open(ctx, &fs.RangeOption{Start: c.start + int64(c.n), End: c.start + int64(want) - 1})
		if err != nil {
			last = err
			continue
		}
		n, err := io.ReadFull(rc, c.buf[c.n:])
		_ = rc.Close()
		c.n += n
		if c.n >= want {
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

func (o *aheadReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
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
	if o.cancel != nil {
		o.cancel()
	}
	o.cancel = nil
	o.ctx = nil
	o.sem = nil
	o.chunks = nil
}
