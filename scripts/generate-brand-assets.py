#!/usr/bin/env python3
"""Renders GameDay's original launcher icon and Fire TV banner.

The mark is deliberately simple: a dark stadium-blue field, a green yard-line accent and a "GD"
wordmark. No league, team or broadcaster marks are used anywhere in the artwork.

Usage:  python3 scripts/generate-brand-assets.py
Output: apps/fire-tv/src/main/res/mipmap-*/ic_launcher.png
        apps/fire-tv/src/main/res/drawable-xhdpi/tv_banner.png
"""
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover - developer convenience
    sys.exit("Pillow is required: pip install Pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "apps", "fire-tv", "src", "main", "res")

BG = (10, 14, 19, 255)
SURFACE = (20, 27, 36, 255)
ACCENT = (43, 212, 107, 255)
TEXT = (243, 246, 249, 255)
MUTED = (155, 168, 181, 255)

ICON_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def load_font(size):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def rounded_background(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    radius = int(size * 0.22)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=BG)
    # Yard-line accent across the lower third.
    d.rounded_rectangle(
        [0, int(size * 0.74), size - 1, int(size * 0.82)],
        radius=max(1, size // 48),
        fill=ACCENT,
    )
    # Single subtle highlight arc suggesting a stadium bowl.
    d.rounded_rectangle(
        [int(size * 0.10), int(size * 0.10), int(size * 0.90), int(size * 0.62)],
        radius=int(size * 0.14),
        outline=SURFACE,
        width=max(2, size // 32),
    )
    return img, d


def draw_icon(size, path):
    img, d = rounded_background(size)
    font = load_font(int(size * 0.40))
    text = "GD"
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text(((size - tw) / 2 - bbox[0], int(size * 0.30) - bbox[1]), text, font=font, fill=TEXT)
    # Small accent dot under the wordmark.
    r = max(2, size // 26)
    cx, cy = size // 2, int(size * 0.655)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACCENT)
    img.save(path, "PNG")


def draw_banner(width, height, path):
    img = Image.new("RGBA", (width, height), BG)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, width - 1, height - 1], fill=BG)
    # Accent bar on the left edge.
    d.rectangle([0, 0, int(width * 0.018), height], fill=ACCENT)

    title_font = load_font(int(height * 0.42))
    sub_font = load_font(int(height * 0.16))

    d.text((int(width * 0.06), int(height * 0.14)), "GameDay", font=title_font, fill=TEXT)
    d.text(
        (int(width * 0.065), int(height * 0.62)),
        "YOUR NFL APP, LAUNCHED PROPERLY",
        font=sub_font,
        fill=MUTED,
    )
    # Football-ish ellipse on the right.
    ex0, ey0 = int(width * 0.76), int(height * 0.24)
    ex1, ey1 = int(width * 0.95), int(height * 0.76)
    d.ellipse([ex0, ey0, ex1, ey1], fill=SURFACE, outline=ACCENT, width=max(2, height // 40))
    d.line(
        [int(width * 0.80), int(height * 0.50), int(width * 0.91), int(height * 0.50)],
        fill=ACCENT,
        width=max(2, height // 44),
    )
    img.save(path, "PNG")


def main():
    for folder, size in ICON_SIZES.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        draw_icon(size, os.path.join(out_dir, "ic_launcher.png"))
    banner_dir = os.path.join(RES, "drawable-xhdpi")
    os.makedirs(banner_dir, exist_ok=True)
    draw_banner(320, 180, os.path.join(banner_dir, "tv_banner.png"))
    print("brand assets written to", RES)


if __name__ == "__main__":
    main()
