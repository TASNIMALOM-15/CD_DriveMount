// Package terabox provides an interface to Terabox using the "ndus" login
// cookie of the user's own account.
//
// Terabox has no public API for personal accounts, so this talks to the same
// web API the website uses. The calls are modelled on the open source
// "terabox-api" library (MIT) and the TeraPlay sample app.
package terabox

import (
	"bytes"
	"context"
	"crypto/md5"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/rclone/rclone/fs"
	"github.com/rclone/rclone/fs/config/configmap"
	"github.com/rclone/rclone/fs/config/configstruct"
	"github.com/rclone/rclone/fs/config/obscure"
	"github.com/rclone/rclone/fs/fshttp"
	"github.com/rclone/rclone/fs/hash"
)

const (
	userAgent     = "terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox"
	defaultDomain = "terabox.com"
	appID         = "250528"
	chunkSize     = 4 * 1024 * 1024
	listPageSize  = 20000
	dummyBlock1   = "5910a591dd8fc18c32a8f3df4fdc1761"
	dummyBlock2   = "a5fc157d78e6ad1c7e114b056c92821e"
)

var (
	templateRe = regexp.MustCompile(`<script>var templateData = (.*);</script>`)
	tokenRe    = regexp.MustCompile(`%28%22(.*)%22%29`)
	tokenAlt   = regexp.MustCompile(`window.jsToken%20%3D%20a%7D%3Bfn%28%22(.*)%22%29`)

	sessionsMu sync.Mutex
	sessions   = map[string]*session{}
)

// Register with Fs
func init() {
	fs.Register(&fs.RegInfo{
		Name:        "terabox",
		Description: "Terabox (login with the ndus cookie)",
		NewFs:       NewFs,
		Options: []fs.Option{{
			Name:       "ndus",
			Help:       "The value of the \"ndus\" cookie of your logged in Terabox account.",
			Required:   true,
			IsPassword: true,
			Sensitive:  true,
		}, {
			Name:     "domain",
			Help:     "Terabox domain to use.",
			Default:  defaultDomain,
			Advanced: true,
		}},
	})
}

// Options defines the configuration for this backend
type Options struct {
	Ndus   string `config:"ndus"`
	Domain string `config:"domain"`
}

// flexInt decodes a JSON number that may also be quoted or fractional.
type flexInt int64

// UnmarshalJSON implements json.Unmarshaler
func (v *flexInt) UnmarshalJSON(b []byte) error {
	s := strings.Trim(strings.TrimSpace(string(b)), "\"")
	if s == "" || s == "null" {
		*v = 0
		return nil
	}
	if n, err := strconv.ParseInt(s, 10, 64); err == nil {
		*v = flexInt(n)
		return nil
	}
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return err
	}
	*v = flexInt(int64(f))
	return nil
}

type fileInfo struct {
	FsID  flexInt `json:"fs_id"`
	Path  string  `json:"path"`
	Name  string  `json:"server_filename"`
	Size  flexInt `json:"size"`
	IsDir flexInt `json:"isdir"`
	MTime flexInt `json:"server_mtime"`
}

func (i fileInfo) name() string {
	if i.Name != "" {
		return i.Name
	}
	return path.Base(i.Path)
}

type apiError struct {
	op    string
	errno int64
}

func (e *apiError) Error() string {
	return fmt.Sprintf("terabox: %s failed (errno %d)", e.op, e.errno)
}

func isNotFoundErrno(errno int64) bool {
	return errno == -9 || errno == 31066 || errno == 31079
}

func isExistsErrno(errno int64) bool {
	return errno == -8 || errno == 31061 || errno == 31039
}

// notFoundAs converts a "does not exist" API error into target
func notFoundAs(err error, target error) error {
	var ae *apiError
	if errors.As(err, &ae) && isNotFoundErrno(ae.errno) {
		return target
	}
	return err
}

func snippet(b []byte) string {
	s := strings.TrimSpace(string(b))
	if len(s) > 200 {
		s = s[:200] + "..."
	}
	return s
}

// ---------------------------------------------------------------------------
// session: login state shared by every Fs that uses the same account
// ---------------------------------------------------------------------------

type response struct {
	code   int
	header http.Header
	body   []byte
}

type session struct {
	domain     string
	client     *http.Client
	noRedirect *http.Client

	cmu     sync.Mutex
	cookies map[string]string

	initMu sync.Mutex // serialises login / token refresh

	mu       sync.Mutex // protects the fields below
	whost    string
	jsToken  string
	bdsToken string
	logID    string
	uhost    string
	ready    bool

	dirMu sync.Mutex
	dirs  map[string]bool
}

