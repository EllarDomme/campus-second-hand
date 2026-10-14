-- ============================================================
-- 订单模块回归测试：把数据重置成"基线状态"
-- 用途：跑 tools/order-* 回归脚本之前先执行这个
-- 说明：只动测试用的 5 条订单和 5 件商品，不碰 user/category 等其它表
-- ============================================================
USE campus_second_hand;

-- 订单1：待确认 + 未支付，商品2（小米移动电源）
UPDATE trade_order SET status=0, pay_status=0, cancel_reason=NULL, finish_time=NULL, pay_no=NULL, update_time=NOW() WHERE id=1;
UPDATE product SET status=1 WHERE id=2;

-- 订单2：待确认 + 未支付，商品1（考研数学）—— 买家是小张(3)
UPDATE trade_order SET status=0, pay_status=0, cancel_reason=NULL, finish_time=NULL, pay_no=NULL, update_time=NOW() WHERE id=2;
UPDATE product SET status=1 WHERE id=1;

-- 订单3：待确认 + 未支付，商品3（英语真题）—— 买家是抢购用户(10)
UPDATE trade_order SET status=0, pay_status=0, cancel_reason=NULL, finish_time=NULL, pay_no=NULL, update_time=NOW() WHERE id=3;
UPDATE product SET status=1 WHERE id=3;

-- 订单4：待确认 + 已支付，商品5（支付验收商品A）—— 买家是小明(1)
UPDATE trade_order SET status=0, pay_status=1, cancel_reason=NULL, finish_time=NULL, update_time=NOW() WHERE id=4;
UPDATE product SET status=1 WHERE id=5;

-- 订单5：待确认 + 已支付，商品6（支付验收商品B）—— 买家是小张(3)
UPDATE trade_order SET status=0, pay_status=1, cancel_reason=NULL, finish_time=NULL, update_time=NOW() WHERE id=5;
UPDATE product SET status=1 WHERE id=6;

-- ⚠️ 关键：把所有订单的支付截止时间顺延，否则"发起支付"会被超时校验拦住
--    （订单是几天前创建的，expire_time 早就过期了）
UPDATE trade_order SET expire_time = DATE_ADD(NOW(), INTERVAL 30 MINUTE) WHERE id IN (1,2,3,4,5);

-- 清掉测试产生的退款/放款流水，只保留两条真实支付流水
DELETE FROM payment_record WHERE pay_type IN (2, 3);

-- 核对
SELECT id, product_id, status AS 订单状态, pay_status, expire_time FROM trade_order ORDER BY id;
SELECT id, status AS 商品状态 FROM product ORDER BY id;
SELECT id, order_no, LEFT(pay_no,3) AS 前缀, pay_type FROM payment_record ORDER BY id;
