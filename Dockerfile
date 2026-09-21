# Votify в контейнере: сам интерфейс (браузер + ярлык на iPhone/Android) и его бекенд.
#
# Образ самодостаточный: внутри Node, свежий yt-dlp (Linux) и статика из src/.
# Разверните где угодно (VPS, NAS, Raspberry Pi, Render/Fly) и добавьте адрес на экран
# «Домой» — приложение будет работать без компьютера.
#
#   docker build -t votify .
#   docker run -p 17217:17217 -v votify-data:/root/.config/Votify votify
#
# Переменные окружения:
#   VOTIFY_PORT=17217          — порт
#   VOTIFY_BASIC_AUTH=user:pass — закрыть паролем (обязательно для публичного сервера)
#   VOTIFY_FIREBASE_CONFIG={...} — Firebase Web Config (аккаунты и Мастерская тем)

FROM node:20-bookworm-slim

# curl — за свежим yt-dlp; ca-certificates — https; python3 — на случай других сборок yt-dlp.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl python3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Зависимости: только runtime (Electron и сборка приложения тут не нужны).
# npm install, а не npm ci: в репозитории старый lock-файл (из версии 0.7.0), и npm ci
# на нём падает — тот же приём использует release.yml.
COPY package.json package-lock.json ./
RUN npm install --omit=dev --no-audit --no-fund --ignore-scripts

# Свежий yt-dlp на каждом билде: YouTube ломает старые версии, и «тихий» плеер
# обычно означает именно устаревший бинарник. Пересборка образа = обновление.
RUN mkdir -p /app/bin \
    && curl -fsSL -o /app/bin/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux \
    && chmod +x /app/bin/yt-dlp \
    && /app/bin/yt-dlp --version

COPY . .

ENV NODE_ENV=production \
    VOTIFY_PORT=17217

EXPOSE 17217

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s \
    CMD curl -fsS "http://127.0.0.1:${VOTIFY_PORT}/api/network/lan" >/dev/null || exit 1

CMD ["node", "server.js"]
