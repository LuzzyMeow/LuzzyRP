# ============================================================
# sync-upstream.ps1 —— RP-Hub 上游同步脚本（AGENTS.md §4 SOP）
# ============================================================
# 用法:  .\tools\sync-upstream.ps1 [-DryRun]
# 前置:  rp-hub-reference/ 的 remote 可 fetch（**必须走 SSH**，见下）
# 流程:
#   0. 参考克隆必须干净 + 保护快照（同步前记录我方独有文件，同步后断言未被删）
#   1. git fetch + 改动统计；HEAD 落后时提示先更新参考克隆
#   2. 清单驱动覆盖：按上游 git ls-files 逐个文件覆盖；**只删上游确实删了的文件**
#   3. 重放登记 patch（apply-patches.ps1，退出码有契约）
#   4. 更新指纹基线（覆盖全部上游文件 + 表头 commit）
#   5. 回归清单提示
# 注意:  此脚本只做「文件同步 + patch 重放」，不提交 git、不构建 APK——
#        回归实测通过后由维护者手动 commit（AGENTS.md §3.4）。
#
# ★ 2026-09-21 重写：原「整目录删除 → 复制」的实现会在**标准同步路径**上
#   破坏二创资产。参考克隆顶层是 assets/ character/ novel/ presence-server/
#   index.html LICENSE README.md，而原脚本的 $excludeDirs = @('vendor','fonts')
#   在顶层**永远匹配不到任何东西**（vendor/ 与 assets/fonts/ 在我方树下，
#   不在上游克隆顶层）→ `assets/` 被整目录删除，连带删掉
#   assets/fonts/ 下 11 个字体（16.2 MB）；而 $protectedFiles 只备份了
#   local-fonts.css，字体**无备份、无恢复**。
#   现改为清单驱动 + 保护不变式，并在同步后断言我方独有文件集合未变。
# ============================================================

