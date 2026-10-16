# -*- coding: utf-8 -*-
"""
S3-10b 投诉工单 回归测试（共 14 项断言）

覆盖：
  一、商品举报               3 项
  二、交易纠纷               2 项
  三、权限（第三人看工单）     1 项
  四、管理端处理：成立         5 项   ← 核心（含"重复处理只扣一次分"）
  五、管理端处理：不成立       2 项
  六、工单详情               1 项

前置条件：
  1. 服务已启动
  2. 已执行 sql/test-reset-complaint.sql

用法：py -3 tools/complaint-test.py
退出码：0 = 全部通过；1 = 有失败
"""
import json
import sys
import threading
import urllib.error
import urllib.parse
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


def score_of(token):
    return (call("GET", "/user/credit/my", {"authentication": token}).get("data") or {}).get("creditScore")


ming = login("test_openid_buyer_001")     # id=1 买家
seller = login("test_openid_seller_001")  # id=2 卖家（被投诉人）
zhang = login("test_openid_runner_001")   # id=3 第三人
Hm = {"authentication": ming}
Hs = {"authentication": seller}
Hz = {"authentication": zhang}
admin = login_admin()
Ha = {"token": admin}

# ---------- 一、商品举报 ----------
print("\n===== 一、商品举报（3 项）=====")
r1 = call("POST", "/user/complaint", Hz, {
    "complaintType": 0,                 # 商品举报
    "productId": 107,
    "complaintReason": 2,               # 虚假描述
    "content": "商品描述写的是全新，实际有明显划痕，怀疑虚假描述",
    "images": '["https://example.com/proof1.jpg","https://example.com/proof2.jpg"]'})
check("T1", "小张举报商品107（虚假描述 + 2 张凭证）", "code=1", r1)
product_complaint_id = r1.get("data")
print("      商品举报工单id = %s" % product_complaint_id)

check("T2", "学姐举报【自己】的商品", "不能举报自己的商品",
      call("POST", "/user/complaint", Hs, {
          "complaintType": 0, "productId": 107, "complaintReason": 2, "content": "我自己举报自己"}))

check("T3", "小张对同一商品再举报一次（上一单还没处理完）", "你对该对象的投诉还在处理中",
      call("POST", "/user/complaint", Hz, {
          "complaintType": 0, "productId": 107, "complaintReason": 2, "content": "再举报一次"}))

# ---------- 二、交易纠纷 ----------
print("\n===== 二、交易纠纷（2 项）=====")
r2 = call("POST", "/user/complaint", Hm, {
    "complaintType": 1,                 # 交易纠纷
    "orderId": 920,
    "complaintReason": 1,               # 货不对板
    "content": "收到的书缺了 30 页，和描述不符"})
check("T4", "小明对订单920发起交易纠纷（货不对板）", "code=1", r2)
order_complaint_id = r2.get("data")
print("      交易纠纷工单id = %s" % order_complaint_id)

check("T5", "第三人（小张）对别人的订单发起纠纷", "无权对该订单发起纠纷",
      call("POST", "/user/complaint", Hz, {
          "complaintType": 1, "orderId": 920, "complaintReason": 1, "content": "我插一脚"}))

# ---------- 三、权限 ----------
print("\n===== 三、权限（1 项）=====")
check("T6", "第三人（小张）看【别人】的工单详情", "无权查看该工单",
      call("GET", "/user/complaint/%s" % order_complaint_id, Hz))

# ---------- 四、管理端处理：成立 ----------
print("\n===== 四、管理端处理：投诉成立（4 项）=====")
page = call("GET", "/admin/complaint/page?handleStatus=0&page=1&pageSize=10", Ha)
total_waiting = (page.get("data") or {}).get("total")
check_true("T7", "管理员看到 2 条待处理工单", total_waiting == 2, "（实际 %s 条）" % total_waiting)

check("T8", "管理员判定【交易纠纷】成立", "code=1",
      call("PUT", "/admin/complaint/handle", Ha, {
          "id": order_complaint_id, "handleResult": 1,
          "handleNote": "经核实商品确实缺页，判定投诉成立，扣被投诉人信用分"}))

