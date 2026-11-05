"""Draws the official icon of Cobblemon Battle Showtime: a star in the spotlights, between stage curtains.

Writes the mod's icon and the music pack template's pack.png. Run from the repository's root: python tools/icon.py
Needs Pillow.
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SCALE = 4
SIZE = 512 * SCALE
OUTPUTS = {
    "src/main/resources/assets/cobblemon_battle_showtime/icon.png": 512,
    "resourcepack-template/Cobblemon Battle Showtime Music/pack.png": 256,
}


def s(value: float) -> float:
    return value * SCALE


def vertical_gradient(top: tuple, bottom: tuple) -> Image.Image:
    column = Image.new("RGB", (1, SIZE))
    for y in range(SIZE):
        t = y / (SIZE - 1)
        column.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return column.resize((SIZE, SIZE))


def star_points(cx: float, cy: float, outer: float, inner: float, points: int = 5) -> list:
    result = []
    for i in range(points * 2):
        radius = outer if i % 2 == 0 else inner
        angle = -math.pi / 2 + i * math.pi / points
        result.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return result


def beam(image: Image.Image, origin: tuple, left: tuple, right: tuple, opacity: int) -> None:
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).polygon([origin, left, right], fill=opacity)
    mask = mask.filter(ImageFilter.GaussianBlur(s(5)))
    image.paste((255, 226, 150), (0, 0), mask)


def curtain(image: Image.Image, mirrored: bool) -> None:
    """A curtain gathered towards the bottom, its folds drawn as alternating bands."""
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    folds = 4
    for fold in range(folds):
        width_top = 150 / folds
        width_bottom = 72 / folds
        points = []
        for edge in (fold, fold + 1):
            side = []
            for step in range(41):
                t = step / 40
                # Tied back near the bottom third, then falling straight.
                pull = math.sin(min(t / 0.72, 1) * math.pi / 2)
                width = width_top + (width_bottom - width_top) * pull
                side.append((s(edge * width), s(t * 512)))
            points.append(side)
        color = (196, 28, 52) if fold % 2 == 0 else (142, 14, 40)
        draw.polygon(points[0] + points[1][::-1], fill=color)
    if mirrored:
        layer = layer.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    image.paste(layer, (0, 0), layer)


def sparkle(draw: ImageDraw.ImageDraw, cx: float, cy: float, radius: float) -> None:
    draw.polygon(star_points(s(cx), s(cy), s(radius), s(radius * 0.16), 4), fill=(255, 246, 214))


def main() -> None:
    image = vertical_gradient((20, 16, 52), (44, 26, 92)).convert("RGB")

    # Spotlights, from the top corners to the star.
    beam(image, (s(70), s(-20)), (s(150), s(400)), (s(330), s(330)), 105)
    beam(image, (s(442), s(-20)), (s(182), s(330)), (s(362), s(400)), 105)

    # The pool of light on the stage's floor.
    pool = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(pool).ellipse((s(96), s(388), s(416), s(476)), fill=150)
    image.paste((255, 214, 120), (0, 0), pool.filter(ImageFilter.GaussianBlur(s(9))))

    curtain(image, False)
    curtain(image, True)

    # Pelmet along the top.
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, SIZE, s(44)), fill=(142, 14, 40))
    for i in range(7):
        x = i * 512 / 7
        draw.pieslice((s(x), s(14), s(x + 512 / 7), s(74)), 0, 180, fill=(196, 28, 52))
    draw.rectangle((0, s(38), SIZE, s(46)), fill=(244, 184, 66))

    # Glow, then the star.
    cx, cy = 256, 262
    glow = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(glow).polygon(star_points(s(cx), s(cy), s(150), s(72)), fill=210)
    image.paste((255, 196, 72), (0, 0), glow.filter(ImageFilter.GaussianBlur(s(20))))
    draw = ImageDraw.Draw(image)
    draw.polygon(star_points(s(cx), s(cy + 8), s(136), s(64)), fill=(120, 58, 20))
    draw.polygon(star_points(s(cx), s(cy), s(136), s(64)), fill=(255, 196, 46))
    draw.polygon(star_points(s(cx), s(cy - 3), s(92), s(44)), fill=(255, 232, 128))

    sparkle(draw, 132, 142, 25)
    sparkle(draw, 384, 138, 19)
    sparkle(draw, 404, 300, 14)
    sparkle(draw, 110, 312, 13)

    # Rounded corners, so the icon reads well in Mod Menu.
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), s(64), fill=255)
    final = image.convert("RGBA")
    final.putalpha(ImageChops.multiply(final.getchannel("A"), mask))

    for output, size in OUTPUTS.items():
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        final.resize((size, size), Image.Resampling.LANCZOS).save(path, optimize=True)
        print(path, size)


if __name__ == "__main__":
    main()
