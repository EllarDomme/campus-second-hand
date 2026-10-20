# -*- coding: utf-8 -*-
"""
生成本地 SVG 素材：商品品类插画 + 用户头像

为什么是 SVG 而不是照片：
  这台机器不能联网、也没有图库，我无法提供真实照片。
  但"渐变 + 数字"那种占位符太假，所以按品类画了简笔插画：
  书 / 数码 / 杯子 / 球拍 / 自行车 / T恤 / 吉他 / 纸箱。
  纯矢量、离线可用、每个 1KB 左右，缩放不糊。

输出：
  static/ui/img/cat_<key>_<variant>.svg   每个品类 3 个变体（不同底色，模拟"多角度"）
  static/ui/img/avatar_<n>.svg            12 个渐变头像

用法：py -3 tools/gen-assets.py
"""
import os

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "campus-server", "src", "main", "resources", "static", "ui", "img")

# ---------------------------------------------------------------- 品类插画
# 每个品类：3 套底色 + 一个简笔画路径（用白色描边，像图标风格的实物剪影）
CATEGORIES = {
    # key: (中文名, 三套背景渐变色对, 图形内容)
    "book": ("教材书籍", [("#6366f1", "#a855f7"), ("#0ea5e9", "#22d3ee"), ("#f59e0b", "#fbbf24")], """
      <g fill="none" stroke="#ffffff" stroke-width="13" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <path d="M-105 -70 h150 a22 22 0 0 1 22 22 v136 a22 22 0 0 1 -22 22 h-150 z" fill="#ffffff" fill-opacity="0.14"/>
        <path d="M-105 -70 v180" opacity="0.75"/>
        <path d="M-72 -34 h96 M-72 4 h96 M-72 42 h64" opacity="0.55"/>
      </g>"""),

    "digital": ("数码产品", [("#0ea5e9", "#22d3ee"), ("#6366f1", "#7c3aed"), ("#14b8a6", "#5eead4")], """
      <g fill="none" stroke="#ffffff" stroke-width="12" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <rect x="-110" y="-78" width="150" height="196" rx="24" fill="#ffffff" fill-opacity="0.14"/>
        <rect x="-92" y="-58" width="114" height="140" rx="12" opacity="0.6"/>
        <circle cx="-35" cy="104" r="9" fill="#ffffff" stroke="none" opacity="0.75"/>
        <path d="M62 -30 h48 v96 h-48 z" opacity="0.5"/>
      </g>"""),

    "life": ("生活用品", [("#f59e0b", "#fbbf24"), ("#ef4444", "#fb7185"), ("#10b981", "#34d399")], """
      <g fill="none" stroke="#ffffff" stroke-width="13" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <path d="M-92 -62 h140 v150 a30 30 0 0 1 -30 30 h-80 a30 30 0 0 1 -30 -30 z" fill="#ffffff" fill-opacity="0.14"/>
        <path d="M48 -20 h26 a34 34 0 0 1 0 68 h-26"/>
        <path d="M-92 -30 h140" opacity="0.5"/>
        <path d="M-64 132 v14 M-6 132 v14" opacity="0.6"/>
      </g>"""),

    "sport": ("运动器材", [("#10b981", "#34d399"), ("#0ea5e9", "#38bdf8"), ("#f59e0b", "#fbbf24")], """
      <g fill="none" stroke="#ffffff" stroke-width="13" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <circle cx="0" cy="10" r="92" fill="#ffffff" fill-opacity="0.14"/>
        <path d="M-64 -52 a92 92 0 0 1 128 0"/>
        <path d="M-92 10 h184" opacity="0.6"/>
        <path d="M0 -82 v184" opacity="0.35"/>
      </g>"""),

    "vehicle": ("交通工具", [("#14b8a6", "#5eead4"), ("#6366f1", "#818cf8"), ("#ef4444", "#fb7185")], """
      <g fill="none" stroke="#ffffff" stroke-width="12" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <circle cx="-72" cy="62" r="48"/>
        <circle cx="86" cy="62" r="48"/>
        <path d="M-72 62 L-10 -40 h52 M-10 -40 L34 62 M86 62 L34 62 M-52 -40 h44"/>
        <path d="M30 -52 h44" opacity="0.6"/>
      </g>"""),

    "cloth": ("服饰鞋包", [("#8b5cf6", "#c084fc"), ("#f59e0b", "#fcd34d"), ("#0ea5e9", "#38bdf8")], """
      <g fill="none" stroke="#ffffff" stroke-width="13" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <path d="M-40 -78 l-64 30 24 54 34 -14 v130 h92 v-130 l34 14 24 -54 -64 -30 a34 34 0 0 1 -34 22 a34 34 0 0 1 -34 -22 z"
              fill="#ffffff" fill-opacity="0.14"/>
      </g>"""),

    "music": ("乐器", [("#a855f7", "#c084fc"), ("#ef4444", "#fb7185"), ("#14b8a6", "#5eead4")], """
      <g fill="none" stroke="#ffffff" stroke-width="12" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <circle cx="-18" cy="52" r="66" fill="#ffffff" fill-opacity="0.14"/>
        <circle cx="-18" cy="52" r="24" opacity="0.6"/>
        <path d="M26 12 L96 -96 l22 12 l-70 110"/>
        <path d="M88 -86 l20 12" opacity="0.7"/>
      </g>"""),

    "other": ("其他", [("#64748b", "#94a3b8"), ("#0ea5e9", "#22d3ee"), ("#f59e0b", "#fbbf24")], """
      <g fill="none" stroke="#ffffff" stroke-width="13" stroke-linejoin="round" stroke-linecap="round" opacity="0.95">
        <path d="M-96 -34 l96 -54 96 54 -96 54 z" fill="#ffffff" fill-opacity="0.16"/>
        <path d="M-96 -34 v92 l96 54 96 -54 v-92"/>
        <path d="M0 20 v92" opacity="0.55"/>
      </g>"""),
}

