# ============================================================
# verify-markers.ps1 —— LuzzyRP 二创标记与上游完整性校验门
# ============================================================
# 用法:  .\tools\verify-markers.ps1
# 前置:  sync-upstream.ps1 + apply-patches.ps1 已完成（同步后必跑）
# 行为:  四组校验，全部 PASS 才算同步合格（硬性规定 1/6/10）：
#        A. 实体后像等值（8 项）——我方文件 == 实体头 index <post> 的 LF 归一 blob id。
#           一项顶过去上百条 needle：实体 patch 本身已编码完整二创意图，
#           且随实体重生成自动更新，不会出现「登记了但不存在」的假绿。
#        B. 纯净等值（13 项）——无标记的上游文件与上游基线逐字节相等（硬性规定 2）。
#           参考克隆可用时与 git show <commit>:<file> 比对；不可用时降级为指纹表并打印所用模式。
#        C. 语义锚点（约 16 项）——跨文件的关键契约，防「patch 重放成功但语义丢失」
#           （1.9.5 合并时踩过：getCustomApiUrlKey 被误删、契约类名丢失、导出悬空）。
#        D. 红线 + 扩展层（8 项）——nsfw_rules 块哈希（硬性规定 1）+ ext 层 6 文件 + orphan 检查。
#
# 历史：
#   ① 本门原校验 rphub/** 的二创标记 + 敏感文件指纹（旧版 158 项 needle 清单）；
#   ② v3.0 P6 期间 rphub/** 整体删除（原生 Compose 路线），本门收编为只校验 assets/ext/**；
#   ③ [2026-09-20 放弃原生 Kotlin + Compose、回到 WebView 路线] rphub/** 复位，
#      上游标记校验**应当恢复**；
#   ④ [2026-09-21] 已完成恢复：按 1.9.7 基线重新登记**精简但完整**的一套（约 45 项），
#      21 个上游文件 100% 受覆盖。旧 158 项清单未复活——它含大量已退役项，
#      且大量 needle 与实体 patch 重复；实体后像等值一项即可等价覆盖。
#
# 负控（每次改动本门后必须验一次，证明它真的会响）：
#   在仓库副本上破坏任一项（删一个字体 / 改一个锚点 / 动 nsfw 块），本门须判红。
# ============================================================

$ErrorActionPreference = "Stop"
$RepoRoot = Join-Path $PSScriptRoot ".."
$RphubDir = Join-Path $RepoRoot "app\src\main\assets\rphub"
$ExtDir = Join-Path $RepoRoot "app\src\main\assets\ext"
$EntitiesDir = Join-Path $RepoRoot "tools\patches\entities"
$FingerprintPath = Join-Path $RepoRoot "tools\upstream-fingerprints.txt"
$RefDir = Join-Path $RepoRoot "rp-hub-reference"

$failCount = 0
$passCount = 0
function Report-Fail([string]$Message) { $script:failCount++; Write-Host "[FAIL] $Message" }
function Report-Pass([string]$Message) { $script:passCount++; Write-Host "[PASS] $Message" }

