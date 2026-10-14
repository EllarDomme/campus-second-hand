-- ============================================================
-- 议价模块回归测试：数据基线
-- 用法：跑 tools/bargain-test.py 之前先执行本脚本
-- （对应地，订单模块回归用 sql/test-reset.sql）
-- ============================================================
USE campus_second_hand;

-- 清空议价记录：让"不能重复议价""没有被拒绝过"这些前置条件成立
DELETE FROM bargain;

-- 商品1（考研数学 25.00）和商品3（英语真题 50.00）恢复成"在售 + 已过审"
-- 卖家都是学姐(2)，测试里由小明(1)和小张(3)来出价
UPDATE product SET status = 0, audit_status = 1 WHERE id IN (1, 3);

-- 核对
SELECT id, title, price, seller_id, status AS 商品状态, audit_status AS 审核状态
FROM product WHERE id IN (1, 3);
SELECT COUNT(*) AS 议价记录数 FROM bargain;
