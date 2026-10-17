-- ============================================================
-- S4-1 用户管理：回归测试数据基线
-- 用法：跑 tools/admin-user-test.py 之前先执行
-- ============================================================
USE campus_second_hand;

-- 把所有测试账号恢复成"正常状态"（防止上一轮测试留下封禁状态，
-- 那会让后面所有模块的接口全部 401）
UPDATE user SET status = 0, credit_score = 100
WHERE openid LIKE 'test_openid%';

-- ------------------------------------------------------------
-- 造一笔"进行中"的跑腿单，专门验证功能A 的关键守卫：
--   跑腿员手上还有单子时，不能把他降级成普通用户
-- ------------------------------------------------------------
DELETE FROM delivery_order WHERE trade_order_id = 930;
DELETE FROM trade_order WHERE id = 930;

INSERT INTO trade_order (id, order_no, seller_id, buyer_id, product_id, real_price, deal_type,
                         order_source, status, pay_status, expire_time, create_time, update_time)
VALUES (930, 'TESTADM930', 2, 1, 6, 88.00, 1, 1, 0, 0,
        DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW());

INSERT INTO delivery_order (trade_order_id, runner_user_id, pick_address, receive_address,
                            delivery_fee, status, accept_time, create_time, update_time)
VALUES (930, 3, '3号宿舍楼下', '教学楼B座', 5.00, 1, NOW(), NOW(), NOW());
--  ↑ 小张(id=3) 手上有一个"已接单(1)"的跑腿单

-- 核对
SELECT id, openid, nickname, role, status, credit_score FROM user
WHERE openid LIKE 'test_openid%' ORDER BY id LIMIT 12;
SELECT CONCAT('测试账号数 = ', COUNT(*)) FROM user WHERE openid LIKE 'test_openid%';
SELECT CONCAT('小张名下进行中的跑腿单 = ', COUNT(*)) FROM delivery_order
WHERE runner_user_id = 3 AND status IN (1, 2);
