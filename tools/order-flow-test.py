# -*- coding: utf-8 -*-
"""
订单模块回归测试（共 19 项断言）

覆盖：
  一、卖家确认 / 拒单          9 项
  二、买家确认收货             4 项
  三、取消订单（买卖双方）      6 项

前置条件：
  1. 服务已启动（http://localhost:8080）
  2. 已执行 sql/test-reset.sql 重置测试数据到基线状态
     （重置脚本会把 5 条测试订单的 expire_time 顺延，否则"发起支付"会被超时校验拦住）

用法：
  py -3 tools/order-flow-test.py
  或（不带 py launcher 时）python tools/order-flow-test.py

退出码：0 = 全部通过；1 = 有失败
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"

# ============ 工具函数 ============
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
    """断言返回内容里包含期望的片段"""
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


# ============ 准备身份 ============
buyer = login("test_openid_buyer_001")    # 小明 id=1（订单1、4 的买家）
seller = login("test_openid_seller_001")  # 学姐 id=2（订单1、4、5 的卖家）
other = login("test_openid_runner_001")   # 小张 id=3（订单2 的买家、订单1/4/5 的无关人）
Hb = {"authentication": buyer}
Hs = {"authentication": seller}
Ho = {"authentication": other}

ORDER1 = "202610110915435146"
ORDER2 = "202610110915452674"

# 一、卖家确认 / 拒单
print("\n===== 一、卖家确认 / 拒单（9 项）=====")
check("T1", "买家冒充卖家确认订单4", "您没有权限确认该订单",
      call("PUT", "/user/order/confirm/4", Hb))
check("T2", "卖家(学姐)确认订单4", "code=1",
      call("PUT", "/user/order/confirm/4", Hs))
check("T3", "重复确认订单4（状态已不是待确认）", "订单状态不是待确认",
      call("PUT", "/user/order/confirm/4", Hs))
check("T4", "确认一个不存在的订单", "订单不存在",
      call("PUT", "/user/order/confirm/9999", Hs))
check("T5", "买家冒充卖家拒单订单5", "您没有权限拒绝该订单",
      call("PUT", "/user/order/reject", Hb, {"id": 5, "reason": "测试越权"}))
check("T6", "卖家拒单订单5（已支付 → 应退款）", "code=1",
      call("PUT", "/user/order/reject", Hs, {"id": 5, "reason": "东西已经被同学买走了"}))
check("T7", "卖家拒单订单1（未支付 → 不应退款）", "code=1",
      call("PUT", "/user/order/reject", Hs, {"id": 1, "reason": "暂时不想卖了"}))
check("T8", "拒单不填原因（订单2）", "拒单原因不能为空",
      call("PUT", "/user/order/reject", Hs, {"id": 2, "reason": "   "}))
check("T9", "重复拒单订单5（已经取消了）", "订单状态不是待确认",
      call("PUT", "/user/order/reject", Hs, {"id": 5, "reason": "再来一次"}))

# 二、买家确认收货（订单4 此时已是"待交易"）
print("\n===== 二、买家确认收货（4 项）=====")
check("T10", "卖家冒充买家确认收货订单4", "您没有权限确认收货",
      call("PUT", "/user/order/complete/4", Hs))
check("T11", "买家(小明)确认收货订单4（应成功 + 放款）", "code=1",
      call("PUT", "/user/order/complete/4", Hb))
check("T12", "重复确认收货订单4（状态已不是待交易）", "订单状态不是待交易",
      call("PUT", "/user/order/complete/4", Hb))
check("T13", "确认收货一个不存在的订单", "订单不存在",
      call("PUT", "/user/order/complete/9999", Hb))

# 三、取消订单
print("\n===== 三、取消订单（6 项）=====")
check("T14", "无关的人(小张)取消订单3", "您没有权限取消该订单",
      call("PUT", "/user/order/cancel", Ho, {"id": 3, "reason": "跟我没关系"}))
check("T15", "取消但不填原因（订单2）", "取消原因不能为空",
      call("PUT", "/user/order/cancel", Ho, {"id": 2, "reason": "  "}))

# 先让订单2 变成"已支付"，用来测"取消 + 退款"这条分支
r = call("POST", "/user/order/pay?orderNo=" + ORDER2, Ho)
pay_no = (r.get("data") or {}).get("payNo")
if not pay_no:
    print("  ⚠️ 订单2 发起支付失败（%s）——多半是 expire_time 已过期，请先执行 sql/test-reset.sql" % r.get("msg"))
    failed += 2
else:
    call("POST", "/notify/paySuccess?payNo=" + pay_no)

check("T16", "买家(小张)取消已支付的订单2（应成功 + 退款）", "code=1",
      call("PUT", "/user/order/cancel", Ho, {"id": 2, "reason": "临时有事，买不了了"}))
check("T17", "卖家(学姐)取消未支付的订单3（应成功，不退款）", "code=1",
      call("PUT", "/user/order/cancel", Hs, {"id": 3, "reason": "东西坏了，不卖了"}))
check("T18", "重复取消订单2（已是终态）", "订单已进入终态",
      call("PUT", "/user/order/cancel", Ho, {"id": 2, "reason": "再来一次"}))
check("T19", "取消一个不存在的订单", "订单不存在",
      call("PUT", "/user/order/cancel", Ho, {"id": 9999, "reason": "测试"}))

# 四、普通下单 submit（用 sql/test-reset.sql 里专造的商品 100 / 101）
#   这一节的存在意义：submit 是"锁商品 + 建订单"公共逻辑的调用方之一，
#   抽取/重构它时必须能被回归盯住，否则改坏了都不知道。
print("\n===== 四、普通下单 submit（4 项）=====")
check("T20", "学姐下单自己的商品100", "不能购买自己发布的商品",
      call("POST", "/user/order/submit", Hs,
           {"productId": 100, "dealType": 0, "pickupPlace": "回归测试地点"}))
check("T21", "小明下单商品100（应成功）", "code=1",
      call("POST", "/user/order/submit", Hb,
           {"productId": 100, "dealType": 0, "pickupPlace": "回归测试地点", "remark": "回归"}))
check("T22", "小明再下单商品100（已被锁成交易中）", "商品当前不可购买",
      call("POST", "/user/order/submit", Hb,
           {"productId": 100, "dealType": 0, "pickupPlace": "回归测试地点"}))
check("T23", "小明下单商品101（未过审）", "商品还未通过审核",
      call("POST", "/user/order/submit", Hb,
           {"productId": 101, "dealType": 0, "pickupPlace": "回归测试地点"}))

# ============ 汇总 ============
total = passed + failed
print("\n" + "=" * 60)
print("订单模块回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
