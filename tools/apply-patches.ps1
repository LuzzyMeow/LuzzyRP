# ============================================================
# apply-patches.ps1 —— 上游同步后的二创 patch 重放脚本
# ============================================================
# 用法:  .\tools\apply-patches.ps1 [-BaselineCommit <sha>]
# 前置:  sync-upstream.ps1 已覆盖上游文件（app/src/main/assets/rphub/）
# 行为:  两段重放（顺序关键，会话 25 修正）：
#        ① 实体段（patches/entities/）——实体前像 = 上游纯净基线，必须先于字符串块落盘；
#        ② 字符串块段（001-011）——实体已覆盖同名改动，此段在覆盖态下多为 SKIP。
#        失败时逐条报告（AGENTS.md §4.3 冲突处理）。
# 基线:  「全新上游覆盖态」兜底判定所需的上游 commit——v1.5.0 起**不再硬编码**
#        （会话 26 发现旧版写死 d2f2625，同步到 1.9.3 后兜底判定必然失效）。
#        优先级：-BaselineCommit 参数 > tools/upstream-fingerprints.txt 头部
#        「(commit <sha>)」> 参考克隆 FETCH_HEAD。取不到时仅跳过兜底分支（前像判定仍生效）。
# ============================================================
param(
    [string]$BaselineCommit = ''
)

$ErrorActionPreference = "Stop"
$RphubDir = Join-Path $PSScriptRoot "..\app\src\main\assets\rphub"

Write-Host "== LuzzyRP patch 重放（目标: $RphubDir）=="

