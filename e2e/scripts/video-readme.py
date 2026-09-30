#!/usr/bin/env python3
"""Polish round videos: gather run-video.sh output into shots/polish/<sha>/<platform>/ and write its README.

usage: video-readme.py <dest dir> [--vs <other dest dir>]   (dest/runs/video-<sha>-<ts>/ = the mini's output dirs, rsynced)
Hardlinks each video into <dest>/ (runs/ stays intact, since the next rsync would put moved files back). Per file, the
latest take with flow rc=0 wins, or the latest take when none passed. Reads each run's videos.tsv/device.txt,
and probes containers with ffprobe -count_packets (demux only, no decode: cheap on the MacBook).
"""
import csv, json, os, subprocess, sys
from pathlib import Path

dest = Path(sys.argv[1]); vs = Path(sys.argv[sys.argv.index("--vs") + 1]) if "--vs" in sys.argv else None
runs = sorted(p for p in (dest / "runs").glob("video-*") if p.is_dir())
rows, devices = {}, {}
for run in runs:
    tsv = run / "videos.tsv"
    if not tsv.exists():
        continue
    devices[run.name] = (run / "device.txt").read_text().strip() if (run / "device.txt").exists() else "?"
    for r in csv.DictReader(tsv.open(), delimiter="\t"):
        src = run / r["file"]
        if not (src.exists() and src.stat().st_size > 0):
            continue
        prev = rows.get(r["file"])
        if prev and prev["flow_rc"] == "0" and r["flow_rc"] != "0":
            continue  # keep the earlier passing take
        takes = (prev or {}).get("takes", 0) + 1
        rows[r["file"]] = {**r, "run": run.name, "src": src, "takes": takes}
for name, r in rows.items():
    out = dest / name
    if out.exists() or out.is_symlink():
        out.unlink()
    os.link(r["src"], out)

def probe(f: Path) -> dict:
    out = subprocess.run(["nice", "-n", "19", "ffprobe", "-v", "error", "-select_streams", "v:0", "-count_packets",
                          "-show_entries", "stream=codec_name,width,height,r_frame_rate,avg_frame_rate,nb_read_packets:format=duration,size",
                          "-of", "json", str(f)], capture_output=True, text=True).stdout
    j = json.loads(out or "{}"); s = (j.get("streams") or [{}])[0]; fm = j.get("format", {})
    dur = float(fm.get("duration") or 0); pk = int(s.get("nb_read_packets") or 0)
    ts = sorted(float(x) for x in subprocess.run(["nice", "-n", "19", "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
                "packet=pts_time", "-of", "csv=p=0", str(f)], capture_output=True, text=True).stdout.split() if x.strip() not in ("", "N/A"))
    peak = max((sum(1 for u in ts if t0 <= u < t0 + 1.0) for t0 in ts), default=0)
    return {"codec": s.get("codec_name", "?"), "size": f"{s.get('width')}x{s.get('height')}", "r_fps": s.get("r_frame_rate", "?"),
            "dur": round(dur, 1), "packets": pk, "pk_fps": round(pk / dur, 1) if dur else 0, "mb": round(int(fm.get("size") or 0) / 1e6, 1), "peak": peak}

lines = []
for name in sorted(rows):
    r = rows[name]; p = probe(dest / name)
    lines.append(f"| `{name}` | {r['lang']} | {r['font']} | {r['recorder']} | {p['codec']} {p['size']} | {p['dur']} | {p['r_fps']} | "
                 f"{p['packets']} ({p['pk_fps']}/s) | {p['peak']} | {r['app_frames']} ({r['app_fps']}/s) | {r.get('janky_frames') or '-'} ({r.get('janky_per_s') or '-'}/s) | {r['janky_pct'] or '-'} | {r['p90_ms'] or '-'} | {p['mb']} | {'ok' if r['flow_rc'] == '0' else 'rc=' + r['flow_rc']} | {r['run'][-6:]}{' (' + str(r['takes']) + ' takes)' if r['takes'] > 1 else ''} |")

