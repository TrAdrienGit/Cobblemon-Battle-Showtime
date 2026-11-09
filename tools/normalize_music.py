"""Evens out the loudness of a music pack: every track brought to the same perceived loudness (EBU R128).

Usage:
  python tools/normalize_music.py measure <music dir> [report.csv]
  python tools/normalize_music.py apply <music dir> <out dir> [--target -16] [--peak -1.5]

<music dir> is a pack's assets/<namespace>/sounds/music folder (its moment subfolders are kept). "measure" prints and
saves each file's integrated loudness (LUFS), loudness range and true peak. "apply" writes normalised copies: a
two-pass ffmpeg loudnorm in linear mode (one gain per file, so the music's own dynamics are kept; if the gain would
push the true peak over --peak, loudnorm limits it), re-encoded as Ogg Vorbis at quality 6. The source is not touched.

Needs ffmpeg: on the PATH, or the one bundled by the imageio-ffmpeg Python package (pip install imageio-ffmpeg).
"""
import csv
import json
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def ffmpeg():
    found = shutil.which("ffmpeg")
    if found:
        return found
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


FFMPEG = ffmpeg()


def loudnorm_json(stderr):
    text = stderr[stderr.rindex("{"):]
    return json.loads(text[:text.index("}") + 1])


def measure(path, target=-16.0, peak=-1.5):
    result = subprocess.run([FFMPEG, "-hide_banner", "-nostats", "-i", str(path), "-af",
                             f"loudnorm=I={target}:TP={peak}:LRA=11:print_format=json", "-f", "null", "-"],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    return loudnorm_json(result.stderr)


def files(root):
    return sorted(root.rglob("*.ogg"))


def cmd_measure(root, report):
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(measure, files(root)))
    rows = []
    for path, m in zip(files(root), results):
        rows.append({"file": str(path.relative_to(root)).replace("\\", "/"), "lufs": float(m["input_i"]),
                     "range": float(m["input_lra"]), "true_peak": float(m["input_tp"])})
    rows.sort(key=lambda r: r["lufs"])
    if report:
        with open(report, "w", newline="", encoding="utf-8") as out:
            writer = csv.DictWriter(out, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    values = [r["lufs"] for r in rows]
    print(f"{len(rows)} files: quietest {values[0]:.1f} LUFS, loudest {values[-1]:.1f} LUFS, "
          f"median {values[len(values) // 2]:.1f} LUFS")
    for r in rows[:5] + rows[-5:]:
        print(f"  {r['lufs']:6.1f} LUFS  peak {r['true_peak']:5.1f}  {r['file']}")


def normalize(job):
    source, dest, target, peak = job
    m = measure(source, target, peak)
    dest.parent.mkdir(parents=True, exist_ok=True)
    af = (f"loudnorm=I={target}:TP={peak}:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}"
          f":measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}:offset={m['target_offset']}"
          f":linear=true:print_format=json")
    result = subprocess.run([FFMPEG, "-hide_banner", "-nostats", "-y", "-i", str(source), "-af", af, "-ar", "48000",
                             "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "6", str(dest)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        return f"FAILED {source}: {result.stderr[-300:]}"
    out = loudnorm_json(result.stderr)
    return f"{float(m['input_i']):6.1f} -> {float(out['output_i']):6.1f} LUFS  {out['normalization_type']:7}  {dest.name}"


def cmd_apply(root, out_root, target, peak):
    jobs = [(f, out_root / f.relative_to(root), target, peak) for f in files(root)]
    with ThreadPoolExecutor(max_workers=8) as pool:
        for line in pool.map(normalize, jobs):
            print(line, flush=True)


def main():
    args = sys.argv[1:]
    if args[0] == "measure":
        cmd_measure(Path(args[1]), args[2] if len(args) > 2 else None)
    elif args[0] == "apply":
        target = float(args[args.index("--target") + 1]) if "--target" in args else -16.0
        peak = float(args[args.index("--peak") + 1]) if "--peak" in args else -1.5
        cmd_apply(Path(args[1]), Path(args[2]), target, peak)


if __name__ == "__main__":
    main()
