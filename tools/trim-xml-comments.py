# -*- coding: utf-8 -*-
"""精简 ReportMapper.xml 的注释：A-1 只留验收数字，A-2 的注释压成短句"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\mapper\ReportMapper.xml"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()
import re

# ---------- 1. A-1：删掉整个教程，只留验收数字 ----------
a1_start = src.find("    <!--\n        ============================================================================\n        【你的任务 A-1】")
if a1_start < 0:
    # 兼容分隔线长度
    a1_start = src.find("【你的任务 A-1】")
    a1_start = src.rfind("<!--", 0, a1_start)
a1_end = src.find("-->", a1_start)
if a1_start < 0 or a1_end < 0:
    raise SystemExit("!! 找不到 A-1 注释块")

NEW_A1 = '''    <!--
        【你的任务 A-1】分类聚合：每个分类卖了几件、还有几件在卖

        验收（必须正好是这 8 行，按此顺序）：
            1 教材书籍  sold=2  onSale=8
            2 数码产品  sold=1  onSale=6
            6 服饰鞋包  sold=1  onSale=2
            3 生活用品  sold=0  onSale=4
            4 运动器材  sold=0  onSale=3
            8 其他      sold=0  onSale=3
            5 交通工具  sold=0  onSale=2
            7 乐器      sold=0  onSale=2

        提示：同文件上面的 productSummary 已经用了 sum(case when ... then 1 else 0 end)，
              照着那个形状写，只是把"整体"换成"按 category 分组"。
        过滤：audit_status = 1 且 status in (0,1,2)
        排序：soldCount desc, onSaleCount desc, c.id asc
        限量：limit #{topCategories}
    -->
'''

src = src[:a1_start] + NEW_A1.rstrip("\n") + src[a1_end + len("-->"):]
print("[1] A-1 教程已删，只留验收数字")

# ---------- 2. A-2：把长注释压成短句 ----------
src = src.replace(
"""               <!-- 收藏数：用子查询，避免 join 造成行数翻倍 -->
               (select count(*) from favorite f where f.product_id = p.id) as favoriteCount,

               <!-- 是否已成交：status = 2 表示已售罄（卖出去了） -->
               case when p.status = 2 then 1 else 0 end as sold,
""",
"""               (select count(*) from favorite f where f.product_id = p.id) as favoriteCount,
               case when p.status = 2 then 1 else 0 end as sold,
""")

# 删掉 elapsedDays 上面那段大注释
start = src.find("               <!--\n                   挂了多久 / 多快卖掉")
if start < 0:
    start = src.find("挂了多久 / 多快卖掉")
    start = src.rfind("<!--", 0, start)
end = src.find("-->", start)
if start > 0 and end > start:
    src = src[:start] + "               <!-- elapsedDays = DATEDIFF(结束时刻, 起算时刻) -->\n" + src[end + len("-->"):]
    print("[2] elapsedDays 上方的大注释已压成一行")

src = src.replace(
"""                   <!-- 结束时刻。多包一层 coalesce：status=2 但没有已完成订单的商品，
                        子查询会返回 NULL，DATEDIFF 结果就会变 NULL（打分变 0），
                        这里退化成 NOW() 更合理 -->
                   coalesce(""",
"""                   coalesce(""")

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print()
print("体检：")
print("  <select :", src.count("<select "), " </select>:", src.count("</select>"))
print("  <mapper :", src.count("<mapper "), " </mapper>:", src.count("</mapper>"))
print("  注释里还有 -- 吗:", "有 ⚠️" if re.search(r"<!--(?:(?!-->)[\s\S])*?--(?:(?!-->)[\s\S])*?-->", src) else "没有 ✅")
print("  文件行数:", src.count("\n") + 1)
