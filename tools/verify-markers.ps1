# ============================================================
# verify-markers.ps1 —— 上游同步后的二创标记校验门（硬性规定 10）
# ============================================================
# 用法:  .\tools\verify-markers.ps1
# 前置:  sync-upstream.ps1 覆盖 + apply-patches.ps1 重放已完成
# 行为:  按 LuzzyRP 标记登记逐项校验「文件 + 标记串 + 最低出现次数」，
#        并对规定 1/2 敏感文件做上游指纹一致性校验；全部 PASS 才算同步完成。
# 维护:  新增 patch 时必须同步更新 $Manifest（与 tools/patches/README.md 一致）。
# ============================================================

$ErrorActionPreference = "Stop"
$RepoRoot = Join-Path $PSScriptRoot ".."
$RphubDir = Join-Path $RepoRoot "app\src\main\assets\rphub"
$FingerprintFile = Join-Path $RepoRoot "tools\upstream-fingerprints.txt"

# ---- 指纹基线载入（SHA256 → 相对路径小写）----
$Fingerprints = @{}
if (Test-Path $FingerprintFile) {
    Get-Content $FingerprintFile | ForEach-Object {
        if ($_ -match '^([0-9A-Fa-f]{64})\s+\*?(.+)$') {
            $Fingerprints[$Matches[2].Trim().Replace('\', '/').ToLower()] = $Matches[1].ToUpper()
        }
    }
}

function Get-FileSha256([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            return (($sha.ComputeHash($stream)) | ForEach-Object { $_.ToString('x2') }) -join ''
        } finally { $stream.Dispose() }
    } finally { $sha.Dispose() }
}

# ---- 校验清单：@{ Id; File; Mode(contains/notcontains/hash-upstream); Needle; Min } ----
$Manifest = @(
    @{ Id = '001-title';            File = 'index.html';                       Mode = 'contains';     Needle = '<title>LuzzyRP</title>';                Min = 1 },
    @{ Id = '002-no-update-api';    File = 'index.html';                       Mode = 'notcontains';  Needle = 'rphub-update-api' },
    @{ Id = '003-logo';             File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 003]';                   Min = 1 },
    @{ Id = '004-vendor';           File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 004]';                   Min = 1 },
    @{ Id = '004-no-cdn';           File = 'index.html';                       Mode = 'notcontains';  Needle = 'cdn.tailwindcss.com' },
    @{ Id = '005-ext-mount';        File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 005]';                   Min = 1 },
    @{ Id = '005-luzzy-ext';        File = 'index.html';                       Mode = 'contains';     Needle = 'ext/luzzy-ext.js';                      Min = 1 },
    @{ Id = '006-local-fonts';      File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 006]';                   Min = 1 },
    @{ Id = '006-no-gfonts';        File = 'index.html';                       Mode = 'notcontains';  Needle = 'fonts.googleapis.com' },
    @{ Id = '007-character';        File = 'character/index.html';             Mode = 'contains';     Needle = '[LuzzyRP patch 007]';                   Min = 1 },
    @{ Id = '007-character-nocdn';  File = 'character/index.html';             Mode = 'notcontains';  Needle = 'cdn.tailwindcss.com' },
    @{ Id = '007-novel';            File = 'novel/index.html';                 Mode = 'contains';     Needle = '[LuzzyRP patch 007]';                   Min = 1 },
    @{ Id = '007-novel-nocdn';      File = 'novel/index.html';                 Mode = 'notcontains';  Needle = 'cdn.tailwindcss.com' },
    @{ Id = '008-alpha-value';      File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 008]';                   Min = 1 },
    @{ Id = '008-alpha-count';      File = 'index.html';                       Mode = 'contains';     Needle = '<alpha-value>';                         Min = 10 },
    @{ Id = '009-font-options';     File = 'assets/js/core-utils.js';          Mode = 'contains';     Needle = '[LuzzyRP patch 009]';                   Min = 1 },
    @{ Id = '009-luzzy-value';      File = 'assets/js/core-utils.js';          Mode = 'contains';     Needle = "value: 'luzzy'";                        Min = 1 },
    @{ Id = '010-defaults';         File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 010]';                   Min = 1 },
    @{ Id = '010-font-luzzy';       File = 'assets/js/app.js';                 Mode = 'contains';     Needle = "fontFamily: 'luzzy'";                   Min = 1 },
    # [注] 011 的 index.html 主题卡已由 patch 028 主题单轨化移除，校验由 011-theme-logic 承担
    @{ Id = '028-no-theme-switch';    File = 'index.html';                       Mode = 'notcontains';  Needle = '界面主题';                               Min = 0 },
    @{ Id = '011-theme-logic';      File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 011]';                   Min = 2 },
    @{ Id = '012-multi-provider';   File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 012]';                   Min = 8 },
    @{ Id = '012-ui-components';    File = 'assets/js/ui-components.js';       Mode = 'contains';     Needle = '[LuzzyRP patch 012]';                   Min = 1 },
    @{ Id = '012-index';            File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 012]';                   Min = 1 },
    @{ Id = '013-legacy-register';  File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 013]';                   Min = 1 },
    @{ Id = '014-appearance-about'; File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 014]';                   Min = 2 },
    @{ Id = '014-ui-components';    File = 'assets/js/ui-components.js';       Mode = 'contains';     Needle = '[LuzzyRP patch 014]';                   Min = 1 },
    @{ Id = '014-index';            File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 014]';                   Min = 3 },
    @{ Id = '015-protocols';        File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 015]';                   Min = 10 },
    @{ Id = '015-runtime';          File = 'assets/js/runtime-services.js';    Mode = 'contains';     Needle = '[LuzzyRP patch 015]';                   Min = 4 },
    @{ Id = '015-index';            File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 015]';                   Min = 5 },
    @{ Id = '016-recall-nomerge';   File = 'assets/js/data-services.js';       Mode = 'contains';     Needle = '[LuzzyRP patch 016]';                   Min = 1 },
    @{ Id = '017-manager-app';      File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 017]';                   Min = 5 },
    @{ Id = '017-manager-html';     File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 017]';                   Min = 2 },
    @{ Id = '018-no-flash';           File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 018]';                   Min = 1 },
    @{ Id = '018-theme-css-head';     File = 'index.html';                       Mode = 'contains';     Needle = 'luzzy-theme.css?v=';                    Min = 1 },
    @{ Id = '018-ext-snapshot';       File = '../ext/luzzy-ext.js';              Mode = 'contains';     Needle = 'luzzy_theme_snapshot';                  Min = 1 },
    @{ Id = '019-drawer-ui';          File = 'assets/js/ui-components.js';       Mode = 'contains';     Needle = '[LuzzyRP patch 019]';                   Min = 2 },
    @{ Id = '019-preview-index';      File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 019]';                   Min = 1 },
    @{ Id = '020-vector-toast';       File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 020]';                   Min = 2 },
    @{ Id = '020-toast-throttle';     File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '__luzzyVectorToastAt';                  Min = 2 },
    @{ Id = '021-settings-cleanup';   File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 021]';                   Min = 1 },
    @{ Id = '021-auto-stats';         File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 021]';                   Min = 1 },
    @{ Id = '022-fullscreen-gone';    File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 022]';                   Min = 1 },
    @{ Id = '022-fullscreen-app';     File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 022]';                   Min = 1 },
    # [LuzzyRP patch 029 退役 023] STA1N/OpenRouter/SiliconFlow 已退出内置列表（v1.3.0 需求 2），
    # 023 图标修复随条目退位；029 校验接管（精简列表 + editable + 迁移标记）
    @{ Id = '029-providers-core';     File = 'assets/js/core-utils.js';          Mode = 'contains';     Needle = '[LuzzyRP patch 029]';                   Min = 1 },
    @{ Id = '029-providers-novel';    File = 'novel/index.html';                 Mode = 'contains';     Needle = '[LuzzyRP patch 029]';                   Min = 1 },
    @{ Id = '024-about-enhance';      File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 024]';                   Min = 1 },
    @{ Id = '024-about-app';          File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 024]';                   Min = 1 },
    @{ Id = '025-usage-chart';        File = 'assets/js/ui-components.js';       Mode = 'contains';     Needle = '[LuzzyRP patch 025]';                   Min = 1 },
    @{ Id = '025-usage-app';          File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 025]';                   Min = 1 },
    @{ Id = '025-usage-runtime';      File = 'assets/js/runtime-services.js';    Mode = 'contains';     Needle = '[LuzzyRP patch 025]';                   Min = 1 },
    @{ Id = '025-usage-index';        File = 'index.html';                       Mode = 'contains';     Needle = 'chart-data="usageChartData"';              Min = 1 },
    @{ Id = '026-vector-fix';         File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 026]';                   Min = 3 },
    @{ Id = '027-splash-index';       File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 027]';                   Min = 1 },
    @{ Id = '027-splash-js';          File = '../ext/luzzy-splash.js';           Mode = 'contains';     Needle = 'lsp-dive';                              Min = 1 },
    @{ Id = '028-theme-single';       File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 028]';                   Min = 1 },
    @{ Id = '028-theme-app';          File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 028]';                   Min = 1 },
    @{ Id = '029-providers-app';      File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 029]';                   Min = 7 },
    @{ Id = '029-providers-index';    File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 029]';                   Min = 3 },
    @{ Id = '030-version-index';      File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 030]';                   Min = 1 },
    @{ Id = '030-version-app';        File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 030]';                   Min = 1 },
    @{ Id = '030-version-ext';        File = '../ext/luzzy-ext.js';              Mode = 'contains';     Needle = '[LuzzyRP patch 030]';                   Min = 1 },
    @{ Id = '031-memory-node-app';    File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 031]';                   Min = 3 },
    @{ Id = '032-stream-runtime';     File = 'assets/js/runtime-services.js';    Mode = 'contains';     Needle = '[LuzzyRP patch 032]';                   Min = 2 },
    @{ Id = '032-stream-index';       File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 032]';                   Min = 1 },
    @{ Id = '033-input-index';        File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 033]';                   Min = 2 },
    @{ Id = '034-perf-theme';         File = '../ext/luzzy-theme.css';           Mode = 'contains';     Needle = '[LuzzyRP patch 034]';                   Min = 4 },
    @{ Id = 'R1-built-in-content';  File = 'assets/js/built-in-content.js';    Mode = 'hash-upstream' },
    @{ Id = 'R2-styles-css';        File = 'assets/css/styles.css';            Mode = 'hash-upstream' },
    @{ Id = 'R3-changelog-sync';    File = '../ext/luzzy-changelog.js';        Mode = 'changelog-sync' }
)

$failCount = 0
$passCount = 0
Write-Host "== LuzzyRP 标记校验门（verify-markers）=="
foreach ($item in $Manifest) {
    $path = Join-Path $RphubDir ($item.File -replace '/', '\')
    $ok = $false
    $detail = ''
    if (-not (Test-Path $path)) {
        $detail = '文件不存在'
    } elseif ($item.Mode -eq 'contains') {
        $count = ([regex]::Matches(([System.IO.File]::ReadAllText($path)), [regex]::Escape($item.Needle))).Count
        $ok = $count -ge $item.Min
        $detail = "命中 $count / 要求 $($item.Min)"
    } elseif ($item.Mode -eq 'notcontains') {
        $count = ([regex]::Matches(([System.IO.File]::ReadAllText($path)), [regex]::Escape($item.Needle))).Count
        $ok = $count -eq 0
        $detail = if ($ok) { '未发现（正确）' } else { "发现 $count 处残留" }
    } elseif ($item.Mode -eq 'changelog-sync') {
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $nodeOut = & node (Join-Path $RepoRoot 'tools\gen-changelog.mjs') --check 2>&1
        $ErrorActionPreference = $prevEap
        $nodeText = (($nodeOut | Out-String).Trim())
        if ($LASTEXITCODE -eq 0) { $ok = $true; $detail = '应用内数据与 CHANGELOG.md 一致' }
        elseif ($LASTEXITCODE -eq 3) { $ok = $false; $detail = '应用内 CHANGELOG 数据过期：运行 node tools/gen-changelog.mjs 同步' }
        else { $ok = $false; $detail = 'changelog-sync 执行失败: ' + $nodeText.Substring(0, [Math]::Min(120, $nodeText.Length)) }
    } elseif ($item.Mode -eq 'hash-upstream') {
        $relKey = $item.File.ToLower()
        $current = Get-FileSha256 $path
        $baseline = $Fingerprints[$relKey]
        if (-not $baseline) { $detail = '指纹基线缺少该文件'; $ok = $false }
        elseif ($current -eq $baseline) { $ok = $true; $detail = '与上游基线逐字节一致' }
        else { $detail = '与上游基线不一致（规定 1/2 违规或指纹待更新）' }
    }
    if ($ok) { $passCount++; Write-Host "[PASS] $($item.Id) — $detail" }
    else { $failCount++; Write-Host "[FAIL] $($item.Id) — $detail" }
}
Write-Host "== 结果: $passCount PASS / $failCount FAIL =="
if ($failCount -gt 0) {
    Write-Host "存在 FAIL：同步未完成。按 AGENTS.md §4.3 冲突处理修复后复跑。"
    exit 1
}
Write-Host "全部通过：二创标记完整，同步状态合格（硬性规定 10）。"
exit 0
