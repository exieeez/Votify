#!/usr/bin/env bash
# Генератор вариантов иконки приложения (Android, adaptive icons 108dp = 432px).
#
# Рисует три набора слоёв в android/app/src/main/res/drawable-nodpi/:
#   ic_launcher_visual.png       — «как сейчас» (эталон, файл уже в репозитории и не трогается)
#   ic_launcher_visual_bw.png    — «Ч/Б»: та же иконка, но без серой полосы
#   ic_launcher_visual_glass.png — «стеклянная» (старый логотип в духе iOS 27: прозрачность,
#                                  светлая кромка, тёмный контур, блик сверху)
#   ic_launcher_visual_classic.png — «старая»: тёмная плитка, чёрный диск, полосы белые/серая
#   ic_launcher_mono.png         — монохромный слой для тематических иконок (не меняется)
# Плюс превью всех вариантов: assets/app-icon-variants.png
#
# Геометрия снята с текущей иконки (сканы строк/столбцов 432x432):
#   плитка 74..358 (284) радиус 80, кольцо R=100/толщина 6 (#333333),
#   полосы w=17.5: левая h=70, центральная h=124, правая h=97 (центры x 180.5 / 216 / 252, y 216).
set -euo pipefail

cd "$(dirname "$0")/.."
RES="android/app/src/main/res/drawable-nodpi"
S=4                                # супер-сэмплинг: рисуем в 4x, потом уменьшаем
C=$((432 * S))                     # размер холста

# Параметры в сетке 432px (умножаются на S внутри draw-скрипта)
sc() { printf '%s' "$(( $1 * S ))"; }

draw_common() {
  # $1 — файл, $2... — параметры цветов: tile, ring, bar1, bar2, bar3
  local out="$1" tile="$2" ring="$3" b1="$4" b2="$5" b3="$6"
  convert -size "${C}x${C}" xc:none \
    -fill "$tile" -draw "roundrectangle $(sc 74),$(sc 74) $(sc 358),$(sc 358) $(sc 80),$(sc 80)" \
    -fill none -stroke "$ring" -strokewidth "$(sc 6)" \
    -draw "circle $(sc 216),$(sc 216) $(sc 216),$(sc 118)" \
    -stroke none -fill "$b1" -draw "roundrectangle $(sc 172),$(sc 182) $(sc 189),$(sc 250) $(sc 9),$(sc 9)" \
    -fill "$b2" -draw "roundrectangle $(sc 207),$(sc 155) $(sc 224),$(sc 277) $(sc 9),$(sc 9)" \
    -fill "$b3" -draw "roundrectangle $(sc 243),$(sc 168) $(sc 260),$(sc 264) $(sc 9),$(sc 9)" \
    -resize 432x432 -depth 8 "PNG32:$out"
}

echo "→ ic_launcher_visual_bw.png (Ч/Б: серая полоса — белая, всё остальное как сейчас)"
# Вариант «как сейчас» — уже в репозитории (ic_launcher_visual.png). Ч/Б версия получается
# из него же: уровни поднимают только серую полосу (70% -> 100%), остальное побитово то же.
convert "$RES/ic_launcher_visual.png" -region 40x120+234+156 -level 0%,70% +region \
  -depth 8 "PNG32:$RES/ic_launcher_visual_bw.png"

echo "→ ic_launcher_visual_glass.png (старая иконка в духе iOS 27)"
# 1) стеклянная плитка: полупрозрачная тёмная заливка
convert -size "${C}x${C}" xc:none \
  -fill 'rgba(30,30,30,0.78)' -draw "roundrectangle $(sc 74),$(sc 74) $(sc 358),$(sc 358) $(sc 80),$(sc 80)" \
  /tmp/glass_tile.png
# 2) блик сверху (лёгкий градиент белого, обрезанный по плитке)
convert -size "${C}x${C}" gradient:'rgba(255,255,255,0.22)-rgba(255,255,255,0)' \
  \( -size "${C}x${C}" xc:none -fill white -draw "roundrectangle $(sc 74),$(sc 74) $(sc 358),$(sc 358) $(sc 80),$(sc 80)" \) \
  -compose DstIn -composite /tmp/glass_sheen.png
# 3) тёмный контур (iOS 27: darkened edge) + светлая кромка
convert -size "${C}x${C}" xc:none \
  -fill none -stroke 'rgba(0,0,0,0.45)' -strokewidth "$(sc 3)" \
  -draw "roundrectangle $(sc 76),$(sc 76) $(sc 356),$(sc 356) $(sc 79),$(sc 79)" \
  -stroke 'rgba(255,255,255,0.30)' -strokewidth "$(sc 2)" \
  -draw "roundrectangle $(sc 76),$(sc 76) $(sc 356),$(sc 356) $(sc 79),$(sc 79)" \
  /tmp/glass_edge.png
# 4) внутренний круг: полупрозрачная тёмная заливка + светлое кольцо
convert -size "${C}x${C}" xc:none \
  -fill 'rgba(0,0,0,0.55)' -stroke none -draw "circle $(sc 216),$(sc 216) $(sc 216),$(sc 121)" \
  -fill none -stroke 'rgba(255,255,255,0.16)' -strokewidth "$(sc 3)" \
  -draw "circle $(sc 216),$(sc 216) $(sc 216),$(sc 118)" \
  /tmp/glass_circle.png