func newSession(ctx context.Context, domain, cookieInput string) *session {
	base := fshttp.NewClient(ctx)
	noRedir := *base
	noRedir.CheckRedirect = func(req *http.Request, via []*http.Request) error {
		return http.ErrUseLastResponse
	}
	s := &session{
		domain:     domain,
		client:     base,
		noRedirect: &noRedir,
		cookies:    map[string]string{"lang": "en"},
		whost:      "https://www." + domain,
		logID:      "0",
		dirs:       map[string]bool{},
	}
	c := strings.Trim(strings.TrimSpace(cookieInput), "\"'")
	if strings.Contains(c, "=") {
		for _, part := range strings.Split(c, ";") {
			i := strings.Index(part, "=")
			if i > 0 {
				s.cookies[strings.TrimSpace(part[:i])] = strings.TrimSpace(part[i+1:])
			}
		}
	} else if c != "" {
		s.cookies["ndus"] = c
	}
	return s
}

func (s *session) cookieHeader() string {
	s.cmu.Lock()
	defer s.cmu.Unlock()
	parts := make([]string, 0, len(s.cookies))
	for k, v := range s.cookies {
		parts = append(parts, k+"="+v)
	}
	return strings.Join(parts, "; ")
}

func (s *session) mergeCookies(h http.Header) {
	s.cmu.Lock()
	defer s.cmu.Unlock()
	for _, line := range h.Values("Set-Cookie") {
		first := strings.SplitN(line, ";", 2)[0]
		i := strings.Index(first, "=")
		if i <= 0 {
			continue
		}
		v := strings.TrimSpace(first[i+1:])
		if v != "" {
			s.cookies[strings.TrimSpace(first[:i])] = v
		}
	}
}

func (s *session) getHost() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.whost
}

func (s *session) setHost(h string) {
	s.mu.Lock()
	s.whost = h
	s.mu.Unlock()
}

func (s *session) setLogID(id string) {
	s.mu.Lock()
	s.logID = id
	s.mu.Unlock()
}

func (s *session) setTokens(js, bds string) {
	s.mu.Lock()
	s.jsToken = js
	s.bdsToken = bds
	s.mu.Unlock()
}

func (s *session) getJsToken() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.jsToken
}

func (s *session) isReady() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.ready
}

func (s *session) setReady() {
	s.mu.Lock()
	s.ready = true
	s.mu.Unlock()
}

func (s *session) knownDir(p string) bool {
	s.dirMu.Lock()
	defer s.dirMu.Unlock()
	return s.dirs[p]
}

func (s *session) rememberDir(p string) {
	s.dirMu.Lock()
	s.dirs[p] = true
	s.dirMu.Unlock()
}

// forgetDir forgets p and everything below it
func (s *session) forgetDir(p string) {
	s.dirMu.Lock()
	defer s.dirMu.Unlock()
	for k := range s.dirs {
		if k == p || strings.HasPrefix(k, p+"/") {
			delete(s.dirs, k)
		}
	}
}

// request performs one HTTP request with retries for network errors and 5xx.
func (s *session) request(ctx context.Context, method, rawURL, body string, headers map[string]string, noRedirect bool) (*response, error) {
	var lastErr error
	for attempt := 0; attempt < 4; attempt++ {
		if attempt > 0 {
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(time.Duration(attempt) * 700 * time.Millisecond):
			}
		}
		var reader io.Reader
		if body != "" {
			reader = strings.NewReader(body)
		}
		req, err := http.NewRequestWithContext(ctx, method, rawURL, reader)
		if err != nil {
			return nil, err
		}
		req.Header.Set("User-Agent", userAgent)
		req.Header.Set("Cookie", s.cookieHeader())
		if body != "" {
			req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		}
		for k, v := range headers {
			req.Header.Set(k, v)
		}
		client := s.client
		if noRedirect {
			client = s.noRedirect
		}
		resp, err := client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		data, err := io.ReadAll(resp.Body)
		_ = resp.Body.Close()
		if err != nil {
			lastErr = err
			continue
		}
		if resp.StatusCode >= 500 {
			lastErr = fmt.Errorf("terabox: HTTP %d: %s", resp.StatusCode, snippet(data))
			continue
		}
		return &response{code: resp.StatusCode, header: resp.Header, body: data}, nil
	}
	return nil, lastErr
}

// apiJSON calls an endpoint on the current host and decodes the JSON reply.
func (s *session) apiJSON(ctx context.Context, method, endpoint string, query, form url.Values, out any) error {
	target := s.getHost() + endpoint
	if len(query) > 0 {
		target += "?" + query.Encode()
	}
	body := ""
	if form != nil {
		body = strings.ReplaceAll(form.Encode(), "+", "%20")
	}
	resp, err := s.request(ctx, method, target, body, nil, false)
	if err != nil {
		return err
	}
	if resp.code != http.StatusOK {
		return fmt.Errorf("terabox: %s: HTTP %d: %s", endpoint, resp.code, snippet(resp.body))
	}
	if err := json.Unmarshal(resp.body, out); err != nil {
		return fmt.Errorf("terabox: %s: unexpected reply: %s", endpoint, snippet(resp.body))
	}
	return nil
}