# ---- 工具函数 ----
function Get-FileGitBlobIdLfNormalized([string]$Path) {
    # 与 apply-patches.ps1 同口径：LF 归一内容的 git blob id。
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
function Get-EntityBlobIds([string]$EntityPath) {
    if (-not (Test-Path $EntityPath)) { return $null }
    foreach ($line in [System.IO.File]::ReadAllLines($EntityPath)) {
        if ($line -match '^index ([0-9a-f]{7,40})\.\.([0-9a-f]{7,40})') {
            return @{ Pre = $Matches[1]; Post = $Matches[2] }
        }
    }
    return $null
}
function Get-BaselineCommit {
    if (-not (Test-Path $FingerprintPath)) { return $null }
    foreach ($line in (Get-Content $FingerprintPath)) {
        if ($line -match '\(commit\s+([0-9a-fA-F]{7,40})') { return $Matches[1] }
    }
    return $null
}
function Get-Sha256LfNormalized([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    $text = [System.IO.File]::ReadAllText($Path)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return (($sha.ComputeHash($bytes)) | ForEach-Object { $_.ToString('x2') }) -join '' } finally { $sha.Dispose() }
}
function Get-UpstreamBlobIdLfNormalized([string]$Ref, [string]$RelPath) {
    # 从参考克隆取该 ref 的原始字节（cmd 重定向保字节），LF 归一后算 blob id。
    if (-not (Test-Path (Join-Path $RefDir '.git'))) { return $null }
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) "luzzy-vm-$PID.bin"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $null = & cmd /c ('git -C "{0}" show {1}:{2} > "{3}" 2>nul' -f $RefDir, $Ref, $RelPath, $tmp)
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($exit -ne 0 -or -not (Test-Path $tmp)) { return $null }
    $result = Get-FileGitBlobIdLfNormalized $tmp
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $result
}

