#!/usr/bin/env bash
#
# Перестройка веток Votify: две долгоживущие ветки вместо кучи временных.
#
#   pc      — версия для компьютера (Electron + сервер + веб-интерфейс)
#   mobile  — версия для телефона (Android-приложение)
#
# Знания о репозитории:
#   main    — старые ручные выгрузки ПК-папки (ветка по умолчанию), без android/;
#   android — старая ветка телефона (ПК-код + android/);
#   my-pc   — версия 1.0 с компьютера (её заливал владелец). Она уже влита в ветку
#             сессии arena/01a0ae5a-votify вместе с иконками, ярлыком на телефон и скриптами;
#   arena/* — временные ветки сессий Arena, самая свежая — 01a0ae5a-votify.
#
# Скрипт запрашивает подтверждение на каждый опасный шаг. Запускать в Git Bash
# (Windows), в терминале macOS/Linux — из корня клона репозитория.
#
#   bash scripts/git-split-branches.sh --dry-run   # только показать план
#   bash scripts/git-split-branches.sh             # сделать (с вопросами)
#   bash scripts/git-split-branches.sh --yes       # сделать без вопросов
#
set -euo pipefail

PC_BRANCH="pc"
MOBILE_BRANCH="mobile"
SESSION_BRANCH="arena/01a0ae5a-votify"

DRY_RUN=0
ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --yes|-y) ASSUME_YES=1 ;;
    -h|--help)
      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Неизвестный ключ: $arg (см. --help)" >&2; exit 2 ;;
  esac
done

say()  { printf '\033[1m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

run() {
  if [ "$DRY_RUN" = "1" ]; then
    printf '  [dry-run] %s\n' "$*"
  else
    printf '  $ %s\n' "$*"
    "$@"
  fi
}

confirm() {
  [ "$ASSUME_YES" = "1" ] && return 0
  printf '%s [y/N] ' "$1"
  read -r answer
  case "$answer" in [yYдД]*) return 0 ;; *) return 1 ;; esac
}

# ---------------------------------------------------------------- проверки
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Это не git-репозиторий: запустите скрипт из клона Votify."
git remote get-url origin >/dev/null 2>&1 || die "У репозитория нет remote 'origin'."

if [ -n "$(git status --porcelain)" ]; then
  warn "В рабочей папке есть незакоммиченные изменения:"
  git status --short | head -20
  confirm "Продолжить? Незакоммиченные правки переедут в новую ветку." || die "Отменено. Сначала закоммитьте или спрячьте изменения (git stash)."
fi

say "→ Обновляю данные с GitHub…"
run git fetch origin --prune

# Свежайший код лежит в ветке сессии Arena; если её уже удалили — берём текущий HEAD.
SESSION_REF=""
if git rev-parse --verify --quiet "origin/$SESSION_BRANCH" >/dev/null; then
  SESSION_REF="origin/$SESSION_BRANCH"
elif git rev-parse --verify --quiet "$SESSION_BRANCH" >/dev/null; then
  SESSION_REF="$SESSION_BRANCH"
else
  SESSION_REF="HEAD"
  warn "Ветка $SESSION_BRANCH не найдена — беру текущий HEAD ($(git rev-parse --short HEAD))."
fi
say "Источник свежего кода: $SESSION_REF ($(git rev-parse --short "$SESSION_REF"))"

cat <<PLAN

