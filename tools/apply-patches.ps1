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
# Patch 008 · 主题色板 var() 化 v2（tailwind.config gray/primary → var() 引用）
# 对应：DESIGN.md 主题技术契约（已验证 Tailwind Play CDN JIT 接受 var() 色值）
# 预期冲突点：上游改色板结构/新增色阶时需重打
# ------------------------------------------------------------------
$titleContent = [System.IO.File]::ReadAllText($titlePath)
if ($titleContent -match 'var\(--tw-gray-50\)') {
    Write-Host "[SKIP] 008-theme-vars (已应用)"
} else {
    if ($titleContent -match "50: '#f9fafb'") {
        $keys = @(50, 100, 200, 300, 400, 500, 600, 700, 800, 900)
        $grayHex = @('#f9fafb', '#f3f4f6', '#e5e7eb', '#d1d5db', '#9ca3af', '#6b7280', '#4b5563', '#374151', '#1f2937', '#111827')
        $priHex = @('#eff6ff', '#dbeafe', '#bfdbfe', '#93c5fd', '#60a5fa', '#3b82f6', '#2563eb', '#1d4ed8', '#1e40af', '#1e3a8a')
        for ($i = 0; $i -lt 10; $i++) {
            $titleContent = $titleContent.Replace("$($keys[$i]): '$($grayHex[$i])'", "$($keys[$i]): 'var(--tw-gray-$($keys[$i]))'")
            $titleContent = $titleContent.Replace("$($keys[$i]): '$($priHex[$i])'", "$($keys[$i]): 'var(--tw-primary-$($keys[$i]))'")
        }
        [System.IO.File]::WriteAllText($titlePath, $titleContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "[ OK ] 008-theme-vars"
    } else {
        Write-Host "[FAIL] 008-theme-vars: 未找到原色板，上游可能已改色板结构"
    }
}

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
if ($titleContent -match '界面主题') {
    Write-Host "[SKIP] 011-theme-ui (已应用)"
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

Write-Host "== patch 重放完成 =="
Write-Host "提示: 重放后请执行 sync 回归清单（AGENTS.md §6.2）"
