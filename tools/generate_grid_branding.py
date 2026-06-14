#!/usr/bin/env python3
"""Generate GRID Netflix-style launcher icons and Android TV banner PNGs."""

from __future__ import annotations

import math
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

BG_BLACK = (11, 11, 15)
BG_BLACK_END = (28, 6, 8)
ACCENT_RED = (229, 9, 20)
GLOW_RED = (255, 42, 42)
WHITE = (255, 255, 255)

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def _load_font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/calibrib.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def _vertical_gradient(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(size - 1, 1)
        r = int(BG_BLACK[0] + (BG_BLACK_END[0] - BG_BLACK[0]) * t)
        g = int(BG_BLACK[1] + (BG_BLACK_END[1] - BG_BLACK[1]) * t)
        b = int(BG_BLACK[2] + (BG_BLACK_END[2] - BG_BLACK[2]) * t)
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
    return img


def _draw_faint_grid(draw: ImageDraw.ImageDraw, size: int, alpha: int = 18) -> None:
    step = max(size // 8, 6)
    color = (255, 255, 255, alpha)
    for x in range(0, size + 1, step):
        draw.line([(x, 0), (x, size)], fill=color, width=1)
    for y in range(0, size + 1, step):
        draw.line([(0, y), (size, y)], fill=color, width=1)


def _draw_spaced_text(
    base: Image.Image,
    text: str,
    *,
    font: ImageFont.FreeTypeFont,
    y_center: float,
    tracking: float,
    fill: tuple[int, int, int, int],
) -> None:
    draw = ImageDraw.Draw(base)
    chars = list(text)
    widths = [draw.textbbox((0, 0), c, font=font)[2] for c in chars]
    total = sum(widths) + tracking * (len(chars) - 1)
    x = (base.width - total) / 2
    ascent, descent = font.getmetrics()
    y = y_center - (ascent + descent) / 2
    for ch, w in zip(chars, widths):
        draw.text((x, y), ch, font=font, fill=fill)
        x += w + tracking


def render_launcher_foreground(size: int) -> Image.Image:
    """Adaptive-icon foreground: transparent with GRID wordmark + glow."""
    base = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_radius = int(size * 0.28)
    cx, cy = size // 2, int(size * 0.52)
    glow_draw.ellipse(
        (cx - glow_radius, cy - glow_radius, cx + glow_radius, cy + glow_radius),
        fill=(*GLOW_RED, 52),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(size // 16, 2)))
    base = Image.alpha_composite(base, glow)

    font_size = max(int(size * 0.22), 8)
    font = _load_font(font_size)
    tracking = max(size * 0.028, 1.5)

    _draw_spaced_text(
        base,
        "GRID",
        font=font,
        y_center=size * 0.52,
        tracking=tracking,
        fill=(*WHITE, 255),
    )

    accent = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ad = ImageDraw.Draw(accent)
    bar_w = max(int(size * 0.18), 2)
    bar_h = max(int(size * 0.012), 1)
    ad.rounded_rectangle(
        (
            (size - bar_w) // 2,
            int(size * 0.66),
            (size + bar_w) // 2,
            int(size * 0.66) + bar_h,
        ),
        radius=bar_h,
        fill=(*ACCENT_RED, 220),
    )
    return Image.alpha_composite(base, accent)


def render_launcher_icon(size: int) -> Image.Image:
    base = _vertical_gradient(size)
    grid_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    _draw_faint_grid(ImageDraw.Draw(grid_layer), size)
    base = Image.alpha_composite(base, grid_layer)

    foreground = render_launcher_foreground(size)
    base = Image.alpha_composite(base, foreground)
    return base.convert("RGB")


def render_round_icon(size: int) -> Image.Image:
    square = render_launcher_icon(size).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    output = Image.new("RGBA", (size, size), (*BG_BLACK, 255))
    output.paste(square, (0, 0), mask)
    return output.convert("RGB")


def render_tv_banner(width: int, height: int) -> Image.Image:
    base = Image.new("RGB", (width, height), BG_BLACK)
    draw = ImageDraw.Draw(base)
    for y in range(height):
        t = y / max(height - 1, 1)
        r = int(BG_BLACK[0] + (BG_BLACK_END[0] - BG_BLACK[0]) * t * 0.85)
        g = int(BG_BLACK[1] + (BG_BLACK_END[1] - BG_BLACK[1]) * t * 0.85)
        b = int(BG_BLACK[2] + (BG_BLACK_END[2] - BG_BLACK[2]) * t * 0.85)
        draw.line([(0, y), (width, y)], fill=(r, g, b))

    right = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    rd = ImageDraw.Draw(right)
    blocks = [
        (0.58, 0.18, 0.22, 0.28, 210),
        (0.72, 0.12, 0.18, 0.34, 170),
        (0.82, 0.38, 0.14, 0.22, 150),
        (0.64, 0.52, 0.28, 0.18, 130),
        (0.78, 0.62, 0.16, 0.26, 110),
    ]
    for bx, by, bw, bh, alpha in blocks:
        x0 = int(width * bx)
        y0 = int(height * by)
        x1 = int(width * (bx + bw))
        y1 = int(height * (by + bh))
        rd.rounded_rectangle((x0, y0, x1, y1), radius=int(height * 0.02), fill=(*ACCENT_RED, alpha))
    right = right.filter(ImageFilter.GaussianBlur(radius=max(width // 128, 3)))
    base = Image.alpha_composite(base.convert("RGBA"), right).convert("RGB")

    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for i in range(6):
        x = int(width * (0.55 + i * 0.07))
        od.line([(x, 0), (x, height)], fill=(255, 255, 255, 10), width=1)
    for i in range(4):
        y = int(height * (0.15 + i * 0.2))
        od.line([(int(width * 0.5), y), (width, y)], fill=(255, 255, 255, 10), width=1)
    base = Image.alpha_composite(base.convert("RGBA"), overlay).convert("RGB")

    canvas = base.convert("RGBA")
    title_font = _load_font(max(int(height * 0.18), 24))
    subtitle_font = _load_font(max(int(height * 0.055), 12), bold=False)
    tracking = max(width * 0.012, 4)

    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    _draw_spaced_text(
        glow,
        "GRID",
        font=title_font,
        y_center=height * 0.38,
        tracking=tracking,
        fill=(*GLOW_RED, 80),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(width // 80, 4)))
    canvas = Image.alpha_composite(canvas, glow)

    _draw_spaced_text(
        canvas,
        "GRID",
        font=title_font,
        y_center=height * 0.38,
        tracking=tracking,
        fill=(*WHITE, 255),
    )

    sub_draw = ImageDraw.Draw(canvas)
    subtitle = "All your content in one place."
    bbox = sub_draw.textbbox((0, 0), subtitle, font=subtitle_font)
    sub_w = bbox[2] - bbox[0]
    sub_draw.text(
        (int(width * 0.08), int(height * 0.58)),
        subtitle,
        font=subtitle_font,
        fill=(220, 220, 225, 230),
    )

    accent_draw = ImageDraw.Draw(canvas)
    accent_draw.rounded_rectangle(
        (int(width * 0.08), int(height * 0.72), int(width * 0.08) + int(width * 0.12), int(height * 0.725)),
        radius=2,
        fill=(*ACCENT_RED, 255),
    )

    return canvas.convert("RGB")


def write_png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)
    print(f"Wrote {path.relative_to(ROOT)}")


def main() -> None:
    for folder, size in MIPMAP_SIZES.items():
        write_png(RES / folder / "ic_launcher.png", render_launcher_icon(size))
        write_png(RES / folder / "ic_launcher_round.png", render_round_icon(size))

    write_png(RES / "drawable-xxxhdpi" / "tv_banner.png", render_tv_banner(1280, 720))
    write_png(RES / "drawable-xhdpi" / "tv_banner.png", render_tv_banner(640, 360))

    foreground = render_launcher_foreground(432)
    write_png(RES / "drawable" / "ic_launcher_foreground.png", foreground)
    print("Done.")


if __name__ == "__main__":
    main()
