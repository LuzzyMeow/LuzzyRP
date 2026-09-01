# ============================================================
# apply-patches.ps1 —— 上游同步后的二创 patch 重放脚本
# ============================================================
# 用法:  .\tools\apply-patches.ps1
# 前置:  sync-upstream.ps1 已覆盖上游文件（app/src/main/assets/rphub/）
# 行为:  按登记顺序重放 patch；失败时逐条报告（AGENTS.md §4.3 冲突处理）
# ============================================================

$ErrorActionPreference = "Stop"
$RphubDir = Join-Path $PSScriptRoot "..\app\src\main\assets\rphub"

Write-Host "== LuzzyRP patch 重放（目标: $RphubDir）=="

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
# Patch 003 · 入口 logo 品牌化（RP/HUB -> LUZZY/RP）
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match 'entry-logo-rp">LUZZY<') {
    Write-Host "[SKIP] 003-entry-logo (已应用)"
} else {
    if ($titleContent -match 'entry-logo-rp">RP</span>\s*$' -and $titleContent -match 'entry-logo-hub">HUB</span>') {
        # 逐行处理，保持行尾风格
        $lines = [System.IO.File]::ReadAllLines($titlePath)
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -match 'entry-logo-rp' -and $lines[$i] -match '>RP<') {
                $lines[$i] = $lines[$i].Replace('>RP<', '>LUZZY<')
            }
            if ($lines[$i] -match 'entry-logo-hub' -and $lines[$i] -match '>HUB<') {
                $lines[$i] = $lines[$i].Replace('>HUB<', '>RP<')
            }
        }
        [System.IO.File]::WriteAllLines($titlePath, $lines, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 003-entry-logo"
    } else {
        Write-Host "[FAIL] 003-entry-logo: 入口 logo 结构变化，请手工更新此 patch"
    }
}

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
if ($titleContent -match 'luzzy-theme\.css' -and $titleContent -match 'luzzy-ext\.js') {
    Write-Host "[SKIP] 005-ext-mount (已应用)"
} else {
    # 在 </body> 前插入扩展层引用（注意上游行尾风格）
    if ($titleContent -match '</body>') {
        $extBlock = "`n    <!-- LuzzyRP 扩展层（AGENTS.md §5：独立文件，与上游零冲突） -->`n" +
            '    <link rel="stylesheet" href="../ext/luzzy-theme.css">' + "`n" +
            '    <script src="../ext/luzzy-bridge.js"></script>' + "`n" +
            '    <script src="../ext/luzzy-ext.js"></script>' + "`n"
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


Write-Host "== patch 重放完成 =="
Write-Host "提示: 重放后请执行 sync 回归清单（AGENTS.md §6.2）"
