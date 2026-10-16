-- ============================================================
-- S3-10 评价 + 信用分：回归测试数据基线
-- 用法：跑 tools/credit-test.py 之前先执行
-- ============================================================
USE campus_second_hand;

DELETE FROM review;
DELETE FROM credit_log;
UPDATE user SET credit_score = 100 WHERE id IN (1, 2, 3);

-- ★ 先清理"别的模块遗留的过期订单"！
--   教训：私信模块的 fixture 造了一笔 expire_time 已过期的订单（买家也是小明），
--   信用分测试跑的时候定时任务把它也关了 → 小明被多扣了 5 分 → 断言失败。
--   系统行为没错，是【测试数据跨模块污染】。所以这里先把所有"待确认+已过期"的单子
--   推到未来，让定时任务只可能处理本次专用的订单 912。
UPDATE trade_order SET expire_time = DATE_ADD(NOW(), INTERVAL 1 DAY)
WHERE status = 0 AND expire_time IS NOT NULL AND expire_time < NOW();

-- 清掉本次专用数据
DELETE FROM payment_record WHERE order_id IN (910, 911, 912);
DELETE FROM trade_order WHERE id IN (910, 911, 912);
DELETE FROM product WHERE id IN (103, 104, 105);

-- 三个专用商品（状态要和下面的订单对得上，否则"确认收货/超时关单"会因为商品状态异常而失败）
INSERT INTO product (id, seller_id, category_id, title, price, images, description, condition_level,
                     is_flash_sale, audit_status, status, create_time, update_time)
VALUES
  -- 103：交易中 → 对应"待交易"的订单 911（用于测确认收货 +2）
  (103, 2, 1, '回归测试商品-评价A', 20.00, '["https://example.com/p103.jpg"]', '专供评价回归', 1, 0, 1, 1, NOW(), NOW()),
  -- 104：交易中 → 对应"超时未支付"的订单 912（用于测超时爽约 -5）
  (104, 2, 1, '回归测试商品-评价B', 20.00, '["https://example.com/p104.jpg"]', '专供评价回归', 1, 0, 1, 1, NOW(), NOW()),
  -- 105：已售罄 → 对应"已完成"的订单 910（用于测评价；已完成的商品就是已售罄状态）
  (105, 2, 1, '回归测试商品-评价C', 20.00, '["https://example.com/p105.jpg"]', '专供评价回归', 1, 0, 1, 2, NOW(), NOW());

INSERT INTO trade_order (id, order_no, seller_id, buyer_id, product_id, real_price, deal_type,
                         order_source, status, pay_status, finish_time, expire_time, create_time, update_time)
VALUES
  -- 910：已完成 + 已支付 → 可以评价
  (910, 'TESTREV910', 2, 1, 105, 20.00, 0, 1, 2, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR),
   DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW(), NOW()),
  -- 911：待交易 + 已支付 → 用于"确认收货"（会触发双方各 +2）
  (911, 'TESTREV911', 2, 1, 103, 20.00, 0, 1, 1, 1, NULL,
   DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW()),
  -- 912：待确认 + 未支付 + 【已过期】→ 定时任务（每分钟一次）会把它关掉，并扣买家 5 分
  (912, 'TESTREV912', 2, 1, 104, 20.00, 0, 1, 0, 0, NULL,
   DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW(), NOW());

-- ★ 预置"小明今天已经加满 10 分"：
--   这样后面所有【加分】对他都失效（每日上限），而【扣分】照常生效。
--   一条数据同时验证两件事：① 每日加分上限 ② 扣分不受上限限制
INSERT INTO credit_log (user_id, change_score, score_after, reason_type, ref_id, remark, create_time, update_time)
VALUES (1, 10, 110, 8, NULL, '测试预置：今日已加满（平台任务）', NOW(), NOW());
UPDATE user SET credit_score = 110 WHERE id = 1;

-- 核对
SELECT id, nickname, credit_score FROM user WHERE id IN (1, 2, 3) ORDER BY id;
SELECT COUNT(*) AS 评价数 FROM review;
SELECT user_id, change_score, score_after, reason_type FROM credit_log ORDER BY id;
SELECT id, order_no, status, pay_status FROM trade_order WHERE id IN (910, 911, 912) ORDER BY id;
