# -*- coding: utf-8 -*-
"""
S4-1 管理端-用户管理 回归测试（共 11 项断言）

覆盖：
  一、用户列表与筛选              3 项
  二、封禁的"执法"（重点）         3 项   ← 拦截器 + 登录双保险
  三、解封立即生效（缓存失效）      3 项
  四、参数边界                    2 项

前置条件：
  1. 服务已启动
  2. 已执行 sql/test-reset-admin-user.sql

说明：本测试用【专用账号】test_openid_ban_001 做封禁实验，
      即使测试中途失败，也不会污染其它模块的测试账号。

用法：py -3 tools/admin-user-test.py
退出码：0 = 全部通过；1 = 有失败
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
BAN_OPENID = "test_openid_ban_001"

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


admin = login_admin()
Ha = {"token": admin}

# 专用实验账号：不存在会被 testLogin 自动注册
r = call("GET", "/user/user/testLogin?openid=" + BAN_OPENID)
if r.get("code") != 1:
    print("❌ 实验账号登录失败：%s" % r.get("msg"))
    sys.exit(1)
ban_uid = r["data"]["id"]
ban_token = r["data"]["token"]
Hb = {"authentication": ban_token}
print("【准备】实验账号 id=%s（openid=%s）" % (ban_uid, BAN_OPENID))

# ---------- 一、列表与筛选 ----------
print("\n===== 一、用户列表与筛选（3 项）=====")
page = call("GET", "/admin/user/page?page=1&pageSize=5", Ha)
d = page.get("data") or {}
check_true("T1", "用户分页列表能查到数据", (d.get("total") or 0) >= 3,
           "（total=%s，本页 %d 条）" % (d.get("total"), len(d.get("records") or [])))

page2 = call("GET", "/admin/user/page?role=1&page=1&pageSize=20", Ha)
recs = (page2.get("data") or {}).get("records") or []
check_true("T2", "按 role=1 筛选 → 只返回跑腿员", len(recs) > 0 and all(x.get("role") == 1 for x in recs),
           "（%d 条，角色 %s）" % (len(recs), sorted(set(x.get("role") for x in recs))))

detail = (call("GET", "/admin/user/3", Ha).get("data") or {})
check_true("T3", "用户详情含信用分与好评差评数（小张）",
           detail.get("creditScore") is not None
           and detail.get("goodReviewCount") is not None
           and detail.get("roleName") == "跑腿员",
           "（信用分=%s, 好评=%s, 差评=%s, 角色=%s）"
           % (detail.get("creditScore"), detail.get("goodReviewCount"),
              detail.get("badReviewCount"), detail.get("roleName")))

# ---------- 二、封禁 ----------
print("\n===== 二、封禁的执法（3 项）=====")
check("T4", "管理员封禁该用户", "code=1",
      call("PUT", "/admin/user/status/1?id=%s" % ban_uid, Ha))

check("T5", "被封禁用户【拿着旧 token】访问接口（拦截器拦住）", "HTTP 401",
      call("GET", "/user/credit/my", Hb))

check("T6", "被封禁用户【重新登录】（登录处拦住）", "账号已被封禁",
      call("GET", "/user/user/testLogin?openid=" + BAN_OPENID))

# ---------- 三、解封（验证缓存失效）----------
print("\n===== 三、解封立即生效（3 项）=====")
check("T7", "管理员解封", "code=1",
      call("PUT", "/admin/user/status/0?id=%s" % ban_uid, Ha))

check("T8", "解封后【旧 token 立刻又能用】（说明缓存被清掉了）", "code=1",
      call("GET", "/user/credit/my", Hb))

banned_list = call("GET", "/admin/user/page?status=1&page=1&pageSize=20", Ha)
banned_total = (banned_list.get("data") or {}).get("total") or 0
check_true("T9", "按 status=1 筛选 → 没有已封禁用户（解封已生效）", banned_total == 0,
           "（已封禁 %s 人）" % banned_total)

# ---------- 四、参数边界 ----------
print("\n===== 四、参数边界（2 项）=====")
check("T10", "把状态改成非法值 5", "状态不合法",
      call("PUT", "/admin/user/status/5?id=%s" % ban_uid, Ha))

check("T11", "封禁一个不存在的用户", "用户不存在",
      call("PUT", "/admin/user/status/1?id=999999", Ha))

total = passed + failed
print("\n" + "=" * 60)
print("用户管理回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
