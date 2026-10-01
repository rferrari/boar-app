#!/usr/bin/env python3
"""Knowledge Sanctuary preview art: the source PNGs → 512x512 WebP in assets/sanctuary/.

    python3 scripts/prepare-sanctuary-assets.py [SOURCE_DIR]   # default: assets/

Sources (not committed): boar_sanctuary_entrance_{1x1,1x2,2x1,2x2}.png and
boar_matrix_download_{combt,music,code,survive}.png. Writes entrance-1..4.webp and
boar-{combat,music,code,survive}.webp, and a contact sheet to check them by eye.
"""
import sys
from pathlib import Path

from PIL import Image

SRC = Path(sys.argv[1] if len(sys.argv) > 1 else "assets")
OUT = Path("assets/sanctuary")
SIZE = 512

FILES = {
    "entrance-1": "boar_sanctuary_entrance_1x1.png",
    "entrance-2": "boar_sanctuary_entrance_1x2.png",
    "entrance-3": "boar_sanctuary_entrance_2x1.png",
    "entrance-4": "boar_sanctuary_entrance_2x2.png",
    "boar-combat": "boar_matrix_download_combt.png",
    "boar-music": "boar_matrix_download_music.png",
    "boar-code": "boar_matrix_download_code.png",
    "boar-survive": "boar_matrix_download_survive.png",
}


def square(img: Image.Image) -> Image.Image:
    """Centre-crop to a square, then resize to SIZE."""
    w, h = img.size
    side = min(w, h)
    left, top = (w - side) // 2, (h - side) // 2
    return img.crop((left, top, left + side, top + side)).resize((SIZE, SIZE), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    done = []
    for name, src in FILES.items():
        img = square(Image.open(SRC / src).convert("RGB"))
        path = OUT / f"{name}.webp"
        img.save(path, "WEBP", quality=82, method=6)
        done.append(img)
        print(f"{path}  {path.stat().st_size // 1024} KB")
    sheet = Image.new("RGB", (SIZE * 4, SIZE * 2))
    for i, img in enumerate(done):
        sheet.paste(img, ((i % 4) * SIZE, (i // 4) * SIZE))
    sheet.resize((SIZE * 2, SIZE)).save("build/sanctuary-contact-sheet.jpg", quality=80)


if __name__ == "__main__":
    main()
