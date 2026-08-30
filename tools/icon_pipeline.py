#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LuzzyRP 图标资产管线（HARD_REQUIREMENTS 规定 6 的落实）

职责：
  1. 扫描 docs/game-icon-pack/间距/256像素/白色/（815 枚实心单色 PNG，12 类）
     → 复制到 app/src/main/res/drawable-nodpi/（ic_game_<拼音>.png）
  2. 生成 GameIcons.kt 注册表（分类 + 中文原名元数据）
  3. 解析 docs/lobe-ui-master/src/icons/lucideExtra/*.tsx 的 SVG 路径数据
     → 机械转换为 VectorDrawable XML（复制路径，非手绘）→ 生成 LobeIcons.kt
  4. 生成 LuzzyIcons.kt 语义别名表（UI 层唯一取图标入口）
  5. 由 docs/brand-logos/luzzy.png 生成启动图标（legacy + adaptive）
  6. 生成通知 small_icon（白色单色 PNG）

用法：python tools/icon_pipeline.py   （在工作区根目录执行）
规则：图标仅允许来自 game-icon-pack 与 lobe-ui-master，禁止自绘（规定 6）。
      重新生成安全：全部输出为确定性生成物。
"""

import json
import math
import re
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

try:
    from pypinyin import lazy_pinyin
except ImportError:
    print("缺少 pypinyin：python -m pip install pypinyin")
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
PACK_ROOT = ROOT / "docs" / "game-icon-pack" / "间距" / "256像素" / "白色"
LOBE_ROOT = ROOT / "docs" / "lobe-ui-master" / "src" / "icons" / "lucideExtra"
BRAND = ROOT / "docs" / "brand-logos" / "luzzy.png"

RES_DIR = ROOT / "app" / "src" / "main" / "res"
DRAWABLE_DIR = RES_DIR / "drawable-nodpi"
VECTOR_DIR = RES_DIR / "drawable"
ICONS_PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "luzzymeow" / "luzzyrp" / "ui" / "icons"

# 类目目录 → 拼音前缀
CATEGORY_MAP = {
    "1-游戏": "game", "2-物品": "item", "3-装备": "equip", "4-自然": "nature",
    "5-食物": "food", "6-建筑": "building", "7-载具": "vehicle", "8-界面": "ui",
    "9-媒体": "media", "10-编辑": "edit", "11-符号": "symbol", "12-杂项": "misc",
}

# ---------------------------------------------------------------------------
# 名称处理
# ---------------------------------------------------------------------------

def to_pinyin(name: str) -> str:
    """中文名 → 拼音蛇形；非字母数字字符剔除/转下划线。"""
    parts = lazy_pinyin(name)
    out = []
    for p in parts:
        p = p.lower()
        p = re.sub(r"[^a-z0-9]", "", p)
        if p:
            out.append(p)
    joined = "_".join(out)
    joined = re.sub(r"_+", "_", joined).strip("_")
    if not joined:
        joined = "icon"
    if joined[0].isdigit():
        joined = "i" + joined
    return joined[:60]  # 资源名长度保护


def sanitize_pinyin_prefix(s: str) -> str:
    """对拼音音节再清洗（pypinyin 可能产出带非字母字符的音节）。"""
    s = re.sub(r"[^a-z0-9_]", "", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s or "icon"



# ---------------------------------------------------------------------------
# 图标清理（v0.2.0）：修复"黑边"问题
# 源 PNG 为 P 模式（索引色）：透明区 RGB 残留杂色、边缘硬锯齿，部分渲染器
# 将透明区渲为黑底。清理流程：P→RGBA → 透明区 RGB 归零 → 字形 bbox 提取 →
# 归一化居中（统一视觉大小，占比 TARGET_OCCUPANCY）→ 轻度抗锯齿边缘。
# ---------------------------------------------------------------------------

TARGET_OCCUPANCY = 0.72   # 字形占画布边长比例（大小统一的核心参数）
CANVAS = 256


def clean_icon(im: Image.Image) -> Image.Image:
    """索引色/任意模式 → 清理后的 RGBA 方形画布，字形归一化居中。"""
    im = im.convert("RGBA")
    # 透明区 RGB 归零（防止杂色渗透）
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                px[x, y] = (0, 0, 0, 0)
    # 字形 bbox（alpha > 8 视为可见）
    alpha = im.split()[3]
    bbox = alpha.point(lambda v: 255 if v > 8 else 0).getbbox()
    if not bbox:
        return Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    glyph = im.crop(bbox)
    # 归一化：长边缩放到 TARGET_OCCUPANCY * CANVAS（仅缩小或温和放大）
    target = int(CANVAS * TARGET_OCCUPANCY)
    scale = target / max(glyph.width, glyph.height)
    new_w = max(1, round(glyph.width * scale))
    new_h = max(1, round(glyph.height * scale))
    glyph = glyph.resize((new_w, new_h), Image.LANCZOS)
    # 透明边缘轻度平滑：低 alpha 压零，去除索引色硬边
    a = glyph.split()[3].point(lambda v: 0 if v < 24 else v)
    glyph.putalpha(a)
    # 居中放置
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(glyph, ((CANVAS - new_w) // 2, (CANVAS - new_h) // 2))
    return canvas


def clean_logo(im: Image.Image) -> Image.Image:
    """品牌 logo 清理：去除暗色柔光晕（低 alpha 且偏黑的像素压零）。"""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 40 and (r + g + b) < 220:  # 低透明度且偏暗 → 黑晕
                px[x, y] = (0, 0, 0, 0)
    return im


# ---------------------------------------------------------------------------
# 第 1+2 步：扫描 game-icon-pack 并生成资源与 GameIcons.kt
# ---------------------------------------------------------------------------

def build_game_icons() -> list[dict]:
    if not PACK_ROOT.exists():
        print(f"图标源目录不存在：{PACK_ROOT}")
        sys.exit(1)

    DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)
    ICONS_PKG.mkdir(parents=True, exist_ok=True)

    used_names: set[str] = set()
    records: list[dict] = []
    for cat_dir in sorted(PACK_ROOT.iterdir()):
        if not cat_dir.is_dir():
            continue
        cat_prefix = CATEGORY_MAP.get(cat_dir.name)
        if cat_prefix is None:
            print(f"跳过未知类目：{cat_dir.name}")
            continue
        for png in sorted(cat_dir.glob("*.png")):
            stem = png.stem
            base = f"ic_game_{sanitize_pinyin_prefix(to_pinyin(stem))}"
            res_name = base
            n = 2
            while res_name in used_names:  # 重名消歧（同名不同变体 -02 等）
                res_name = f"{base}_{n}"
                n += 1
            used_names.add(res_name)
            cleaned = clean_icon(Image.open(png))
            cleaned.save(DRAWABLE_DIR / f"{res_name}.png")
            records.append({
                "res": res_name, "original": stem,
                "category": cat_prefix, "category_cn": cat_dir.name,
            })

    print(f"[game-icon-pack] 共处理 {len(records)} 枚图标")
    return records


GAME_ICONS_TEMPLATE = '''// ============================================================
// 本文件由 tools/icon_pipeline.py 自动生成 —— 禁止手改
// 图标来源：docs/game-icon-pack（规定 6 允许的图标源之一）
// 重新生成：python tools/icon_pipeline.py
// ============================================================
package com.luzzymeow.luzzyrp.ui.icons

import androidx.annotation.DrawableRes
import com.luzzymeow.luzzyrp.R

/** 单枚游戏图标元数据：res 为 drawable 资源 ID；name 为中文原名；category 为拼音类目。 */
data class GameIcon(
    @DrawableRes val res: Int,
    val name: String,
    val category: String,
    val categoryCn: String,
)