# ------------------------------------------------------------------
# 实体 patch（entities/，v1.2.1 硬性规定 10）
# ------------------------------------------------------------------
# 覆盖 007/009/012-028 的全部二创改动（与上游基线的逐文件 diff，
# 由 rp-hub-reference 生成，含 [LuzzyRP patch NNN] 标记）。
# 判定规则（会话 25 重写）：
#   目标文件已含对应标记                        -> SKIP（已应用）
#   目标文件 LF 归一 blob id == 实体头 index pre -> git apply 实体（覆盖态/字符串块已落盘态皆可）
#   目标文件 == 上游纯净基线（rp-hub-reference） -> git apply 实体（前像缺失时的兜底）
#   其余（上游已发新版）                        -> FAIL，按 AGENTS.md §4.3 手工合并
# 应用后强制校验标记落盘：git apply 返回 0 但路径/行尾异常时会静默不写入（会话 25 加固）。
# 注意：手工合并后必须用 rp-hub-reference 重新生成实体并复跑 verify-markers.ps1。
# ------------------------------------------------------------------
$entitiesDir = Join-Path $PSScriptRoot "patches\entities"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$fingerprintPath = Join-Path $repoRoot "tools\upstream-fingerprints.txt"
$fingerprints = @{}
if (Test-Path $fingerprintPath) {
    Get-Content $fingerprintPath | ForEach-Object {
        if ($_ -match '^([0-9A-Fa-f]{64})\s+\*?(.+)$') {
            $fingerprints[$Matches[2].Trim().Replace('\', '/').ToLower()] = $Matches[1].ToUpper()
        }
    }
}

# ---- 上游基线 commit 解析（v1.5.0 参数化，替代旧版硬编码 d2f2625）----
# 解析顺序：-BaselineCommit 参数 > 指纹表头部「(commit <sha>)」> 参考克隆 FETCH_HEAD。
$refDirForBaseline = Join-Path $repoRoot "rp-hub-reference"
if (-not $BaselineCommit -and (Test-Path $fingerprintPath)) {
    foreach ($line in (Get-Content $fingerprintPath)) {
        if ($line -match '\(commit\s+([0-9a-fA-F]{7,40})') { $BaselineCommit = $Matches[1]; break }
    }
}
if (-not $BaselineCommit -and (Test-Path (Join-Path $refDirForBaseline '.git'))) {
    $prevEapB = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $head = (& git -C $refDirForBaseline rev-parse FETCH_HEAD 2>$null)
    $ErrorActionPreference = $prevEapB
    if ($LASTEXITCODE -eq 0 -and $head) { $BaselineCommit = ($head | Select-Object -First 1).Trim() }
}
if ($BaselineCommit) {
    Write-Host "上游基线 commit: $BaselineCommit"
} else {
    Write-Host "[WARN] 无法确定上游基线 commit（指纹表头无 (commit …) 且参考克隆不可用）——仅跳过兜底判定分支"
}

function Get-FileGitBlobIdLfNormalized([string]$Path) {
    # 计算「LF 归一内容」的 git blob 对象 id（与 git hash-object 同构）：
    # 实体 patch 头 index <pre>..<post> 的 pre 即该文件的期望前像 blob id，
    # 用它判定目标文件是否处于实体可应用状态（全新上游覆盖态，或字符串块已落盘态）。
    if (-not (Test-Path $Path)) { return $null }
    $text = [System.IO.File]::ReadAllText($Path)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
    $header = [System.Text.UTF8Encoding]::new($false).GetBytes("blob $($bytes.Length)`0")
    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        $sha1.TransformBlock($header, 0, $header.Length, $header, 0) | Out-Null
        $sha1.TransformFinalBlock($bytes, 0, $bytes.Length) | Out-Null
        return (($sha1.Hash) | ForEach-Object { $_.ToString('x2') }) -join ''
    } finally { $sha1.Dispose() }
}
function Get-EntityPreImage([string]$EntityPath) {
    if (-not (Test-Path $EntityPath)) { return $null }
    foreach ($line in [System.IO.File]::ReadAllLines($EntityPath)) {
        if ($line -match '^index ([0-9a-f]{7,40})\.\.([0-9a-f]{7,40})') { return $Matches[1] }
    }
    return $null
}
function Get-FileSha256Local([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try { return (($sha.ComputeHash($stream)) | ForEach-Object { $_.ToString('x2') }) -join '' } finally { $stream.Dispose() }
    } finally { $sha.Dispose() }
}
# 基线比对用 LF 归一哈希：上游覆盖态（rp-hub-reference 检出，LF）与指纹表（主仓库工作树 CRLF）
# 仅行尾不同，逐字节比对会误判「与上游基线不一致」而阻断重放（会话 25 实证：覆盖后 9 枚实体全部
# 被指纹门拦截，误报为「git apply 静默失败」）。归一为 LF 后比对，两种检出态都可正确判定。
function Get-FileSha256LfNormalized([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    $text = [System.IO.File]::ReadAllText($Path)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return (($sha.ComputeHash($bytes)) | ForEach-Object { $_.ToString('x2') }) -join '' } finally { $sha.Dispose() }
}
function Get-BaselineSha256LfNormalized([string]$RelativePath) {
    # 从上游参考克隆取基线文件内容（$BaselineCommit），LF 归一后哈希，作为「全新上游覆盖态」判定基准。
    # 指纹表存的是主仓库工作树字节的哈希，两者仅行尾不同，故统一归一后比对。
    # v1.5.0：基线 commit 不再硬编码（旧版写死 d2f2625），改由 -BaselineCommit / 指纹表头解析。
    if (-not $BaselineCommit -or -not (Test-Path $refDirForBaseline)) { return $null }
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) "luzzy-baseline-$PID.bin"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    # 用 cmd 重定向取「原始字节」：PowerShell 管道会把原生命令输出文本化（丢尾部空行/补行尾），
    # 对 novel/index.html 这类以多个空行结尾的基线会造成哈希偏差。
    $cmdLine = 'git -C "{0}" show {1}:{2} > "{3}" 2>nul' -f $refDirForBaseline, $BaselineCommit, $RelativePath, $tmp
    $null = & cmd /c $cmdLine
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if (-not (Test-Path $tmp)) { return $null }
    $raw = [System.IO.File]::ReadAllBytes($tmp)
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    if ($exit -ne 0 -or $raw.Length -eq 0) { return $null }
    $text = [System.Text.Encoding]::UTF8.GetString($raw)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $norm = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return (($sha.ComputeHash($norm)) | ForEach-Object { $_.ToString('x2') }) -join '' } finally { $sha.Dispose() }
}
$entityItems = @(
    @{ File = 'character/index.html';         Entity = '007-character-html.patch';        Marker = '[LuzzyRP patch 007]' },
    @{ File = 'novel/index.html';             Entity = '007-029-novel-html.patch';        Marker = '[LuzzyRP patch 007]' },
    @{ File = 'assets/js/core-utils.js';      Entity = '009-035-core-utils-js.patch';     Marker = '[LuzzyRP patch 009]' },
    @{ File = 'index.html';                   Entity = '012-035-index-html.patch';        Marker = '[LuzzyRP patch 014]' },
    @{ File = 'assets/js/app.js';             Entity = '012-036-app-js.patch';            Marker = '[LuzzyRP patch 015]' },
    @{ File = 'assets/js/ui-components.js';   Entity = '012-035-ui-components-js.patch';  Marker = '[LuzzyRP patch 015]' },
    @{ File = 'assets/js/runtime-services.js'; Entity = '012-035-runtime-services-js.patch'; Marker = '[LuzzyRP patch 032]' },
    @{ File = 'assets/js/api-utils.js';       Entity = '015-032-api-utils-js.patch';      Marker = '[LuzzyRP patch 015]' },
    @{ File = 'assets/js/data-services.js';   Entity = '016-035-data-services-js.patch';  Marker = '[LuzzyRP patch 016]' }
)
Write-Host ""
Write-Host "== 实体 patch（007/009/012-035/015-032）=="
foreach ($item in $entityItems) {
    $relKey = $item.File.ToLower()
    $targetPath = Join-Path $RphubDir ($item.File -replace '/', '\')
    $entityPath = Join-Path $entitiesDir $item.Entity
    if (-not (Test-Path $targetPath)) { Write-Host "[FAIL] $($item.Entity): 目标文件不存在"; continue }
    if (([System.IO.File]::ReadAllText($targetPath)).Contains($item.Marker)) {
        Write-Host "[SKIP] $($item.Entity) (已应用)"
        continue
    }
    # 前像判定（会话 25 重写）：实体头 index <pre>..<post> 的 pre 即期望前像 blob id。
    # 目标文件 LF 归一后的 blob id 与 pre 一致 → 实体可干净应用（覆盖态/字符串块已落盘态皆可）。
    # 不一致时再退回「上游纯净基线」比对，两者都不符 → FAIL（上游可能已发新版）。
    $preImage = Get-EntityPreImage $entityPath
    $blobId = Get-FileGitBlobIdLfNormalized $targetPath
    $preMatch = $false
    if ($preImage -and $blobId) {
        $cmpLen = [Math]::Min($preImage.Length, $blobId.Length)
        $preMatch = ($blobId.Substring(0, $cmpLen) -eq $preImage.Substring(0, $cmpLen))
    }
    if (-not $preMatch) {
        $baseline = Get-BaselineSha256LfNormalized $item.File
        if (-not $baseline) {
            # 参考克隆不可用（离线等）→ 退回指纹表比对
            $rawBaseline = $fingerprints[$relKey]
            if (-not $rawBaseline) {
                Write-Host "[FAIL] $($item.Entity): 无法判定基线（rp-hub-reference 不可用且指纹表缺项）"
                continue
            }
            Write-Host "[WARN] $($item.Entity): rp-hub-reference 不可用，退回指纹表比对（请确认基线版本）"
            $baseline = $rawBaseline
        }
        if ((Get-FileSha256LfNormalized $targetPath) -ne $baseline) {
            Write-Host "[FAIL] $($item.Entity): 目标文件与上游基线不一致（上游可能已更新），请手工合并该文件全部二创改动"
            continue
        }
    }
    if (-not (Test-Path $entityPath)) { Write-Host "[FAIL] $($item.Entity): 实体文件缺失"; continue }
    # git apply 会把 "trailing whitespace" 等告警写到 stderr；本脚本 $ErrorActionPreference='Stop'
    # 时 PowerShell 会把原生命令 stderr 视为终止错误（会话 25 实证：第 4 枚实体后脚本整体中断）。
    # 故显式重定向到临时文件，仅在退出码非 0 时读取内容。
    $applyLog = Join-Path ([System.IO.Path]::GetTempPath()) "luzzy-apply-$PID.log"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $null = & git -C $repoRoot apply --ignore-whitespace --directory="app/src/main/assets/rphub" $entityPath 2>$applyLog
    $applyExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    $applyErr = if (Test-Path $applyLog) { (Get-Content $applyLog -Raw) } else { '' }
    if (Test-Path $applyLog) { Remove-Item $applyLog -Force }
    if ($applyExit -eq 0) {
        # 落盘校验：git apply 返回 0 不等于真的写入（路径/行尾异常时会静默跳过），
        # 必须确认标记真的出现，否则视为重放失败（会话 25 加固）。
        if (([System.IO.File]::ReadAllText($targetPath)).Contains($item.Marker)) {
            Write-Host "[ OK ] $($item.Entity)"
        } else {
            Write-Host "[FAIL] $($item.Entity): git apply 返回成功但标记未落盘（$($item.Marker) 缺失），请检查实体与行尾"
        }
    }
    else { Write-Host "[FAIL] $($item.Entity): git apply 失败 — $applyErr" }
}

Write-Host ""
Write-Host "== 字符串块（001-011）=="

# ------------------------------------------------------------------
# Patch 001 · 品牌标题
# ------------------------------------------------------------------
$titlePath = Join-Path $RphubDir "index.html"
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match '<title>LuzzyRP</title>') {
    Write-Host "[SKIP] 001-brand-title (已应用)"
} else {
    if ($titleContent -match '<title>RP Hub</title>') {
        $titleContent = $titleContent.Replace('<title>RP Hub</title>', '<title>LuzzyRP</title>')
        [System.IO.File]::WriteAllText($titlePath, $titleContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 001-brand-title"
    } else {
        Write-Host "[FAIL] 001-brand-title: 未找到 <title>RP Hub</title>，上游可能已改标题结构"
    }
}

# ------------------------------------------------------------------
# Patch 002 · 禁用上游更新检查（移除 rphub-update-api meta）
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -notmatch 'rphub-update-api') {
    Write-Host "[SKIP] 002-disable-update-check (已应用)"
} else {
    $newContent = $titleContent -replace '(?m)^\s*<meta name="rphub-update-api"[^>]*>\r?\n', ''
    if ($newContent -ne $titleContent) {
        [System.IO.File]::WriteAllText($titlePath, $newContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 002-disable-update-check"
    } else {
        Write-Host "[FAIL] 002-disable-update-check: meta 标签格式与预期不符"
    }
}

# ------------------------------------------------------------------
# Patch 003 · 已退役（v1.2.3，patch 027）：入口字标由「开卷」开屏 DOM 承载，
#   上游 entry-transition 区块在 index.html 被整体替换；003 标记保留于 index.html
#   注释，原意图由实体 012-033-index-html.patch 覆盖（先于 003 的重放不再执行）。
# ------------------------------------------------------------------

# ------------------------------------------------------------------
# Patch 004 · CDN 本地化（vendor 引用）
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
$cdnFound = @()
if ($titleContent -match 'https://cdn\.tailwindcss\.com') { $cdnFound += "tailwindcdn" }
if ($titleContent -match 'https://unpkg\.com/vue') { $cdnFound += "unpkg-vue" }
if ($titleContent -match 'https://cdn\.jsdelivr\.net/npm/marked') { $cdnFound += "jsdelivr-marked" }
if ($titleContent -match 'https://cdn\.jsdelivr\.net/npm/dompurify') { $cdnFound += "jsdelivr-dompurify" }
if ($titleContent -match 'https://cdn\.jsdelivr\.net/npm/sortablejs') { $cdnFound += "jsdelivr-sortablejs" }

if ($cdnFound.Count -eq 0) {
    Write-Host "[SKIP] 004-vendor-local (已应用)"
} else {
    Write-Host "[WARN] 004-vendor-local: 发现未本地化的 CDN 引用: $($cdnFound -join ', ')"
    Write-Host "       请核对 vendor/ 目录是否有对应文件后手动替换，或更新本脚本"
}

# ------------------------------------------------------------------
# Patch 005 · 扩展层挂载
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match 'luzzy-theme\.css' -and $titleContent -match 'luzzy-ext\.js' -and $titleContent -match 'luzzy-splash\.js') {
    Write-Host "[SKIP] 005-ext-mount (已应用)"
} else {
    # 在 </body> 前插入扩展层引用（注意上游行尾风格）
    if ($titleContent -match '</body>') {
        $extBlock = "`n    <!-- LuzzyRP 扩展层（AGENTS.md §5：独立文件，与上游零冲突） -->`n" +
            '    <link rel="stylesheet" href="../ext/luzzy-theme.css">' + "`n" +
            '    <script src="../ext/luzzy-bridge.js"></script>' + "`n" +
            '    <script src="../ext/luzzy-ext.js"></script>' + "`n" +
        '    <script src="../ext/luzzy-splash.js"></script>' + "`n"
        # 行尾归一化：上游为 CRLF 或 LF 皆可；统一用文件原有风格
        $isCrlf = $titleContent.Contains("`r`n")
        if ($isCrlf) { $extBlock = $extBlock.Replace("`n", "`r`n") }
        $titleContent = $titleContent.Replace('</body>', $extBlock + '</body>')
        [System.IO.File]::WriteAllText($titlePath, $titleContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 005-ext-mount"
    } else {
        Write-Host "[FAIL] 005-ext-mount: 未找到 </body>"
    }
}

# ------------------------------------------------------------------
# Patch 006 · Lora 本地字体（Google Fonts -> local-fonts.css）
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match 'local-fonts\.css') {
    Write-Host "[SKIP] 006-local-fonts (已应用)"
} else {
    if ($titleContent -match 'fonts\.googleapis\.com.*Lora') {
        # 逐行替换：找到 fonts.googleapis Lora link 和 preconnect，替换为本地引用
        $lines = [System.IO.File]::ReadAllLines($titlePath)
        $lastLoraLine = -1
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -match 'fonts\.googleapis\.com.*Lora') { $lastLoraLine = $i }
        }
        if ($lastLoraLine -ge 0) {
            $lines[$lastLoraLine] = '    <link rel="stylesheet" href="assets/css/local-fonts.css">'
            [System.IO.File]::WriteAllLines($titlePath, $lines, [System.Text.UTF8Encoding]::new($false))
            Write-Host "[ OK ] 006-local-fonts"
        } else {
            Write-Host "[FAIL] 006-local-fonts: 未找到 Lora 链接"
        }
    } else {
        Write-Host "[FAIL] 006-local-fonts: 未找到 Google Fonts Lora 引用，上游可能已改字体加载方式"
    }
}