param(
    [string]$RefDir = (Join-Path $PSScriptRoot "..\rp-hub-reference"),
    [string]$TargetDir = (Join-Path $PSScriptRoot "..\app\src\main\assets\rphub"),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$FingerprintPath = Join-Path $PSScriptRoot "upstream-fingerprints.txt"

Write-Host "== LuzzyRP 上游同步 =="
Write-Host "参考克隆 : $RefDir"
Write-Host "目标目录 : $TargetDir"

# ---------- 0. 参考克隆必须干净（不允许本地改动混入同步） ----------
Push-Location $RefDir
$status = git status --porcelain
Pop-Location
if ($status) {
    Write-Host "[ERROR] rp-hub-reference/ 有未提交改动，请先处理干净再同步（避免二创改动混入上游）"
    exit 1
}

# ---------- 0b. remote 可达性（必须 SSH：本机 443 不通，实测 curl 28） ----------
Push-Location $RefDir
$remoteUrl = (git remote get-url origin 2>$null)
Pop-Location
if ($remoteUrl -match '^https://') {
    Write-Host ""
    Write-Host "[ERROR] rp-hub-reference 的 origin 是 HTTPS（$remoteUrl），本机 443 不通，fetch 必然失败。"
    Write-Host "        改 SSH 形式（只读公开仓库，不涉及凭据）："
    Write-Host "          git -C rp-hub-reference remote set-url origin git@github.com:STA1N156/RP-Hub.git"
    Write-Host "          git -C rp-hub-reference remote -v      # 核验两行都以 git@github.com: 开头"
    exit 1
}

# ---------- 旧清单（我方「最后已知良好状态」的文件集合） ----------
# 语义：指纹表记录的是**我方工作树的最后已知良好状态**，同时其路径列就是
# 「上次同步时上游有哪些文件」的权威来源——用于判定哪些文件是上游删掉的。
$oldManifest = @()
if (Test-Path $FingerprintPath) {
    $oldManifest = @(Get-Content $FingerprintPath |
        ForEach-Object { if ($_ -match '^([0-9A-Fa-f]{64}|MISSING)\s+\*?(.+)$') { $Matches[2].Trim().Replace('\', '/') } })
}
# $Matches 是会话级自动变量，循环结束后仍留着最后一次匹配——
# 后面第 4 步取上游版本号时若不清空，会把这里的残留当成版本号（2026-09-21 实测踩到）。
$Matches = $null

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
    Write-Host "       然后先跑只读预检，确认要重做多少："
    Write-Host "       powershell tools/apply-patches.ps1 -CheckBaseline $baseRef"
    Write-Host "         → 全部「可重放」：重跑本脚本即可"
    Write-Host "         → 有「需三方合并」：按 AGENTS.md §4.1 六步走，禁止盲目覆盖 + 重放"
    Pop-Location
    exit 0
}
# 新清单 = 上游该 ref 的受管文件全集（含 presence-server/ 等一切上游管理的内容）
$newManifest = @(& git ls-files)
Pop-Location
Write-Host "（无改动，已是最新）— 上游受管文件 $($newManifest.Count) 个"

if ($DryRun) {
    Write-Host "`n[DRY-RUN] 已停止（不执行覆盖）"
    exit 0
}

# ---------- 2. 清单驱动覆盖（只动上游管理的文件） ----------
Write-Host "`n[2/5] 清单驱动覆盖上游文件（$($newManifest.Count) 个）..."
Write-Host "  旧清单 $($oldManifest.Count) 个（来自指纹表路径列）"

# 2a. 同步前快照：TargetDir 下**既不在旧清单、也不在新清单**的文件 = 我方独有（保护对象）
# 为何是「并集之外」而不是「旧清单之外」：旧清单来自指纹表，而指纹表可能滞后
# （2026-09-21 实测：它只列了 21 个上游文件中的 15 个，LICENSE / README.md /
# presence-server/* 缺席）。若只按「旧清单之外」判定，这些**其实是上游文件**的路径
# 会被误判成我方独有，同步正当覆盖它们时保护不变式就会误报。
# 用并集是充要的：本脚本只写 newManifest 的路径、只删 oldManifest 中已被上游移除的路径，
# 故两集之外的任何文件**必然**不被触碰——这正是要断言的（fonts / vendor / local-fonts.css）。
$oldSet = @{}
foreach ($rel in $oldManifest) { $oldSet[$rel.ToLower()] = $true }
$newSetEarly = @{}
foreach ($rel in $newManifest) { $newSetEarly[$rel.ToLower()] = $true }
$managedSet = @{}
foreach ($rel in $oldManifest) { $managedSet[$rel.ToLower()] = $true }
foreach ($rel in $newManifest) { $managedSet[$rel.ToLower()] = $true }
$tgtRootLen = (Resolve-Path $TargetDir).Path.Length + 1
$protectedBefore = @{}
Get-ChildItem -Recurse -File $TargetDir | ForEach-Object {
    $rel = $_.FullName.Substring($tgtRootLen).Replace('\', '/')
    if (-not $managedSet.ContainsKey($rel.ToLower())) { $protectedBefore[$rel] = $_.Length }
}
Write-Host "  保护快照：$($protectedBefore.Count) 个我方独有文件（fonts / vendor / local-fonts.css 等）"

# 2b. 逐个上游文件覆盖（建父目录 + 复制；**不删任何目录**）
# ★ 行尾归一（2026-09-21 实测新增）：**不能直接 Copy-Item 参考克隆的工作树**——
#   上游的存储 blob 本身就含 CRLF（该仓库无 .gitattributes），参考克隆按
#   core.autocrlf=true 检出后工作树是 CRLF；直接拷进来会把 CRLF 灌进我方 LF 归一的树，
#   污染 21 个文件（git status 一片 M）。而 `.gitattributes` 要求 `* text=auto eol=lf`，
#   且 AOCI 与实体 patch 都按原始字节判定。
#   故：文本文件一律「读 → LF 归一 → 写」；二进制（字体等）按字节复制。
#   判定用扩展名白名单而非内容嗅探——上游受管文件全是文本，我方独有资产另有保护不变式。
$textExts = @('.html', '.js', '.css', '.json', '.md', '.txt', '.xml', '.svg', '.yml', '.yaml')
$copied = 0
$copiedBin = 0
Push-Location $RefDir
foreach ($rel in $newManifest) {
    $src = Join-Path $RefDir ($rel -replace '/', '\')
    $dst = Join-Path $TargetDir ($rel -replace '/', '\')
    if (-not (Test-Path $src)) { continue }
    $dstDir = Split-Path $dst
    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Force -Path $dstDir | Out-Null }
    $ext = [System.IO.Path]::GetExtension($rel).ToLower()
    if ($textExts -contains $ext -or $ext -eq '') {
        $text = [System.IO.File]::ReadAllText($src).Replace("`r`n", "`n").Replace("`r", "`n")
        [System.IO.File]::WriteAllText($dst, $text, [System.Text.UTF8Encoding]::new($false))
        $copied++
    } else {
        Copy-Item -Force $src $dst
        $copiedBin++
    }
}
Pop-Location
Write-Host "  [sync] 覆盖 $copied 个文本文件（LF 归一）+ $copiedBin 个二进制文件"

# 2c. 仅删除「在旧清单、不在新清单」的路径 = 上游确实删掉的受管文件
$newSet = @{}
foreach ($rel in $newManifest) { $newSet[$rel.ToLower()] = $true }
$removed = 0
foreach ($rel in $oldManifest) {
    if ($newSet.ContainsKey($rel.ToLower())) { continue }
    $dst = Join-Path $TargetDir ($rel -replace '/', '\')
    if (Test-Path $dst) { Remove-Item -Force $dst; Write-Host "  [del] $rel（上游已删除该受管文件）"; $removed++ }
}
if ($removed -eq 0) { Write-Host "  [del] 无（上游未删除任何受管文件）" }

# 2d. ★ 保护不变式：两集之外的文件集合必须一字不动
$protectedAfter = @{}
Get-ChildItem -Recurse -File $TargetDir | ForEach-Object {
    $rel = $_.FullName.Substring($tgtRootLen).Replace('\', '/')
    if (-not $managedSet.ContainsKey($rel.ToLower())) { $protectedAfter[$rel] = $_.Length }
}
$lost = @($protectedBefore.Keys | Where-Object { -not $protectedAfter.ContainsKey($_) })
$changed = @($protectedBefore.Keys | Where-Object {
    $protectedAfter.ContainsKey($_) -and $protectedAfter[$_] -ne $protectedBefore[$_] })
if ($lost.Count -gt 0 -or $changed.Count -gt 0) {
    Write-Host ""
    Write-Host "[FATAL] 保护不变式被破坏 —— 同步删改了我方独有文件："
    $lost | ForEach-Object { Write-Host "        删除: $_" }
    $changed | ForEach-Object { Write-Host "        改动: $_" }
    Write-Host "        用 git status / git checkout 恢复后报告，不要继续。"
    exit 1
}
Write-Host "  [invariant] 我方独有文件 $($protectedAfter.Count) 个全部完好（未删未改）OK"

# 备份二创专属文件在排除目录外时不会被动到（清单驱动下上游不管理的路径永不被删）；
# local-fonts.css 属我方独有文件，已由上面的保护不变式覆盖，无需单独备份/恢复。

# ---------- 3. 重放登记 patch ----------
Write-Host "`n[3/5] 重放二创 patch ..."
$prevEapP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& (Join-Path $PSScriptRoot "apply-patches.ps1")
$patchExit = $LASTEXITCODE
$ErrorActionPreference = $prevEapP
if ($patchExit -ne 0) {
    Write-Host "[ERROR] patch 重放有失败项（退出码 $patchExit）——同步未完成。"
    Write-Host "        上游改了同一片区域时按 AGENTS.md §4.1 走三方合并，禁止盲目覆盖 + 重放。"
    Write-Host "        （本检查在 2026-09-21 之前是死代码：apply-patches.ps1 从不返回非零退出码）"
    exit 1
}
Write-Host "  patch 重放通过（退出码 0）"

# ---------- 4. 更新指纹基线 ----------
Write-Host "`n[4/5] 更新指纹基线 ..."
# 覆盖全部上游受管文件（原为硬编码 15 项，会抹掉 theme.css / theme.js 两行；
# 且清单必须与上一步同源，否则保护不变式的「旧清单」会在下一次同步失真）。
$prevEapFp = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
# 注意：不要在这两处用 `| Select-Object -First 1`——它会提前掐断管道，
# 使 $LASTEXITCODE 变成 -1，从而把「取到了基线」误判成 UNKNOWN（2026-09-21 实测踩到）。
$baselineOut = @(& git -C $RefDir rev-parse HEAD 2>$null)
$baselineExit = $LASTEXITCODE
$ErrorActionPreference = $prevEapFp
$baselineSha = if ($baselineExit -eq 0 -and $baselineOut.Count -gt 0 -and $baselineOut[0]) { $baselineOut[0].Trim() } else { 'UNKNOWN' }
# 版本号：必须用 -match 取 $Matches，**不能**用 Select-String 的 .Matches——
# 且 $Matches 是会话级自动变量，前一个循环的匹配会残留进来（实测把 character/index.html
# 的哈希当成了版本号）。故这里用局部变量显式取值，并先清空 $Matches。
$Matches = $null
$builtInText = (& git -C $RefDir show "HEAD:assets/js/built-in-content.js" 2>$null) -join "`n"
$upstreamVersion = if ($builtInText -match '(?m)^### RP-Hub ([0-9.]+)') { $Matches[1] } else { 'UNKNOWN' }
$Matches = $null
$lines = @(
    "# LuzzyRP 上游文件指纹基线",
    "# 生成: $(Get-Date -Format 'yyyy.MM.dd HH:mm') · 上游基线: RP-Hub $upstreamVersion (commit $baselineSha)",
    "# 用途: 同步验证（硬性规定 1/6）——同步后与 assets/rphub/ 比对；",
    "#       头部「(commit <sha>)」由 tools/apply-patches.ps1 解析为实体前像兜底判定的基线版本，勿删。",
    "#       **路径列同时是 tools/sync-upstream.ps1 的「旧清单」**（判定上游删了哪些受管文件），",
    "#       以及保护不变式的「受管集合」判定依据——故必须覆盖全部上游受管文件，勿手工删行。",
    "# 说明: 哈希为工作树字节（主仓库检出态）；本文件自身按 .gitattributes 以 LF 写出。",
    ""
)
$missing = 0
foreach ($f in $newManifest) {
    $tgtPath = Join-Path $TargetDir ($f -replace '/', '\')
    if (Test-Path $tgtPath) {
        $hash = (Get-FileHash $tgtPath -Algorithm SHA256).Hash
        $lines += "$hash  $f"
    } else {
        $lines += "MISSING  $f"
        $missing++
    }
}
# LF 写出：AOCI 按原始字节做基线，且 .gitattributes 要求 eol=lf
[System.IO.File]::WriteAllText($FingerprintPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
Write-Host "  写入 $($newManifest.Count) 行（MISSING $missing）→ $FingerprintPath"

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
exit 0