План:
  1. создать ветку $PC_BRANCH      — версия для ПК (весь свежий код, версия 1.0 с ПК)
  2. создать ветку $MOBILE_BRANCH  — версия для телефона (тот же код; APK собирается из неё)
  3. по желанию: удалить старые ветки (android, arena/*, main) и сделать $PC_BRANCH веткой по умолчанию

  Обе новые ветки содержат весь проект целиком: один и тот же код с двумя линиями
  релизов (APK для телефона, сборка для ПК), поэтому расхождений между ними нет.

PLAN
confirm "Создаём ветки $PC_BRANCH и $MOBILE_BRANCH?" || die "Отменено."

# ---------------------------------------------------------------- ветка ПК
say "→ Ветка ПК ($PC_BRANCH)"
run git checkout -B "$PC_BRANCH" "$SESSION_REF"

# Ничего не досоздаём: основа — самая свежая версия проекта со всеми файлами.
# Если когда-нибудь понадобятся старые дизайн-исходники, они лежали в ветке main:
#   git checkout origin/main -- design/
if git rev-parse --verify --quiet origin/main >/dev/null; then
  say "  (в ветке main остались старые дизайн-исходники — при необходимости: git checkout origin/main -- design/)"
fi

if [ "$DRY_RUN" = "0" ] && git log --oneline -1 origin/"$PC_BRANCH" >/dev/null 2>&1; then
  :
fi
confirm "Отправить ветку $PC_BRANCH на GitHub?" && run git push -u origin "$PC_BRANCH" || warn "Пропущено: $PC_BRANCH осталась только локально."

# ---------------------------------------------------------------- ветка телефона
say "→ Ветка телефона ($MOBILE_BRANCH)"
run git checkout -B "$MOBILE_BRANCH" "$SESSION_REF"
confirm "Отправить ветку $MOBILE_BRANCH на GitHub?" && run git push -u origin "$MOBILE_BRANCH" || warn "Пропущено: $MOBILE_BRANCH осталась только локально."

# ---------------------------------------------------------------- ветка по умолчанию
say "→ Ветка по умолчанию"
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  if confirm "Сделать $PC_BRANCH веткой по умолчанию на GitHub?"; then
    run gh api -X PATCH "repos/:owner/:repo" -f default_branch="$PC_BRANCH" >/dev/null \
      && say "  готово: ветка по умолчанию — $PC_BRANCH"
  fi
else
  warn "GitHub CLI (gh) недоступен — смените ветку по умолчанию вручную:"
  echo "  GitHub → Settings → Branches → Default branch → $PC_BRANCH"
fi

# ---------------------------------------------------------------- чистка
say "→ Чистка старых веток"
OLD_BRANCHES="android $(git branch -r --list 'origin/arena/*' | sed 's#origin/##' | tr '\n' ' ')"
for b in $OLD_BRANCHES; do
  [ -z "$b" ] && continue
  [ "$b" = "$SESSION_BRANCH" ] && continue
  if confirm "Удалить на GitHub ветку $b?"; then
    run git push origin --delete "$b" || warn "  не получилось удалить $b (возможно, защищена)"
  fi
done

if confirm "Удалить на GitHub старую ветку main (её заменяет $PC_BRANCH)?"; then
  run git push origin --delete main || warn "  GitHub не даёт удалить ветку по умолчанию — сначала смените её (см. выше)"
fi

if confirm "Удалить сейчас и ветку $SESSION_BRANCH (сессия Arena потеряет свою ветку)?"; then
  run git push origin --delete "$SESSION_BRANCH" || true
else
  warn "Ветку $SESSION_BRANCH оставляем: она привязана к текущей сессии Arena. Удалите её позже."
fi

# Рабочая папка остаётся на ветке ПК: с ней работают чаще, чем с телефона.
if [ "$DRY_RUN" = "0" ] && git rev-parse --verify --quiet "refs/heads/$PC_BRANCH" >/dev/null; then
  git checkout -q "$PC_BRANCH" 2>/dev/null || true
fi

say ""
say "Готово. Итог:"
if [ "$DRY_RUN" = "0" ]; then
  git branch -a | sed 's/^/  /'
else
  echo "  (это был dry-run, ничего не изменено)"
fi
cat <<'NEXT'

Дальше:
  • APK для телефона собирается из ветки mobile (CI: Android CI / Release APK);
  • сборка для ПК — из ветки pc;
  • залить папку с ПК: scripts/push-folder.ps1 (Windows) или docs/GIT-BRANCHES.md.
NEXT
