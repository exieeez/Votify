#!/usr/bin/env bash
# Иконки веб-версии (ярлык «на экран Домой» на iPhone/Android + установка в Chrome).
#
# Из src/icon.png (512x512) делает:
#   src/icons/icon-192.png         — для manifest (Chrome/Android)
#   src/icons/icon-512.png         — для manifest (splash на Android)
#   src/icons/icon-maskable-512.png — maskable: иконка внутри safe zone, фон чёрный
#   src/icons/apple-touch-icon.png  — 180x180, iOS сам скругляет углы
#
# Запуск: bash scripts/generate-web-icons.sh
set -euo pipefail

cd "$(dirname "$0")/.."
SRC="src/icon.png"
OUT="src/icons"
mkdir -p "$OUT"

echo "→ $OUT/icon-192.png"
convert "$SRC" -resize 192x192 "PNG32:$OUT/icon-192.png"
echo "→ $OUT/icon-512.png"
convert "$SRC" -resize 512x512 "PNG32:$OUT/icon-512.png"
echo "→ $OUT/apple-touch-icon.png (180x180, без прозрачности — iOS подложит чёрный)"
convert "$SRC" -resize 180x180 -background black -alpha remove -alpha off "PNG24:$OUT/apple-touch-icon.png"
echo "→ $OUT/icon-maskable-512.png (иконка в safe zone maskable)"
convert -size 512x512 xc:'#1E1E1E' \
  \( "$SRC" -resize 384x384 \) -gravity center -composite \
  "PNG32:$OUT/icon-maskable-512.png"

echo "→ готово:"
identify "$OUT/icon-192.png" "$OUT/icon-512.png" "$OUT/icon-maskable-512.png" "$OUT/apple-touch-icon.png"
