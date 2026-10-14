-- ============================================================
-- 限时捡漏抢购：回归测试数据基线
-- 用法：跑 tools/flashsale-test.py 之前先执行
-- 注意：Redis 里的库存 key（flash_sale_stock:*）由运行脚本用 redis-cli 清理，
--       因为 SQL 碰不到 Redis —— 这也是"Redis 和数据库是两套系统"的一个直观体现
-- ============================================================
USE campus_second_hand;

-- 清掉历次抢购测试产生的记录与订单
DELETE FROM flash_sale_record;
DELETE FROM payment_record WHERE order_id IN (SELECT id FROM trade_order WHERE order_source = 3);
DELETE FROM trade_order WHERE order_source = 3;

-- 商品3：捡漏 + 在售 + 抢购时间【已过】→ 状态"抢购中"（用于单线程抢购）
UPDATE product
SET is_flash_sale = 1, audit_status = 1, status = 0,
    flash_start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE id = 3;

-- 商品1：捡漏 + 在售 + 抢购时间【在未来】→ 状态"未开始"
UPDATE product
SET is_flash_sale = 1, audit_status = 1, status = 0,
    flash_start_time = DATE_ADD(NOW(), INTERVAL 1 HOUR)
WHERE id = 1;

-- 商品2：捡漏 + 在售 + 已开始 → 专门用于【并发压测】
UPDATE product
SET is_flash_sale = 1, audit_status = 1, status = 0,
    flash_start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE id = 2;

-- 商品6：普通商品（非捡漏）→ 用于验证"不是捡漏商品不能抢"
UPDATE product SET is_flash_sale = 0, audit_status = 1, status = 0 WHERE id = 6;

-- 核对
SELECT id, title, is_flash_sale AS 捡漏, audit_status AS 审核, status AS 状态, flash_start_time AS 抢购开始
FROM product WHERE id IN (1, 2, 3, 6) ORDER BY id;
SELECT COUNT(*) AS 抢购记录数 FROM flash_sale_record;
