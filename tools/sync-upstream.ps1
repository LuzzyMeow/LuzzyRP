# ============================================================
# sync-upstream.ps1 —— RP-Hub 上游同步脚本（AGENTS.md §4 SOP）
# ============================================================
# 用法:  .\tools\sync-upstream.ps1
# 前置:  rp-hub-reference/ 的 upstream remote 已配置（git remote add upstream ...）
# 流程:
#   1. git fetch upstream + 查看改动统计
#   2. 覆盖上游文件到 app/src/main/assets/rphub/（排除 vendor/ fonts/ 及二创文件）
#   3. 重放登记 patch（apply-patches.ps1）
#   4. 更新指纹基线（upstream-fingerprints.txt）
#   5. 提示回归清单（数据兼容 / 核心功能 / 断网 / 扩展层）
# 注意: 此脚本只做「文件同步 + patch 重放」，不提交 git、不构建 APK——
#       回归实测通过后由维护者手动 commit（AGENTS.md §3.4）。
# ============================================================

param(
    [string]$RefDir = (Join-Path $PSScriptRoot "..\rp-hub-reference"),
    [string]$TargetDir = (Join-Path $PSScriptRoot "..\app\src\main\assets\rphub"),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

Write-Host "== LuzzyRP 上游同步 =="
Write-Host "参考克隆 : $RefDir"
Write-Host "目标目录 : $TargetDir"

# ---------- 0. 参考克隆必须是干净的（不允许本地改动混入同步） ----------
Push-Location $RefDir
$status = git status --porcelain
Pop-Location
if ($status) {
    Write-Host "[ERROR] rp-hub-reference/ 有未提交改动，请先处理干净再同步（避免二创改动混入上游）"
    exit 1
}

# ---------- 1. 拉取上游 + 改动统计 ----------
Push-Location $RefDir
# remote 检测：优先 upstream，否则用 origin（rp-hub-reference 的 origin 即官方仓库 STA1N156/RP-Hub）
$remote = git remote
$fetchFrom = if ($remote -contains 'upstream') { 'upstream' } else { 'origin' }
$baseRef = if ($fetchFrom -eq 'upstream') { 'upstream/main' } else { 'origin/main' }
Write-Host "`n[1/5] git fetch $fetchFrom ..."
git fetch $fetchFrom --quiet
if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host "[ERROR] fetch 失败"; exit 1 }
# 参考克隆 HEAD 可能停在旧版本；diff 提示有差异时，需要先把参考克隆更新到上游版本
# （git -C rp-hub-reference merge --ff-only <baseRef> 或 git checkout <baseRef>），
# 再重跑本脚本——否则覆盖复制会把旧文件拷过去。
$stat = git diff --stat $baseRef HEAD
Write-Host "上游改动概览 ($baseRef → HEAD):"
Write-Host ($stat -join "`n")
if ($stat) {
    Write-Host ""
    Write-Host "[HINT] 检测到上游有新版本。请先将参考克隆更新到上游："
    Write-Host "       git -C rp-hub-reference checkout $baseRef  （或 merge --ff-only）"
    Write-Host "       然后重新运行本脚本继续同步。"
    Pop-Location
    exit 0
}
Write-Host "（无改动，已是最新）"
Pop-Location

if ($DryRun) {
    Write-Host "`n[DRY-RUN] 已停止（不执行覆盖）"
    exit 0
}

# ---------- 2. 覆盖上游文件（排除二创/本地化目录） ----------
Write-Host "`n[2/5] 覆盖上游文件（排除 vendor/ fonts/ 与二创新增）..."
# 上游目录内需要排除的路径（这些目录下的文件不属于上游版本管理）
$excludeDirs = @('vendor', 'fonts')
# 二创专属文件（上游没有，覆盖上游目录时先备份、删后恢复）
$protectedFiles = @(Join-Path $TargetDir "assets\css\local-fonts.css")