VARIANTS = [
    # (后缀, 缩放, 旋转, 背景装饰圆位置)
    ("a", 1.00, 0, (560, 120)),
    ("b", 0.92, -6, (140, 130)),
    ("c", 0.86, 5, (350, 560)),
]

AVATAR_PALETTE = [
    ("#6366f1", "#a855f7"), ("#0ea5e9", "#22d3ee"), ("#10b981", "#34d399"),
    ("#f59e0b", "#fbbf24"), ("#ef4444", "#fb7185"), ("#8b5cf6", "#c084fc"),
    ("#14b8a6", "#5eead4"), ("#f472b6", "#fb7185"), ("#3b82f6", "#60a5fa"),
    ("#84cc16", "#a3e635"), ("#06b6d4", "#67e8f9"), ("#f97316", "#fb923c"),
]


def svg_product(name, cn, bg1, bg2, art, scale, rotate, deco):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="800" height="800" viewBox="0 0 800 800">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="{bg1}"/>
      <stop offset="100%" stop-color="{bg2}"/>
    </linearGradient>
    <radialGradient id="glow" cx="50%" cy="45%" r="60%">
      <stop offset="0%" stop-color="#ffffff" stop-opacity="0.22"/>
      <stop offset="100%" stop-color="#ffffff" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="800" height="800" fill="url(#bg)"/>
  <circle cx="{deco[0]}" cy="{deco[1]}" r="200" fill="#ffffff" opacity="0.07"/>
  <rect width="800" height="800" fill="url(#glow)"/>
  <ellipse cx="400" cy="660" rx="190" ry="26" fill="#000000" opacity="0.14"/>
  <g transform="translate(400 400) scale({scale}) rotate({rotate})">
    {art}
  </g>
  <text x="400" y="752" text-anchor="middle"
        font-family="-apple-system,Segoe UI,Microsoft YaHei,sans-serif"
        font-size="30" font-weight="600" fill="#ffffff" opacity="0.55">{cn} · 示例图</text>
</svg>
"""


def svg_avatar(idx, c1, c2):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="{c1}"/>
      <stop offset="100%" stop-color="{c2}"/>
    </linearGradient>
  </defs>
  <circle cx="100" cy="100" r="100" fill="url(#g)"/>
  <circle cx="132" cy="66" r="46" fill="#ffffff" opacity="0.13"/>
  <g fill="none" stroke="#ffffff" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" opacity="0.95">
    <circle cx="100" cy="78" r="26"/>
    <path d="M52 158 a48 48 0 0 1 96 0"/>
  </g>
</svg>
"""


def main():
    os.makedirs(BASE, exist_ok=True)
    n = 0
    for key, (cn, colors, art) in CATEGORIES.items():
        for (suffix, scale, rot, deco), (c1, c2) in zip(VARIANTS, colors):
            path = os.path.join(BASE, f"cat_{key}_{suffix}.svg")
            with open(path, "w", encoding="utf-8") as f:
                f.write(svg_product(key, cn, c1, c2, art, scale, rot, deco))
            n += 1

    for i, (c1, c2) in enumerate(AVATAR_PALETTE, start=1):
        path = os.path.join(BASE, f"avatar_{i}.svg")
        with open(path, "w", encoding="utf-8") as f:
            f.write(svg_avatar(i, c1, c2))
        n += 1

    print(f"已生成 {n} 个 SVG → {BASE}")
    print("  商品插画:", len(CATEGORIES), "个品类 ×", len(VARIANTS), "个变体")
    print("  用户头像:", len(AVATAR_PALETTE), "个")


if __name__ == "__main__":
    main()
