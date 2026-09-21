<#
.SYNOPSIS
    Заливает актуальную папку Votify с компьютера на GitHub в отдельную ветку.

.DESCRIPTION
    Скрипт рассчитан на папку, которую вы просто держите на ПК (возможно, без git).
    Он инициализирует репозиторий, подключит origin, аккуратно наложит ваши файлы
    поверх выбранной ветки и отправит результат. Ничего не удаляет: если у вас не
    получится отправить — папка и история на GitHub останутся как были.

.EXAMPLE
    # залить в новую ветку pc-upload (безопасно, потом можно сделать Pull Request)
    powershell -ExecutionPolicy Bypass -File scripts\push-folder.ps1 -Folder C:\Votify

.EXAMPLE
    # залить прямо в ветку для ПК
    powershell -ExecutionPolicy Bypass -File scripts\push-folder.ps1 -Folder C:\Votify -Branch pc

.EXAMPLE
    # если GitHub отказывает («non-fast-forward») и папка точно новее — перезаписать ветку
    powershell -ExecutionPolicy Bypass -File scripts\push-folder.ps1 -Folder C:\Votify -Branch pc -Force

.NOTES
    Пароль/токен вводится в окне входа Git (Git Credential Manager). Никому не пересылайте
    токен: он даёт полный доступ к репозиторию.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Folder,
    [string]$Branch = 'pc-upload',
    [string]$RemoteUrl = 'https://github.com/exieeez/Votify.git',
    [string]$Message = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Say  ($m) { Write-Host $m -ForegroundColor Cyan }
function Warn ($m) { Write-Host $m -ForegroundColor Yellow }
function Die  ($m) { Write-Host $m -ForegroundColor Red; exit 1 }

function Invoke-Git {
    # Имя параметра не $Args: это системная переменная PowerShell, с ней путаница вызовов.
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)
    & git @GitArgs
    if ($LASTEXITCODE -ne 0) { Die "Команда не выполнена: git $($GitArgs -join ' ')" }
}

# ------------------------------------------------------------------ проверки
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Die @"
Git не установлен. Скачайте и установите Git for Windows: https://git-scm.com/download/win
(при установке оставьте галочку «Git Credential Manager» — он сам откроет браузер для входа).
"@
}

if (-not (Test-Path -LiteralPath $Folder)) { Die "Папка не найдена: $Folder" }
$Folder = (Resolve-Path -LiteralPath $Folder).Path
Set-Location -LiteralPath $Folder
Say "Папка: $Folder"

if ((Split-Path -Leaf $Folder) -eq '.git') { Die "Укажите папку проекта, а не .git" }

# ------------------------------------------------------------------ репозиторий
if (-not (Test-Path -LiteralPath (Join-Path $Folder '.git'))) {
    Say "Инициализирую git-репозиторий…"
    Invoke-Git init -b $Branch
} else {
    Say "Git-репозиторий уже есть — использую его."
    $current = (git rev-parse --abbrev-ref HEAD).Trim()
    if ($current -ne $Branch) { Invoke-Git checkout -B $Branch }
}

# origin: добавляем или проверяем
$origin = @((git remote) 2>$null | ForEach-Object { "$_".Trim() } | Where-Object { $_ -eq 'origin' })
if (-not $origin) {
    Invoke-Git remote add origin $RemoteUrl
} else {
    $url = (git remote get-url origin).Trim()
    if ($url -ne $RemoteUrl) {
        Warn "origin указывает на $url — меняю на $RemoteUrl"
        Invoke-Git remote set-url origin $RemoteUrl
    }
}

# .gitignore: без него в коммит уедет node_modules (сотни мегабайт)
$ignorePath = Join-Path $Folder '.gitignore'
$needed = @('node_modules/', 'dist/', 'release/', '*.log', '.env', '*.local', 'bin/yt-dlp.exe')
$have = @(if (Test-Path -LiteralPath $ignorePath) { Get-Content -LiteralPath $ignorePath } else { @() })
$missing = $needed | Where-Object { $have -notcontains $_ }
if ($missing) {
    Say "Дописываю в .gitignore: $($missing -join ', ')"
    Add-Content -LiteralPath $ignorePath -Value ("`n# добавлено scripts/push-folder.ps1`n" + ($missing -join "`n"))
}

Say "Забираю свежие ветки с GitHub…"
Invoke-Git fetch origin --prune

# Накладываем рабочую папку поверх выбранной ветки, не трогая файлы на диске.
$remoteBranch = ((git ls-remote --heads origin $Branch) -join '').Trim()
if ($remoteBranch) {
    Say "Ветка $Branch есть на GitHub — прикрепляюсь к её последнему коммиту (файлы на диске не трогаю)."
    Invoke-Git reset --soft "origin/$Branch"
} else {
    Warn "Ветки $Branch на GitHub нет — будет создана новая."
}

Invoke-Git add -A

# Большие файлы: GitHub не принимает файлы больше 100 МБ
$staged = git diff --cached --name-only
$big = @()
foreach ($f in $staged) {
    if (Test-Path -LiteralPath (Join-Path $Folder $f)) {
        $size = (Get-Item -LiteralPath (Join-Path $Folder $f)).Length
        if ($size -gt 90MB) { $big += "$([math]::Round($size / 1MB, 1)) МБ  $f" }
    }
}
if ($big.Count -gt 0) {
    Warn "В коммит попадают очень большие файлы (GitHub откажет):"
    $big | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    Die "Уберите их из папки или добавьте в .gitignore и запустите скрипт снова."
}

if (-not $staged) {
    Say "Изменений нет — коммитить нечего."
} else {
    Say "Файлов в коммите: $($staged.Count)"
    if (-not $Message) { $Message = "Загрузка папки с ПК ($(Get-Date -Format 'yyyy-MM-dd HH:mm'))" }
    Invoke-Git commit -m $Message
}

# ------------------------------------------------------------------ отправка
Say "Отправляю в ветку $Branch…"
if ($Force) { Invoke-Git push --force-with-lease origin "HEAD:$Branch" }
else        { Invoke-Git push origin "HEAD:$Branch" }

$webBranch = $Branch -replace '/', '%2F'
Say ""
Say "Готово! Ветка: https://github.com/exieeez/Votify/tree/$webBranch"
Say "Открыть Pull Request: https://github.com/exieeez/Votify/compare/$webBranch?expand=1"
Say ""
Write-Host "Если GitHub отказал («! [rejected] ... non-fast-forward»), значит на GitHub лежит" -ForegroundColor Yellow
Write-Host "то, чего нет в вашей папке. Посмотрите diff в Pull Request — и либо влейте его," -ForegroundColor Yellow
Write-Host "либо запустите: ... -Branch $Branch -Force  (перезапишет ветку вашей папкой)." -ForegroundColor Yellow