func (s *session) appQuery() url.Values {
	q := url.Values{}
	q.Set("app_id", appID)
	q.Set("web", "1")
	q.Set("channel", "dubox")
	q.Set("clienttype", "0")
	q.Set("jsToken", s.getJsToken())
	return q
}

// checkLogin verifies the ndus cookie and follows the regional host.
func (s *session) checkLogin(ctx context.Context) error {
	for attempt := 0; attempt < 2; attempt++ {
		resp, err := s.request(ctx, "GET", s.getHost()+"/api/check/login", "", nil, false)
		if err != nil {
			return err
		}
		if region := resp.header.Get("region-domain-prefix"); region != "" && attempt == 0 {
			s.setHost("https://" + region + "." + s.domain)
			continue
		}
		var out struct {
			Errno flexInt `json:"errno"`
		}
		if err := json.Unmarshal(resp.body, &out); err != nil {
			return fmt.Errorf("terabox: login check returned an unexpected reply: %s", snippet(resp.body))
		}
		if out.Errno != 0 {
			return fmt.Errorf("terabox: not logged in (errno %d) - the ndus token is wrong or expired", int64(out.Errno))
		}
		return nil
	}
	return errors.New("terabox: login check failed")
}

// updateAppData reads the page tokens (jsToken, bdstoken) from /main.
func (s *session) updateAppData(ctx context.Context) error {
	target := s.getHost() + "/main"
	finalURL := target
	var body []byte
	for i := 0; i < 6; i++ {
		resp, err := s.request(ctx, "GET", target, "", nil, true)
		if err != nil {
			return err
		}
		s.mergeCookies(resp.header)
		if id := resp.header.Get("logid"); id != "" {
			s.setLogID(id)
		}
		finalURL = target
		if resp.code == 301 || resp.code == 302 || resp.code == 303 || resp.code == 307 || resp.code == 308 {
			loc := resp.header.Get("Location")
			if loc == "" {
				break
			}
			base, err := url.Parse(target)
			if err != nil {
				break
			}
			ref, err := url.Parse(loc)
			if err != nil {
				break
			}
			next := base.ResolveReference(ref)
			s.setHost(next.Scheme + "://" + next.Host)
			target = next.String()
			finalURL = target
			continue
		}
		body = resp.body
		break
	}
	if strings.Contains(finalURL, "/login") {
		return errors.New("terabox: login required - the ndus token is wrong or expired")
	}

	var td struct {
		JsToken  string `json:"jsToken"`
		BdsToken string `json:"bdstoken"`
	}
	if m := templateRe.FindSubmatch(body); m != nil {
		raw := strings.Split(string(m[1]), ";</script>")[0]
		_ = json.Unmarshal([]byte(raw), &td)
	}
	js := ""
	if td.JsToken != "" {
		if m := tokenRe.FindStringSubmatch(td.JsToken); m != nil {
			js = m[1]
		}
	} else if m := tokenAlt.FindSubmatch(body); m != nil {
		js = string(m[1])
	}
	if js == "" {
		return errors.New("terabox: could not read the session token (the ndus token may be wrong, or Terabox changed its page)")
	}
	s.setTokens(js, td.BdsToken)
	return nil
}

func (s *session) ensure(ctx context.Context) error {
	s.initMu.Lock()
	defer s.initMu.Unlock()
	if s.isReady() {
		return nil
	}
	if err := s.checkLogin(ctx); err != nil {
		return err
	}
	if err := s.updateAppData(ctx); err != nil {
		return err
	}
	s.setReady()
	return nil
}

func (s *session) refreshTokens(ctx context.Context) error {
	s.initMu.Lock()
	defer s.initMu.Unlock()
	return s.updateAppData(ctx)
}

// filemanager runs delete / rename / move / copy.
func (s *session) filemanager(ctx context.Context, opera string, list any) error {
	payload, err := json.Marshal(list)
	if err != nil {
		return err
	}
	for attempt := 0; attempt < 2; attempt++ {
		q := s.appQuery()
		q.Set("onnest", "fail")
		q.Set("opera", opera)
		form := url.Values{}
		form.Set("filelist", string(payload))
		var r struct {
			Errno flexInt `json:"errno"`
		}
		if err := s.apiJSON(ctx, "POST", "/api/filemanager", q, form, &r); err != nil {
			return err
		}
		if int64(r.Errno) == 450016 && attempt == 0 {
			if err := s.refreshTokens(ctx); err != nil {
				return err
			}
			continue
		}
		if r.Errno != 0 {
			return &apiError{op: opera, errno: int64(r.Errno)}
		}
		return nil
	}
	return errors.New("terabox: " + opera + " failed")
}

