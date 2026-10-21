# -*- coding: utf-8 -*-
"""
修复 ReportMapper.xml：
  1. 把我新写的注释里的 `--` 全部换成全角 `＃`（XML 注释里不允许出现 `--`，会直接解析失败）
  2. 把被吃掉的 <select> 开标签 + 占位 SQL 补回来
"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\mapper\ReportMapper.xml"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

# ---------- 1. 只在 <!-- --> 里面把 -- 换成 ＃ ----------
import re

def fix_comment(m):
    inner = m.group(1)
    inner = inner.replace("--", "＃")
    return "<!--" + inner + "-->"

src = re.sub(r"<!--(.*?)-->", fix_comment, src, flags=re.S)
print("[1] 注释里的 -- 已全部替换成 ＃")

# ---------- 2. 补回被吃掉的 <select> ----------
A1_SELECT = '''    <select id="listCategoryStat" resultType="com.campus.vo.HotCategoryVO">
        select c.id            as categoryId,
               c.category_name as categoryName,
               0               as soldCount,
               0               as onSaleCount
        from category c
        where 1 = 0
    </select>
'''

A2_SELECT = '''    <select id="listProductStatByCategories" resultType="com.campus.vo.HotProductVO">
        select p.id           as productId,
               p.category_id  as categoryId,
               p.title,
               p.price,
               p.status,
               p.view_count   as viewCount,
               0              as sold,
               0              as favoriteCount,
               null           as elapsedDays
        from product p
        where 1 = 0
    </select>
'''

# 找到 A-1 注释结束后紧跟的那个孤儿 </select>
a1_mark = src.find("【你的任务 A-1】")
a1_orphan = src.find("</select>", a1_mark)
if a1_orphan > 0 and "<select " not in src[a1_mark:a1_orphan]:
    src = src[:a1_orphan] + A1_SELECT + src[a1_orphan + len("</select>"):]
    print("[2] A-1 的 <select> 已补回")
else:
    print("  (A-1 无需补)")

a2_mark = src.find("【你的任务 A-2】")
a2_orphan = src.find("</select>", a2_mark)
if a2_orphan > 0 and "<select " not in src[a2_mark:a2_orphan]:
    src = src[:a2_orphan] + A2_SELECT + src[a2_orphan + len("</select>"):]
    print("[3] A-2 的 <select> 已补回")
else:
    print("  (A-2 无需补)")

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print()
print("体检：")
print("  <select : %d   </select>: %d" % (src.count("<select "), src.count("</select>")))
print("  <mapper : %d   </mapper>: %d" % (src.count("<mapper "), src.count("</mapper>")))
print("  注释里还有 -- 吗:", "有 ⚠️" if re.search(r"<!--(?:(?!-->)[\s\S])*?--(?:(?!-->)[\s\S])*?-->", src) else "没有 ✅")
print("  文件行数:", src.count("\n") + 1)
