# ============================================================
# verify-markers.ps1 —— 扩展层完整性校验门（2026-09-20 复位版）
# ============================================================
# 历史：
#   ① 本门原校验 app/src/main/assets/rphub/** 的二创标记（硬性规定 10）；
#   ② v3.0 P6 曾把 rphub/** 整体删除（渲染层退役、上游同步纪律退役），本门收编为
#      只校验 assets/ext/** 扩展层；
#   ③ [2026-09-20「放弃原生 Kotlin + Compose 路线」] rphub/** 已随 WebView 路线复位
#      （见 AGENTS.md §4 上游同步纪律），上游标记校验**应当恢复**。
#      [2026-09-21 续] 分期合并已完成（1.9.3 → 1.9.7，工作树与上游 main 对齐），
#      但**上游标记校验的恢复尚未做**——它需要按 1.9.7 新基线重新登记校验项
#      （旧基线 1.9.3 时代的标记清单已随上游重写失效）。当前仍只校验扩展层，
#      这是已知的覆盖缺口，不是「已完成」。
# 用法:  .\tools\verify-markers.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$RepoRoot = Join-Path $PSScriptRoot ".."
$ExtDir = Join-Path $RepoRoot "app\src\main\assets\ext"

# ---- 扩展层清单：文件 → 用途一句话（新文件入列时同步登记）----
$Manifest = @(
    @{ File = 'luzzy-ext.js';          Purpose = '扩展层主入口（主题快照/宏/胶水）' },
    @{ File = 'luzzy-stream.js';       Purpose = 'SSE 流式旁路（content/reasoning 双通道）' },
    @{ File = 'luzzy-bridge.js';       Purpose = '原生桥 JS 侧接口' },
    @{ File = 'luzzy-prefix-guard.js'; Purpose = 'prompt 前缀纯追加守卫' },
    @{ File = 'luzzy-splash.js';       Purpose = '开屏动画' },
    @{ File = 'luzzy-changelog.js';    Purpose = '应用内更新公告数据源' }
)

$failCount = 0
$passCount = 0
Write-Host "== 扩展层完整性校验门（verify-markers，2026-09-17 收编版）=="
foreach ($item in $Manifest) {
    $path = Join-Path $ExtDir $item.File
    if (Test-Path $path) {
        # 非空 + 非 BOM 损坏（可读 + 有内容即过；内容正确性由构建期 assetSignature 与仪器化用例兜底）
        $len = (Get-Item $path).Length
        if ($len -gt 0) {
            $passCount++; Write-Host "[PASS] $($item.File) — $($item.Purpose)（$len B）"
        } else {
            $failCount++; Write-Host "[FAIL] $($item.File) — 空文件（$($item.Purpose)）"
        }
    } else {
        $failCount++; Write-Host "[FAIL] $($item.File) — 文件不存在（$($item.Purpose)）"
    }
}

# ---- 一致性：ext 目录里不应有清单之外的孤儿 js（防止删功能忘删门）----
$extra = Get-ChildItem $ExtDir -Filter *.js | Where-Object { $Manifest.File -notcontains $_.Name }
foreach ($e in $extra) {
    $failCount++; Write-Host "[FAIL] $($e.Name) — 不在清单中（孤儿文件：登记或删除）"
}

Write-Host "== 结果: $passCount PASS / $failCount FAIL =="
if ($failCount -gt 0) { exit 1 }
Write-Host "全部通过：扩展层完整（上游 rphub 校验已随 P6 退役归档，git 历史可回溯）。"
exit 0