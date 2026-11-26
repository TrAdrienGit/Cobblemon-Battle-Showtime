"""Films Showtime's moves with nobody at the keyboard: python tools/move_capture.py flamethrower thunderbolt ...

Starts a dev game (`gradlew runCapture`, the capture mod in src/capture) that opens a flat world, plays each move
between two Pokémon, pipes the frames to ffmpeg and quits. Then, per move, in run-capture/captures/:
  <move>.mp4         the move at 20 fps (960x540 window)
  <move>_sheet.jpg   a contact sheet, 4 frames a second, to read the move at a glance

Options:
  --all                  every redrawn move (about 900: a long run)
  --file moves.txt       moves from a file (one per line or comma separated)
  --user charizard       the Pokémon using the moves (Cobblemon properties, "charizard shiny=yes" works)
  --target blastoise     the one taking them
  --distance 5.5 --height 1.8 --gap 2.2   camera distance/height from the stage's middle, half the gap between the two
  --sheets-only          only rebuild the contact sheets from existing MP4s
  --grid folder          also write review grids there: one row of 8 frames per move, 6 moves an image

ffmpeg: the FFMPEG environment variable, else the WinGet Gyan.FFmpeg install. Gradle runs with JDK 25 (JAVA_HOME
or the default install path).
"""
import argparse
import glob
import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, "run-capture")
OUT = os.path.join(RUN, "captures")

# A quick, quiet start: low render distance, no music, no onboarding, no pause when the window loses focus.
OPTIONS = {
    "renderDistance": "4", "simulationDistance": "5", "soundCategory_music": "0.0", "soundCategory_master": "0.0",
    "narrator": "0", "onboardAccessibility": "false", "tutorialStep": "none", "pauseOnLostFocus": "false",
    "joinedFirstServer": "true", "skipMultiplayerWarning": "true", "realmsNotifications": "false",
    "inactivityFpsLimit": "\"minimized\"", "maxFps": "60", "fov": "0.0", "bobView": "false", "guiScale": "2",
    "hideServerAddress": "true", "darkMojangStudiosBackground": "true", "fullscreen": "false",
}


def ffmpeg():
    path = os.environ.get("FFMPEG")
    if path:
        return path
    found = glob.glob(os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg*\*\bin\ffmpeg.exe"))
    if not found:
        sys.exit("ffmpeg not found: set FFMPEG")
    return found[0]


def write_options():
    os.makedirs(RUN, exist_ok=True)
    path = os.path.join(RUN, "options.txt")
    lines = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                if ":" in line:
                    key, value = line.rstrip("\n").split(":", 1)
                    lines[key] = value
    lines.update(OPTIONS)
    with open(path, "w", encoding="utf-8") as f:
        for key, value in lines.items():
            f.write(f"{key}:{value}\n")


def sheet(ff, mp4):
    """4 frames a second, 6 a row, 400 px each."""
    out = mp4[:-4] + "_sheet.jpg"
    probe = subprocess.run([ff, "-i", mp4], capture_output=True, text=True).stderr
    seconds = 8.0
    for part in probe.split("Duration: ")[1:2]:
        h, m, s = part.split(",")[0].split(":")
        seconds = int(h) * 3600 + int(m) * 60 + float(s)
    frames = max(1, int(seconds * 4 + 0.999))
    rows = (frames + 5) // 6
    subprocess.run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", mp4, "-vf",
                    f"fps=4,scale=400:-1,tile=6x{rows}:padding=4:color=black", "-frames:v", "1", "-q:v", "3", out],
                   check=True)
    return out


def duration(ff, mp4):
    probe = subprocess.run([ff, "-i", mp4], capture_output=True, text=True).stderr
    for part in probe.split("Duration: ")[1:2]:
        h, m, s = part.split(",")[0].split(":")
        return int(h) * 3600 + int(m) * 60 + float(s)
    return 8.0


def grids(ff, moves, folder, per=6, frames=8):
    """Review grids: one row per move (frames spread over the move, cropped on the stage), `per` moves an image."""
    strips = []
    for move in moves:
        mp4 = os.path.join(OUT, move + ".mp4")
        if not os.path.exists(mp4):
            continue
        strip = os.path.join(folder, "_strip_" + move + ".png")
        rate = frames / max(0.5, duration(ff, mp4))
        subprocess.run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", mp4, "-vf",
                        f"fps={rate:.4f},crop=720:405:120:80,scale=240:-1,tile={frames}x1:padding=2:color=black",
                        "-frames:v", "1", strip], check=True)
        strips.append(strip)
    out = []
    for i in range(0, len(strips), per):
        group = strips[i:i + per]
        target = os.path.join(folder, f"grid_{i // per + 1:02d}.jpg")
        inputs = []
        for strip in group:
            inputs += ["-i", strip]
        graph = "".join(f"[{k}:v]" for k in range(len(group))) + f"vstack=inputs={len(group)}" if len(group) > 1 else "null"
        subprocess.run([ff, "-hide_banner", "-loglevel", "error", "-y"] + inputs + ["-filter_complex", graph, "-q:v", "4", target],
                       check=True)
        out.append(target)
    for strip in strips:
        os.remove(strip)
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("moves", nargs="*")
    p.add_argument("--all", action="store_true")
    p.add_argument("--file")
    p.add_argument("--user", default="charizard")
    p.add_argument("--target", default="blastoise")
    p.add_argument("--distance", default="5.5")
    p.add_argument("--height", default="1.8")
    p.add_argument("--gap", default="2.2")
    p.add_argument("--sheets-only", action="store_true")
    p.add_argument("--grid", help="also write review grids (one row per move, 6 moves an image) into this folder")
    a = p.parse_args()
    ff = ffmpeg()

    moves = [m.lower() for m in a.moves]
    if a.file:
        with open(a.file, encoding="utf-8") as f:
            moves += [m.strip().lower() for m in f.read().replace(",", "\n").splitlines() if m.strip()]
    if a.all:
        moves = ["all"]
    if not moves and not a.sheets_only:
        sys.exit("No move given (names, --file or --all)")

    if not a.sheets_only:
        write_options()
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(RUN, "capture.properties"), "w", encoding="utf-8") as f:
            f.write(f"moves={','.join(moves)}\nuser={a.user}\ntarget={a.target}\nout=captures\n")
            f.write(f"distance={a.distance}\nheight={a.height}\ngap={a.gap}\n")
            f.write("ffmpeg=" + ff.replace("\\", "/") + "\n")
        done = os.path.join(RUN, "capture-done.txt")
        if os.path.exists(done):
            os.remove(done)
        env = dict(os.environ)
        if not env.get("JAVA_HOME") or "25" not in env["JAVA_HOME"]:
            env["JAVA_HOME"] = r"C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
        start = time.time()
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.run([gradlew, "runCapture", "-q", "--console=plain"], cwd=ROOT, env=env)
        print(f"Game closed after {time.time() - start:.0f} s")
        if not os.path.exists(done):
            sys.exit("The capture did not finish: see run-capture/logs/latest.log")
        with open(done, encoding="utf-8") as f:
            captured = [line.split("\t")[0] for line in f if line.strip()]
    else:
        captured = [os.path.basename(m)[:-4] for m in glob.glob(os.path.join(OUT, "*.mp4"))]

    for move in captured:
        mp4 = os.path.join(OUT, move + ".mp4")
        if os.path.exists(mp4):
            print(sheet(ff, mp4))
    if a.grid:
        os.makedirs(a.grid, exist_ok=True)
        order = [m for m in moves if m != "all"] or captured
        for path in grids(ff, order, a.grid):
            print(path)


if __name__ == "__main__":
    main()
