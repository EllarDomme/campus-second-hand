# -*- coding: utf-8 -*-
"""把 A-1 的占位 SQL 换成正确实现（用户燃尽了，直接改）"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\mapper\ReportMapper.xml"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

# 定位 listCategoryStat 的 select 块
start = src.find('<select id="listCategoryStat"')
end = src.find('</select>', start)
if start < 0 or end < 0:
    raise SystemExit("!! 找不到 listCategoryStat")

NEW = '''<select id="listCategoryStat" resultType="com.campus.vo.HotCategoryVO">
        select c.id            as categoryId,
               c.category_name as categoryName,
               ifnull(sum(case when p.status = 2 then 1 else 0 end), 0) as soldCount,
               ifnull(sum(case when p.status = 0 then 1 else 0 end), 0) as onSaleCount
        from category c
        left join product p
               on p.category_id = c.id
              and p.audit_status = 1
              and p.status in (0, 1, 2)
        group by c.id, c.category_name
        order by (soldCount * 10 + onSaleCount) desc, c.id asc
        limit #{topCategories}
    '''

src = src[:start] + NEW + src[end:]

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("A-1 已写入（长度 %d → %d）" % (len(src) - len(NEW) + len(src[start:end]), len(src)) if False else "A-1 已写入")
print("<select :", src.count("<select "), " </select>:", src.count("</select>"))
print("</mapper>:", src.count("</mapper>"))
