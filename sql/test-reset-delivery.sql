-- ============================================================
-- 校园跑腿：回归测试数据基线
-- 用法：跑 tools/delivery-test.py 之前先执行
-- ============================================================
USE campus_second_hand;

-- 清空跑腿单 + 本次专用的三个交易订单
DELETE FROM delivery_order;
DELETE FROM payment_record WHERE order_id IN (901, 902, 903);
DELETE FROM trade_order WHERE id IN (901, 902, 903);

-- 跑腿员账号：把 8 个测试账号设成"正常状态的跑腿员"（用于并发抢单压测）
-- 小张(id=3) 本来就是跑腿员；小明/学姐 保持普通用户（用于测"非跑腿员不能接单"）
UPDATE user SET role = 1, status = 0 WHERE openid LIKE 'test_openid_race%';

-- 用于"真实下单"的商品：在售 + 已过审 + 非捡漏
DELETE FROM product WHERE id = 102;
INSERT INTO product (id, seller_id, category_id, title, price, images, description, condition_level,
                     is_flash_sale, audit_status, status, create_time, update_time)
VALUES (102, 2, 1, '回归测试商品-跑腿', 30.00, '["https://example.com/run102.jpg"]',
        '专供跑腿回归测试', 1, 0, 1, 0, NOW(), NOW());

-- 三个预置交易订单：商品6 设为"交易中"，这样"取消订单"时商品能正常回退（否则会报状态异常）
UPDATE product SET status = 1, audit_status = 1, is_flash_sale = 0 WHERE id = 6;

-- 901：买家=小张(3) —— 测"跑腿员不能给自己的订单跑腿"
-- 902：买家=小明(1) —— 测取消流程
-- 903：买家=小明(1) —— 测"主订单取消 → 跑腿单联动取消"
INSERT INTO trade_order (id, order_no, seller_id, buyer_id, product_id, real_price, deal_type,
                         order_source, status, pay_status, expire_time, create_time, update_time)
VALUES (901, 'TESTDELIV901', 2, 3, 6, 88.00, 1, 1, 0, 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW()),
       (902, 'TESTDELIV902', 2, 1, 6, 88.00, 1, 1, 0, 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW()),
       (903, 'TESTDELIV903', 2, 1, 6, 88.00, 1, 1, 0, 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW());

INSERT INTO delivery_order (trade_order_id, runner_user_id, pick_address, receive_address,
                            delivery_fee, status, create_time, update_time)
VALUES (901, NULL, '3号宿舍楼下', '教学楼B座', 5.00, 0, NOW(), NOW()),
       (902, NULL, '3号宿舍楼下', '教学楼B座', 5.00, 0, NOW(), NOW()),
       (903, NULL, '3号宿舍楼下', '教学楼B座', 5.00, 0, NOW(), NOW());

-- 核对
SELECT COUNT(*) AS 跑腿单数 FROM delivery_order;
SELECT id, trade_order_id, runner_user_id, status FROM delivery_order ORDER BY id;
SELECT COUNT(*) AS 跑腿员数 FROM user WHERE role = 1;
SELECT id, title, price, status FROM product WHERE id IN (6, 102) ORDER BY id;
