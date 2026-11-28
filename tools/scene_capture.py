"""Pictures Showtime's battle scenes with nobody at the keyboard: python tools/scene_capture.py [--kinds preset,stadium]

Starts the dev game (`gradlew runCapture`, the capture mod in src/capture, mode=scenes): in a flat world, two Pokémon
face to face, each scene shown as a battle seen from afar, a screenshot without the HUD once it has settled, then the
game quits. Pictures land in run-capture/scenes/<kind>/<ns>/<name>.png (960x540).

Options:
  --kinds preset,stadium,ambiance   which of Showtime's own scenes (default: all three; "" for none)
  --only id1,id2                    keep only these ids (with or without their namespace)
  --extra file.tsv                  more scenes, one "kind<TAB>id<TAB>scene" line each (another mod's, such as
                                    League Reborn fields); scene = "<ambiance>|<stadium>"
  --settle 80                       ticks a scene plays before its picture
  --view battle                     battle: the scene built whole around the stage, as in a battle (default);
                                    distant: as a battle seen from afar (only its field dressed)
  --user charizard --target blastoise --distance 9 --height 3 --gap 3   the stage and the camera
"""
import argparse
import os
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from move_capture import ROOT, RUN, write_options  # noqa: E402

OUT = os.path.join(RUN, "scenes")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--kinds", default="preset,stadium,ambiance")
    p.add_argument("--only", default="")
    p.add_argument("--extra")
    p.add_argument("--settle", default="80")
    p.add_argument("--view", default="battle", help="battle: built whole as in a battle; distant: as seen from afar")
    p.add_argument("--user", default="charizard")
    p.add_argument("--target", default="blastoise")
    p.add_argument("--distance", default="9")
    p.add_argument("--height", default="3")
    p.add_argument("--gap", default="3")
    a = p.parse_args()

    write_options()
    os.makedirs(OUT, exist_ok=True)
    extra = ""
    if a.extra:
        shutil.copyfile(a.extra, os.path.join(RUN, "scenes-extra.tsv"))
        extra = "scenes-extra.tsv"
    with open(os.path.join(RUN, "capture.properties"), "w", encoding="utf-8") as f:
        f.write(f"mode=scenes\nkinds={a.kinds}\nonly={a.only}\nextra={extra}\nout=scenes\nsettle={a.settle}\nview={a.view}\n")
        f.write(f"user={a.user}\ntarget={a.target}\ndistance={a.distance}\nheight={a.height}\ngap={a.gap}\n")
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
        lines = [line.rstrip("\n") for line in f if line.strip()]
    empty = [line for line in lines if not line.endswith("\tok")]
    print(f"{len(lines)} scenes pictured into {OUT}")
    for line in empty:
        print("  showed nothing:", line)


if __name__ == "__main__":
    main()
