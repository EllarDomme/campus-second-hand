-- ============================================================
-- S4-1 用户管理：回归测试数据基线
-- 用法：跑 tools/admin-user-test.py 之前先执行
-- ============================================================
USE campus_second_hand;

-- 把所有测试账号恢复成"正常状态"（防止上一轮测试留下封禁状态，
-- 那会让后面所有模块的接口全部 401）
UPDATE user SET status = 0, credit_score = 100
WHERE openid LIKE 'test_openid%';

-- 核对
SELECT id, openid, nickname, role, status, credit_score FROM user
WHERE openid LIKE 'test_openid%' ORDER BY id LIMIT 12;
SELECT CONCAT('测试账号数 = ', COUNT(*)) FROM user WHERE openid LIKE 'test_openid%';