s_seller = score_of(seller)
check_true("T9", "被投诉人（学姐）信用分 100 → 90（-10）", s_seller == 90, "（实际 %s）" % s_seller)

# 顺序重复处理：被【状态校验】拦住（友好提示）。
# 注意：handleIfNotDone 的 rows==0 那条路是留给【真正并发】的，见下面 T16/T17。
check("T10", "管理员顺序重复处理同一工单（状态守卫拦住）", "该工单已处理完毕",
      call("PUT", "/admin/complaint/handle", Ha, {
          "id": order_complaint_id, "handleResult": 1, "handleNote": "我再判一次"}))

s_after = score_of(seller)
check_true("T11", "重复处理没有造成【二次扣分】（还是 90）", s_after == 90, "（实际 %s）" % s_after)

# ---------- 五、管理端处理：不成立 ----------
print("\n===== 五、管理端处理：投诉不成立（2 项）=====")
check("T12", "管理员判定【商品举报】不成立", "code=1",
      call("PUT", "/admin/complaint/handle", Ha, {
          "id": product_complaint_id, "handleResult": 2,
          "handleNote": "划痕属于正常使用痕迹，描述中已注明，判定举报不成立"}))

s_seller = score_of(seller)
check_true("T13", "不成立【不扣分】（仍是 90）", s_seller == 90, "（实际 %s）" % s_seller)

# ---------- 六、工单详情 ----------
print("\n===== 六、工单详情（1 项）=====")
d = call("GET", "/user/complaint/%s" % order_complaint_id, Hm)
vo = d.get("data") or {}
check_true("T14", "投诉人能看到工单详情（状态=已完结、结果=成立、备注非空）",
           vo.get("handleStatus") == 2 and vo.get("handleResult") == 1 and bool(vo.get("handleNote")),
           "（状态=%s, 结果=%s, 备注=%s）"
           % (vo.get("handleStatus"), vo.get("handleResult"), (vo.get("handleNote") or "")[:16] + "…"))

# ---------- 七、完结后可以重新投诉 + 并发处理 ----------
print("\n===== 七、重新投诉 + 两个管理员【并发】处理同一工单（3 项）=====")
r3 = call("POST", "/user/complaint", Hz, {
    "complaintType": 0, "productId": 107, "complaintReason": 2,
    "content": "上一单处理完了，又有新的问题：卖家补充的描述仍然与实际不符"})
check("T15", "上一单已完结 → 可以对同一商品重新投诉", "code=1", r3)
second_complaint_id = r3.get("data")

# 两个"管理员"同时点【成立】—— 这才是 handleIfNotDone 里 rows==0 那条路的用武之地
results = []
lock = threading.Lock()
barrier = threading.Barrier(2)


def handle_concurrently(idx):
    barrier.wait()
    r = call("PUT", "/admin/complaint/handle", Ha, {
        "id": second_complaint_id, "handleResult": 1,
        "handleNote": "管理员%s的判定：投诉成立" % idx})
    with lock:
        results.append((idx, r.get("code"), r.get("msg")))


ts = [threading.Thread(target=handle_concurrently, args=(i,)) for i in (1, 2)]
[t.start() for t in ts]
[t.join() for t in ts]
ok_list = [x for x in results if x[1] == 1]
print("  成功：%s" % [x[0] for x in ok_list])
print("  失败：%s" % [(x[0], x[2]) for x in results if x[1] != 1])

check_true("T16", "并发处理同一工单：恰好 1 个成功", len(ok_list) == 1,
           "（成功 %d 个）" % len(ok_list))

# 关键：信用分只被扣一次（如果两个都成功，被投诉人会被扣 20 分）
s_seller = score_of(seller)
check_true("T17", "并发下信用分【只扣一次】：90 - 10 = 80", s_seller == 80, "（实际 %s）" % s_seller)

total = passed + failed
print("\n" + "=" * 60)
print("投诉工单回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