# 方法：参考克隆内（.git 除外）复制到目标；排除目录跳过
Push-Location $RefDir
# 备份二创专属文件
$backups = @{}
foreach ($pf in $protectedFiles) {
    if (Test-Path $pf) { $backups[$pf] = [System.IO.File]::ReadAllBytes($pf) }
}
$items = Get-ChildItem -Force | Where-Object { $_.Name -ne '.git' }
foreach ($item in $items) {
    if ($item.PSIsContainer -and $item.Name -in $excludeDirs) {
        Write-Host "  [skip] $($item.Name)/ (本地化目录，不动)"
        continue
    }
    # 覆盖复制：目录先删后拷（保证上游删除的文件也同步删除）；文件直接拷
    $dest = Join-Path $TargetDir $item.Name
    if ($item.PSIsContainer) {
        if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
        Copy-Item -Recurse -Force $item.FullName $dest
        Write-Host "  [sync] $($item.Name)/"
    } else {
        Copy-Item -Force $item.FullName $dest
        Write-Host "  [sync] $($item.Name)"
    }
}
# 恢复二创专属文件（目录被删后文件丢失，从备份还原）
foreach ($pf in $protectedFiles) {
    if ($backups.ContainsKey($pf) -and -not (Test-Path $pf)) {
        $dir = Split-Path $pf
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        [System.IO.File]::WriteAllBytes($pf, $backups[$pf])
        Write-Host "  [restore] 二创文件已恢复: $pf"
    }
}
Pop-Location

# 注意：目标目录里如有二创新增文件（local-fonts.css 等）在排除目录外，
# 上游没有的同名文件不会覆盖；但上游新增的同名文件会覆盖二创文件——需要 patch 重放恢复。

# ---------- 3. 重放登记 patch ----------
Write-Host "`n[3/5] 重放二创 patch ..."
& (Join-Path $PSScriptRoot "apply-patches.ps1")
if ($LASTEXITCODE -ne 0) {
    Write-Host "[WARN] patch 重放有失败项，请按 AGENTS.md §4.3 手工处理"
}

# ---------- 4. 更新指纹基线 ----------
Write-Host "`n[4/5] 更新指纹基线 ..."
$fingerprintFiles = @(
    'index.html',
    'assets/css/styles.css',
    'assets/js/app.js',
    'assets/js/api-utils.js',
    'assets/js/built-in-content.js',
    'assets/js/core-utils.js',
    'assets/js/data-services.js',
    'assets/js/presence.js',
    'assets/js/runtime-services.js',
    'assets/js/ui-components.js',
    'assets/js/update-check.js'
)
$lines = @(
    "# LuzzyRP 上游文件指纹基线",
    "# 生成: $(Get-Date -Format 'yyyy.MM.dd HH:mm') · 同步来源: rp-hub-reference (upstream)",
    "# 用途: 同步验证（硬性规定 1/6）——同步后与 assets/rphub/ 比对",
    ""
)
foreach ($f in $fingerprintFiles) {
    $refPath = Join-Path $RefDir $f
    $tgtPath = Join-Path $TargetDir $f
    if (Test-Path $tgtPath) {
        $hash = (Get-FileHash $tgtPath -Algorithm SHA256).Hash
        $lines += "$hash  $f"
    } else {
        $lines += "MISSING  $f"
    }
}
$lines | Set-Content (Join-Path $PSScriptRoot "upstream-fingerprints.txt") -Encoding UTF8

# ---------- 5. 回归提示 ----------
Write-Host "`n[5/5] 同步完成！"
Write-Host "================================================"
Write-Host "同步后必须执行（硬性规定 6）:"
Write-Host "  1. 数据兼容: 老 localStorage 数据可读"
Write-Host "  2. 核心功能: 对话 / 角色卡导入导出 / 世界书 / 正则 / 记忆 / 生图"
Write-Host "  3. 断网可用性（飞行模式走查）"
Write-Host "  4. 扩展层功能回归（luzzy-ext.js）"
Write-Host "  5. 检查 vendor/ 依赖版本是否需要更新（上游换 CDN 版本时）"
Write-Host "  6. 检查上游 built-in-content.js 底部更新公告（决定是否清理）"
Write-Host "================================================"
Write-Host "回归通过后: 更新 LuzzyBridge.kt 的 UPSTREAM_VERSION → CHANGELOG → 构建 APK"
