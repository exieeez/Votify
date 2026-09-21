#!/usr/bin/env bash
#
# Votify — установка одной командой (Linux).
#
#   curl -fsSL https://github.com/exieeez/Votify/releases/latest/download/install.sh | bash
#
# Что делает:
#   • находит последний релиз Votify на GitHub;
#   • скачивает AppImage под вашу архитектуру и кладёт в ~/.local/bin/Votify.AppImage;
#   • добавляет ярлык «Votify» в меню приложений (иконка тоже скачивается);
#   • root не нужен, файлы проекта не трогаются.
#
# Ключи (можно дописать в конец команды после «bash -s --»):
#   --deb             поставить пакет .deb через dpkg (Debian/Ubuntu, нужен sudo)
#   --version v1.0.0  конкретная версия вместо последней
#   --dir ПУТЬ        куда положить AppImage (по умолчанию ~/.local/bin)
#   --no-shortcut     не создавать ярлык в меню приложений
#   --uninstall       удалить установленный Votify (AppImage, ярлык, иконку)
#
# Примеры:
#   curl -fsSL .../install.sh | bash
#   curl -fsSL .../install.sh | bash -s -- --deb
#   curl -fsSL .../install.sh | bash -s -- --version v1.0.0
#
set -euo pipefail

REPO="exieeez/Votify"
MODE="appimage"
VERSION=""
BIN_DIR="${HOME}/.local/bin"
SHORTCUT=1

while [ $# -gt 0 ]; do
  case "$1" in
    --deb) MODE="deb" ;;
    --appimage) MODE="appimage" ;;
    --version) VERSION="${2:-}"; shift ;;
    --dir) BIN_DIR="${2:-}"; shift ;;
    --no-shortcut) SHORTCUT=0 ;;
    --uninstall) MODE="uninstall" ;;
    -h|--help)
      sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Неизвестный ключ: $1 (см. --help)" >&2; exit 2 ;;
  esac
  shift
done

say() { printf '\033[1m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

APPIMAGE_PATH="${BIN_DIR}/Votify.AppImage"
LAUNCHER="${BIN_DIR}/votify"
DESKTOP_FILE="${HOME}/.local/share/applications/votify.desktop"
ICON_PATH="${HOME}/.local/share/icons/hicolor/512x512/apps/votify.png"

# ---------------------------------------------------------------- удаление
if [ "$MODE" = "uninstall" ]; then
  rm -f "$APPIMAGE_PATH" "$LAUNCHER" "$DESKTOP_FILE" "$ICON_PATH"
  say "Votify удалён: AppImage, запускалка и ярлык."
  echo "Настройки и кэш остались в ~/.config/Votify — удалите папку вручную, если не нужны."
  exit 0
fi

command -v curl >/dev/null 2>&1 || die "Нужен curl."
command -v uname >/dev/null 2>&1 || die "Нужен uname."

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64 | amd64) IS_X64=1 ;;
  aarch64 | arm64) IS_X64=0 ;;
  *) die "Неизвестная архитектура: $ARCH" ;;
esac

API="https://api.github.com/repos/${REPO}/releases/latest"
if [ -n "$VERSION" ]; then
  case "$VERSION" in v*) ;; *) VERSION="v${VERSION}" ;; esac
  API="https://api.github.com/repos/${REPO}/releases/tags/${VERSION}"
fi

say "→ Ищу релиз Votify на GitHub…"
JSON="$(curl -fsSL "$API")" ||
  die "Не получилось получить релиз ($API). Проверьте интернет и что релиз уже опубликован."

TAG="$(printf '%s' "$JSON" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
URLS="$(printf '%s' "$JSON" |
  grep -o '"browser_download_url"[[:space:]]*:[[:space:]]*"[^"]*"' |
  sed 's/.*"\(https[^"]*\)".*/\1/')"
[ -n "$URLS" ] || die "В релизе ${TAG:-?} нет файлов для скачивания."

# Ссылка под нашу архитектуру: x86_64 берём без arm, arm — только arm.
pick() {
  local ext="$1" url=""
  if [ "$IS_X64" = "1" ]; then
    url="$(printf '%s\n' "$URLS" | grep -E "$ext" | grep -viE 'arm64|aarch64' | head -1 || true)"
  else
    url="$(printf '%s\n' "$URLS" | grep -E "$ext" | grep -iE 'arm64|aarch64' | head -1 || true)"
  fi
  printf '%s' "$url"
}

# ---------------------------------------------------------------- .deb
if [ "$MODE" = "deb" ]; then
  DEB_URL="$(pick '\.deb$')"
  [ -n "$DEB_URL" ] || die "В релизе ${TAG:-?} нет .deb (он собирается только для Linux-релизов)."
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  say "→ Скачиваю $(basename "$DEB_URL")…"
  curl -fL --progress-bar "$DEB_URL" -o "$TMP/votify.deb"
  say "→ Ставлю пакет (нужен sudo)…"
  if command -v apt-get >/dev/null 2>&1; then
    sudo dpkg -i "$TMP/votify.deb" || sudo apt-get -f install -y
  else
    sudo dpkg -i "$TMP/votify.deb"
  fi
  say "Готово: Votify ${TAG:-} установлен как пакет. Запуск — «Votify» в меню приложений."
  exit 0
fi

# ---------------------------------------------------------------- AppImage
APPIMAGE_URL="$(pick '\.AppImage$')"
[ -n "$APPIMAGE_URL" ] || die "В релизе ${TAG:-?} нет AppImage под архитектуру $ARCH."

mkdir -p "$BIN_DIR"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

say "→ Скачиваю $(basename "$APPIMAGE_URL")…"
curl -fL --progress-bar "$APPIMAGE_URL" -o "$TMP/Votify.AppImage"
chmod +x "$TMP/Votify.AppImage"
install -m 755 "$TMP/Votify.AppImage" "$APPIMAGE_PATH"
ln -sf "$APPIMAGE_PATH" "$LAUNCHER"

if [ "$SHORTCUT" = "1" ]; then
  ICON_LINE="Icon=votify"
  ICON_URL="$(printf '%s\n' "$URLS" | grep -E 'votify-icon\.png$' | head -1 || true)"
  if [ -n "$ICON_URL" ]; then
    mkdir -p "$(dirname "$ICON_PATH")"
    if curl -fsSL "$ICON_URL" -o "$ICON_PATH"; then
      ICON_LINE="Icon=${ICON_PATH}"
    else
      warn "Иконку скачать не удалось — ярлык будет с системной."
    fi
  fi
  mkdir -p "$(dirname "$DESKTOP_FILE")"
  cat >"$DESKTOP_FILE" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Votify
Comment=Музыкальный плеер Votify
Exec=${APPIMAGE_PATH} %U
${ICON_LINE}
Terminal=false
Categories=AudioVideo;Audio;Player;
StartupWMClass=Votify
DESKTOP
  update-desktop-database "${HOME}/.local/share/applications" >/dev/null 2>&1 || true
fi

say "Готово: Votify ${TAG:-} установлен."
echo "  Запуск:  votify   (файл: ${APPIMAGE_PATH})"
echo "  Или из меню приложений — «Votify»."
case ":${PATH}:" in
  *":${BIN_DIR}:"*) ;;
  *) warn "Папки ${BIN_DIR} нет в PATH — добавьте её, чтобы команда «votify» работала:"
     echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bashrc" ;;
esac
