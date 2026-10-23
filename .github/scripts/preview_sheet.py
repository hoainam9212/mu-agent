"""Ghép ảnh Paparazzi thành 2 tấm nhỏ và in base64 ra log (để đọc lại qua API log của Actions)."""

import base64
import io
import pathlib
import sys

from PIL import Image

ORDER = ["onboarding", "home", "caveFireBurning", "caveFireOut", "caveNotInside", "celebrate"]
WIDTH = 300
GAP = 10

images_dir = pathlib.Path(sys.argv[1])
shots = []
for name in ORDER:
    match = sorted(images_dir.glob(f"*_{name}.png"))
    if not match:
        print(f"thiếu ảnh {name}")
        continue
    im = Image.open(match[0]).convert("RGB")
    shots.append(im.resize((WIDTH, round(im.height * WIDTH / im.width))))

for idx in range(0, len(shots), 3):
    chunk = shots[idx : idx + 3]
    sheet = Image.new("RGB", (WIDTH * len(chunk) + GAP * (len(chunk) - 1), max(i.height for i in chunk)), (60, 60, 60))
    for i, im in enumerate(chunk):
        sheet.paste(im, (i * (WIDTH + GAP), 0))
    buf = io.BytesIO()
    sheet.save(buf, "JPEG", quality=62)
    data = base64.b64encode(buf.getvalue()).decode()
    print(f"=====SHEET{idx // 3} {len(data)}")
    for i in range(0, len(data), 3000):
        print(data[i : i + 3000])
    print("=====END")