# ------------------------------------------------------------------
# Patch 007 · 子页面离线化（character/novel CDN 本地化）
# ------------------------------------------------------------------
$subPages = @(
    @{ Path = Join-Path $RphubDir "character\index.html"; Name = "007a-character" },
    @{ Path = Join-Path $RphubDir "novel\index.html"; Name = "007b-novel" }
)
foreach ($sub in $subPages) {
    $subPath = $sub.Path
    if (-not (Test-Path $subPath)) {
        Write-Host "[SKIP] $($sub.Name) (文件不存在)"
        continue
    }
    $subContent = [System.IO.File]::ReadAllText($subPath)
    $subCdn = @()
    if ($subContent -match 'https://cdn\.tailwindcss\.com') { $subCdn += "tailwindcdn" }
    if ($subContent -match 'https://unpkg\.com/vue') { $subCdn += "unpkg-vue" }
    if ($subContent -match 'https://cdn\.jsdelivr\.net/npm/marked') { $subCdn += "jsdelivr-marked" }
    if ($subContent -match 'https://cdn\.jsdelivr\.net/npm/daisyui') { $subCdn += "jsdelivr-daisyui" }
    if ($subContent -match 'https://cdn\.jsdelivr\.net/npm/localforage') { $subCdn += "jsdelivr-localforage" }
    if ($subContent -match 'fonts\.googleapis\.com.*Lora') { $subCdn += "fonts-lora" }
    if ($subContent -match 'fonts\.googleapis|fonts\.gstatic') { $subCdn += "fonts" }
    if ($subCdn.Count -eq 0) {
        Write-Host "[SKIP] $($sub.Name) (已应用)"
    } else {
        Write-Host "[WARN] $($sub.Name): 发现未本地化引用: $($subCdn -join ', ')"
        Write-Host "       请手工替换（参考 sync 脚本排除规则），或更新本脚本"
    }
}

