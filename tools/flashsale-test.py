# -*- coding: utf-8 -*-
"""
限时捡漏抢购回归测试（共 11 项断言）

覆盖：
  一、捡漏专区列表（状态计算）        2 项
  二、抢购的各种守卫                  4 项
  三、正常抢购 + 重复抢                 2 项
  四、并发压测：9 个用户同时抢同一件     3 项   ← 核心

前置条件：
  1. 服务已启动
  2. 已执行 sql/test-reset-flashsale.sql
  3. 已用 redis-cli 清掉 flash_sale_stock:* 库存 key（由 run-regression.ps1 负责）

用法：py -3 tools/flashsale-test.py
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


ming = login("test_openid_buyer_001")     # 小明 id=1
seller = login("test_openid_seller_001")  # 学姐 id=2（商品1/2/3 的卖家）
zhang = login("test_openid_runner_001")   # 小张 id=3
Hm = {"authentication": ming}
Hs = {"authentication": seller}
Hz = {"authentication": zhang}

# 重置 Redis 库存 key —— 让脚本【可重复运行】
#   为什么不用 redis-cli：Windows 上的 redis-cli 是 Cygwin 编译的，
#   在受限环境下会因为创建命名对象失败而跑不起来（0xC0000022）。
#   而且"重置捡漏库存"本来就是个正经功能（活动结束/商品重新上架时要用），
#   所以走应用自己的接口最合适。
print("【准备】重置商品 1/2/3 的 Redis 库存…")
admin = login_admin()
for pid in (1, 2, 3):
    r = call("POST", "/admin/flashSale/resetStock/%d" % pid, {"token": admin})
    if r.get("code") != 1:
        print("  ⚠️ 重置库存失败（productId=%d）：%s" % (pid, r.get("msg")))
        print("     请确认 campus.debug.enabled = true")
print("  完成")

# 一、捡漏专区列表
print("\n===== 一、捡漏专区列表（2 项）=====")
r = call("GET", "/user/flashSale/list", Hm)
records = r.get("data") or []
by_id = {x["id"]: x for x in records}
print("  列表返回：%s" % [(x["id"], x.get("flashStatus")) for x in records])

check_true("T1", "商品3 状态应为 1（抢购中）、商品1 状态应为 0（未开始）",
           by_id.get(3, {}).get("flashStatus") == 1 and by_id.get(1, {}).get("flashStatus") == 0,
           "（3→%s, 1→%s）" % (by_id.get(3, {}).get("flashStatus"), by_id.get(1, {}).get("flashStatus")))

check_true("T2", "非捡漏商品6 不应出现在捡漏专区", 6 not in by_id)

# 二、抢购守卫
print("\n===== 二、抢购守卫（4 项）=====")
check("T3", "卖家抢自己发布的商品3", "不能抢自己发布的商品",
      call("POST", "/user/flashSale/3", Hs))
check("T4", "抢还没开始的商品1", "抢购还未开始",
      call("POST", "/user/flashSale/1", Hm))
check("T5", "抢非捡漏商品6", "不是限时捡漏商品",
      call("POST", "/user/flashSale/6", Hm))
check("T6", "抢不存在的商品", "商品不存在",
      call("POST", "/user/flashSale/9999", Hm))

# 三、正常抢购
print("\n===== 三、正常抢购（2 项）=====")
check("T7", "小明抢商品3（应成功）", "code=1",
      call("POST", "/user/flashSale/3", Hm))
check("T8", "小明再抢商品3（商品已被自己抢走，状态已变）", "已经被抢走了",
      call("POST", "/user/flashSale/3", Hm))

# 四、并发压测
print("\n===== 四、并发压测：9 个用户同时抢商品2（3 项）=====")
tokens = [login("test_openid_race_%02d" % i) for i in range(8)] + [zhang]
who = ["race%02d" % i for i in range(8)] + ["小张"]
results = []
lock = threading.Lock()
barrier = threading.Barrier(len(tokens))


def grab(idx, tok):
    barrier.wait()   # 用栅栏让 9 个请求尽量同时发出
    r = call("POST", "/user/flashSale/2", {"authentication": tok})
    with lock:
        results.append((who[idx], r.get("code"), r.get("msg")))


ts = [threading.Thread(target=grab, args=(i, t)) for i, t in enumerate(tokens)]
[t.start() for t in ts]
[t.join() for t in ts]

ok_list = [x for x in results if x[1] == 1]
fail_list = [x for x in results if x[1] != 1]
print("  成功：%s" % [(x[0]) for x in ok_list])
print("  失败：%s" % [(x[0], x[2]) for x in fail_list])

check_true("T9", "9 个并发请求中，恰好 1 个成功", len(ok_list) == 1,
           "（成功 %d 个）" % len(ok_list))

# 成功者应该能查到抢购记录；失败者查不到
winner_token = None
for i, t in enumerate(tokens):
    if who[i] == ok_list[0][0]:
        winner_token = t
        break
loser_token = [t for i, t in enumerate(tokens) if who[i] != ok_list[0][0]][0]

w_rec = call("GET", "/user/flashSale/record", {"authentication": winner_token}).get("data") or []
l_rec = call("GET", "/user/flashSale/record", {"authentication": loser_token}).get("data") or []
check_true("T10", "抢到的人有一条商品2 的抢购记录",
           any(x.get("productId") == 2 for x in w_rec))
check_true("T11", "没抢到的人没有商品2 的抢购记录",
           not any(x.get("productId") == 2 for x in l_rec))

# 五、S3-7c：捡漏订单取消后，Redis 库存要还回来
#   不还的后果：商品回到"在售"，但 Redis 库存还是 0
#              → 捡漏专区显示"抢购中"，一点却报"已经被抢光了"，商品谁也拿不到。
print("\n===== 五、取消捡漏订单后库存恢复（3 项）=====")
r = call("GET", "/user/order/page?page=1&pageSize=10&view=buyer", Hm)
orders = [x for x in (r.get("data") or {}).get("records", [])
          if x.get("orderSource") == 3 and x.get("productId") == 3]
oid = orders[0]["id"] if orders else 0
check("T12", "小明取消自己刚抢到的捡漏订单（商品3，订单id=%s）" % oid, "code=1",
      call("PUT", "/user/order/cancel", Hm, {"id": oid, "reason": "测试：验证取消后库存恢复"}))
check("T13", "小张抢商品3（库存应已随取消恢复 → 能抢到）", "code=1",
      call("POST", "/user/flashSale/3", Hz))
z_rec = call("GET", "/user/flashSale/record", {"authentication": zhang}).get("data") or []
check_true("T14", "小张的抢购记录里出现了商品3", any(x.get("productId") == 3 for x in z_rec))

# ---------- 六、管理端：捡漏活动管理（S4-3）----------
print("\n===== 六、管理端-捡漏活动管理（6 项）=====")
admin = login_admin()
Ha = {"token": admin}

lst = call("GET", "/admin/flashSale/list", Ha).get("data") or []
check_true("T15", "活动列表能查到捡漏商品，且带库存/参与人数",
           len(lst) >= 1 and all("participants" in x for x in lst),
           "（%d 件：%s）" % (len(lst), [(x.get("productId"), x.get("statusName"), x.get("redisStock")) for x in lst[:3]]))

# 对"非捡漏商品"重置库存 → 应该被拒（商品6 是普通商品）
check("T16", "对普通商品（商品6）重置库存", "该商品不是捡漏商品",
      call("POST", "/admin/flashSale/resetStock/6", Ha))

# 对"已经卖掉(交易中)"的捡漏商品重置库存 → 应该被拒
#   商品2 在并发压测里已经被抢走（status=交易中）
check("T17", "对已卖掉的捡漏商品（商品2）重置库存", "不是在售状态",
      call("POST", "/admin/flashSale/resetStock/2", Ha))

# 对正常在售的捡漏商品重置库存 → 应该成功
check("T18", "对在售的捡漏商品（商品1）重置库存", "code=1",
      call("POST", "/admin/flashSale/resetStock/1", Ha))

# 结束活动：商品1 变回普通商品
check("T19", "结束商品1 的捡漏活动", "code=1",
      call("PUT", "/admin/flashSale/end/1", Ha))
lst2 = call("GET", "/admin/flashSale/list", Ha).get("data") or []
check_true("T20", "结束后的商品1 已不在捡漏活动列表里",
           not any(x.get("productId") == 1 for x in lst2),
           "（列表里现在是 %s）" % [x.get("productId") for x in lst2])

check("T21", "对【已经不是捡漏】的商品1 再次结束", "本来就是普通商品",
      call("PUT", "/admin/flashSale/end/1", Ha))

total = passed + failed
print("\n" + "=" * 60)
print("捡漏抢购回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