sha = dest.parent.name
readme = f"""# Polish round videos, Android, integration {sha}

TL;DR: one video per flow, language and font (POLISH-2026-09-28.md #1). Headless AVD on the Mac mini, dark theme, airplane mode.
Font 1.0 on every flow, plus large font (`_ax`) on flows 01, 02 and 05. Maestro drives the taps, and `show_touches` puts a dot on each one.

## How to read "fps"

- **Container fps** (`r_frame_rate`): the rate the recorder writes. `emu` = `adb emu screenrecord --fps 60` encodes on the host at a fixed 60 fps, so a frame the app did not redraw shows up as a duplicate. `dev` = `adb shell screenrecord` has a variable frame rate and writes a frame only when the screen changes. It encodes H.264 in software inside the emulator, on the same 4 vCPUs as the app, which lowers app frames/s. Compare builds only between videos made with the same recorder.
- **Peak fps (1 s)**: the most frames written in any 1 s window. With `dev`, this is the real rate during the busiest animation (60 = the display's full rate).
- **Packets/s**: frames actually written, divided by the video length. For `dev` this is the real screen-update rate. For `emu` it is about 60 by construction.
- **App frames/s**: frames the app rendered (`dumpsys gfxinfo`, reset when recording starts), divided by the recording's seconds. This is the app's real frame rate on this emulator. It counts idle stretches too, so compare builds with janky/s and p90, as in review/perf/GFXINFO.md. The janky % misleads when fewer frames are drawn.
- The gfxinfo window is the whole recording: Maestro's ~5 s startup with the screen still, the flow, and a 1.5 s tail. For `02` it is almost all the send → answer stream.
- This is an emulator (arm64 AVD, see `-gpu` below), not a phone. Timing and smoothness are indicative only. Layout, order, and presence or absence of transitions are what these videos can show reliably.

## Files

| file | lang | font | recorder | video | s | container fps | packets | peak fps (1 s) | app frames | janky frames | janky % | p90 ms | MB | flow | run |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
{chr(10).join(lines)}

Names follow `<nn>-<flow>_<en|pt>[_ax|_rm]`. `_rm` = reduce motion: Android "Remove animations", all three animation scales set to 0. RN's `isReduceMotionEnabled` reads `transition_animation_scale == 0`. `01a` = setup 1→3 plus the start of the import, and `01b` = import → done → chat. The plan asked for `.mp4`. The `emu` recorder writes WebM (VP8), and ffmpeg/Prism read it the same way.

## Device (per run)

""" + "\n\n".join(f"### {k}\n```\n{v}\n```" for k, v in devices.items()) + """

## Frame by frame

```bash
ffmpeg -i 02-chat-send_en.webm -vf "select='gt(scene,0.002)',showinfo" -vsync vfr frames/%05d.png   # changed frames only
ffmpeg -ss 12.0 -i 02-chat-send_en.webm -frames:v 90 frames/%03d.png                                # 1.5 s at 60 fps from t=12 s
```

{VS}Raw run output (Maestro logs, gfxinfo, emulator log) is in `runs/`.
"""
cmp = ""
if vs:
    old = {}
    for run in sorted(p for p in (vs / "runs").glob("video-*") if (p / "videos.tsv").exists()):
        for r in csv.DictReader((run / "videos.tsv").open(), delimiter="\t"):
            if (run / r["file"]).exists() and not (r["file"] in old and old[r["file"]]["flow_rc"] == "0" and r["flow_rc"] != "0"):
                old[r["file"]] = r
    def key(n): return n.replace("_r2", "")
    lines2 = []
    for name in sorted(rows):
        a, b = old.get(key(name)), rows[name]
        if not a: continue
        ok = a["flow_rc"] == "0" and b["flow_rc"] == "0"
        lines2.append(f"| `{name}` | {a.get('janky_per_s') or '-'} → {b.get('janky_per_s') or '-'} | {a['p90_ms']} → {b['p90_ms']} | {a['app_fps']} → {b['app_fps']} | {'' if ok else 'a take failed: compare with care'} |")
    cmp = (f"## Before × after ({vs.parent.name} → {sha})\n\nSame recorder, AVD and flows. janky/s and p90 from gfxinfo over the whole recording.\n\n"
           "| file | janky/s | p90 ms | app frames/s | note |\n|---|---|---|---|---|\n" + "\n".join(lines2) + "\n\n")
readme = readme.replace("{VS}", cmp)
(dest / "README.md").write_text(readme)
print(f"{len(rows)} videos -> {dest}/README.md")