# ------------------------------------------------------------------
# Patch 008 · 已退役（v1.3.0 会话 21）：本字符串块是 v2→v3 历史迁移路径（var() 单值→
#   RGB 三元组），实际 v4 色板（三元组+blue/indigo 收编）由实体 012-035-index-html.patch
#   承载——上游 1.9.1 色板为十六进制硬编码，v2 形态锚不存在属预期，重放报 FAIL 为误报。
#   实体对 1.9.1 dry-run CLEAN 实证，退役后由实体统一承载。
# ------------------------------------------------------------------

# ------------------------------------------------------------------
# Patch 009 · 字体选项：内置改「经典」系 + 新增 luzzy 默认（core-utils.js）
# 对应：用户指令（系统内置字体改为经典；默认字体 = PuHuiTi + AlibabaSans）
# 预期冲突点：上游改 fontFamilies 结构/文案时需重打
# ------------------------------------------------------------------
$corePath = Join-Path $RphubDir "assets\js\core-utils.js"
$coreContent = [System.IO.File]::ReadAllText($corePath)
if ($coreContent -match "value: 'luzzy'") {
    Write-Host "[SKIP] 009-font-options (已应用)"
} else {
    $oldFF = @(
        '            fontFamilies: Object.freeze([',
        "                { value: 'modern', label: '现代通用字体' },",
        "                { value: 'serif', label: '衬线字体' },",
        "                { value: 'system', label: '系统字体' }",
        '            ]),'
    ) -join "`r`n"
    $newFF = @(
        '            fontFamilies: Object.freeze([',
        "                { value: 'luzzy', label: 'Luzzy 默认' },",
        "                { value: 'modern', label: '经典（原版）' },",
        "                { value: 'serif', label: '经典衬线（Lora）' },",
        "                { value: 'system', label: '系统' }",
        '            ]),'
    ) -join "`r`n"
    if ($coreContent.Contains($oldFF)) {
        $coreContent = $coreContent.Replace($oldFF, $newFF)
        [System.IO.File]::WriteAllText($corePath, $coreContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 009-font-options"
    } else {
        Write-Host "[FAIL] 009-font-options: fontFamilies 结构变化，请手工更新"
    }
}

# ------------------------------------------------------------------
# Patch 010 · 默认字体 luzzy + normalizeFontFamily 白名单（app.js）
# 对应：新用户默认 Luzzy 字体（用户指令）
# 预期冲突点：上游改默认值/白名单时需重打
# ------------------------------------------------------------------
$appPath = Join-Path $RphubDir "assets\js\app.js"
$appContent = [System.IO.File]::ReadAllText($appPath)
if ($appContent -match "fontFamily: 'luzzy'") {
    Write-Host "[SKIP] 010-defaults (已应用)"
} else {
    $appContent = $appContent.Replace("            fontFamily: 'modern',", "            fontFamily: 'luzzy',")
    $appContent = $appContent.Replace(
        "const normalizeFontFamily = (value) => ['modern', 'serif', 'system'].includes(value) ? value : 'modern';",
        "const normalizeFontFamily = (value) => ['luzzy', 'modern', 'serif', 'system'].includes(value) ? value : 'modern';")
    [System.IO.File]::WriteAllText($appPath, $appContent, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[ OK ] 010-defaults"
}

# ------------------------------------------------------------------
# Patch 011 · 设置页主题卡（主题+模式+字体附属）+ theme 字段/watch/迁移
# 对应：用户指令（设置页新增主题功能；字体为主题附属设置；新用户默认 luzzy；老用户保留经典）
# 预期冲突点：上游改设置页结构 / fontFamily watch 区域 / settings 加载块时需重打
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
# [LuzzyRP patch 028 退役 011]：主题单轨化已移除「界面主题」卡 DOM，重放目标不复存在；
# 主题字段逻辑由实体 012-031-app-js.patch 承载。会话 21 实证：SKIP 检测失配导致重放崩溃，故退役。
if (-not ($titleContent -match '界面主题')) {
    Write-Host "[SKIP] 011-theme-ui (已退役：028 单轨化移除主题卡 DOM，目标不复存在)"
} else {
    $startIdx = $titleContent.IndexOf('                                    <!-- Font Family Setting -->')
    $endIdx = $titleContent.IndexOf('                                    <!-- Font Size Setting -->', $startIdx)
    if ($startIdx -ge 0 -and $endIdx -gt $startIdx) {
        $isCrlf = $titleContent.Contains("`r`n")
        $eol = if ($isCrlf) { "`r`n" } else { "`n" }
        $card = @(
            '                                    <!-- Theme Setting (LuzzyRP 扩展：主题 + 模式 + 字体附属设置) -->',
            '                                    <div',
            '                                        class="bg-gray-50/60 p-4 rounded-xl border border-gray-100 hover:bg-white hover:border-gray-200 hover:shadow-sm transition-all duration-200">',
            '                                        <label',
            '                                            class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">界面主题</label>',
            '                                        <custom-select v-model="settings.theme" :options="themeOptions"',
            '                                            button-class="rounded-lg px-3 py-1.5 text-sm text-gray-700 focus:border-indigo-400 focus:ring-indigo-100"',
            '                                            menu-class="text-sm">',
            '                                        </custom-select>',
            "                                        <div v-if=`"settings.theme === 'luzzy'`" class=`"mt-2 flex items-center gap-2`">",
            '                                            <label class="text-xs font-bold text-gray-500 uppercase tracking-wider">模式</label>',
            '                                            <custom-select v-model="settings.themeMode" :options="themeModeOptions"',
            '                                                button-class="rounded-lg px-3 py-1.5 text-sm text-gray-700 focus:border-indigo-400 focus:ring-indigo-100"',
            '                                                menu-class="text-sm">',
            '                                            </custom-select>',
            '                                        </div>',
            '                                        <label',
            '                                            class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 mt-3">界面字体</label>',
            '                                        <custom-select v-model="settings.fontFamily" :options="fontFamilyOptions"',
            '                                            button-class="rounded-lg px-3 py-1.5 text-sm text-gray-700 focus:border-indigo-400 focus:ring-indigo-100"',
            '                                            menu-class="text-sm">',
            '                                        </custom-select>',
            '                                    </div>',
            '',
            '                                    <!-- Font Size Setting -->'
        ) -join $eol
        $titleContent = $titleContent.Substring(0, $startIdx) + $card + $titleContent.Substring($endIdx)
        [System.IO.File]::WriteAllText($titlePath, $titleContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 011-theme-ui"
    } else {
        Write-Host "[FAIL] 011-theme-ui: 未找到 Font Family Setting 锚点"
    }
}

$appContent = [System.IO.File]::ReadAllText($appPath)
if ($appContent -match "theme: 'luzzy'") {
    Write-Host "[SKIP] 011b-theme-logic (已应用)"
} else {
    $isCrlf = $appContent.Contains("`r`n")
    $eol = if ($isCrlf) { "`r`n" } else { "`n" }
    # 1) settings 默认值（新用户 luzzy/light）
    $appContent = $appContent.Replace("            fontFamily: 'luzzy',",
        "            theme: 'luzzy',$eol            themeMode: 'light',$eol            fontFamily: 'luzzy',")
    # 2) options 常量（} = uiOptions; 行后插入）
    $lines = $appContent -split [regex]::Escape($eol)
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i].Trim() -eq '} = uiOptions;') {
            $insert = @(
                '        const themeOptions = Object.freeze([',
                "            { value: 'luzzy', label: '暖幕手记（Luzzy）' },",
                "            { value: 'classic', label: '经典（原版）' }",
                '        ]);',
                '        const themeModeOptions = Object.freeze([',
                "            { value: 'light', label: '亮色' },",
                "            { value: 'dark', label: '暗色' }",
                '        ]);'
            )
            $lines = $lines[0..$i] + $insert + $lines[($i + 1)..($lines.Length - 1)]
            break
        }
    }
    $appContent = $lines -join $eol
    # 3) applyTheme/applyThemeMode + immediate watch（fontFamily watch 后）
    $watchAnchor = '        watch(() => settings.fontFamily, applyFontFamily, { immediate: true });'
    $watchBlock = @(
        '        watch(() => settings.fontFamily, applyFontFamily, { immediate: true });',
        '        const applyTheme = (value) => {',
        "            document.documentElement.dataset.theme = value === 'classic' ? 'classic' : 'luzzy';",
        '        };',
        '        const applyThemeMode = (value) => {',
        "            document.documentElement.dataset.mode = value === 'dark' ? 'dark' : 'light';",
        '            if (window.LuzzyBridge && window.LuzzyBridge.setSystemBarStyle) {',
        "                window.LuzzyBridge.setSystemBarStyle(value === 'dark' ? 'dark' : 'light');",
        '            }',
        '        };',
        '        watch(() => settings.theme, applyTheme, { immediate: true });',
        '        watch(() => settings.themeMode, applyThemeMode, { immediate: true });'
    ) -join $eol
    $appContent = $appContent.Replace($watchAnchor, $watchBlock)
    # 4) setup return 暴露
    $appContent = $appContent.Replace('fontFamilyOptions, fontSizeOptions, availableImageStyleOptions',
        'fontFamilyOptions, fontSizeOptions, themeOptions, themeModeOptions, availableImageStyleOptions')
    # 5) 老用户迁移（forEach 闭合后、apiProviderId 前；仅 savedSettings 存在时执行）
    $migBlock = @(
        "                    if (!Object.prototype.hasOwnProperty.call(savedSettings, 'theme')) {",
        "                        settings.theme = 'classic'; // 老用户保留经典主题",
        '                    }',
        "                    if (!Object.prototype.hasOwnProperty.call(savedSettings, 'themeMode')) {",
        "                        settings.themeMode = 'light';",
        '                    }'
    ) -join $eol
    $appContent = [regex]::Replace($appContent,
        "(\}\);(\r?\n))(                    if \(!Object\.prototype\.hasOwnProperty\.call\(savedSettings, 'apiProviderId'\)\))",
        ('$1' + $migBlock.Replace('$', '$$') + '$2'))
    [System.IO.File]::WriteAllText($appPath, $appContent, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[ OK ] 011b-theme-logic"
}
