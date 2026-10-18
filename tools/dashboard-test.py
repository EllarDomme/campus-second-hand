# -*- coding: utf-8 -*-
"""
S4-2 管理端-数据看板 回归测试（共 6 项断言）

覆盖：
  一、接口可用性：13 个指标都能返回
  二、数据自洽性：几个"不可能反过来的大小关系"

前置条件：服务已启动（数据用当前库里的真实数据，不需要专门造）

说明：这里【不硬编码具体数字】—— 具体数字会随测试数据变化。
      真正能长期守住的是两类断言：
        ① 结构：字段都返回了、类型正常
        ② 自洽：今日 ≤ 本月、跑腿员 ≤ 总用户、累计额 ≥ 本月额 等
      这类"不变量"断言比"等于 4"更耐用。

用法：py -3 tools/dashboard-test.py
退出码：0 = 全部通过；1 = 有失败
"""
import json
import sys
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
        return json.loads(urllib.request.urlopen(req, timeout=20).read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": -1, "msg": "HTTP %s" % e.code}
    except Exception as e:
        return {"code": -1, "msg": str(e)[:90]}


def check_true(no, desc, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print("  ✅ [%s] %s %s" % (no, desc, detail))
    else:
        failed += 1
        print("  ❌ [%s] %s %s" % (no, desc, detail))


admin = call("POST", "/admin/employee/login", body={"username": "zhangsan", "password": "123456"})
if admin.get("code") != 1:
    print("❌ 管理端登录失败：%s" % admin.get("msg"))
    sys.exit(1)
Ha = {"token": admin["data"]["token"]}

r = call("GET", "/admin/dashboard?lowCredit=80", Ha)
d = r.get("data") or {}
trade = d.get("trade") or {}
user = d.get("user") or {}
product = d.get("product") or {}
print("  看板返回：%s" % json.dumps(d, ensure_ascii=False)[:220])

# ---------- 一、结构 ----------
print("\n===== 一、接口可用性（4 项）=====")
check_true("T1", "看板接口正常返回（code=1）", r.get("code") == 1, "（msg=%s）" % r.get("msg"))

todo_fields = ["pendingAuditProducts", "pendingComplaints", "waitingDeliveries"]
todo_ok = all(isinstance(d.get(k), int) for k in todo_fields)
check_true("T2", "三个待办指标都返回了（整数）", todo_ok,
           "（%s）" % ", ".join("%s=%s" % (k, d.get(k)) for k in todo_fields))

trade_fields = ["todayOrders", "todayAmount", "monthOrders", "monthAmount", "totalAmount"]
check_true("T3", "五个交易指标都返回了", all(trade.get(k) is not None for k in trade_fields),
           "（%s）" % ", ".join("%s=%s" % (k, trade.get(k)) for k in trade_fields))

user_fields = ["totalUsers", "totalRunners", "bannedUsers", "lowCreditUsers"]
product_fields = ["onSaleProducts", "inTradeProducts", "soldOutProducts", "pendingAuditProducts"]
check_true("T4", "用户 4 项 + 商品 4 项都返回了",
           all(user.get(k) is not None for k in user_fields)
           and all(product.get(k) is not None for k in product_fields),
           "（用户 %s / 商品 %s）" % (user, product))

# ---------- 二、自洽性 ----------
print("\n===== 二、数据自洽性（2 项）=====")
tu = user.get("totalUsers") or 0
tr = user.get("totalRunners") or 0
ba = user.get("bannedUsers") or 0
lc = user.get("lowCreditUsers") or 0
check_true("T5", "用户数关系自洽：总数>0、跑腿员≤总数、封禁≤总数、低信用分≤总数",
           tu > 0 and tr <= tu and ba <= tu and lc <= tu,
           "（总数=%s 跑腿员=%s 封禁=%s 低分=%s）" % (tu, tr, ba, lc))

to = trade.get("todayOrders") or 0
mo = trade.get("monthOrders") or 0
ta = float(trade.get("totalAmount") or 0)
ma = float(trade.get("monthAmount") or 0)
check_true("T6", "交易数关系自洽：今日单数≤本月单数、本月成交额≤累计成交额",
           to <= mo and ma <= ta,
           "（今日/本月单数=%s/%s, 本月/累计额=%s/%s）" % (to, mo, ma, ta))

total = passed + failed
print("\n" + "=" * 60)
print("数据看板回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
