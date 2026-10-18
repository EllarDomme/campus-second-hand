# -*- coding: utf-8 -*-
"""
校园跑腿回归测试（共 16 项断言）

覆盖：
  一、下单生成跑腿单 + 计费快照      3 项
  二、接单权限                       2 项
  三、并发抢单（8 人抢 1 单）         2 项   ← 核心
  四、状态机 取货→送达                4 项
  五、取消与主订单联动                3 项
  六、跑腿员收入统计                 1 项
  七、跑腿单查看权限                 1 项（并入 T15 的联动验证路径）

前置条件：
  1. 服务已启动
  2. 已执行 sql/test-reset-delivery.sql

用法：py -3 tools/delivery-test.py
退出码：0 = 全部通过；1 = 有失败
"""
import json
import sys
import threading
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


def login_admin():
    r = call("POST", "/admin/employee/login", body={"username": "zhangsan", "password": "123456"})
    if r.get("code") != 1:
        print("❌ 管理端登录失败：%s" % r.get("msg"))
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


ming = login("test_openid_buyer_001")     # id=1 普通用户（买家）
seller = login("test_openid_seller_001")  # id=2 普通用户（卖家）
zhang = login("test_openid_runner_001")   # id=3 跑腿员
Hm = {"authentication": ming}
Hs = {"authentication": seller}
Hz = {"authentication": zhang}

# ---------- 一、下单生成跑腿单 ----------
print("\n===== 一、下单生成跑腿单 + 计费快照（3 项）=====")
r = call("POST", "/user/order/submit", Hm, {
    "productId": 102, "dealType": 1,
    "pickAddress": "3号宿舍楼下", "receiveAddress": "教学楼B座",
    "distanceLevel": 2,          # 中档 = 4 公里 → 5 + 1 = 6 元
    "remark": "跑腿测试"})
check("T1", "小明下单（跑腿，距离档位=中/4km）", "code=1", r)
trade_order_id = r.get("data", {}).get("orderId")
print("      交易订单id = %s" % trade_order_id)

d = call("GET", "/user/delivery/byTradeOrder/%s" % trade_order_id, Hm)
dv = d.get("data") or {}
check_true("T2", "自动生成了跑腿单，且跑腿费 = 6.00（计费快照）",
           str(dv.get("deliveryFee")) in ("6.0", "6.00"), "（deliveryFee=%s）" % dv.get("deliveryFee"))
check_true("T3", "跑腿单地址正确，初始状态 = 0 待接单",
           dv.get("pickAddress") == "3号宿舍楼下" and dv.get("receiveAddress") == "教学楼B座"
           and dv.get("status") == 0,
           "（%s → %s, status=%s）" % (dv.get("pickAddress"), dv.get("receiveAddress"), dv.get("status")))
delivery_id = dv.get("id")

# ---------- 二、接单权限 ----------
print("\n===== 二、接单权限（2 项）=====")
check("T4", "普通用户（小明）抢单", "只有跑腿员可以接单",
      call("PUT", "/user/delivery/accept/%s" % delivery_id, Hm))

d901 = (call("GET", "/user/delivery/byTradeOrder/901", Hz).get("data") or {})
delivery_901 = d901.get("id")
check("T5", "跑腿员抢【自己是买家】的订单（901）", "不能给自己的订单跑腿",
      call("PUT", "/user/delivery/accept/%s" % delivery_901, Hz))

# ---------- 三、并发抢单 ----------
print("\n===== 三、并发抢单：8 个跑腿员抢同一单（2 项）=====")
tokens = [login("test_openid_race_%02d" % i) for i in range(7)] + [zhang]
who = ["race%02d" % i for i in range(7)] + ["小张"]
results = []
lock = threading.Lock()
barrier = threading.Barrier(len(tokens))


def grab(idx, tok):
    barrier.wait()   # 用栅栏让 8 个请求尽量同时发出
    r = call("PUT", "/user/delivery/accept/%s" % delivery_id, {"authentication": tok})
    with lock:
        results.append((who[idx], r.get("code"), r.get("msg"), tok))


ts = [threading.Thread(target=grab, args=(i, t)) for i, t in enumerate(tokens)]
[t.start() for t in ts]
[t.join() for t in ts]

ok_list = [x for x in results if x[1] == 1]
fail_list = [x for x in results if x[1] != 1]
print("  成功：%s" % [x[0] for x in ok_list])
print("  失败：%s" % [(x[0], x[2]) for x in fail_list])

check_true("T6", "8 个并发请求中，恰好 1 个抢到", len(ok_list) == 1,
           "（成功 %d 个）" % len(ok_list))

winner_name = ok_list[0][0] if ok_list else None
winner_token = ok_list[0][3] if ok_list else None
loser_token = [x[3] for x in results if x[1] != 1][0]

check("T7", "被抢走后再抢（另一个人）", "手慢了，这单已被别人接走",
      call("PUT", "/user/delivery/accept/%s" % delivery_id, {"authentication": loser_token}))

