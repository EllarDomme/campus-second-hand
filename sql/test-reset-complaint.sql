-- ============================================================
-- S3-10b 投诉工单：回归测试数据基线
-- 用法：跑 tools/complaint-test.py 之前先执行
-- ============================================================
USE campus_second_hand;

DELETE FROM complaint;
DELETE FROM credit_log;
UPDATE user SET credit_score = 100 WHERE id IN (1, 2, 3);

-- 防污染：把别的模块遗留的"待确认+已过期"订单推到未来，
-- 否则定时任务会在本模块测试期间把它们关掉、顺带扣信用分，干扰断言
UPDATE trade_order SET expire_time = DATE_ADD(NOW(), INTERVAL 1 DAY)
WHERE status = 0 AND expire_time IS NOT NULL AND expire_time < NOW();

-- 清掉本次专用数据
DELETE FROM trade_order WHERE id = 920;
DELETE FROM product WHERE id = 107;

-- 被举报的商品（卖家=学姐2）→ 用于"商品举报"
INSERT INTO product (id, seller_id, category_id, title, price, images, description, condition_level,
                     is_flash_sale, audit_status, status, create_time, update_time)
VALUES (107, 2, 1, '回归测试商品-投诉', 30.00, '["https://example.com/p107.jpg"]',
        '专供投诉回归', 1, 0, 1, 0, NOW(), NOW());

-- 用于"交易纠纷"的订单（买家=小明1，卖家=学姐2）
INSERT INTO trade_order (id, order_no, seller_id, buyer_id, product_id, real_price, deal_type,
                         order_source, status, pay_status, finish_time, expire_time, create_time, update_time)
VALUES (920, 'TESTCMP920', 2, 1, 107, 30.00, 0, 1, 2, 1,
        DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW());

-- 核对
SELECT COUNT(*) AS 工单数 FROM complaint;
SELECT id, nickname, credit_score FROM user WHERE id IN (1, 2, 3) ORDER BY id;
SELECT id, title, seller_id FROM product WHERE id = 107;
SELECT id, order_no, buyer_id, seller_id, status FROM trade_order WHERE id = 920;
