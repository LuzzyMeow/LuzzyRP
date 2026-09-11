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
    # [LuzzyRP patch 035 退役 015-runtime] 上游 1.9.1 传输层迁往 api-utils.js，015 适配器随迁；由 015-api-utils 接管
    @{ Id = '015-api-utils';          File = 'assets/js/api-utils.js';           Mode = 'contains';     Needle = '[LuzzyRP patch 015]';                   Min = 6 },
    @{ Id = '015-index';            File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 015]';                   Min = 5 },
    @{ Id = '016-recall-nomerge';   File = 'assets/js/data-services.js';       Mode = 'contains';     Needle = '[LuzzyRP patch 016]';                   Min = 1 },
    @{ Id = '017-manager-app';      File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 017]';                   Min = 5 },
    @{ Id = '017-manager-html';     File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 017]';                   Min = 2 },
    @{ Id = '018-no-flash';           File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 018]';                   Min = 1 },
    @{ Id = '018-theme-css-head';     File = 'index.html';                       Mode = 'contains';     Needle = 'luzzy-theme.css?v=';                    Min = 1 },
    @{ Id = '018-ext-snapshot';       File = '../ext/luzzy-ext.js';              Mode = 'contains';     Needle = 'luzzy_theme_snapshot';                  Min = 1 },
    # [v1.5.0 助手] 侧栏入口与桥接契约（PLAN §3.3/§14）——扩展层自持文件，非上游 patch
    @{ Id = 'V15-assistant-entry';    File = '../ext/luzzy-assistant.js';        Mode = 'contains';     Needle = 'luzzy-assistant-entry';                 Min = 1 },
    @{ Id = 'V15-assistant-loader';   File = '../ext/luzzy-ext.js';              Mode = 'contains';     Needle = 'luzzy-assistant.js';                    Min = 1 },
    @{ Id = 'V15-assistant-bridge';   File = '../ext/luzzy-bridge.js';           Mode = 'contains';     Needle = 'Luzzy.openAssistant';                   Min = 1 },
    @{ Id = 'V15-assistant-config';   File = '../ext/luzzy-bridge.js';           Mode = 'contains';     Needle = 'Luzzy.pushAssistantConfig';             Min = 1 },
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
    @{ Id = '032-stream-runtime';     File = 'assets/js/runtime-services.js';    Mode = 'contains';     Needle = '[LuzzyRP patch 032]';                   Min = 1 },
    @{ Id = '032-stream-apiutils';    File = 'assets/js/api-utils.js';           Mode = 'contains';     Needle = '[LuzzyRP patch 032]';                   Min = 1 },
    @{ Id = '032-stream-index';       File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 032]';                   Min = 1 },
    @{ Id = '033-input-index';        File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 033]';                   Min = 2 },
    @{ Id = '034-perf-theme';         File = '../ext/luzzy-theme.css';           Mode = 'contains';     Needle = '[LuzzyRP patch 034]';                   Min = 4 },
    @{ Id = '035-icon-index';         File = 'index.html';                       Mode = 'contains';     Needle = '[LuzzyRP patch 035]';                   Min = 3 },
    @{ Id = '035-icon-app';           File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 035]';                   Min = 7 },
    @{ Id = '036-manager-live';       File = 'assets/js/app.js';                 Mode = 'contains';     Needle = '[LuzzyRP patch 036]';                   Min = 1 },
    @{ Id = '037-usage-timefilter-gone'; File = 'assets/js/ui-components.js';     Mode = 'notcontains';  Needle = 'token-usage-time-filter-container' },
    @{ Id = '037-usage-timefilter-app';  File = 'assets/js/app.js';               Mode = 'notcontains';  Needle = 'showTokenUsageTimeFilter.value' },
    @{ Id = '037-usage-timefilter-runtime'; File = 'assets/js/runtime-services.js'; Mode = 'notcontains'; Needle = 'tokenUsageTimeFilterOptions' },
    @{ Id = '037-usage-timefilter-index'; File = 'index.html';                    Mode = 'notcontains';  Needle = ':time-filter="tokenUsageTimeFilter"' },
    @{ Id = '038-update-notice-brand';   File = 'assets/js/ui-components.js';     Mode = 'contains';     Needle = '同步更新上游节点';                       Min = 1 },
    @{ Id = '039-changelog-highlight';   File = 'assets/js/app.js';               Mode = 'contains';     Needle = '[LuzzyRP patch 039]';                   Min = 2 },
    @{ Id = '039-mark-token';            File = '../ext/luzzy-theme.css';         Mode = 'contains';     Needle = '--luzzy-mark';                          Min = 2 },
    @{ Id = '040-model-dialog-index';    File = 'index.html';                     Mode = 'contains';     Needle = '[LuzzyRP patch 040]';                   Min = 2 },
    @{ Id = '040-model-dialog-app';      File = 'assets/js/app.js';               Mode = 'contains';     Needle = '[LuzzyRP patch 040]';                   Min = 2 },
    @{ Id = '040-model-dialog-state';    File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'showModelEditor';                       Min = 5 },
    @{ Id = '041-vision-native';         File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'buildNativeImageContent';               Min = 2 },
    @{ Id = '041-vision-condition';      File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'chatModelSupportsImages';               Min = 3 },
    @{ Id = '041-vision-inject';         File = 'assets/js/app.js';               Mode = 'contains';     Needle = '用户上传了一张图，图片内容为';           Min = 1 },
    @{ Id = '041-video-gone-app';        File = 'assets/js/app.js';               Mode = 'notcontains';  Needle = "'text', 'image', 'video'" },
    @{ Id = '041-video-gone-index';      File = 'index.html';                     Mode = 'notcontains';  Needle = "['text', 'image', 'video']" },
    @{ Id = '041-video-gone-ui';         File = 'assets/js/ui-components.js';     Mode = 'notcontains';  Needle = "video: '视频'" },
    @{ Id = '042-stream-directive';      File = 'index.html';                     Mode = 'contains';     Needle = '[LuzzyRP patch 042]';  Min = 2 },
    @{ Id = '042-stream-script';         File = 'index.html';                     Mode = 'contains';     Needle = 'ext/luzzy-stream.js';  Min = 1 },
    @{ Id = '042-stream-vhtml-gone';     File = 'index.html';                     Mode = 'notcontains';  Needle = 'main, true).text, msg.role, false, { cache: false }' },
    @{ Id = '042-stream-ext-directive';  File = '../ext/luzzy-stream.js';         Mode = 'contains';     Needle = "directive('lsp-stream'";  Min = 1 },
    @{ Id = '042-stream-ext-proof';      File = '../ext/luzzy-stream.js';         Mode = 'contains';     Needle = 'fullHtml === st.prefixHtml';  Min = 1 },
    @{ Id = '043-manual-model-marks';    File = 'assets/js/app.js';               Mode = 'contains';     Needle = '[LuzzyRP patch 043]';  Min = 6 },
    @{ Id = '043-seed-fn';               File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'seedManualProviderModels';  Min = 2 },
    @{ Id = '043-seed-called';           File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'seedManualProviderModels();';  Min = 1 },
    @{ Id = '043-fetch-flag';            File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'providerModelsFetched';  Min = 4 },
    @{ Id = '043-manual-first-merge';    File = 'assets/js/app.js';               Mode = 'contains';     Needle = 'const detectedOnly = models.filter';  Min = 1 },
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