func (s *session) precreate(ctx context.Context, absPath string, size int64, blocks []string) (string, error) {
	blockJSON, err := json.Marshal(blocks)
	if err != nil {
		return "", err
	}
	for attempt := 0; attempt < 2; attempt++ {
		form := url.Values{}
		form.Set("path", absPath)
		form.Set("autoinit", "1")
		form.Set("size", strconv.FormatInt(size, 10))
		form.Set("file_limit_switch_v34", "true")
		form.Set("block_list", string(blockJSON))
		form.Set("rtype", "2")
		var r struct {
			Errno    flexInt `json:"errno"`
			UploadID string  `json:"uploadid"`
		}
		if err := s.apiJSON(ctx, "POST", "/api/precreate", s.appQuery(), form, &r); err != nil {
			return "", err
		}
		if int64(r.Errno) == 4000023 && attempt == 0 {
			if err := s.refreshTokens(ctx); err != nil {
				return "", err
			}
			continue
		}
		if r.Errno != 0 {
			return "", fmt.Errorf("terabox: could not start the upload (errno %d)", int64(r.Errno))
		}
		if r.UploadID == "" {
			return "", errors.New("terabox: the upload session was not created")
		}
		return r.UploadID, nil
	}
	return "", errors.New("terabox: could not start the upload")
}

func (s *session) uploadHost(ctx context.Context) string {
	s.mu.Lock()
	h := s.uhost
	s.mu.Unlock()
	if h != "" {
		return h
	}
	var r struct {
		Errno flexInt `json:"errno"`
		Host  string  `json:"host"`
	}
	q := url.Values{}
	q.Set("method", "locateupload")
	err := s.apiJSON(ctx, "GET", "/rest/2.0/pcs/file", q, nil, &r)
	host := "https://c-all." + s.domain
	if err == nil && r.Errno == 0 && r.Host != "" {
		host = "https://" + r.Host
	}
	s.mu.Lock()
	s.uhost = host
	s.mu.Unlock()
	return host
}

func (s *session) uploadChunk(ctx context.Context, uhost, absPath, uploadID string, part int, data []byte) (string, error) {
	q := url.Values{}
	q.Set("method", "upload")
	q.Set("app_id", appID)
	q.Set("web", "1")
	q.Set("channel", "dubox")
	q.Set("clienttype", "0")
	q.Set("path", absPath)
	q.Set("uploadid", uploadID)
	q.Set("partseq", strconv.Itoa(part))
	target := uhost + "/rest/2.0/pcs/superfile2?" + q.Encode()

	sum := md5.Sum(data)
	localMD5 := hex.EncodeToString(sum[:])

	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		var buf bytes.Buffer
		mw := multipart.NewWriter(&buf)
		fw, err := mw.CreateFormFile("file", "blob")
		if err != nil {
			return "", err
		}
		if _, err := fw.Write(data); err != nil {
			return "", err
		}
		if err := mw.Close(); err != nil {
			return "", err
		}
		resp, err := s.request(ctx, "POST", target, buf.String(), map[string]string{"Content-Type": mw.FormDataContentType()}, false)
		if err != nil {
			lastErr = err
			continue
		}
		var r struct {
			MD5       string  `json:"md5"`
			ErrorCode flexInt `json:"error_code"`
		}
		if resp.code != http.StatusOK || json.Unmarshal(resp.body, &r) != nil {
			lastErr = fmt.Errorf("terabox: uploading part %d failed: HTTP %d %s", part, resp.code, snippet(resp.body))
			continue
		}
		if r.ErrorCode != 0 {
			lastErr = fmt.Errorf("terabox: uploading part %d failed (error %d)", part, int64(r.ErrorCode))
			continue
		}
		if r.MD5 != "" {
			return r.MD5, nil
		}
		return localMD5, nil
	}
	return "", lastErr
}

func (s *session) createFile(ctx context.Context, absPath string, size int64, uploadID string, md5s []string) (int64, error) {
	blockJSON, err := json.Marshal(md5s)
	if err != nil {
		return 0, err
	}
	form := url.Values{}
	form.Set("path", absPath)
	form.Set("size", strconv.FormatInt(size, 10))
	form.Set("isdir", "0")
	form.Set("block_list", string(blockJSON))
	form.Set("uploadid", uploadID)
	form.Set("rtype", "2")
	var r struct {
		Errno flexInt `json:"errno"`
		FsID  flexInt `json:"fs_id"`
	}
	if err := s.apiJSON(ctx, "POST", "/api/create", nil, form, &r); err != nil {
		return 0, err
	}
	if r.Errno != 0 {
		return 0, fmt.Errorf("terabox: could not finish the upload (errno %d)", int64(r.Errno))
	}
	return int64(r.FsID), nil
}

