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

# ------------------------------------------------------------------
# Patch 008 · 主题色板 var() 化（tailwind.config gray/primary → var() 引用）
# ------------------------------------------------------------------
$titlePath = Join-Path $RphubDir "index.html"
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match "var\(--tw-gray-50\)") {
    Write-Host "[SKIP] 008-theme-vars (已应用)"
} else {
    if ($titleContent -match "50: '#f9fafb'") {
        # 逐行替换 gray/primary 色板为 var() 引用
        $lines = [System.IO.File]::ReadAllLines($titlePath)
        $inGray = $false; $inPrimary = $false
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $l = $lines[$i]
            if ($l -match '^\s+gray: \{') { $inGray = $true; continue }
            if ($l -match '^\s+primary: \{') { $inPrimary = $true; continue }
            if ($inGray -and $l -match '^\s+\},?$') { $inGray = $false; continue }
            if ($inPrimary -and $l -match '^\s+\},?$') { $inPrimary = $false; continue }
            if ($inGray -and $l -match "^\s+(\d+): '#[0-9a-fA-F]+',$") {
                $lines[$i] = $l -replace "^\s+(\d+): '#[0-9a-fA-F]+',$", "`$1`: 'var(--tw-gray-`$1)',"
            }
            if ($inPrimary -and $l -match "^\s+(\d+): '#[0-9a-fA-F]+',$") {
                $lines[$i] = $l -replace "^\s+(\d+): '#[0-9a-fA-F]+',$", "`$1`: 'var(--tw-primary-`$1)',"
            }
        }
        [System.IO.File]::WriteAllLines($titlePath, $lines, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 008-theme-vars"
    } else {
        Write-Host "[FAIL] 008-theme-vars: 未找到原色板，上游可能已改色板结构"
    }
}

# ------------------------------------------------------------------
# Patch 009 · fontFamilies 增加 luzzy 选项（core-utils.js）
# ------------------------------------------------------------------
$corePath = Join-Path $RphubDir "assets\js\core-utils.js"
$coreContent = [System.IO.File]::ReadAllText($corePath)
if ($coreContent -match "value: 'luzzy', label: 'Luzzy 默认字体'") {
    Write-Host "[SKIP] 009-font-luzzy (已应用)"
} else {
    if ($coreContent -match "fontFamilies: Object.freeze") {
        $lines = [System.IO.File]::ReadAllLines($corePath)
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -match "fontFamilies: Object.freeze") {
                $insert = @("                { value: 'luzzy', label: 'Luzzy 默认字体' },")
                $lines = $lines[0..$i] + $insert + $lines[($i+1)..($lines.Length-1)]
                break
            }
        }
        [System.IO.File]::WriteAllLines($corePath, $lines, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 009-font-luzzy"
    } else {
        Write-Host "[FAIL] 009-font-luzzy: 未找到 fontFamilies"
    }
}

# ------------------------------------------------------------------
# Patch 010 · 默认字体/主题 + normalizeFontFamily + theme watch（app.js）
# ------------------------------------------------------------------
$appPath = Join-Path $RphubDir "assets\js\app.js"
$appContent = [System.IO.File]::ReadAllText($appPath)
if ($appContent -match "fontFamily: 'luzzy'") {
    Write-Host "[SKIP] 010-defaults (已应用)"
} else {
    $lines = [System.IO.File]::ReadAllLines($appPath)
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s+fontFamily: 'modern',$") {
            $lines[$i] = "            fontFamily: 'luzzy',"
        }
        if ($lines[$i] -match "normalizeFontFamily = \(value\) => \['modern', 'serif', 'system'\]") {
            $lines[$i] = "        const normalizeFontFamily = (value) => ['luzzy', 'modern', 'serif', 'system'].includes(value) ? value : 'modern';"
        }
    }
    [System.IO.File]::WriteAllLines($appPath, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[ OK ] 010-defaults"
}

