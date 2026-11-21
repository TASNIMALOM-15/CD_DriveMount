#!/usr/bin/env python3
"""পূর্ণ rclone সোর্স থেকে শুধু ব্যবহৃত Go প্যাকেজ রেখে ছাঁটা engine/rclone-master.zip বানায়।
ব্যবহার (প্রজেক্ট root থেকে):
  python3 tools/prune_rclone.py /path/to/rclone-master  engine/rclone-master.zip
শুরুর বিন্দু: librclone/gomobile, librclone/dmstream, backend/terabox (+ engine/ এর Go ফাইলের import)।
Build tag উপেক্ষা করে সব import অনুসরণ হয় (নিরাপদ দিক)। _test.go, docs, cmd, testdata বাদ।"""
import os, re, sys, shutil, glob, tempfile, zipfile
src, out = sys.argv[1], sys.argv[2]
here = os.path.dirname(os.path.abspath(__file__))
extra = [os.path.join(here, "..", "engine", p) for p in ("terabox/terabox.go", "dmstream/stream.go")]
MOD = "github.com/rclone/rclone/"
imp_re = re.compile(r'^\s*(?:[A-Za-z_.]+\s+)?"([^"]+)"', re.M)
def imports(p):
    s = open(p, encoding="utf-8", errors="ignore").read()
    o = set()
    for b in re.findall(r'^import\s*\((.*?)^\)', s, re.S | re.M): o |= set(imp_re.findall(b))
    return o | set(re.findall(r'^import\s+(?:[A-Za-z_.]+\s+)?"([^"]+)"', s, re.M))
def gofiles(d): return [os.path.join(d, f) for f in os.listdir(d) if f.endswith(".go") and not f.endswith("_test.go")]
seen, stack = set(), []
def add(pkg):
    if pkg.startswith(MOD):
        r = pkg[len(MOD):]
        if r not in seen and os.path.isdir(os.path.join(src, r)): seen.add(r); stack.append(r)
for e in extra:
    if os.path.exists(e):
        for i in imports(e): add(i)
for p in ("librclone/gomobile", "backend/terabox"):
    if os.path.isdir(os.path.join(src, p)): seen.add(p); stack.append(p)
add(MOD + "fs/sync")
while stack:
    d = stack.pop()
    for f in gofiles(os.path.join(src, d)):
        for i in imports(f): add(i)
tmp = tempfile.mkdtemp(); dst = os.path.join(tmp, "rclone-master"); os.makedirs(dst)
for f in ("go.mod", "go.sum", "COPYING", "VERSION"):
    if os.path.exists(os.path.join(src, f)): shutil.copy(os.path.join(src, f), dst)
embeds = []
for d in sorted(seen):
    os.makedirs(os.path.join(dst, d), exist_ok=True)
    for f in os.listdir(os.path.join(src, d)):
        p = os.path.join(src, d, f)
        if os.path.isfile(p) and not f.endswith("_test.go"):
            if d == "docs/data/backends" and not (f.endswith(".yaml") or f == "embed.go"): continue
            shutil.copy(p, os.path.join(dst, d, f))
    for f in gofiles(os.path.join(src, d)):
        for m in re.findall(r'//go:embed\s+(.+)', open(f, encoding="utf-8", errors="ignore").read()):
            embeds += [(d, x.replace("all:", "")) for x in m.split()]
for d, pat in embeds:
    for m in glob.glob(os.path.join(src, d, pat)):
        t = m.replace(src, dst, 1)
        if os.path.isdir(m): shutil.copytree(m, t, dirs_exist_ok=True, ignore=shutil.ignore_patterns("*_test.go"))
        else: os.makedirs(os.path.dirname(t), exist_ok=True); shutil.copy(m, t)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for dp, _, fs in os.walk(dst):
        for f in sorted(fs):
            p = os.path.join(dp, f); z.write(p, os.path.relpath(p, tmp))
print("packages kept:", len(seen), "->", out, os.path.getsize(out) // 1024, "KB")
