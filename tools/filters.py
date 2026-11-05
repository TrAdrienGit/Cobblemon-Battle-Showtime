"""Writes Showtime's screen filters (post effects) for the battle ambiances: python tools/filters.py ."""
import json
import os
import sys

ROOT = os.path.join(sys.argv[1], "src", "main", "resources", "assets")
PROGRAM = os.path.join(ROOT, "minecraft", "shaders", "program")
POST = os.path.join(ROOT, "cobblemon_battle_showtime", "shaders", "post")
os.makedirs(PROGRAM, exist_ok=True)
os.makedirs(POST, exist_ok=True)

BASE_UNIFORMS = [
    {"name": "ProjMat", "type": "matrix4x4", "count": 16,
     "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
    {"name": "InSize", "type": "float", "count": 2, "values": [1.0, 1.0]},
    {"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]},
    {"name": "Time", "type": "float", "count": 1, "values": [0.0]},
]

FRAGMENTS = {
    # Colours split apart, drifting: a glitch.
    "showtime_split": ("Amount", 0.006, """
void main() {
    float drift = sin(Time * 6.2831) * 0.5 + 0.5;
    vec2 offset = vec2(Amount * (0.6 + drift), Amount * 0.3 * sin(Time * 12.566));
    float r = texture(DiffuseSampler, texCoord + offset).r;
    float g = texture(DiffuseSampler, texCoord).g;
    float b = texture(DiffuseSampler, texCoord - offset).b;
    // Now and then a band of the screen jumps sideways.
    float band = step(0.97, fract(sin(floor(texCoord.y * 40.0) + floor(Time * 20.0)) * 43758.5453));
    vec2 jump = vec2(band * Amount * 4.0, 0.0);
    vec3 jumped = texture(DiffuseSampler, texCoord + jump).rgb;
    fragColor = vec4(mix(vec3(r, g, b), jumped, band), 1.0);
}
"""),
    # The screen waving like heat haze or warped space.
    "showtime_wobble": ("Amount", 0.008, """
void main() {
    vec2 uv = texCoord;
    uv.x += sin(uv.y * 18.0 + Time * 6.2831 * 2.0) * Amount;
    uv.y += cos(uv.x * 14.0 + Time * 6.2831 * 1.5) * Amount;
    fragColor = vec4(texture(DiffuseSampler, uv).rgb, 1.0);
}
"""),
    # An old television: scanlines, a dark rim, washed-out colour, a flicker.
    "showtime_old_tv": ("Amount", 1.0, """
void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(grey), 0.55 * Amount);
    float scan = 0.82 + 0.18 * sin(texCoord.y * OutSize.y * 1.5);
    vec2 centred = texCoord - 0.5;
    float rim = 1.0 - dot(centred, centred) * 1.6 * Amount;
    float flicker = 0.96 + 0.04 * sin(Time * 6.2831 * 9.0);
    fragColor = vec4(color * scan * rim * flicker, 1.0);
}
"""),
}

for name, (uniform, default, body) in FRAGMENTS.items():
    with open(os.path.join(PROGRAM, name + ".fsh"), "w", encoding="utf-8") as f:
        f.write("#version 150\n\nuniform sampler2D DiffuseSampler;\nuniform vec2 OutSize;\nuniform float Time;\n"
                f"uniform float {uniform};\n\nin vec2 texCoord;\n\nout vec4 fragColor;\n" + body)
    program = {
        "blend": {"func": "add", "srcrgb": "one", "dstrgb": "zero"},
        "vertex": "blit",
        "fragment": name,
        "attributes": ["Position"],
        "samplers": [{"name": "DiffuseSampler"}],
        "uniforms": BASE_UNIFORMS + [{"name": uniform, "type": "float", "count": 1, "values": [default]}],
    }
    with open(os.path.join(PROGRAM, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(program, f, indent=4)
        f.write("\n")


def post(name, program, uniforms=None):
    passes = [{"name": program, "intarget": "minecraft:main", "outtarget": "swap"},
              {"name": "blit", "intarget": "swap", "outtarget": "minecraft:main"}]
    if uniforms:
        passes[0]["uniforms"] = [{"name": k, "values": v} for k, v in uniforms.items()]
    with open(os.path.join(POST, name + ".json"), "w", encoding="utf-8") as f:
        json.dump({"targets": ["swap"], "passes": passes}, f, indent=4)
        f.write("\n")


post("black_white", "color_convolve", {"Saturation": [0.0]})
# Gentle (Adrien 2026-10-30: 4 colour levels and big blocks made the game unwatchable): fine pixels, many levels.
post("pixels", "bits", {"Resolution": [24.0], "MosaicSize": [2.5]})
post("glitch", "showtime_split")
# The corrupted ambiance's lasting split, softer than a moment's glitch (Adrien 2026-10-30: twice asked lighter).
post("glitch_soft", "showtime_split", {"Amount": [0.0022]})
post("wobble", "showtime_wobble")
post("old_tv", "showtime_old_tv")
print("filters written")