# 指纹基线载入（SHA256 → 相对路径小写）
$Fingerprints = @{}
if (Test-Path $FingerprintPath) {
    Get-Content $FingerprintPath | ForEach-Object {
        if ($_ -match '^([0-9A-Fa-f]{64})\s+\*?(.+)$') {
            $Fingerprints[$Matches[2].Trim().Replace('\', '/').ToLower()] = $Matches[1].ToUpper()
        }
    }
}
$Matches = $null   # $Matches 是会话级自动变量，前面循环的残留会串到后面（2026-09-21 实测踩过）

$BaselineCommit = Get-BaselineCommit
$RefAvailable = Test-Path (Join-Path $RefDir '.git')

Write-Host "== LuzzyRP 校验门（verify-markers，2026-09-21 上游层恢复版）=="
Write-Host "   上游基线: $(if ($BaselineCommit) { $BaselineCommit } else { '未登记' })"  
Write-Host "   纯净等值比对模式: $(if ($RefAvailable -and $BaselineCommit) { '参考克隆 git show（权威）' } else { '指纹表（降级，仅供参考）' })"
Write-Host ""

# ============================================================
# A. 实体后像等值（8 项）
# ============================================================
Write-Host "-- A. 实体后像等值（我方文件 == 实体头 index <post>）--"
$EntityItems = @(
    @{ File = 'character/index.html';           Entity = '007-character-html.patch' },
    @{ File = 'novel/index.html';               Entity = '007-029-novel-html.patch' },
    @{ File = 'assets/js/core-utils.js';        Entity = '009-035-core-utils-js.patch' },
    @{ File = 'index.html';                     Entity = '012-035-index-html.patch' },
    @{ File = 'assets/js/runtime-services.js';  Entity = '012-035-runtime-services-js.patch' },
    @{ File = 'assets/js/ui-components.js';     Entity = '012-035-ui-components-js.patch' },
    @{ File = 'assets/js/app.js';               Entity = '012-036-app-js.patch' },
    @{ File = 'assets/js/api-utils.js';         Entity = '015-032-api-utils-js.patch' }
)
foreach ($item in $EntityItems) {
    $target = Join-Path $RphubDir ($item.File -replace '/', '\')
    $entity = Join-Path $EntitiesDir $item.Entity
    $ids = Get-EntityBlobIds $entity
    if (-not $ids) { Report-Fail "A $($item.Entity): 实体缺失或头无 index 行"; continue }
    $act = Get-FileGitBlobIdLfNormalized $target
    if (-not $act) { Report-Fail "A $($item.Entity): 目标文件缺失 $($item.File)"; continue }
    $cl = [Math]::Min($ids.Post.Length, $act.Length)
    if ($act.Substring(0, $cl) -eq $ids.Post.Substring(0, $cl)) {
        Report-Pass "A $($item.File) == $($ids.Post)（实体后像）"
    } else {
        Report-Fail "A $($item.File): 实测 $($act.Substring(0,8)) != 实体后像 $($ids.Post)（文件被绕过 patch 改动，或实体需重生成）"
    }
}

# ============================================================
# B. 纯净等值（13 项，硬性规定 2：未登记 patch 的上游文件不得被改）
# ============================================================
Write-Host ""
Write-Host "-- B. 纯净等值（无标记上游文件 == 上游基线，硬性规定 2）--"
$EntityFileSet = @{}
foreach ($item in $EntityItems) { $EntityFileSet[$item.File.ToLower()] = $true }
# 全部上游受管文件 = 指纹表路径列
$upstreamFiles = @()
foreach ($line in (Get-Content $FingerprintPath)) {
    if ($line -match '^([0-9A-Fa-f]{64}|MISSING)\s+\*?(.+)$') { $upstreamFiles += $Matches[2].Trim().Replace('\', '/') }
}
$Matches = $null
$pureChecked = 0
foreach ($rel in $upstreamFiles) {
    if ($EntityFileSet.ContainsKey($rel.ToLower())) { continue }   # 实体文件由 A 组管
    $target = Join-Path $RphubDir ($rel -replace '/', '\')
    if (-not (Test-Path $target)) { Report-Fail "B ${rel}: 文件缺失"; continue }
    $pureChecked++
    if ($RefAvailable -and $BaselineCommit) {
        $upBlob = Get-UpstreamBlobIdLfNormalized $BaselineCommit $rel
        $actBlob = Get-FileGitBlobIdLfNormalized $target
        if (-not $upBlob) { Report-Fail "B ${rel}: 取不到上游基线内容（ref/路径问题？）"; continue }
        if ($actBlob -eq $upBlob) { Report-Pass "B ${rel} 与上游基线逐字节一致" }
        else { Report-Fail "B ${rel}: 与上游基线不一致（$($actBlob.Substring(0,8)) != $($upBlob.Substring(0,8))）—— 硬性规定 2 违规或指纹待更新" }
    } else {
        $actSha = Get-Sha256LfNormalized $target
        $rec = $Fingerprints[$rel.ToLower()]
        if (-not $rec) { Report-Fail "B ${rel}: 指纹表缺项"; continue }
        if ($actSha -eq $rec.ToLower()) { Report-Pass "B ${rel} 与指纹表一致（降级模式）" }
        else { Report-Fail "B ${rel}: 与指纹表不一致（降级模式，建议恢复 rp-hub-reference 后复跑）" }
    }
}
Write-Host "   （B 组实检 $pureChecked 项）"

# ============================================================
# C. 语义锚点（功能契约，防「重放成功但语义丢失」）
# ============================================================
Write-Host ""
Write-Host "-- C. 语义锚点（跨文件关键契约）--"
$Anchors = @(
    # 品牌与离线化（001/004/006/007）
    @{ Id = 'C001-brand-title';      File = 'index.html';             Pat = '<title>LuzzyRP</title>';                     Min = 1 },
    @{ Id = 'C002-no-update-api';    File = 'index.html';             Pat = 'rphub-update-api';                           Max = 0 },
    @{ Id = 'C004-no-tailwind-cdn';  File = 'index.html';             Pat = 'cdn\.tailwindcss\.com';                      Max = 0 },
    @{ Id = 'C004-no-unpkg-vue';     File = 'index.html';             Pat = 'unpkg\.com/vue';                             Max = 0 },
    @{ Id = 'C006-no-google-fonts';  File = 'index.html';             Pat = 'fonts\.googleapis\.com';                     Max = 0 },
    @{ Id = 'C006-local-fonts';      File = 'index.html';             Pat = 'local-fonts\.css';                           Min = 1 },
    @{ Id = 'C007a-char-no-cdn';     File = 'character/index.html';   Pat = 'cdn\.tailwindcss\.com';                      Max = 0 },
    @{ Id = 'C007b-novel-no-cdn';    File = 'novel/index.html';       Pat = 'cdn\.tailwindcss\.com';                      Max = 0 },
    # 扩展层挂载（005/027/042/047）
    @{ Id = 'C005-mount-theme';      File = 'index.html';             Pat = '\.\./ext/luzzy-theme\.css';                  Min = 1 },
    @{ Id = 'C005-mount-ext';        File = 'index.html';             Pat = '\.\./ext/luzzy-ext\.js';                     Min = 1 },
    @{ Id = 'C027-mount-splash';     File = 'index.html';             Pat = '\.\./ext/luzzy-splash\.js';                  Min = 1 },
    @{ Id = 'C047-mount-prefix';     File = 'index.html';             Pat = '\.\./ext/luzzy-prefix-guard\.js';            Min = 1 },
    # patch 053：返回键接管（挂载 + 扩展层入口契约；上游是 SPA 不用 History API，
    # 故这条链必须有 —— 缺挂载或缺 window.__luzzyHandleBack 都会退回「任意页按返回键直接退出」）
    @{ Id = 'C053-mount-back';       File = 'index.html';             Pat = '\.\./ext/luzzy-back\.js';                    Min = 1 },
    # 主题/字体单轨（009/010/011/028）
    @{ Id = 'C009-font-luzzy';       File = 'assets/js/core-utils.js'; Pat = "value: 'luzzy'";                            Min = 1 },
    @{ Id = 'C010-font-default';     File = 'assets/js/app.js';        Pat = "fontFamily: 'luzzy'";                       Min = 1 },
    @{ Id = 'C011-theme-default';    File = 'assets/js/app.js';        Pat = "theme: 'luzzy'";                            Min = 1 },
    @{ Id = 'C028-no-theme-switch';  File = 'index.html';             Pat = '界面主题';                                    Max = 0 },
    # 内置商精简（029）：只留 DeepSeek + 老用户迁移
    @{ Id = 'C029-default-deepseek'; File = 'assets/js/core-utils.js'; Pat = "defaultApiProviderId: 'deepseek'";          Min = 1 },
    @{ Id = 'C029-migrate';          File = 'assets/js/app.js';        Pat = 'migrateRemovedBuiltinProviders';            Min = 2 },
    # 多商路由（012/015）
    @{ Id = 'C012-resolve-model';    File = 'assets/js/app.js';        Pat = 'resolveModelRequest';                       Min = 5 },
    @{ Id = 'C012-all-providers';    File = 'assets/js/app.js';        Pat = 'allApiProviders';                           Min = 10 },
    @{ Id = 'C015-gemini-adapter';   File = 'assets/js/api-utils.js';  Pat = 'requestGeminiCompletion';                   Min = 2 },
    # 侧栏品牌与入口（019/014）
    @{ Id = 'C019-brand-sidebar';    File = 'assets/js/ui-components.js'; Pat = 'LuzzyRP';                                Min = 1 },
    @{ Id = 'C014-about-view';       File = 'assets/js/ui-components.js'; Pat = 'about';                                  Min = 1 },
    # 流式活通道（044/045）
    @{ Id = 'C044-live-buffer';      File = 'assets/js/app.js';        Pat = 'livePending';                               Min = 5 },
    @{ Id = 'C044-live-commit';      File = 'assets/js/app.js';        Pat = 'commitLiveDelta';                           Min = 3 },
    # 输入解耦（046）
    @{ Id = 'C046-input-mirror';     File = 'assets/js/app.js';        Pat = 'chatInputMirror';                           Min = 5 },
    @{ Id = 'C046-input-hastext';    File = 'assets/js/app.js';        Pat = 'chatInputHasText';                          Min = 3 },
    # 前缀稳定化（047）与 Anthropic 断点（048）
    @{ Id = 'C047-next-response';    File = 'assets/js/app.js';        Pat = 'buildNextResponsePromptText';               Min = 2 },
    @{ Id = 'C048-anthropic-cache';  File = 'assets/js/api-utils.js';  Pat = 'withAnthropicCacheBreakpoint';              Min = 2 },
    @{ Id = 'C048-cache-control';    File = 'assets/js/api-utils.js';  Pat = 'cache_control';                             Min = 3 },
    # 潜伏缺陷修复（051）与结束原因（052）
    @{ Id = 'C051-toolcalls-guard';  File = 'assets/js/app.js';        Pat = 'toolCalls\?\.length';                       Min = 3 },
    @{ Id = 'C051-max-tokens';       File = 'assets/js/api-utils.js';  Pat = 'MAX_TOKENS';                                Min = 1 },
    @{ Id = 'C052-finish-record';    File = 'assets/js/runtime-services.js'; Pat = 'finishReason: String\(meta\.finishReason'; Min = 1 },
    @{ Id = 'C052-finish-ref';       File = 'assets/js/app.js';        Pat = 'lastFinishReason';                          Min = 3 }
)
foreach ($a in $Anchors) {
    $p = Join-Path $RphubDir ($a.File -replace '/', '\')
    if (-not (Test-Path $p)) { Report-Fail "C $($a.Id): 文件不存在 $($a.File)"; continue }
    $n = ([regex]::Matches([System.IO.File]::ReadAllText($p), $a.Pat)).Count
    if ($a.ContainsKey('Max')) {
        if ($n -le $a.Max) { Report-Pass "C $($a.Id)（命中 $n，上限 $($a.Max)）" }
        else { Report-Fail "C $($a.Id): 命中 $n 处，应为 ≤$($a.Max)（残留未清除）" }
    } else {
        if ($n -ge $a.Min) { Report-Pass "C $($a.Id)（命中 $n，下限 $($a.Min)）" }
        else { Report-Fail "C $($a.Id): 命中 $n 处，应 ≥$($a.Min)（patch 语义丢失？）" }
    }
}

# ============================================================
# D. 红线 + 扩展层完整性
# ============================================================
Write-Host ""
Write-Host "-- D. 红线与扩展层 --"
# D1. nsfw_rules 块哈希（硬性规定 1：永远不可触碰）
# 用块哈希而非整文件哈希：整文件受 029 内置商精简影响本就会变，
# 而红线只关心 <nsfw_rules>…</nsfw_rules> 这一段是否一字未动。
$NSFW_EXPECTED = '1f7ac3424ed78d4e7459f5d178e6bc3a55aab561cc02788cb47b201577455a28'
$biPath = Join-Path $RphubDir 'assets\js\built-in-content.js'
if (Test-Path $biPath) {
    $biText = [System.IO.File]::ReadAllText($biPath)
    $i0 = $biText.IndexOf('<nsfw_rules>')
    $i1 = if ($i0 -ge 0) { $biText.IndexOf('</nsfw_rules>', $i0) } else { -1 }
    if ($i0 -lt 0 -or $i1 -lt 0) {
        Report-Fail "D1-nsfw-block: 找不到 <nsfw_rules>…</nsfw_rules> 块（硬性规定 1 无法校验）"
    } else {
        $block = $biText.Substring($i0, $i1 - $i0 + '</nsfw_rules>'.Length)
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { $h = (($sha.ComputeHash([System.Text.UTF8Encoding]::new($false).GetBytes($block))) | ForEach-Object { $_.ToString('x2') }) -join '' } finally { $sha.Dispose() }
        if ($h -eq $NSFW_EXPECTED) { Report-Pass "D1-nsfw-block 哈希一致（硬性规定 1 未触碰）" }
        else { Report-Fail "D1-nsfw-block: 哈希 $h != $NSFW_EXPECTED —— 硬性规定 1 被违反！" }
    }
} else { Report-Fail "D1-nsfw-block: built-in-content.js 缺失" }

# D2. 扩展层 7 文件
$ExtManifest = @(
    @{ File = 'luzzy-ext.js';          Purpose = '扩展层主入口（主题快照/宏/胶水）' },
    @{ File = 'luzzy-stream.js';       Purpose = 'SSE 流式旁路（content/reasoning 双通道）' },
    @{ File = 'luzzy-bridge.js';       Purpose = '原生桥 JS 侧接口' },
    @{ File = 'luzzy-prefix-guard.js'; Purpose = 'prompt 前缀纯追加守卫' },
    @{ File = 'luzzy-splash.js';       Purpose = '开屏动画' },
    @{ File = 'luzzy-changelog.js';    Purpose = '应用内更新公告数据源' },
    @{ File = 'luzzy-back.js';         Purpose = '系统返回键接管（patch 053：弹窗→抽屉→回对话页）' }
)
foreach ($item in $ExtManifest) {
    $path = Join-Path $ExtDir $item.File
    if (-not (Test-Path $path)) { Report-Fail "D2 $($item.File) 不存在（$($item.Purpose)）"; continue }
    $len = (Get-Item $path).Length
    if ($len -gt 0) { Report-Pass "D2 $($item.File)（$len B）— $($item.Purpose)" }
    else { Report-Fail "D2 $($item.File) 空文件（$($item.Purpose)）" }
}
# D3. ext 目录不应有清单外的孤儿 js
foreach ($e in (Get-ChildItem $ExtDir -Filter *.js | Where-Object { $ExtManifest.File -notcontains $_.Name })) {
    Report-Fail "D3 $($e.Name): 不在清单中（孤儿文件：登记或删除）"
}
# D4. 应用内 CHANGELOG 数据与仓库根 CHANGELOG.md 一致
$prevEapN = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$null = & node (Join-Path $RepoRoot 'tools\gen-changelog.mjs') --check 2>&1
$clExit = $LASTEXITCODE
$ErrorActionPreference = $prevEapN
if ($clExit -eq 0) { Report-Pass "D4 changelog-sync（应用内数据与 CHANGELOG.md 一致）" }
elseif ($clExit -eq 3) { Report-Fail "D4 changelog-sync: 应用内数据过期 → 运行 node tools/gen-changelog.mjs" }
else { Report-Fail "D4 changelog-sync: 执行失败（退出码 $clExit）" }
# D5. 二创独有资产存活（同步不得吃掉 fonts / vendor）
# ★ 必须断言**精确数量**而不是「目录存在」——负控实测：只检查存在时，
#   删掉一个字体文件（16.2 MB 里少 1 个）本门完全不响，而 sync-upstream.ps1
#   的历史缺陷恰好就是整目录吃掉 assets/fonts/。数量是这里唯一有效的判据。
$OwnAssets = @(
    @{ Rel = 'assets\fonts';              Files = 11; What = '本地字体（离线化硬性规定 4）' },
    @{ Rel = 'vendor';                    Files = 7;  What = 'vendor 本地化依赖（硬性规定 4）' },
    @{ Rel = 'assets\css\local-fonts.css'; Files = 1;  What = '本地字体 @font-face 表' }
)
foreach ($asset in $OwnAssets) {
    $p = Join-Path $RphubDir $asset.Rel
    if (-not (Test-Path $p)) { Report-Fail "D5 $($asset.Rel) 缺失 —— 同步吃掉了二创资产（$($asset.What)）"; continue }
    $n = if ((Get-Item $p).PSIsContainer) { @(Get-ChildItem -Recurse -File $p).Count } else { 1 }
    if ($n -eq $asset.Files) { Report-Pass "D5 $($asset.Rel) 在位（$n/$($asset.Files) 个文件）— $($asset.What)" }
    else { Report-Fail "D5 $($asset.Rel): 实测 $n 个文件，应为 $($asset.Files) —— 有资产被删（$($asset.What)）" }
}

Write-Host ""
Write-Host "== 结果: $passCount PASS / $failCount FAIL =="
if ($failCount -gt 0) {
    Write-Host "存在 FAIL：同步未完成。按 AGENTS.md §4.1 处置后复跑。"
    exit 1
}
Write-Host "全部通过：实体后像等值 + 上游纯净等值 + 语义锚点 + 红线（硬性规定 1/2/6/10）。"
exit 0
