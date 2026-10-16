-- ============================================================
-- 私信模块：回归测试数据基线
-- 用法：跑 tools/message-test.py 之前先执行
-- ============================================================
USE campus_second_hand;

-- 清空会话与消息（消息没有外键，但逻辑上属于会话，先删消息再删会话）
DELETE FROM message;
DELETE FROM conversation;

-- 保证商品3 处于"普通商品"状态（主流程：小明 → 学姐 的普通私信）
UPDATE product SET status = 0, audit_status = 1, is_flash_sale = 0 WHERE id = 3;

-- ------------------------------------------------------------
-- S3-8b：捡漏商品的四种私信场景（都是学姐的商品，方便小明去点"联系卖家"）
-- ------------------------------------------------------------

-- ① 捡漏·预热期（抢购还没开始）→ 谁都不能私信
UPDATE product SET is_flash_sale = 1, audit_status = 1, status = 0,
       flash_start_time = DATE_ADD(NOW(), INTERVAL 1 HOUR) WHERE id = 1;

-- ② 捡漏·已开始，但小明【没抢到】→ 只有抢到的人能私信
--    （不给他造抢购记录，他自然就是"没抢到"）
UPDATE product SET is_flash_sale = 1, audit_status = 1, status = 0,
       flash_start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id = 2;
DELETE FROM flash_sale_record WHERE product_id = 2;

-- ③ 捡漏·已开始且小明【抢到了】，但那笔订单已经超时
--    → 会话能建（因为确实抢到了），但一发消息就会被"会话已超过可发送时间"拦住
--    这就是"10 分钟窗口期"的端到端验证
UPDATE product SET is_flash_sale = 1, audit_status = 1, status = 1,
       flash_start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id = 5;

DELETE FROM flash_sale_record WHERE product_id = 5;
DELETE FROM trade_order WHERE id = 900;
INSERT INTO trade_order (id, order_no, seller_id, buyer_id, product_id, real_price, deal_type,
                         order_source, status, pay_status, expire_time, create_time, update_time)
VALUES (900, 'TESTFLASH0001', 2, 1, 5, 66.00, 0, 3, 0, 0,
        DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW(), NOW());
INSERT INTO flash_sale_record (product_id, user_id, is_success, order_id, create_time, update_time)
VALUES (5, 1, 1, 900, NOW(), NOW());

-- 核对
SELECT COUNT(*) AS 会话数 FROM conversation;
SELECT COUNT(*) AS 消息数 FROM message;
SELECT id, title, is_flash_sale AS 捡漏, status AS 状态, flash_start_time AS 抢购开始
FROM product WHERE id IN (1, 2, 3, 5) ORDER BY id;
SELECT id, product_id, user_id, order_id FROM flash_sale_record WHERE product_id = 5;
