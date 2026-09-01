# LuzzyRP 主题切换 · 动效方案（草案）

> 依据 huashu-design 动效纪律（动效=物理学）+ open-design 动画哲学。
> 方向板选定后，本方案与 DESIGN.md 同步定稿。

## 主题切换转场（亮暗切换）

**目标**：切换不突兀，有「呼吸感」——200ms 内完成，符合 open-design 进入 200ms 令牌。

### 方案：CSS 变量过渡 + 遮罩淡入

```css
/* luzzy-theme.css 追加 */
:root {
    transition: background-color 200ms cubic-bezier(0.23, 1, 0.32, 1);
}
/* 所有使用主题变量的元素继承过渡 */
body, .bg-white, .bg-gray-50, .text-gray-800, ... {
    transition: background-color 200ms cubic-bezier(0.23, 1, 0.32, 1),
                color 200ms cubic-bezier(0.23, 1, 0.32, 1),
                border-color 200ms cubic-bezier(0.23, 1, 0.32, 1);
}
```

**问题**：RP-Hub 大量使用 Tailwind 工具类（bg-white、text-gray-800 等），逐个加 transition 不现实。
**方案**：全局通配过渡（性能可接受，元素数量有限）：

```css
* {
    transition: background-color 200ms cubic-bezier(0.23, 1, 0.32, 1),
                color 200ms cubic-bezier(0.23, 1, 0.32, 1),
                border-color 200ms cubic-bezier(0.23, 1, 0.32, 1),
                fill 200ms cubic-bezier(0.23, 1, 0.32, 1),
                stroke 200ms cubic-bezier(0.23, 1, 0.32, 1);
}
```

**注意**：通配 transition 会覆盖上游已有的 transition 类（如 hover 效果）——需要 `transition: none` 排除或提高优先级。备选：只对 `body` 和主要容器做过渡，其余靠遮罩。

### 方案 B：遮罩淡入（更稳）

切换瞬间加一个全屏遮罩（背景色 = 目标主题底色），200ms 淡入淡出，遮住颜色跳变：

```js
// luzzy-ext.js
function switchTheme(theme, mode) {
    const overlay = document.createElement('div');
    overlay.style.cssText = 'position:fixed;inset:0;z-index:9999;pointer-events:none;' +
        'background:' + (mode === 'dark' ? '#1A1815' : '#FAF9F5') + ';' +
        'opacity:0;transition:opacity 200ms cubic-bezier(0.23,1,0.32,1);';
    document.body.appendChild(overlay);
    requestAnimationFrame(() => { overlay.style.opacity = '1'; });
    setTimeout(() => {
        document.documentElement.dataset.theme = theme;
        document.documentElement.dataset.mode = mode;
        requestAnimationFrame(() => { overlay.style.opacity = '0'; });
        setTimeout(() => overlay.remove(), 200);
    }, 200);
}
```

**推荐**：方案 B（遮罩淡入）——不干扰上游 transition 类，实现简单，效果稳定。

## 交互动画（主题内）

| 元素 | 动画 | 令牌 |
|------|------|------|
| 按钮按压 | scale 0.98 + 阴影收缩 | 150ms ease-out |
| 卡片 hover | 阴影抬升 + 边框色过渡 | 200ms ease-out |
| 弹窗进入 | opacity 0→1 + translateY 8px→0 | 200ms ease-out |
| 弹窗退出 | opacity 1→0 + translateY 0→4px | 140ms ease-out |
| 侧边栏滑入 | translateX -100%→0 | 200ms ease-out |
| 消息气泡进入 | opacity 0→1 + translateY 4px→0 | 200ms ease-out |
| 思考卡片展开 | opacity + max-height 过渡 | 200ms ease-out |

## 转场动画（页面切换）

RP-Hub 是单页应用（currentView 切换），页面转场：

```css
/* 页面切换：淡入 + 轻微上移 */
.management-view-enter {
    animation: viewIn 200ms cubic-bezier(0.23, 1, 0.32, 1);
}
@keyframes viewIn {
    from { opacity: 0; transform: translateY(8px); }
    to { opacity: 1; transform: translateY(0); }
}
```

**注意**：RP-Hub 已有 `animate-fade-in` / `animate-slide-up`（0.3s ease-out）——扩展层覆盖为 200ms 版本，或保留上游节奏（0.3s 也在可接受范围）。方向板选定后定夺。

## reduced-motion

```css
@media (prefers-reduced-motion: reduce) {
    * { transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
```