# 5) полосы: две белые, третья приглушённая (как в старой иконке) + тёмный контур
convert -size "${C}x${C}" xc:none -fill none \
  -draw "stroke rgba(0,0,0,0.35) stroke-width $(sc 2) roundrectangle $(sc 172),$(sc 182) $(sc 189),$(sc 250) $(sc 9),$(sc 9)" \
  -draw "stroke rgba(0,0,0,0.35) stroke-width $(sc 2) roundrectangle $(sc 207),$(sc 155) $(sc 224),$(sc 277) $(sc 9),$(sc 9)" \
  -draw "stroke rgba(0,0,0,0.35) stroke-width $(sc 2) roundrectangle $(sc 243),$(sc 168) $(sc 260),$(sc 264) $(sc 9),$(sc 9)" \
  -fill 'rgba(255,255,255,1.0)' -stroke none -draw "roundrectangle $(sc 173),$(sc 183) $(sc 188),$(sc 249) $(sc 9),$(sc 9)" \
  -fill 'rgba(255,255,255,1.0)' -stroke none -draw "roundrectangle $(sc 208),$(sc 156) $(sc 223),$(sc 276) $(sc 9),$(sc 9)" \
  -fill 'rgba(255,255,255,0.72)' -stroke none -draw "roundrectangle $(sc 244),$(sc 169) $(sc 259),$(sc 263) $(sc 9),$(sc 9)" \
  /tmp/glass_bars.png

convert /tmp/glass_tile.png /tmp/glass_sheen.png -compose over -composite \
  /tmp/glass_circle.png -compose over -composite \
  /tmp/glass_bars.png -compose over -composite \
  /tmp/glass_edge.png -compose over -composite \
  -resize 432x432 -depth 8 "PNG32:$RES/ic_launcher_visual_glass.png"

echo "→ ic_launcher_visual_classic.png (старая иконка, плоская: тёмная плитка + чёрный диск)"
# Параметры сняты с design/logotip_votify.jpg: плитка #1E1E1E, диск #000000,
# полосы #FEFEFE/#FEFEFE/#B2B2B2, кольцо чуть светлее диска. Углы старые — чуть прямее.
convert -size "${C}x${C}" xc:none \
  -fill '#1E1E1E' -draw "roundrectangle $(sc 74),$(sc 74) $(sc 358),$(sc 358) $(sc 100),$(sc 100)" \
  -fill '#000000' -stroke none -draw "circle $(sc 216),$(sc 216) $(sc 216),$(sc 116)" \
  -fill none -stroke '#3A3A3A' -strokewidth "$(sc 3)" -draw "circle $(sc 216),$(sc 216) $(sc 216),$(sc 118)" \
  -stroke none -fill '#FEFEFE' -draw "roundrectangle $(sc 172),$(sc 182) $(sc 189),$(sc 250) $(sc 9),$(sc 9)" \
  -fill '#FEFEFE' -draw "roundrectangle $(sc 207),$(sc 155) $(sc 224),$(sc 277) $(sc 9),$(sc 9)" \
  -fill '#B2B2B2' -draw "roundrectangle $(sc 243),$(sc 168) $(sc 260),$(sc 264) $(sc 9),$(sc 9)" \
  -resize 432x432 -depth 8 "PNG32:$RES/ic_launcher_visual_classic.png"

echo "→ assets/app-icon-variants.png (превью всех вариантов для документации)"
D="$RES"
convert -size 200x200 "$D/ic_launcher_visual.png" -resize 200x200 /tmp/v1.png
convert -size 200x200 "$D/ic_launcher_visual_bw.png" -resize 200x200 /tmp/v2.png
convert -size 200x200 "$D/ic_launcher_visual_glass.png" -resize 200x200 /tmp/v3.png
convert -size 200x200 "$D/ic_launcher_visual_classic.png" -resize 200x200 /tmp/v4.png
convert -size 1000x360 xc:'#121212' \
  /tmp/v1.png -geometry +40+40 -composite /tmp/v2.png -geometry +280+40 -composite \
  /tmp/v3.png -geometry +520+40 -composite /tmp/v4.png -geometry +760+40 -composite \
  -font DejaVu-Sans -pointsize 20 -fill '#E5E5EA' \
  -annotate +40+290 'Как сейчас' -annotate +280+290 'Ч/Б' \
  -annotate +520+290 'В духе iOS 27' -annotate +760+290 'Старая' \
  -pointsize 16 -fill '#8E8E93' \
  -annotate +40+325 'Votify для Android' -annotate +280+325 'без серой полосы' \
  -annotate +520+325 'прежний логотип в стекле' -annotate +760+325 'прежний логотип как есть' \
  assets/app-icon-variants.png

echo "→ готово:"
identify "$RES/ic_launcher_visual.png" "$RES/ic_launcher_visual_bw.png" "$RES/ic_launcher_visual_glass.png" "$RES/ic_launcher_visual_classic.png"