// downloadLink returns a temporary direct download URL for a file.
func (s *session) downloadLink(ctx context.Context, absPath string, fsID int64) (string, error) {
	target, err := json.Marshal([]string{absPath})
	if err != nil {
		return "", err
	}
	form := url.Values{}
	form.Set("dlink", "1")
	form.Set("origin", "dlna")
	form.Set("target", string(target))
	var meta struct {
		Errno flexInt `json:"errno"`
		Info  []struct {
			Dlink string `json:"dlink"`
		} `json:"info"`
	}
	if err := s.apiJSON(ctx, "POST", "/api/filemetas", nil, form, &meta); err == nil && meta.Errno == 0 && len(meta.Info) > 0 && meta.Info[0].Dlink != "" {
		return meta.Info[0].Dlink, nil
	}
	return s.signedDownloadLink(ctx, fsID)
}

func (s *session) signedDownloadLink(ctx context.Context, fsID int64) (string, error) {
	var home struct {
		Errno flexInt `json:"errno"`
		Data  struct {
			Sign1     string  `json:"sign1"`
			Sign3     string  `json:"sign3"`
			Timestamp flexInt `json:"timestamp"`
		} `json:"data"`
	}
	if err := s.apiJSON(ctx, "GET", "/api/home/info", nil, nil, &home); err != nil {
		return "", err
	}
	if home.Errno != 0 || home.Data.Sign1 == "" {
		return "", fmt.Errorf("terabox: could not prepare the download (errno %d)", int64(home.Errno))
	}
	form := url.Values{}
	form.Set("fidlist", "["+strconv.FormatInt(fsID, 10)+"]")
	form.Set("type", "dlink")
	form.Set("vip", "2")
	form.Set("sign", signDownload(home.Data.Sign3, home.Data.Sign1))
	form.Set("timestamp", strconv.FormatInt(int64(home.Data.Timestamp), 10))
	form.Set("need_speed", "1")
	var r struct {
		Errno flexInt         `json:"errno"`
		Dlink json.RawMessage `json:"dlink"`
	}
	if err := s.apiJSON(ctx, "POST", "/api/download", nil, form, &r); err != nil {
		return "", err
	}
	if r.Errno != 0 {
		return "", fmt.Errorf("terabox: the download link request failed (errno %d)", int64(r.Errno))
	}
	var asString string
	if json.Unmarshal(r.Dlink, &asString) == nil && asString != "" {
		return asString, nil
	}
	var asList []struct {
		Dlink string `json:"dlink"`
	}
	if json.Unmarshal(r.Dlink, &asList) == nil && len(asList) > 0 && asList[0].Dlink != "" {
		return asList[0].Dlink, nil
	}
	return "", errors.New("terabox: no download link was returned")
}

// signDownload is a port of the website's download signing (RC4 style).
func signDownload(key, data string) string {
	if len(key) == 0 {
		return ""
	}
	var a, p [256]int
	for i := 0; i < 256; i++ {
		p[i] = int(key[i%len(key)])
		a[i] = i
	}
	o := 0
	for i := 0; i < 256; i++ {
		o = (o + a[i] + p[i]) % 256
		a[i], a[o] = a[o], a[i]
	}
	n := 0
	o = 0
	out := make([]byte, 0, len(data))
	for i := 0; i < len(data); i++ {
		n = (n + 1) % 256
		o = (o + a[n]) % 256
		a[n], a[o] = a[o], a[n]
		k := a[(a[n]+a[o])%256]
		out = append(out, byte(int(data[i])^k))
	}
	return base64.StdEncoding.EncodeToString(out)
}

// ---------------------------------------------------------------------------
// Fs
// ---------------------------------------------------------------------------

// Fs represents a remote Terabox
type Fs struct {
	name     string
	root     string // no leading or trailing slash
	opt      Options
	s        *session
	features *fs.Features
}

// NewFs constructs an Fs from the path
func NewFs(ctx context.Context, name, root string, m configmap.Mapper) (fs.Fs, error) {
	opt := new(Options)
	if err := configstruct.Set(m, opt); err != nil {
		return nil, err
	}
	if opt.Ndus == "" {
		return nil, errors.New("terabox: the ndus token is required")
	}
	ndus, err := obscure.Reveal(opt.Ndus)
	if err != nil {
		return nil, fmt.Errorf("terabox: couldn't decrypt the ndus token: %w", err)
	}
	domain := strings.TrimSpace(opt.Domain)
	if domain == "" {
		domain = defaultDomain
	}

	key := domain + "|" + ndus
	sessionsMu.Lock()
	s := sessions[key]
	if s == nil {
		s = newSession(ctx, domain, ndus)
		sessions[key] = s
	}
	sessionsMu.Unlock()

	f := &Fs{
		name: name,
		root: strings.Trim(root, "/"),
		opt:  *opt,
		s:    s,
	}
	f.features = (&fs.Features{
		CanHaveEmptyDirectories: true,
	}).Fill(ctx, f)
	return f, nil
}

