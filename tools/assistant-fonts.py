#!/usr/bin/env python3
"""
assistant-fonts.py —— 助手页字体转换（woff2 → ttf）

【为什么需要】
上游 RP-Hub 的本地字体是 **woff2**（Web 专用压缩格式）。WebView 能直接读，
但 Android Compose 的 `Font(R.font.x)` / `Font(file)` **只接受 TTF/OTF**。
硬性规定 4 要求字体本地打包、禁 CDN，因此需要一次性的格式转换。

【用法】
    python tools/assistant-fonts.py            # 转换默认集（Lora + AlibabaSans）
    python tools/assistant-fonts.py --all      # 追加 PuHuiTi 三个字重（约 +21MB）
    python tools/assistant-fonts.py --check    # 只校验产物是否最新，不写文件

【输入 / 输出】
    输入：app/src/main/assets/rphub/assets/fonts/*.woff2   （上游同步排除项，随包分发）
    输出：app/src/main/assets/assistant/fonts/*.ttf        （助手模块专用，产物入库）

【许可】Lora = SIL OFL 1.1；Alibaba Sans / Alibaba PuHuiTi 3.0 = 阿里巴巴免费商用授权。
两者均允许打包分发（与上游 Web 端一致）。**禁止**改为运行时从 CDN 拉取（硬性规定 4）。

【依赖】fontTools（含 brotli 支持）：`pip install fonttools brotli`
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

try:
    from fontTools.ttLib import TTFont  # type: ignore
except ImportError:  # pragma: no cover
    print("[assistant-fonts] 缺少依赖 fontTools，请先 `pip install fonttools brotli`", file=sys.stderr)
    raise SystemExit(2)

REPO_ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = REPO_ROOT / "app/src/main/assets/rphub/assets/fonts"
DST_DIR = REPO_ROOT / "app/src/main/assets/assistant/fonts"
MANIFEST = DST_DIR / "manifest.json"

# 默认集：体积小、决定「文学排印声音」的拉丁字体
DEFAULT_FONTS = ["Lora-Regular", "Lora-Italic", "AlibabaSans-Regular", "AlibabaSans-Medium", "AlibabaSans-Bold"]
# 可选集：CJK 全字集，每枚约 7MB（APK 体积敏感，需用户拍板后启用）
HEAVY_FONTS = ["AlibabaPuHuiTi-3-55-Regular", "AlibabaPuHuiTi-3-65-Medium", "AlibabaPuHuiTi-3-85-Bold"]


def convert(name: str, check_only: bool) -> dict:
    src = SRC_DIR / f"{name}.woff2"
    dst = DST_DIR / f"{name}.ttf"
    if not src.is_file():
        return {"name": name, "status": "missing-source", "src": str(src.relative_to(REPO_ROOT))}

    src_sha = hashlib.sha256(src.read_bytes()).hexdigest()
    if check_only:
        if not dst.is_file():
            return {"name": name, "status": "missing-output"}
        return {
            "name": name,
            "status": "up-to-date" if _manifest_sha(name) == src_sha else "stale",
            "srcSha256": src_sha,
        }

    font = TTFont(str(src))
    font.flavor = None  # 去掉 woff2 封装 → 裸 sfnt(ttf)
    DST_DIR.mkdir(parents=True, exist_ok=True)
    font.save(str(dst))
    return {
        "name": name,
        "status": "converted",
        "srcSha256": src_sha,
        "outBytes": dst.stat().st_size,
        "glyphs": len(font.getBestCmap() or {}),
    }


_manifest_cache: dict | None = None


def _manifest_sha(name: str) -> str | None:
    global _manifest_cache
    if _manifest_cache is None:
        _manifest_cache = json.loads(MANIFEST.read_text("utf-8")) if MANIFEST.is_file() else {}
    entry = _manifest_cache.get("fonts", {}).get(name)
    return entry.get("srcSha256") if entry else None


def main() -> int:
    ap = argparse.ArgumentParser(description="助手页字体转换（woff2 → ttf）")
    ap.add_argument("--all", action="store_true", help="追加 PuHuiTi 三个字重（约 +21MB）")
    ap.add_argument("--check", action="store_true", help="只校验，不写文件")
    args = ap.parse_args()

    names = DEFAULT_FONTS + (HEAVY_FONTS if args.all else [])
    results = [convert(n, args.check) for n in names]

    total = sum(r.get("outBytes", 0) for r in results)
    for r in results:
        if r["status"] == "converted":
            print(f"  [ OK ] {r['name']:<34} {r['outBytes'] / 1024 / 1024:6.2f} MB  glyphs={r['glyphs']}")
        else:
            print(f"  [{r['status'].upper():>10}] {r['name']}")

    if not args.check:
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(
            json.dumps(
                {
                    "generatedBy": "tools/assistant-fonts.py",
                    "note": "woff2 → ttf（Compose 只接受 sfnt）；源文件为上游同步排除项 assets/rphub/assets/fonts/",
                    "totalBytes": total,
                    "fonts": {r["name"]: r for r in results},
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            "utf-8",
        )
        print(f"[assistant-fonts] 共 {len(results)} 枚，合计 {total / 1024 / 1024:.2f} MB → {DST_DIR.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
