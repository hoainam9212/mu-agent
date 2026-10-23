"""Thu nhỏ ảnh Paparazzi thành JPEG gọn để lưu vào docs/ui-preview (xem trên GitHub hoặc kéo về)."""

import pathlib
import sys

from PIL import Image

ORDER = [
    "onboarding", "home", "caveFireBurning", "caveFireOut", "caveNotInside", "celebrate", "caveFullCrew",
    "caveSquareSheet", "squareFireBurning", "squareFireOut", "squareFullCrew", "squareFullCrewAsleep", "squareSmall",
    "squareSeven", "squareSevenAsleep", "squareEight",
    "squarePaintedFive", "squarePaintedTwoAsleep",
]
WIDTH = 540

src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
for i, name in enumerate(ORDER, 1):
    match = sorted(src.glob(f"*_{name}.png"))
    if not match:
        print(f"thiếu ảnh {name}")
        continue
    im = Image.open(match[0]).convert("RGB")
    # Ảnh hang vuông giữ nguyên cỡ thật (1080px) để xem chi tiết.
    if not name.startswith("square"):
        im = im.resize((WIDTH, round(im.height * WIDTH / im.width)), Image.LANCZOS)
    im.save(out / f"{i}-{name}.jpg", "JPEG", quality=85)
    print(out / f"{i}-{name}.jpg")