object GameIcons {{
{entries}

    // —— 分类索引 ——
{category_lists}
}}
'''


def generate_game_icons_kt(records: list[dict]) -> None:
    entries = []
    for r in records:
        entries.append(
            '    val {}: GameIcon = GameIcon(R.drawable.{}, "{}", "{}", "{}")'.format(
                r["res"].replace("ic_game_", "", 1).replace("-", "_"),
                r["res"], r["original"], r["category"], r["category_cn"],
            )
        )
    cats: dict[str, list[str]] = {}
    for r in records:
        prop = r["res"].replace("ic_game_", "", 1).replace("-", "_")
        cats.setdefault(r["category"], []).append(prop)
    cat_lists = []
    for cat, props in cats.items():
        cat_lists.append(
            '    val {}: List<GameIcon> = listOf({})'.format(
                cat, ", ".join(f"GameIcons.{p}" for p in props)
            )
        )
    cat_lists.append(
        "    val ALL: List<GameIcon> = " + " + ".join(f"{c}" for c in cats.keys())
    )
    out = GAME_ICONS_TEMPLATE.format(entries="\n".join(entries), category_lists="\n".join(cat_lists))
    (ICONS_PKG / "GameIcons.kt").write_text(out, encoding="utf-8")
    print(f"[GameIcons.kt] 生成完成（{len(records)} 项）")


# ---------------------------------------------------------------------------
# 第 3 步：lucideExtra → VectorDrawable（机械转换，非手绘）
# ---------------------------------------------------------------------------

def tsx_to_path_data(tag: str, attrs: dict) -> str | None:
    """SVG 元素 → VectorDrawable pathData（lucide 24×24 视口，描边风格）。"""
    if tag == "path":
        return attrs.get("d")
    if tag == "line":
        x1, y1 = float(attrs.get("x1", 0)), float(attrs.get("y1", 0))
        x2, y2 = float(attrs.get("x2", 0)), float(attrs.get("y2", 0))
        return f"M{x1} {y1}L{x2} {y2}"
    if tag == "circle":
        cx, cy = float(attrs.get("cx", 0)), float(attrs.get("cy", 0))
        r = float(attrs.get("r", 0))
        return (f"M{cx - r} {cy}A{r} {r} 0 1 0 {cx + r} {cy}"
                f"A{r} {r} 0 1 0 {cx - r} {cy}Z")
    if tag == "rect":
        x, y = float(attrs.get("x", 0)), float(attrs.get("y", 0))
        w, h = float(attrs.get("width", 0)), float(attrs.get("height", 0))
        return f"M{x} {y}L{x + w} {y}L{x + w} {y + h}L{x} {y + h}Z"
    if tag in ("polyline", "polygon"):
        pts = re.findall(r"[-0-9.]+", attrs.get("points", ""))
        if len(pts) >= 4:
            cmds = [f"M{pts[0]} {pts[1]}"]
            for i in range(2, len(pts) - 1, 2):
                cmds.append(f"L{pts[i]} {pts[i + 1]}")
            if tag == "polygon":
                cmds.append("Z")
            return "".join(cmds)
    return None


ATTR_RE = re.compile(r"(\w+)\s*:\s*'([^']*)'|(\w+)\s*:\s*\"([^\"]*)\"")
ELEMENT_RE = re.compile(r"\[\s*'(\w+)'\s*,\s*\{([^}]*)\}\s*,?\s*\]", re.S)
NAME_RE = re.compile(r"createLucideIcon\(\s*'(\w+)'")

VECTOR_TEMPLATE = '''<?xml version="1.0" encoding="utf-8"?>
<!-- 由 tools/icon_pipeline.py 从 lobe-ui-master lucideExtra 机械转换生成 —— 禁止手改 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{paths}
</vector>
'''


def build_lobe_icons() -> list[dict]:
    VECTOR_DIR.mkdir(parents=True, exist_ok=True)
    records = []
    for tsx in sorted(LOBE_ROOT.glob("*.tsx")):
        text = tsx.read_text(encoding="utf-8")
        m = NAME_RE.search(text)
        if not m:
            continue
        comp_name = m.group(1)
        body = text
        paths = []
        for em in ELEMENT_RE.finditer(body):
            tag, attr_blob = em.group(1), em.group(2)
            attrs = {}
            for am in ATTR_RE.finditer(attr_blob):
                key = am.group(1) or am.group(3)
                val = am.group(2) if am.group(2) is not None else am.group(4)
                attrs[key] = val
            # 提取数字属性（circle/rect/line 用）
            for k in ("x1", "y1", "x2", "y2", "cx", "cy", "r", "x", "y", "width", "height"):
                if k in attrs:
                    try:
                        attrs[k] = float(re.findall(r"[-0-9.]+", str(attrs[k]))[0])
                    except (ValueError, IndexError):
                        pass
            d = tsx_to_path_data(tag, attrs)
            if d:
                paths.append(
                    '    <path\n        android:pathData="{}"\n'
                    '        android:strokeColor="#FFFFFFFF"\n'
                    '        android:strokeWidth="2"\n'
                    '        android:strokeLineCap="round"\n'
                    '        android:strokeLineJoin="round"\n'
                    '        android:fillType="nonZero" />'.format(d.replace('"', "&quot;"))
                )
        if not paths:
            print(f"[lobe] 跳过（无可转换路径）：{tsx.name}")
            continue
        snake = re.sub(r"Icon$", "", comp_name)
        snake = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", snake).lower()
        res_name = f"ic_lobe_{snake}"
        (VECTOR_DIR / f"{res_name}.xml").write_text(
            VECTOR_TEMPLATE.format(paths="\n".join(paths)), encoding="utf-8"
        )
        records.append({"res": res_name, "component": comp_name})
    print(f"[lobe-ui lucideExtra] 共转换 {len(records)} 枚 VectorDrawable")
    return records


LOBE_ICONS_TEMPLATE = '''// ============================================================
// 本文件由 tools/icon_pipeline.py 自动生成 —— 禁止手改
// 图标来源：docs/lobe-ui-master lucideExtra（规定 6 允许的图标源之一）
// 重新生成：python tools/icon_pipeline.py
// ============================================================
package com.luzzymeow.luzzyrp.ui.icons