// Name of the remote
func (f *Fs) Name() string { return f.name }

// Root of the remote
func (f *Fs) Root() string { return f.root }

// String converts this Fs to a string
func (f *Fs) String() string { return fmt.Sprintf("Terabox root '%s'", f.root) }

// Features returns the optional features of this Fs
func (f *Fs) Features() *fs.Features { return f.features }

// Precision: Terabox cannot set modification times
func (f *Fs) Precision() time.Duration { return fs.ModTimeNotSupported }

// Hashes returns the supported hash sets (none)
func (f *Fs) Hashes() hash.Set { return hash.Set(hash.None) }

// abs turns a path relative to the root into an absolute Terabox path
func (f *Fs) abs(remote string) string {
	return "/" + strings.Trim(path.Join(f.root, remote), "/")
}

func (f *Fs) newObject(remote string, info fileInfo) *Object {
	return &Object{
		fs:      f,
		remote:  remote,
		size:    int64(info.Size),
		modTime: time.Unix(int64(info.MTime), 0),
		fsID:    int64(info.FsID),
	}
}

// listAbs lists a directory by absolute path
func (f *Fs) listAbs(ctx context.Context, dir string) ([]fileInfo, error) {
	if err := f.s.ensure(ctx); err != nil {
		return nil, err
	}
	var all []fileInfo
	for page := 1; page <= 1000; page++ {
		form := url.Values{}
		form.Set("order", "name")
		form.Set("desc", "0")
		form.Set("dir", dir)
		form.Set("num", strconv.Itoa(listPageSize))
		form.Set("page", strconv.Itoa(page))
		form.Set("showempty", "0")
		var r struct {
			Errno flexInt    `json:"errno"`
			List  []fileInfo `json:"list"`
		}
		if err := f.s.apiJSON(ctx, "POST", "/api/list", nil, form, &r); err != nil {
			return nil, err
		}
		if r.Errno != 0 {
			if isNotFoundErrno(int64(r.Errno)) {
				return nil, fs.ErrorDirNotFound
			}
			return nil, &apiError{op: "list", errno: int64(r.Errno)}
		}
		all = append(all, r.List...)
		if len(r.List) < listPageSize {
			break
		}
	}
	return all, nil
}

// List the objects and directories in dir
func (f *Fs) List(ctx context.Context, dir string) (fs.DirEntries, error) {
	items, err := f.listAbs(ctx, f.abs(dir))
	if err != nil {
		return nil, err
	}
	entries := make(fs.DirEntries, 0, len(items))
	for _, it := range items {
		name := it.name()
		if name == "" {
			continue
		}
		remote := path.Join(dir, name)
		if int64(it.IsDir) == 1 {
			entries = append(entries, fs.NewDir(remote, time.Unix(int64(it.MTime), 0)))
		} else {
			entries = append(entries, f.newObject(remote, it))
		}
	}
	return entries, nil
}

// NewObject finds the Object at remote
func (f *Fs) NewObject(ctx context.Context, remote string) (fs.Object, error) {
	absPath := f.abs(remote)
	items, err := f.listAbs(ctx, path.Dir(absPath))
	if err != nil {
		if errors.Is(err, fs.ErrorDirNotFound) {
			return nil, fs.ErrorObjectNotFound
		}
		return nil, err
	}
	leaf := path.Base(absPath)
	for _, it := range items {
		if it.name() == leaf {
			if int64(it.IsDir) == 1 {
				return nil, fs.ErrorIsDir
			}
			return f.newObject(remote, it), nil
		}
	}
	return nil, fs.ErrorObjectNotFound
}

// mkdirAll creates a directory and its parents
func (f *Fs) mkdirAll(ctx context.Context, absDir string) error {
	absDir = path.Clean("/" + strings.Trim(absDir, "/"))
	if absDir == "/" || f.s.knownDir(absDir) {
		return nil
	}
	if err := f.mkdirAll(ctx, path.Dir(absDir)); err != nil {
		return err
	}
	items, err := f.listAbs(ctx, path.Dir(absDir))
	if err == nil {
		leaf := path.Base(absDir)
		for _, it := range items {
			if it.name() == leaf && int64(it.IsDir) == 1 {
				f.s.rememberDir(absDir)
				return nil
			}
		}
	}
	form := url.Values{}
	form.Set("path", absDir)
	form.Set("isdir", "1")
	form.Set("block_list", "[]")
	q := url.Values{}
	q.Set("a", "commit")
	var r struct {
		Errno flexInt `json:"errno"`
	}
	if err := f.s.apiJSON(ctx, "POST", "/api/create", q, form, &r); err != nil {
		return err
	}
	if r.Errno != 0 && !isExistsErrno(int64(r.Errno)) {
		return fmt.Errorf("terabox: could not create the folder %q (errno %d)", absDir, int64(r.Errno))
	}
	f.s.rememberDir(absDir)
	return nil
}

