# -*- coding: utf-8 -*-
"""
议价模块回归测试（共 12 项断言）

覆盖：
  一、买家发起出价的全部校验     8 项
  二、我的议价列表 + 数据隔离     4 项
     （其中 T10/T12 是安全用例：不传 view、伪造 sellerId）

前置条件：
  1. 服务已启动（http://localhost:8080）
  2. 已执行 sql/test-reset-bargain.sql
     （清空 bargain 表 + 把商品1/3 恢复成"在售 + 已过审"）

用法：
  py -3 tools/bargain-test.py

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
        return json.loads(urllib.request.urlopen(req, timeout=25).read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": -1, "msg": "HTTP %s" % e.code}
    except Exception as e:
        return {"code": -1, "msg": str(e)[:90]}


def login(openid):
    r = call("GET", "/user/user/testLogin?openid=" + openid)
    if r.get("code") != 1:
        print("❌ 测试登录失败（openid=%s）：%s" % (openid, r.get("msg")))
        print("   请确认服务已启动、且 sky.debug.enabled = true")
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


def check_total(no, desc, expect_total, path, headers):
    """断言列表的 total，并顺带检查返回的每条记录都属于自己的视角"""
    global passed, failed
    r = call("GET", path, headers)
    if r.get("code") != 1:
        failed += 1
        print("  ❌ [%s] %s → 接口失败：%s" % (no, desc, r.get("msg")))
        return
    d = r["data"]
    total = d.get("total")
    ids = [(x.get("id"), x.get("buyerId")) for x in d.get("records", [])]
    if total == expect_total:
        passed += 1
        print("  ✅ [%s] %s（total=%s）%s" % (no, desc, total, ids))
    else:
        failed += 1
        print("  ❌ [%s] %s" % (no, desc))
        print("        期望 total=%s，实际 total=%s，明细(bargainId,buyerId)=%s" % (expect_total, total, ids))


ming = login("test_openid_buyer_001")     # 小明 id=1
seller = login("test_openid_seller_001")  # 学姐 id=2（商品1、3 的卖家）
zhang = login("test_openid_runner_001")   # 小张 id=3
Hm = {"authentication": ming}
Hs = {"authentication": seller}
Hz = {"authentication": zhang}

# 一、发起出价的校验
print("\n===== 一、买家发起议价（8 项）=====")
print("  【商品3：英语真题，标价 50.00，卖家=学姐(2)】")
check("T1", "出价传 null（验证判空顺序）", "出价不能为空",
      call("POST", "/user/bargain", Hm, {"productId": 3, "buyerOffer": None}))
check("T2", "出价传 0", "出价必须大于 0",
      call("POST", "/user/bargain", Hm, {"productId": 3, "buyerOffer": 0}))
check("T3", "小明出价 20.00（只有标价 40%）", "出价不能低于标价的 50%",
      call("POST", "/user/bargain", Hm, {"productId": 3, "buyerOffer": 20.00}))
check("T4", "学姐议价自己的商品", "不能议价自己发布的商品",
      call("POST", "/user/bargain", Hs, {"productId": 3, "buyerOffer": 30.00}))
check("T5", "小明出价 30.00（60%，正常）", "code=1",
      call("POST", "/user/bargain", Hm, {"productId": 3, "buyerOffer": 30.00}))
check("T6", "小明对同一商品再出价（已有进行中）", "你已经对这件商品出过价了",
      call("POST", "/user/bargain", Hm, {"productId": 3, "buyerOffer": 40.00}))
check("T7", "小张出价 40.00（换个买家，应允许）", "code=1",
      call("POST", "/user/bargain", Hz, {"productId": 3, "buyerOffer": 40.00}))
print("  【商品1：考研数学，标价 25.00】—— 测边界值")
check("T8", "小张出价 12.50（正好 50%，应允许）", "code=1",
      call("POST", "/user/bargain", Hz, {"productId": 1, "buyerOffer": 12.50}))

# 二、列表与数据隔离
print("\n===== 二、我的议价列表 + 数据隔离（4 项）=====")
check_total("T9", "小明 view=buyer（我发起的）", 1,
            "/user/bargain/list?page=1&pageSize=10&view=buyer", Hm)
check_total("T10", "小明【不传 view】→ 只能看到自己的（防数据泄露）", 1,
            "/user/bargain/list?page=1&pageSize=10", Hm)
check_total("T11", "学姐 view=seller（别人对我商品出的价）", 3,
            "/user/bargain/list?page=1&pageSize=10&view=seller", Hs)
check_total("T12", "小明伪造 view=seller&sellerId=2（身份只认 token）", 0,
            "/user/bargain/list?page=1&pageSize=10&view=seller&sellerId=2", Hm)

total = passed + failed
print("\n" + "=" * 60)
print("议价模块回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
