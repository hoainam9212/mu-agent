"""Xoá nền xanh (#00FF00) của ảnh Gemini, tách lưới nhân vật, xuất WebP trong suốt cho app.

Dùng:
  python3 keyout.py sheet chars_sit.png sit   --cols 2 --rows 3 --out <thư mục drawable-nodpi>
  python3 keyout.py fire  fire_burning.png fire_out.png               --out <thư mục>
  python3 keyout.py bg    bg_lit.png bg_dark.png [bg_exit.png]         --out <thư mục>

Nhân vật đánh số theo thứ tự đọc (trái→phải, trên→dưới): caveman_<i>_<pose>.webp.
Lưới được cắt ở khe trống giữa các ô (không chia đều), để tay/chân lấn ô không bị cắt dính.
"""

import argparse
import pathlib

import numpy as np
from PIL import Image, ImageFilter


def key(path: pathlib.Path) -> Image.Image:
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    spill = g - np.maximum(r, b)  # độ "xanh" của điểm ảnh
    alpha = np.clip(1.0 - (spill - 25.0) / 60.0, 0, 1)
    g = np.where(spill > 0, np.maximum(r, b), g)  # khử viền xanh
    im = Image.fromarray(np.dstack([r, g, b, alpha * 255]).astype(np.uint8), "RGBA")
    im.putalpha(im.getchannel("A").filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8)))
    return im


def tight(im: Image.Image, pad: int = 6):
    ys, xs = np.nonzero(np.asarray(im.getchannel("A")) > 20)
    return max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad, im.width), min(ys.max() + pad, im.height)


def cuts(profile: np.ndarray, n: int) -> list[int]:
    """Chỗ cắt giữa n ô theo một chiều: điểm ít điểm ảnh nhất quanh mỗi mốc k/n."""
    size = len(profile)
    out = [0]
    for k in range(1, n):
        lo, hi = int(size * (k / n - 0.12)), int(size * (k / n + 0.12))
        out.append(lo + int(np.argmin(profile[lo:hi])))
    return out + [size]


def save(im: Image.Image, out: pathlib.Path, name: str, height: int):
    im = im.resize((round(im.width * height / im.height), height), Image.LANCZOS)
    im.save(out / f"{name}.webp", "WEBP", quality=88, method=6)
    print(name, im.size)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["sheet", "fire", "bg"])
    ap.add_argument("files", nargs="+")
    ap.add_argument("--cols", type=int, default=2)
    ap.add_argument("--rows", type=int, default=3)
    ap.add_argument("--out", required=True)
    ap.add_argument("--height", type=int, default=300)
    args = ap.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    if args.mode == "sheet":
        path, pose = pathlib.Path(args.files[0]), args.files[1]
        im = key(path)
        mask = np.asarray(im.getchannel("A")) > 20
        xs = cuts(mask.sum(0), args.cols)
        for c in range(args.cols):
            ys = cuts(mask[:, xs[c]:xs[c + 1]].sum(1), args.rows)
            for r in range(args.rows):
                cell = im.crop((xs[c], ys[r], xs[c + 1], ys[r + 1]))
                save(cell.crop(tight(cell)), out, f"caveman_{r * args.cols + c}_{pose}", args.height)
    elif args.mode == "fire":
        burn, dead = key(pathlib.Path(args.files[0])), key(pathlib.Path(args.files[1]))
        b1, b2 = tight(burn), tight(dead)
        box = (min(b1[0], b2[0]), min(b1[1], b2[1]), max(b1[2], b2[2]), max(b1[3], b2[3]))  # cùng khung cho cả hai
        save(burn.crop(box), out, "fire_burning", 360)
        save(dead.crop(box), out, "fire_out", 360)
    else:
        for path, name in zip(args.files, ["cave_lit", "cave_dark", "cave_exit"]):
            Image.open(path).convert("RGB").save(out / f"{name}.webp", "WEBP", quality=80, method=6)
            print(name)


if __name__ == "__main__":
    main()