// Mkdir makes the directory
func (f *Fs) Mkdir(ctx context.Context, dir string) error {
	if err := f.s.ensure(ctx); err != nil {
		return err
	}
	return f.mkdirAll(ctx, f.abs(dir))
}

// Rmdir removes an empty directory
func (f *Fs) Rmdir(ctx context.Context, dir string) error {
	absDir := f.abs(dir)
	if absDir == "/" {
		return errors.New("terabox: cannot remove the root folder")
	}
	items, err := f.listAbs(ctx, absDir)
	if err != nil {
		return err
	}
	if len(items) > 0 {
		return fs.ErrorDirectoryNotEmpty
	}
	if err := f.s.filemanager(ctx, "delete", []string{absDir}); err != nil {
		return notFoundAs(err, fs.ErrorDirNotFound)
	}
	f.s.forgetDir(absDir)
	return nil
}

// Purge deletes a directory with everything inside
func (f *Fs) Purge(ctx context.Context, dir string) error {
	absDir := f.abs(dir)
	if absDir == "/" {
		return fs.ErrorCantPurge
	}
	if err := f.s.ensure(ctx); err != nil {
		return err
	}
	if err := f.s.filemanager(ctx, "delete", []string{absDir}); err != nil {
		return notFoundAs(err, fs.ErrorDirNotFound)
	}
	f.s.forgetDir(absDir)
	return nil
}

// Put uploads a file
func (f *Fs) Put(ctx context.Context, in io.Reader, src fs.ObjectInfo, options ...fs.OpenOption) (fs.Object, error) {
	o := &Object{fs: f, remote: src.Remote()}
	if err := o.Update(ctx, in, src, options...); err != nil {
		return nil, err
	}
	return o, nil
}

func (f *Fs) moveAbs(ctx context.Context, srcAbs, dstAbs string) error {
	if err := f.s.ensure(ctx); err != nil {
		return err
	}
	dstDir := path.Dir(dstAbs)
	dstName := path.Base(dstAbs)
	if err := f.mkdirAll(ctx, dstDir); err != nil {
		return err
	}
	op := "move"
	item := map[string]string{"path": srcAbs, "dest": dstDir, "newname": dstName}
	if path.Dir(srcAbs) == dstDir {
		op = "rename"
		item = map[string]string{"path": srcAbs, "newname": dstName}
	}
	if err := f.s.filemanager(ctx, op, []map[string]string{item}); err != nil {
		return err
	}
	f.s.forgetDir(srcAbs)
	return nil
}

// Move src to this remote using server-side move operations
func (f *Fs) Move(ctx context.Context, src fs.Object, remote string) (fs.Object, error) {
	srcObj, ok := src.(*Object)
	if !ok || srcObj.fs.s != f.s {
		return nil, fs.ErrorCantMove
	}
	if err := f.moveAbs(ctx, srcObj.fs.abs(srcObj.remote), f.abs(remote)); err != nil {
		return nil, notFoundAs(err, fs.ErrorObjectNotFound)
	}
	return &Object{fs: f, remote: remote, size: srcObj.size, modTime: srcObj.modTime, fsID: srcObj.fsID}, nil
}

// DirMove moves a directory using server-side move operations
func (f *Fs) DirMove(ctx context.Context, src fs.Fs, srcRemote, dstRemote string) error {
	srcFs, ok := src.(*Fs)
	if !ok || srcFs.s != f.s {
		return fs.ErrorCantDirMove
	}
	srcAbs := srcFs.abs(srcRemote)
	dstAbs := f.abs(dstRemote)
	if srcAbs == "/" || dstAbs == "/" {
		return fs.ErrorCantDirMove
	}
	items, err := f.listAbs(ctx, path.Dir(dstAbs))
	if err == nil {
		leaf := path.Base(dstAbs)
		for _, it := range items {
			if it.name() == leaf {
				return fs.ErrorDirExists
			}
		}
	}
	if err := f.moveAbs(ctx, srcAbs, dstAbs); err != nil {
		return notFoundAs(err, fs.ErrorDirNotFound)
	}
	return nil
}

// About gets quota information
func (f *Fs) About(ctx context.Context) (*fs.Usage, error) {
	if err := f.s.ensure(ctx); err != nil {
		return nil, err
	}
	q := url.Values{}
	q.Set("checkexpire", "1")
	q.Set("checkfree", "1")
	var r struct {
		Errno flexInt `json:"errno"`
		Total flexInt `json:"total"`
		Used  flexInt `json:"used"`
	}
	if err := f.s.apiJSON(ctx, "GET", "/api/quota", q, nil, &r); err != nil {
		return nil, err
	}
	if r.Errno != 0 {
		return nil, &apiError{op: "quota", errno: int64(r.Errno)}
	}
	total := int64(r.Total)
	used := int64(r.Used)
	free := total - used
	return &fs.Usage{Total: &total, Used: &used, Free: &free}, nil
}

