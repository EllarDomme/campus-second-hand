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
import urllib.parse          # 中文地点要转义后才能放进 URL（urllib 不接受 URL 里的非 ASCII）
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
check_total("T11", "学姐 view=seller（别人对我商品出的价；含1条预置的过期数据）", 4,
            "/user/bargain/list?page=1&pageSize=10&view=seller", Hs)
check_total("T12", "小明伪造 view=seller&sellerId=2（身份只认 token）", 0,
            "/user/bargain/list?page=1&pageSize=10&view=seller&sellerId=2", Hm)


# ==================================================================
# 三、议价响应动作 + 成交生成订单（S3-6b）
#   这一节的存在意义：
#     ① "谁有权操作"是安全边界 —— 特意测了【第三人越权】，专盯"排除法"漏洞
#     ② 成交要能正确生成订单，且成交价必须取"最后一次报价"
#     ③ 方案A：议价成交的订单没有交易地点，买家要能后补
# ==================================================================
def find_bargain(headers, view, buyer_id=None, product_id=None):
    """按 买家/商品 从议价列表里找出那条记录（找不到返回 None）"""
    r = call("GET", "/user/bargain/list?page=1&pageSize=20&view=" + view, headers)
    for x in r.get("data", {}).get("records", []):
        if buyer_id is not None and x.get("buyerId") != buyer_id:
            continue
        if product_id is not None and x.get("productId") != product_id:
            continue
        return x
    return None


def find_bargain_order(headers):
    """从订单列表里找出"议价生成"的订单（orderSource=2）"""
    r = call("GET", "/user/order/page?page=1&pageSize=20&view=buyer", headers)
    for x in r.get("data", {}).get("records", []):
        if x.get("orderSource") == 2:
            return x
    return None


print("\n===== 三、议价响应动作 + 成交生成订单（18 项）=====")
A = find_bargain(Hs, "seller", buyer_id=1, product_id=3)   # 小明×商品3，出价 30
B = find_bargain(Hs, "seller", buyer_id=3, product_id=3)   # 小张×商品3，出价 40
C = find_bargain(Hs, "seller", buyer_id=3, product_id=1)   # 小张×商品1，出价 12.5
D = find_bargain(Hs, "seller", buyer_id=3, product_id=2)   # 预置的【已过期】议价
if not all([A, B, C, D]):
    print("  ❌ 前置数据不齐（A=%s B=%s C=%s D=%s），请先执行 sql/test-reset-bargain.sql"
          % (bool(A), bool(B), bool(C), bool(D)))
    failed += 1
else:
    print("  A(id=%s 小明×商品3 出价%s)  B(id=%s 小张×商品3 出价%s)  C(id=%s)  D(过期 id=%s)"
          % (A["id"], A["buyerOffer"], B["id"], B["buyerOffer"], C["id"], D["id"]))

    # ---- 权限：谁能接受 ----
    check("T13", "买家(小明)接受自己的出价（越权）", "只有卖家可以接受这个报价",
          call("PUT", "/user/bargain/accept/%d" % A["id"], Hm))
    check("T14", "第三人(小张)接受别人的议价（专盯「排除法」漏洞）", "只有卖家可以接受这个报价",
          call("PUT", "/user/bargain/accept/%d" % A["id"], Hz))
    check("T15", "卖家(学姐)接受小明的出价（应成功 → 生成订单）", "code=1",
          call("PUT", "/user/bargain/accept/%d" % A["id"], Hs))
    check("T16", "重复接受同一条（议价已结束）", "议价已结束",
          call("PUT", "/user/bargain/accept/%d" % A["id"], Hs))

    # ---- 还价（单轮制）----
    # 用 C（小张×商品1）做"还价 + 买家接受"这条线。
    # 注意不能再用商品3：它的议价 A 刚刚成交，商品3 已被锁成"交易中"，
    # 再接受同一商品的另一条议价会被"商品已被别人买走"拦住 —— 一件商品只能卖一次。
    check("T17", "卖家对小张的出价还价 20.00（应成功）", "code=1",
          call("PUT", "/user/bargain/counter", Hs, {"id": C["id"], "counterOffer": 20.00}))
    check("T18", "卖家再还一次（单轮制，应被拒）", "卖家已还价，只能接受或拒绝",
          call("PUT", "/user/bargain/counter", Hs, {"id": C["id"], "counterOffer": 22.00}))
    check("T19", "小明试图接受小张的还价（身份不对）", "只有买家可以接受这个报价",
          call("PUT", "/user/bargain/accept/%d" % C["id"], Hm))
    check("T20", "买家(小张)接受卖家的还价 20.00（应成功）", "code=1",
          call("PUT", "/user/bargain/accept/%d" % C["id"], Hz))

    # ---- 拒绝（用 B：小张×商品3，它没被成交，可以正常拒绝）----
    check("T21", "买家(小明)拒绝别人的议价（越权）", "只有卖家可以拒绝这个报价",
          call("PUT", "/user/bargain/reject/%d" % B["id"], Hm))
    check("T22", "卖家(学姐)拒绝小张的出价（应成功）", "code=1",
          call("PUT", "/user/bargain/reject/%d" % B["id"], Hs))

    # ---- 过期 ----
    check("T23", "卖家给【已过期】的议价还价（应被拒）", "议价已过期",
          call("PUT", "/user/bargain/counter", Hs, {"id": D["id"], "counterOffer": 25.00}))
    check("T24", "卖家接受【已过期】的议价（应被拒）", "议价已过期",
          call("PUT", "/user/bargain/accept/%d" % D["id"], Hs))

    # ---- 成交订单核对：来源必须是"议价生成"，成交价必须取最后一次报价 ----
    order_a = find_bargain_order(Hm)   # 小明 30 元成交那单
    order_b = find_bargain_order(Hz)   # 小张 20 元成交那单（取的是卖家还价 20，不是买家出价 12.5）
    if order_a and order_b:
        for no, order, want in (("T25", order_a, 30.00), ("T26", order_b, 20.00)):
            ok = (float(order["realPrice"]) == want and order.get("orderSource") == 2)
            passed += 1 if ok else 0
            failed += 0 if ok else 1
            print("  %s [%s] 成交订单 来源=%s 成交价=%s（期望：来源2、价%.2f）"
                  % ("✅" if ok else "❌", no, order.get("orderSource"), order.get("realPrice"), want))
    else:
        failed += 2
        print("  ❌ [T25/T26] 没找到议价生成的订单（orderSource=2）")

    # ---- 方案A：交易地点后补（买家是小明 → 用 Hm）----
    #    注意：地点里有中文，必须 urllib.parse.quote 转义后再拼进 URL
    oid = order_a["id"] if order_a else 0
    check("T27", "买家补充交易地点（应成功）", "code=1",
          call("PUT", "/user/order/pickup/%d?pickupPlace=%s" % (oid, urllib.parse.quote("3号宿舍楼下")), Hm))
    check("T28", "卖家改交易地点（越权）", "只有买家可以填写交易地点",
          call("PUT", "/user/order/pickup/%d?pickupPlace=%s" % (oid, urllib.parse.quote("图书馆")), Hs))
    check("T29", "交易地点只填空格（应被拒）", "交易地点不能为空",
          call("PUT", "/user/order/pickup/%d?pickupPlace=%%20%%20" % oid, Hm))
    check("T30", "交易地点超长 300 字（应被拒）", "交易地点太长了",
          call("PUT", "/user/order/pickup/%d?pickupPlace=%s" % (oid, "x" * 300), Hm))

total = passed + failed
print("\n" + "=" * 60)
print("议价模块回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