import androidx.annotation.DrawableRes
import com.luzzymeow.luzzyrp.R

object LobeIcons {{
{entries}
}}
'''


def generate_lobe_icons_kt(records: list[dict]) -> None:
    entries = []
    for r in records:
        prop = r["res"].replace("ic_lobe_", "", 1)
        entries.append('    val {}: GameIcon = GameIcon(R.drawable.{}, "{}", "lobe", "lobe-ui")'.format(
            prop, r["res"], r["component"]))
    (ICONS_PKG / "LobeIcons.kt").write_text(
        LOBE_ICONS_TEMPLATE.format(entries="\n".join(entries)), encoding="utf-8")
    print(f"[LobeIcons.kt] 生成完成（{len(records)} 项）")


# ---------------------------------------------------------------------------
# 第 4 步：LuzzyIcons 语义别名（UI 层唯一入口）
# ---------------------------------------------------------------------------

# 语义名 → 候选中文原名（按优先级做精确/包含匹配）
ALIASES: dict[str, list[str]] = {
    "Send": ["发送", "纸飞机", "送出"],
    "NewChat": ["羽毛笔", "书写", "钢笔", "对话", "评论", "气泡"],
    "History": ["历史", "时钟", "时间"],
    "Star": ["五角星", "星形", "星星", "星"],
    "StarFilled": ["书签", "收藏"],
    "Settings": ["设置", "齿轮", "齿轮组"],
    "Delete": ["删除", "垃圾桶", "垃圾"],
    "Edit": ["编辑", "铅笔", "笔"],
    "Search": ["搜索", "放大镜", "查找"],
    "Copy": ["复制", "剪切板", "剪贴板", "拷贝"],
    "Share": ["分享", "共享"],
    "Close": ["叉号", "关闭", "叉"],
    "Back": ["左箭头", "返回", "向左"],
    "Refresh": ["刷新", "循环", "重试"],
    "Lock": ["上锁", "锁", "挂锁"],
    "Unlock": ["解锁", "开锁"],
    "Info": ["信息", "感叹号", "圆圈感叹"],
    "Book": ["书", "书籍", "书本"],
    "Map": ["地图", "藏宝图"],
    "Sword": ["剑", "刀"],
    "Dice": ["六面骰子", "骰子"],
    "Brain": ["大脑", "脑", "智慧"],
    "Memory": ["笔记本", "备忘", "大脑"],
    "Stop": ["停止", "方块", "方框"],
    "Play": ["播放", "三角", "播放键"],
    "Pause": ["暂停", "双竖线"],
    "Plus": ["加号", "添加", "加", "十字"],
    "Minus": ["减号", "减"],
    "Check": ["对勾", "勾号", "打钩", "对号"],
    "Warning": ["警告", "双感叹号", "感叹号", "警示"],
    "User": ["用户", "女性", "人形", "男人"],
    "Users": ["多用户", "删除用户", "用户组"],
    "Menu": ["菜单", "列表", "对齐"],
    "Download": ["下载", "向下箭头"],
    "Upload": ["上传", "向上箭头"],
    "Image": ["图片", "图像", "照片"],
    "Camera": ["相机", "摄像头", "照相机"],
    "Mic": ["麦克风", "话筒", "录音"],
    "Volume": ["音量", "喇叭", "扬声器"],
    "Moon": ["夜晚", "月亮", "月"],
    "Sun": ["太阳", "日"],
    "Home": ["房子", "主页", "家"],
    "Chat": ["消息", "气泡", "对话", "聊天"],
    "More": ["省略号", "更多", "三个点", "菜单"],
    "Expand": ["展开", "箭头下", "下箭头", "向下", "扩展"],
    "Collapse": ["箭头上", "收起", "上箭头", "向上", "折叠"],
    "Pin": ["图钉", "钉住"],
    "Eye": ["眼睛", "可见", "视野"],
    "EyeOff": ["不可见", "闭眼", "眼睛-02"],
    "Trophy": ["奖杯", "冠军"],
    "Save": ["保存", "储存"],
    "Bag": ["背包", "袋", "包"],
    "Clock": ["时钟", "钟表", "时间"],
    "Calendar": ["日历", "日期"],
    "Globe": ["地球", "世界", "星球"],
    "Fire": ["火", "火焰", "燃烧"],
    "Heart": ["心形", "爱心", "心", "喜欢"],
    "Shield": ["盾", "盾牌", "防御"],
    "Wand": ["魔杖", "法杖", "魔法棒"],
    "Crown": ["王冠", "皇冠"],
    "Flag": ["旗帜", "旗", "旗子"],
    "Lightning": ["闪电", "雷电", "电"],
    "Scroll": ["纸", "卷轴", "羊皮纸"],
    "Quill": ["羽毛", "羽毛笔", "钢笔"],
    "Robot": ["电脑主机", "机器人", "机械"],
    "Sparkle": ["闪光", "星光", "闪烁", "亮"],
    "Filter": ["过滤器", "漏斗", "筛选"],
    "Sort": ["排序", "筛选", "分类"],
    "Trash": ["垃圾", "垃圾桶", "废物"],
    "Translate": ["翻译", "语言", "地球"],
    "Palette": ["画笔", "调色板", "颜料", "画板"],
    "Font": ["字体", "文字"],
    "Link": ["链接", "链条", "连接"],
    "Key": ["钥匙", "密钥"],
    "Cloud": ["云", "云端"],
    "Database": ["芯片", "数据库", "存储", "硬盘"],
    "Code": ["代码", "编程", "程序"],
    "Terminal": ["键盘", "终端", "命令行", "控制台"],
    "Gift": ["箱子", "礼物", "礼盒"],
    "Coin": ["金币", "元宝", "钱"],
    "Gem": ["宝石", "钻石", "钻石"],
    "Potion": ["药水", "药剂", "药瓶"],
    "ScrollUp": ["箭头上-02", "上箭头", "向上"],
    "Cat": ["猫", "猫脸", "小猫"],
    "Fox": ["狐狸"],
    "Wolf": ["狼"],
    "Ghost": ["幽灵", "鬼"],
    "Skull": ["骷髅", "头骨"],
}

# 通知小图标（白色单色）：优先猫形（品牌 LuzzyMeow），回退书本
NOTIFICATION_ICON_CANDIDATES = ["猫", "猫脸", "小猫", "书", "书本"]


def match_alias(candidates: list[str], records: list[dict]) -> dict | None:
    for cand in candidates:
        for r in records:  # 精确优先
            if r["original"] == cand:
                return r
    for cand in candidates:
        for r in records:  # 包含次之
            if cand in r["original"]:
                return r
    return None


def generate_luzzy_icons_kt(records: list[dict], lobe_records: list[dict]) -> list[str]:
    resolved, unresolved = [], []
    for alias, candidates in ALIASES.items():
        hit = match_alias(candidates, records)
        if hit:
            prop = hit["res"].replace("ic_game_", "", 1).replace("-", "_")
            resolved.append((alias, f"GameIcons.{prop}", hit["original"]))
        else:
            unresolved.append(alias)
    # lobe 图标别名
    for alias, comp in [("BotPrompt", "BotPrompt"), ("Mcp", "Mcp"), ("CreateBot", "CreateBot"),
                        ("GroupBot", "GroupBot"), ("ProviderIcon", "Provider")]:
        for r in lobe_records:
            if r["component"].startswith(comp):
                prop = r["res"].replace("ic_lobe_", "", 1)
                resolved.append((alias, f"LobeIcons.{prop}", comp))
                break

    lines = ["// ============================================================",
             "// 本文件由 tools/icon_pipeline.py 自动生成 —— 禁止手改",
             "// LuzzyIcons：UI 层语义别名（UI 代码只允许从这里取图标）",
             "// 重新生成：python tools/icon_pipeline.py",
             "// ============================================================",
             "package com.luzzymeow.luzzyrp.ui.icons",
             "",
             "object LuzzyIcons {"]
    for alias, target, original in resolved:
        lines.append(f"    val {alias}: GameIcon = {target} // 原名「{original}」")
    lines.append("}")
    (ICONS_PKG / "LuzzyIcons.kt").write_text("\n".join(lines), encoding="utf-8")
    print(f"[LuzzyIcons.kt] 语义别名 {len(resolved)} 项；未解析 {len(unresolved)} 项：{unresolved}")
    return unresolved


# ---------------------------------------------------------------------------
# 第 5+6 步：启动图标 + 通知小图标
# ---------------------------------------------------------------------------

LAUNCHER_DPI = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_DPI = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def build_launcher_icons(records: list[dict]) -> None:
    if not BRAND.exists():
        print(f"品牌 logo 缺失：{BRAND}")
        return
    logo = clean_logo(Image.open(BRAND).convert("RGBA"))

    mipmap = RES_DIR / "mipmap-anydpi-v26"
    mipmap.mkdir(parents=True, exist_ok=True)
    (mipmap / "ic_launcher.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n', encoding="utf-8")
    shutil.copyfile(mipmap / "ic_launcher.xml", mipmap / "ic_launcher_round.xml")

    for dpi, size in ADAPTIVE_DPI.items():  # 自适应前景（66% 安全区）
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = int(size * 0.66)
        fg = logo.resize((inner, inner), Image.LANCZOS)
        offset = ((size - inner) // 2, (size - inner) // 2)
        canvas.alpha_composite(fg, offset)
        d = RES_DIR / f"mipmap-{dpi}"
        d.mkdir(parents=True, exist_ok=True)
        canvas.save(d / "ic_launcher_foreground.png")

    for dpi, size in LAUNCHER_DPI.items():  # legacy 图标（圆角方形底 + 居中 logo）
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        radius = size // 5
        bg = Image.new("RGBA", (size, size), (42, 14, 34, 255))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
        canvas.paste(bg, (0, 0), mask)
        inner = int(size * 0.78)
        fg = logo.resize((inner, inner), Image.LANCZOS)
        canvas.alpha_composite(fg, ((size - inner) // 2, (size - inner) // 2))
        d = RES_DIR / f"mipmap-{dpi}"
        d.mkdir(parents=True, exist_ok=True)
        canvas.save(d / "ic_launcher.png")
        round_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        circle_mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(circle_mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        round_canvas.paste(bg, (0, 0), circle_mask)
        round_canvas.alpha_composite(fg, ((size - inner) // 2, (size - inner) // 2))
        round_canvas.save(d / "ic_launcher_round.png")
    print("[launcher] legacy + adaptive 启动图标生成完成")


def build_notification_icon(records: list[dict]) -> None:
    hit = match_alias(NOTIFICATION_ICON_CANDIDATES, records)
    if hit is None:
        print("[notification] 未找到合适图标，跳过")
        return
    src = DRAWABLE_DIR / f"{hit['res']}.png"
    shutil.copyfile(src, VECTOR_DIR / "ic_notification.png")
    print(f"[notification] small_icon ← {hit['original']}（{hit['res']}）")


# ---------------------------------------------------------------------------

def main() -> None:
    records = build_game_icons()
    generate_game_icons_kt(records)
    lobe_records = build_lobe_icons()
    generate_lobe_icons_kt(lobe_records)
    generate_luzzy_icons_kt(records, lobe_records)
    build_launcher_icons(records)
    build_notification_icon(records)
    print("资产管线完成。")


if __name__ == "__main__":
    main()
