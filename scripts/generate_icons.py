#!/usr/bin/env python3
"""
Generates the Repvane app icon files from the logo design (dumbbell as a compass needle).

Outputs (in composeApp/icons/):
  repvane.ico   Windows installer / shortcut / taskbar icon (16-256 px)
  repvane.png   512 px PNG: Linux desktop icon and Google Play store icon
  repvane.icns  macOS app icon

Same geometry as ui/brand/RepvaneLogo.kt and the Android vector drawables (200-unit grid).
Run:  python3 scripts/generate_icons.py   (needs Pillow)
"""
from pathlib import Path

from PIL import Image, ImageDraw

NAVY = (0, 27, 63, 255)
CONTAINER = (215, 227, 255, 255)
TICK = (215, 227, 255, 179)
ACCENT = (255, 184, 112, 255)
WHITE = (255, 255, 255, 255)

OUT = Path(__file__).resolve().parent.parent / "composeApp" / "icons"


def render(size: int, supersample: int = 4) -> Image.Image:
    big = size * supersample
    s = big / 200.0

    def p(v: float) -> float:
        return v * s

    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Navy rounded-square tile
    d.rounded_rectangle([0, 0, big - 1, big - 1], radius=p(46), fill=NAVY)
    # Compass ring
    d.ellipse([p(34), p(34), p(166), p(166)], outline=CONTAINER, width=max(1, round(p(6))))
    # Ticks
    w = max(1, round(p(5)))
    for (x1, y1), (x2, y2) in [((100, 40), (100, 50)), ((160, 100), (150, 100)), ((100, 160), (100, 150)), ((40, 100), (50, 100))]:
        d.line([p(x1), p(y1), p(x2), p(y2)], fill=TICK, width=w)

    # Dumbbell drawn upright on its own layer, then rotated 45 degrees clockwise around the centre.
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.rectangle([p(95), p(62), p(105), p(138)], fill=WHITE)
    ld.rounded_rectangle([p(78), p(46), p(122), p(68)], radius=p(7), fill=ACCENT)
    ld.rounded_rectangle([p(78), p(132), p(122), p(154)], radius=p(7), fill=WHITE)
    layer = layer.rotate(-45, resample=Image.BICUBIC, center=(big / 2, big / 2))
    img.alpha_composite(layer)

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    master = render(512)
    master.save(OUT / "repvane.png")
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    render(256).save(OUT / "repvane.ico", sizes=[(n, n) for n in sizes])
    render(1024).save(OUT / "repvane.icns")
    print("Wrote repvane.png, repvane.ico and repvane.icns to", OUT)


if __name__ == "__main__":
    main()
