"""Builds the gallery from in-game recordings: python tools/gallery.py ambiances|intros

For each ambiance or intro: a screenshot and a 3-second clip, then a reel chaining them with crossfades that loops
without a jump (MP4, a 640 px GIF and a lighter 400 px GIF), in art/<kind>/. The recordings and the time of each
clip are listed in SETS.
"""
import os
import subprocess
import sys

FF = r"C:\Users\adrie\AppData\Roaming\Python\Python313\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v9.0.2.exe"
KIND = sys.argv[1] if len(sys.argv) > 1 else "ambiances"
ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "art")
SETS = {
    "ambiances": (r"D:\Main_user_data\Video\Ambiances.mp4", 0, [
        ("abyss", 1), ("arcade", 29), ("blizzard", 74), ("corrupted", 124), ("cosmos", 174), ("disco", 219),
        ("fairy_tale", 254), ("inferno", 309), ("meadow", 359), ("mirage", 399), ("starry_night", 444),
        ("sakura_festival", 479), ("silver_screen", 524), ("spectral", 554), ("storm", 609), ("synthwave", 644),
        ("ultra_space", 697)]),
    # Intros: (name, clip start, screenshot time).
    "intros": (r"D:\Main_user_data\Video\intros.mp4", 1, [
        ("rby_clock", 11.5, 13.5), ("gsc_stairs", 18.5, 20.5), ("emerald", 24.5, 27.0), ("colosseum_swirl", 29.5, 31.5),
        ("diamond_pearl", 39.0, 41.0), ("pbr_card", 45.5, 47.0), ("pbr_entrance", 52.0, 53.0), ("pbr_face_off", 64.5, 67.0),
        ("dp_blinds", 71.0, 73.5), ("black_white", 77.5, 80.0), ("xy", 83.0, 85.0), ("oras_silhouette", 88.5, 90.5),
        ("sm_vortex", 95.5, 98.0), ("usum_surge", 102.5, 104.5), ("sword_shield", 109.5, 111.0),
        ("sv_showdown", 116.5, 118.0)]),
}
SRC, HAS_SHOT, CLIPS = SETS[KIND]
OUT = os.path.join(ROOT, KIND)
LEN, FADE = 3.0, 0.6

shots = os.path.join(OUT, "screenshots")
clips = os.path.join(OUT, "clips")
os.makedirs(shots, exist_ok=True)
os.makedirs(clips, exist_ok=True)


def run(args):
    subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y"] + args, check=True)


paths = []
for i, entry in enumerate(CLIPS, 1):
    name, t = entry[0], entry[1]
    shot = entry[2] if HAS_SHOT else t + 1.5
    stem = f"{i:02d}_{name}"
    run(["-ss", str(shot), "-i", SRC, "-frames:v", "1", os.path.join(shots, stem + ".png")])
    p = os.path.join(clips, stem + ".mp4")
    run(["-ss", str(t), "-i", SRC, "-t", str(LEN), "-an", "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p",
         "-r", "30", p])
    paths.append(p)

# Chain every clip with crossfades, then the first clip's head again: the reel is then cut to start FADE seconds in,
# so its last frame leads straight into its first and it loops without a jump.
inputs = []
for p in paths:
    inputs += ["-i", p]
inputs += ["-i", paths[0]]
n = len(paths)
parts = [f"[{n}:v]trim=0:{FADE},setpts=PTS-STARTPTS[head]"]
prev, offset = "[0:v]", LEN - FADE
for k in range(1, n):
    parts.append(f"{prev}[{k}:v]xfade=transition=fade:duration={FADE}:offset={offset:.3f}[x{k}]")
    prev, offset = f"[x{k}]", offset + LEN - FADE
parts.append(f"{prev}[head]xfade=transition=fade:duration={FADE}:offset={offset:.3f}[all]")
total = offset + FADE
parts.append(f"[all]trim={FADE}:{total:.3f},setpts=PTS-STARTPTS[reel]")
graph = ";".join(parts)
reel = os.path.join(OUT, KIND + "_reel.mp4")
run(inputs + ["-filter_complex", graph, "-map", "[reel]", "-c:v", "libx264", "-crf", "20", "-pix_fmt", "yuv420p", reel])

run(["-i", reel, "-vf",
     "fps=12,scale=640:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=sierra2_4a",
     "-loop", "0", os.path.join(OUT, KIND + ".gif")])
run(["-i", reel, "-vf",
     "fps=8,scale=400:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle",
     "-loop", "0", os.path.join(OUT, KIND + "_400px.gif")])
print("done")
