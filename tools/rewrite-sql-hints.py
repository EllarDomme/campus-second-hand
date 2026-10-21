# -*- coding: utf-8 -*-
"""
把 ReportMapper.xml 里两段占位 SQL 的注释，改写成"手把手"版本：
  · 给出可以直接复制出来填洞的骨架
  · 每一步都解释为什么
  · 指向同一个文件里已经写好的同类写法（productSummary 的 sum(case when ...)）
"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\mapper\ReportMapper.xml"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()
before = len(src)

# ============================================================
# A-1 分类聚合
# ============================================================
old_a1_start = src.find("    <!--\n        =====================================================================\n        【你的任务 A-1】")
old_a1_end = src.find("</select>", old_a1_start)
if old_a1_start < 0 or old_a1_end < 0:
    raise SystemExit("!! 找不到 A-1 的位置")

new_a1 = '''    <!--
        =====================================================================
        【你的任务 A-1】分类聚合：每个分类卖了几件、还剩几件在卖
        =====================================================================

        ⭐ 先看同一个文件里已经写好的 productSummary（在它上面），
           它用的就是 sum(case when 条件 then 1 else 0 end) ——
           这个写法你要照抄，只是这次的"分组维度"从"整体"变成"每个分类"。

        ---------------------------------------------------------------------
        目标结果长这样（8 行）：
            categoryId | categoryName | soldCount | onSaleCount
                1      | 教材书籍      |     2     |     8
                2      | 数码产品      |     1     |     6
                ...
        ---------------------------------------------------------------------

        ⭐ 骨架（复制出去，把 3 个 ？？？ 换成真的）：

        select c.id            as categoryId,
               c.category_name as categoryName,
               ① ？？？          as soldCount,
               ② ？？？          as onSaleCount
        from category c
        left join product p
               on ③ ？？？                        -- 关联条件 + 过滤条件
        where ④ ？？？                             -- 商品的有效性过滤
        group by c.id, c.category_name
        order by soldCount desc, onSaleCount desc, c.id asc
        limit #{topCategories}


        ---------------------------------------------------------------------
        逐句怎么填
        ---------------------------------------------------------------------

        ① soldCount = "该分类下卖掉了多少件"
           卖掉 = status = 2（已售罄）
           计数写法：sum(case when p.status = 2 then 1 else 0 end)
           ⚠️ 用 sum(...) 不是 count(...)：count 会把"不满足的行"也算进去

        ② onSaleCount = "该分类下还有多少件在卖"
           在卖 = status = 0
           写法同上，只是条件换成 p.status = 0
           ⚠️ 这里不能写 count(*)，因为一行商品只会属于一种状态

        ③ 关联条件 = "product 的哪个字段指向 category 的 id" + "哪些商品算数"
           两个条件用 and 连起来：
               p.category_id = c.id
               and p.audit_status = 1            -- 审核通过（未被驳回）
               and p.status in (0, 1, 2)         -- 在售 / 交易中 / 已售罄（排除 3 已下架）
           ⚠️ 为什么把过滤写进 on 而不是 where：
              用 left join 时，写在 where 里会把"没有商品的分类"过滤掉（变成 inner join）；
              写在 on 里才能保留空分类（0 件）。本项目 8 个分类都有商品，两种写法结果一样，
              但"on 里过滤"是更稳的习惯。

        ④ 如果你把过滤写在 on 里，这里就不需要 where 了；
           也可以写成 where p.id is not null（但没必要）。

        ---------------------------------------------------------------------
        ⭐ 自测小技巧：先跑一个"最小版本"，再加复杂度
        ---------------------------------------------------------------------

        第 1 步：先确认能按分类分组
            select category_id, count(*) from product
            where audit_status = 1 and status in (0,1,2)
            group by category_id;
            → 应该看到 8 行，每行有数量

        第 2 步：确认 case when 计数对不对
            select category_id,
                   sum(case when status = 2 then 1 else 0 end) as sold,
                   sum(case when status = 0 then 1 else 0 end) as on_sale
            from product where audit_status = 1 and status in (0,1,2)
            group by category_id;
            → 分类 1 应该是 sold=2, on_sale=8

        第 3 步：把 category 表 left join 进来，补上 category_name

        第 4 步：最后加 order by / limit，替换掉占位 SQL
        =====================================================================
    -->
'''

# ============================================================
# A-2 商品明细
# ============================================================
old_a2_start = src.find("    <!--\n        =====================================================================\n        【你的任务 A-2】")
old_a2_end = src.find("</select>", old_a2_start)
if old_a2_start < 0 or old_a2_end < 0:
    raise SystemExit("!! 找不到 A-2 的位置")

new_a2 = '''    <!--
        =====================================================================
        【你的任务 A-2】商品明细：把 A-1 选中的分类下的商品全查出来
        =====================================================================

        这段是这次练习的【核心难点】，难在 elapsedDays（挂了多久 / 多快卖掉）。
        别一次写完，按下面 4 步拆开做。

        ---------------------------------------------------------------------
        目标结果长这样（每个商品一行）：
          productId|categoryId|title|price|status|viewCount|favoriteCount|sold|elapsedDays
             1000  |    1     |考研..| 45  |  2   |   53    |      0      | 1  |     7
        ---------------------------------------------------------------------

        ⭐ 骨架（复制出去，把 5 个 ？？？ 换成真的）：

        select p.id               as productId,
               p.category_id      as categoryId,
               p.title            as title,
               p.price            as price,
               p.status           as status,
               p.view_count       as viewCount,
               ① ？？？            as favoriteCount,
               ② ？？？            as sold,
               ③ ？？？            as elapsedDays
        from product p
        where p.audit_status = 1
          and p.status in (0, 1, 2)
          and p.category_id in
              <foreach collection="categoryIds" item="cid" open="(" separator="," close=")">
                  #{cid}
              </foreach>


        ---------------------------------------------------------------------
        ①② 比较简单，先做
        ---------------------------------------------------------------------

        ① favoriteCount = 这件商品被多少人收藏
           收藏在 favorite 表里（字段 product_id）
           写法一（子查询，推荐，和 ProductMapper.xml 里 selectList 的写法一样）：
               (select count(*) from favorite f where f.product_id = p.id)
           ⚠️ 别用 join + count，会因为有收藏/没收藏而产生行数差异，容易数错

        ② sold = 是否已成交，1 或 0
               case when p.status = 2 then 1 else 0 end
           （status=2 表示已售罄 = 卖出去了）

        ---------------------------------------------------------------------
        ③ elapsedDays —— 分 4 小步
        ---------------------------------------------------------------------

        【第 1 步】写"结束时刻"
            已成交 → 该商品那笔【已完成】订单的 finish_time
            未成交 → 现在（NOW()）
          ⚠️ 一个商品可能有多笔订单（先取消后成交），所以要过滤 status = 2 并取 MAX：
             (select max(o.finish_time) from trade_order o
               where o.product_id = p.id and o.status = 2)
          写成 CASE：
             case when p.status = 2
                  then (select max(o.finish_time) from trade_order o
                         where o.product_id = p.id and o.status = 2)
                  else now()
             end

        【第 2 步】写"起算时刻"
            捡漏商品（is_flash_sale = 1 且 flash_start_time 不为空）→ flash_start_time
            其他商品 → online_time
          写成 CASE：
             case when p.is_flash_sale = 1 and p.flash_start_time is not null
                  then p.flash_start_time
                  else p.online_time
             end
          ⚠️ 两个条件都要判：is_flash_sale = 1 但 flash_start_time 为空的老数据，
             不能拿 NULL 去算（DATEDIFF(x, NULL) 结果还是 NULL）

        【第 3 步】套 DATEDIFF
             datediff(结束时刻, 起算时刻)
          ⚠️ 顺序是"结束 - 起算"，反了会得到负数
          ⚠️ DATEDIFF 只算日期差（忽略时分秒），这正是我们要的"挂了多少天"

        【第 4 步】给起算时刻兜底
            online_time 在表结构里是【可空】的（YES），
            万一为空，整个 DATEDIFF 就是 NULL，打分时会被当成 0 分。
            稳妥做法：把【第 2 步】整体包一层 COALESCE，兜底用 create_time：
               coalesce(第2步的case, p.create_time)
          然后：
               datediff(第1步的case, coalesce(第2步的case, p.create_time))

        ---------------------------------------------------------------------
        ⭐ 自测：先单独验证 elapsedDays，别混在整段 SQL 里查
        ---------------------------------------------------------------------

            select p.id, p.title, p.is_flash_sale, p.online_time, p.flash_start_time,
                   datediff(
                       case when p.status = 2
                            then (select max(o.finish_time) from trade_order o
                                   where o.product_id = p.id and o.status = 2)
                            else now() end,
                       coalesce(
                           case when p.is_flash_sale = 1 and p.flash_start_time is not null
                                then p.flash_start_time else p.online_time end,
                           p.create_time)
                   ) as elapsedDays
            from product p
            where p.id in (1000, 3, 1024, 1027);

            期望：1000 → 7   3 → 12   1024 → 1   1027 → 1
            ⚠️ 1024 和 1027 最关键：
               如果错误地用 online_time 算，会得到 3 和 2（而不是 1 和 1）。

        ---------------------------------------------------------------------
        最后：把 ① ② ③ 填进骨架，替换掉占位 SQL
        =====================================================================
    -->

'''

# 用新注释替换旧注释（保留 <select> 标签和它们的占位 SQL）
src = src[:old_a1_start] + new_a1 + "    " + src[old_a1_end:]
# 重新定位 A-2（因为上面长度变了）
old_a2_start = src.find("    <!--\n        =====================================================================\n        【你的任务 A-2】")
old_a2_end = src.find("</select>", old_a2_start)
src = src[:old_a2_start] + new_a2 + "    " + src[old_a2_end:]

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("注释已重写：%d -> %d 字符" % (before, len(src)))

# 结构体检
import re
print("<select 数量:", src.count("<select "), " </select> 数量:", src.count("</select>"))
print("</mapper> 数量:", src.count("</mapper>"))
