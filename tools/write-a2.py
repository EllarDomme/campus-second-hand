# -*- coding: utf-8 -*-
"""只替换 A-2 的占位 SQL（A-1 保持原样，留给用户写）"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\mapper\ReportMapper.xml"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

OLD = '''    <select id="listProductStatByCategories" resultType="com.campus.vo.HotProductVO">
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

NEW = '''    <select id="listProductStatByCategories" resultType="com.campus.vo.HotProductVO">
        select p.id           as productId,
               p.category_id  as categoryId,
               p.title,
               p.price,
               p.status,
               p.view_count   as viewCount,

               <!-- 收藏数：用子查询，避免 join 造成行数翻倍 -->
               (select count(*) from favorite f where f.product_id = p.id) as favoriteCount,

               <!-- 是否已成交：status = 2 表示已售罄（卖出去了） -->
               case when p.status = 2 then 1 else 0 end as sold,

               <!--
                   挂了多久 / 多快卖掉
                   DATEDIFF(结束时刻, 起算时刻)
                   结束时刻：已成交 → 该商品那笔已完成订单的 finish_time
                            未成交 → NOW()
                   起算时刻：捡漏商品 → flash_start_time（开抢时刻，不是发布时刻）
                            普通商品 → online_time
                   兜底：online_time 可空，空了就用 create_time
               -->
               datediff(
                   case when p.status = 2
                        then (select max(o.finish_time) from trade_order o
                               where o.product_id = p.id and o.status = 2)
                        else now()
                   end,
                   coalesce(
                       case when p.is_flash_sale = 1 and p.flash_start_time is not null
                            then p.flash_start_time
                            else p.online_time
                       end,
                       p.create_time)
               ) as elapsedDays

        from product p
        where p.audit_status = 1
          and p.status in (0, 1, 2)
          and p.category_id in
              <foreach collection="categoryIds" item="cid" open="(" separator="," close=")">
                  #{cid}
              </foreach>
    </select>
'''

if OLD not in src:
    raise SystemExit("!! 没找到 A-2 的占位 SQL")

src = src.replace(OLD, NEW, 1)

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("A-2 已写入")
print("<select :", src.count("<select "), " </select>:", src.count("</select>"))
print("</mapper>:", src.count("</mapper>"))
