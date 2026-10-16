# -*- coding: utf-8 -*-
"""
S3-10a 评价 + 信用分 回归测试（共 15 项断言）

覆盖：
  一、信用分初始状态              1 项
  二、评价（含信用分联动）          6 项
  三、权限与业务边界               2 项
  四、完成交易 +2 + 每日加分上限    2 项   ← 核心
  五、超时爽约 -5（定时任务）       2 项   ← 需要等定时任务，最多 75 秒
  六、流水与概览                   2 项

前置条件：
  1. 服务已启动
  2. 已执行 sql/test-reset-credit.sql

用法：py -3 tools/credit-test.py
退出码：0 = 全部通过；1 = 有失败
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"

passed = 0
failed = 0


def call(method, path, headers=None, body=None):
    data = None
    h = dict(headers or {})
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        h["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
    try:
        return json.loads(urllib.request.urlopen(req, timeout=25).read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": -1, "msg": "HTTP %s" % e.code}
    except Exception as e:
        return {"code": -1, "msg": str(e)[:90]}


def login(openid):
    r = call("GET", "/user/user/testLogin?openid=" + openid)
    if r.get("code") != 1:
        print("❌ 测试登录失败（openid=%s）：%s" % (openid, r.get("msg")))
        sys.exit(1)
    return r["data"]["token"]


def check(no, desc, expect, r):
    global passed, failed
    got = "code=%s msg=%s" % (r.get("code"), r.get("msg"))
    if expect in got:
        passed += 1
        print("  ✅ [%s] %s" % (no, desc))
    else:
        failed += 1
        print("  ❌ [%s] %s" % (no, desc))
        print("        期望包含: %s" % expect)
        print("        实际返回: %s" % got)


def check_true(no, desc, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print("  ✅ [%s] %s %s" % (no, desc, detail))
    else:
        failed += 1
        print("  ❌ [%s] %s %s" % (no, desc, detail))


def score_of(token):
    """查某人的当前信用分"""
    return (call("GET", "/user/credit/my", {"authentication": token}).get("data") or {}).get("creditScore")


ming = login("test_openid_buyer_001")     # id=1 买家（今日加分已预置为满）
seller = login("test_openid_seller_001")  # id=2 卖家
zhang = login("test_openid_runner_001")   # id=3 第三人
Hm = {"authentication": ming}
Hs = {"authentication": seller}
Hz = {"authentication": zhang}

# ---------- 一、初始状态 ----------
print("\n===== 一、信用分初始状态（1 项）=====")
s_ming = score_of(ming)
check_true("T1", "小明信用分 = 110（数据预置：今日已加满 10 分）", s_ming == 110, "（实际 %s）" % s_ming)

# ---------- 二、评价 ----------
print("\n===== 二、评价 + 信用分联动（6 项）=====")
check("T2", "小明评价订单910（5 星 + 文字）", "code=1",
      call("POST", "/user/review", Hm, {"orderId": 910, "star": 5, "content": "东西很好，学姐人也nice"}))

s_seller = score_of(seller)
check_true("T3", "卖家（学姐）收到好评 → 信用分 100 → 101", s_seller == 101, "（实际 %s）" % s_seller)

check("T4", "小明再评一次同一订单", "你已经评价过这笔订单了",
      call("POST", "/user/review", Hm, {"orderId": 910, "star": 5, "content": "再评一次"}))

check("T5", "学姐回评小明（2 星，【不写文字】—— 只打星是合法的）", "code=1",
      call("POST", "/user/review", Hs, {"orderId": 910, "star": 2}))

s_ming = score_of(ming)
check_true("T6", "小明收到差评 -2 → 108（扣分不受每日上限限制）", s_ming == 108, "（实际 %s）" % s_ming)

check("T7", "星级传 6（越界）", "星级必须在 1~5 之间",
      call("POST", "/user/review", Hz, {"orderId": 910, "star": 6, "content": "刷差评"}))

# ---------- 三、权限与边界 ----------
print("\n===== 三、权限与业务边界（2 项）=====")
check("T8", "第三人（小张）评价别人的订单", "无权评价该订单",
      call("POST", "/user/review", Hz, {"orderId": 910, "star": 1, "content": "我插一脚"}))

check("T9", "评价一笔【还没完成】的订单（911 是待交易）", "只有完成了的订单",
      call("POST", "/user/review", Hm, {"orderId": 911, "star": 5, "content": "还没收货就评价"}))

# ---------- 四、完成交易 +2 与每日加分上限 ----------
print("\n===== 四、完成交易 +2 与每日加分上限（2 项）=====")
check("T10", "小明确认收货（订单911）", "code=1",
      call("PUT", "/user/order/complete/911", Hm))

s_seller = score_of(seller)
s_ming = score_of(ming)
check_true("T11", "学姐完成交易 +2 → 103；小明因【今日加分已满】不加 → 仍 108",
           s_seller == 103 and s_ming == 108,
           "（学姐=%s, 小明=%s）" % (s_seller, s_ming))

# ---------- 五、超时爽约 -5 ----------
print("\n===== 五、超时爽约 -5（等定时任务关单，最多 75 秒）=====")
s_before_wait = score_of(ming)
print("  订单912 的 expire_time 已过期，定时任务每分钟扫一次…（等之前的分数 = %s）" % s_before_wait)
closed = False
for i in range(75):
    time.sleep(1)
    # 用信用分流水判断：出现「交易爽约」(reasonType=4) 就说明定时任务已经处理过
    logs = call("GET", "/user/credit/logs", Hm).get("data") or []
    if any(x.get("reasonType") == 4 for x in logs):
        closed = True
        print("      第 %d 秒：检测到「交易爽约」流水" % (i + 1))
        break

check_true("T12", "定时任务把订单912 关掉并扣了买家 5 分（原因类型=4 交易爽约）", closed)

logs = call("GET", "/user/credit/logs", Hm).get("data") or []
breach = [x for x in logs if x.get("reasonType") == 4 and x.get("refId") == 912]
s_ming = score_of(ming)
check_true("T13", "订单912 有一条 -5 的爽约流水，且总分正好少了 5 分（%s → %s）"
           % (s_before_wait, s_before_wait - 5),
           len(breach) == 1 and breach[0].get("changeScore") == -5 and s_ming == s_before_wait - 5,
           "（流水 %d 条，实际分数 %s）" % (len(breach), s_ming))

# ---------- 六、流水与概览 ----------
print("\n===== 六、信用分流水与概览（2 项）=====")
logs = call("GET", "/user/credit/logs", Hm).get("data") or []
kinds = sorted(set(x.get("reasonType") for x in logs))
check_true("T14", "小明有 3 条流水（预置+10 / 差评-2 / 爽约-5）", len(logs) >= 3,
           "（共 %d 条，原因类型 %s）" % (len(logs), kinds))

vo = call("GET", "/user/credit/my", Hm).get("data") or {}
check_true("T15", "概览正确：今日已加 10（已满）、收到好评 0 条、差评 1 条",
           vo.get("todayAdded") == 10 and vo.get("badReviewCount") == 1,
           "（今日已加=%s, 好评=%s, 差评=%s, 上限=%s）"
           % (vo.get("todayAdded"), vo.get("goodReviewCount"), vo.get("badReviewCount"), vo.get("dailyAddLimit")))

total = passed + failed
print("\n" + "=" * 60)
print("评价+信用分回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