# ------------------------------------------------------------------
# Patch 011 · 设置页主题 UI + theme 字段 + watch + 迁移（index.html + app.js）
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match "界面主题") {
    Write-Host "[SKIP] 011-theme-ui (已应用)"
} else {
    if ($titleContent -match "Font Family Setting") {
        $lines = [System.IO.File]::ReadAllLines($titlePath)
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -match "Font Family Setting") {
                $newBlock = @(
                    '                                    <!-- Theme Setting (LuzzyRP 扩展) -->',
                    '                                    <div',
                    '                                        class="bg-gray-50/60 p-4 rounded-xl border border-gray-100 hover:bg-white hover:border-gray-200 hover:shadow-sm transition-all duration-200">',
                    '                                        <label',
                    '                                            class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">界面主题</label>',
                    '                                        <custom-select v-model="settings.theme" :options="themeOptions"',
                    '                                            button-class="rounded-lg px-3 py-1.5 text-sm text-gray-700 focus:border-indigo-400 focus:ring-indigo-100"',
                    '                                            menu-class="text-sm">',
                    '                                        </custom-select>',
                    '                                        <div v-if="settings.theme === ''luzzy''" class="mt-2 flex items-center gap-2">',
                    '                                            <label class="text-xs font-bold text-gray-500 uppercase tracking-wider">模式</label>',
                    '                                            <custom-select v-model="settings.themeMode" :options="themeModeOptions"',
                    '                                                button-class="rounded-lg px-3 py-1.5 text-sm text-gray-700 focus:border-indigo-400 focus:ring-indigo-100"',
                    '                                                menu-class="text-sm">',
                    '                                            </custom-select>',
                    '                                        </div>',
                    '                                    </div>',
                    '',
                    '                                    <!-- Font Family Setting -->'
                )
                $lines = $lines[0..($i-1)] + $newBlock + $lines[$i..($lines.Length-1)]
                break
            }
        }
        [System.IO.File]::WriteAllLines($titlePath, $lines, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 011-theme-ui"
    } else {
        Write-Host "[FAIL] 011-theme-ui: 未找到 Font Family Setting"
    }
}

# app.js 部分：theme 字段 + themeOptions + watch + 迁移
$appContent = [System.IO.File]::ReadAllText($appPath)
if ($appContent -match "theme: 'luzzy'") {
    Write-Host "[SKIP] 011b-theme-logic (已应用)"
} else {
    $lines = [System.IO.File]::ReadAllLines($appPath)
    # 1) settings 增加 theme/themeMode 字段
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s+fontFamily: 'luzzy',$") {
            $insert = @("            theme: 'luzzy',", "            themeMode: 'light',")
            $lines = $lines[0..($i-1)] + $insert + $lines[$i..($lines.Length-1)]
            break
        }
    }
    # 2) uiOptions 解构后加 themeOptions/themeModeOptions
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s+\} = uiOptions;$") {
            $insert = @(
                "        const themeOptions = Object.freeze([",
                "            { value: 'luzzy', label: '暖纸书房（Luzzy）' },",
                "            { value: 'classic', label: '经典（原版）' }",
                "        ]);",
                "        const themeModeOptions = Object.freeze([",
                "            { value: 'light', label: '亮色' },",
                "            { value: 'dark', label: '暗色' }",
                "        ]);"
            )
            $lines = $lines[0..$i] + $insert + $lines[($i+1)..($lines.Length-1)]
            break
        }
    }
    # 3) fontFamily watch 后加 theme watch
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "watch\(\(\) => settings.fontFamily, applyFontFamily") {
            $insert = @(
                "        const applyTheme = (value) => {",
                "            document.documentElement.dataset.theme = value === 'classic' ? 'classic' : 'luzzy';",
                "        };",
                "        const applyThemeMode = (value) => {",
                "            document.documentElement.dataset.mode = value === 'dark' ? 'dark' : 'light';",
                "            if (window.LuzzyBridge && window.LuzzyBridge.setSystemBarStyle) {",
                "                window.LuzzyBridge.setSystemBarStyle(value === 'dark' ? 'dark' : 'light');",
                "            }",
                "        };",
                "        watch(() => settings.theme, applyTheme, { immediate: true });",
                "        watch(() => settings.themeMode, applyThemeMode, { immediate: true });"
            )
            $lines = $lines[0..$i] + $insert + $lines[($i+1)..($lines.Length-1)]
            break
        }
    }
    # 4) setup return 暴露 themeOptions/themeModeOptions
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "fontFamilyOptions, fontSizeOptions, availableImageStyleOptions") {
            $lines[$i] = $lines[$i].Replace("fontFamilyOptions, fontSizeOptions, availableImageStyleOptions", "fontFamilyOptions, fontSizeOptions, themeOptions, themeModeOptions, availableImageStyleOptions")
            break
        }
    }
    # 5) 老用户迁移：savedSettings 无 theme 字段 → classic
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "if \(!Object.prototype.hasOwnProperty.call\(savedSettings, 'apiProviderId'\)\)") {
            $insert = @(
                "                    if (!Object.prototype.hasOwnProperty.call(savedSettings, 'theme')) {",
                "                        settings.theme = 'classic'; // 老用户保留经典主题",
                "                    }",
                "                    if (!Object.prototype.hasOwnProperty.call(savedSettings, 'themeMode')) {",
                "                        settings.themeMode = 'light';",
                "                    }"
            )
            $lines = $lines[0..($i-1)] + $insert + $lines[$i..($lines.Length-1)]
            break
        }
    }
    [System.IO.File]::WriteAllLines($appPath, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[ OK ] 011b-theme-logic"
}

Write-Host "== patch 重放完成 =="
Write-Host "提示: 重放后请执行 sync 回归清单（AGENTS.md §6.2）"