# ---------- 四、状态机 ----------
print("\n===== 四、状态机：取货 → 送达（4 项）=====")
check("T8", "接单的跑腿员确认取货（1 → 2）", "code=1",
      call("PUT", "/user/delivery/pickup/%s" % delivery_id, {"authentication": winner_token}))

check("T9", "没接单的跑腿员去取货（越权）", "只有接单的跑腿员可以操作",
      call("PUT", "/user/delivery/pickup/%s" % delivery_id, {"authentication": loser_token}))

check("T10", "接单的跑腿员确认送达（2 → 3）", "code=1",
      call("PUT", "/user/delivery/finish/%s" % delivery_id, {"authentication": winner_token}))

check("T11", "重复送达（状态已经不匹配）", "状态已变化",
      call("PUT", "/user/delivery/finish/%s" % delivery_id, {"authentication": winner_token}))

# ---------- 五、取消与联动 ----------
print("\n===== 五、取消与主订单联动（3 项）=====")
delivery_902 = (call("GET", "/user/delivery/byTradeOrder/902", Hm).get("data") or {}).get("id")

check("T12", "跑腿员自己取消跑腿单（不允许）", "无权取消该跑腿单",
      call("PUT", "/user/delivery/cancel/%s" % delivery_902, {"authentication": winner_token}))

check("T13", "买家取消跑腿单（902）", "code=1",
      call("PUT", "/user/delivery/cancel/%s" % delivery_902, Hm))

check("T14", "取消主订单 903 → 跑腿单应联动取消", "code=1",
      call("PUT", "/user/order/cancel", Hm, {"id": 903, "reason": "测试：验证跑腿单联动取消"}))
d903 = (call("GET", "/user/delivery/byTradeOrder/903", Hm).get("data") or {})
check_true("T15", "903 的跑腿单状态已变成 4（已取消）", d903.get("status") == 4,
           "（status=%s）" % d903.get("status"))

# ---------- 六、收入统计 ----------
print("\n===== 六、跑腿员收入统计（1 项）=====")
inc = (call("GET", "/user/delivery/income", {"authentication": winner_token}).get("data") or {})
check_true("T16", "赢家的收入统计：完成 ≥1 单、累计费用 ≥6 元",
           (inc.get("finishedCount") or 0) >= 1 and float(inc.get("totalFee") or 0) >= 6.0,
           "（完成 %s 单，累计 %s 元，进行中 %s 单）"
           % (inc.get("finishedCount"), inc.get("totalFee"), inc.get("ongoingCount")))

# ---------- 七、管理端：跑腿单管理（S4-4）----------
print("\n===== 七、管理端-跑腿单管理（6 项）=====")
admin = login_admin()
Ha = {"token": admin}

pg = call("GET", "/admin/delivery/page?page=1&pageSize=10", Ha).get("data") or {}
check_true("T17", "管理端能分页查到跑腿单（全局视角）", (pg.get("total") or 0) >= 1,
           "（total=%s）" % pg.get("total"))

# 管理员看详情：不受"仅当事人"限制
d = call("GET", "/admin/delivery/%s" % delivery_id, Ha)
check_true("T18", "管理员能查看任意跑腿单详情（不受当事人限制）",
           d.get("code") == 1 and (d.get("data") or {}).get("id") == delivery_id)

# 原因必填
check("T19", "强制取消时不填原因", "强制取消必须填写原因",
      call("PUT", "/admin/delivery/cancel/%s?reason=%s" % (delivery_901, "%20%20"), Ha))

# 对"待接单"的单子强制取消 → 应该成功
# ⚠️ reason 是中文，必须 URL 转义（urllib 不接受 URL 里的非 ASCII，流程纪律第 2 条）
check("T20", "对【待接单】的单子（901）强制取消", "code=1",
      call("PUT", "/admin/delivery/cancel/%s?reason=%s"
           % (delivery_901, urllib.parse.quote("跑腿员失联，管理员介入")), Ha))

# ⭐ 再取消一次（已经取消了）→ 应该被拒（这就是你漏掉的那个守卫）
check("T21", "对【已取消】的单子再次强制取消", "已取消",
      call("PUT", "/admin/delivery/cancel/%s?reason=%s"
           % (delivery_901, urllib.parse.quote("再取消一次")), Ha))

# ⭐ 对"已送达"的单子强制取消 → 应该被拒（不能抹掉跑腿员的劳动记录）
check("T22", "对【已送达】的单子强制取消", "已送达",
      call("PUT", "/admin/delivery/cancel/%s?reason=%s"
           % (delivery_id, urllib.parse.quote("想抹掉记录")), Ha))

# 按状态筛选：status=4 应该全是已取消
pg4 = call("GET", "/admin/delivery/page?status=4&page=1&pageSize=20", Ha).get("data") or {}
recs4 = pg4.get("records") or []
check_true("T23", "按 status=4 筛选 → 返回的全是已取消单", len(recs4) >= 1 and all(x.get("status") == 4 for x in recs4),
           "（%d 条）" % len(recs4))

total = passed + failed
print("\n" + "=" * 60)
print("校园跑腿回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