// ---------------------------------------------------------------------------
// Object
// ---------------------------------------------------------------------------

// Object describes a Terabox file
type Object struct {
	fs      *Fs
	remote  string
	size    int64
	modTime time.Time
	fsID    int64
}

// Fs returns the parent Fs
func (o *Object) Fs() fs.Info { return o.fs }

// String returns a description of the Object
func (o *Object) String() string {
	if o == nil {
		return "<nil>"
	}
	return o.remote
}

// Remote returns the remote path
func (o *Object) Remote() string { return o.remote }

// Hash returns no hash: Terabox does not expose a usable one
func (o *Object) Hash(ctx context.Context, t hash.Type) (string, error) {
	return "", hash.ErrUnsupported
}

// Size returns the size of the file
func (o *Object) Size() int64 { return o.size }

// ModTime returns the modification time
func (o *Object) ModTime(ctx context.Context) time.Time { return o.modTime }

// SetModTime is not supported
func (o *Object) SetModTime(ctx context.Context, modTime time.Time) error {
	return fs.ErrorCantSetModTime
}

// Storable returns whether the object is storable
func (o *Object) Storable() bool { return true }

// Open opens the file for read
func (o *Object) Open(ctx context.Context, options ...fs.OpenOption) (io.ReadCloser, error) {
	s := o.fs.s
	if err := s.ensure(ctx); err != nil {
		return nil, err
	}
	absPath := o.fs.abs(o.remote)
	var lastErr error
	for attempt := 0; attempt < 2; attempt++ {
		link, err := s.downloadLink(ctx, absPath, o.fsID)
		if err != nil {
			return nil, err
		}
		req, err := http.NewRequestWithContext(ctx, "GET", link, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("User-Agent", userAgent)
		req.Header.Set("Cookie", s.cookieHeader())
		for k, v := range fs.OpenOptionHeaders(options) {
			req.Header.Set(k, v)
		}
		resp, err := s.client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		if resp.StatusCode == http.StatusOK || resp.StatusCode == http.StatusPartialContent {
			return resp.Body, nil
		}
		msg, _ := io.ReadAll(io.LimitReader(resp.Body, 300))
		_ = resp.Body.Close()
		lastErr = fmt.Errorf("terabox: download failed: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(msg)))
	}
	return nil, lastErr
}

// Update uploads new content for the object
func (o *Object) Update(ctx context.Context, in io.Reader, src fs.ObjectInfo, options ...fs.OpenOption) error {
	f := o.fs
	s := f.s
	if err := s.ensure(ctx); err != nil {
		return err
	}

	size := src.Size()
	if size < 0 {
		// The size must be known before the upload starts, so spool to a temp file
		tmp, err := os.CreateTemp("", "terabox-upload-")
		if err != nil {
			return err
		}
		defer func() {
			_ = tmp.Close()
			_ = os.Remove(tmp.Name())
		}()
		n, err := io.Copy(tmp, in)
		if err != nil {
			return err
		}
		if _, err := tmp.Seek(0, io.SeekStart); err != nil {
			return err
		}
		size = n
		in = tmp
	}

	absPath := f.abs(o.remote)
	if err := f.mkdirAll(ctx, path.Dir(absPath)); err != nil {
		return err
	}

	blocks := []string{dummyBlock1}
	if size > chunkSize {
		blocks = append(blocks, dummyBlock2)
	}
	uploadID, err := s.precreate(ctx, absPath, size, blocks)
	if err != nil {
		return err
	}
	uhost := s.uploadHost(ctx)

	var md5s []string
	buf := make([]byte, chunkSize)
	for part := 0; ; part++ {
		n, rerr := io.ReadFull(in, buf)
		if rerr != nil && rerr != io.EOF && rerr != io.ErrUnexpectedEOF {
			return rerr
		}
		if n == 0 && part > 0 {
			break
		}
		sum, err := s.uploadChunk(ctx, uhost, absPath, uploadID, part, buf[:n])
		if err != nil {
			return err
		}
		md5s = append(md5s, sum)
		if rerr == io.EOF || rerr == io.ErrUnexpectedEOF {
			break
		}
	}

	fsID, err := s.createFile(ctx, absPath, size, uploadID, md5s)
	if err != nil {
		return err
	}
	o.size = size
	o.modTime = time.Now()
	o.fsID = fsID
	return nil
}

// Remove deletes the file
func (o *Object) Remove(ctx context.Context) error {
	if err := o.fs.s.ensure(ctx); err != nil {
		return err
	}
	if err := o.fs.s.filemanager(ctx, "delete", []string{o.fs.abs(o.remote)}); err != nil {
		return notFoundAs(err, fs.ErrorObjectNotFound)
	}
	return nil
}
