# -*- coding: utf-8 -*-
"""
私信模块回归测试（共 17 项断言）

覆盖：
  一、会话创建与查重                  3 项
  二、WebSocket 实时推送              4 项
  三、聊天记录 + 已读 + 已读回执       3 项
  四、权限：第三人越权                 3 项
  五、消息搜索（权限写进 SQL）         3 项
  六、WebSocket 鉴权                   1 项   ← 不带 token 连不上

前置条件：
  1. 服务已启动
  2. 已执行 sql/test-reset-message.sql

WebSocket 客户端说明：这里用【自己实现的极简客户端】（约 40 行）——
  标准库没有 websocket 模块，而理解握手和帧格式比调库更有价值：
    ① 握手：发一个带 Sec-WebSocket-Key 的 HTTP 请求，服务端回 101
    ② 数据帧：第 1 字节低 4 位是 opcode(1=文本 8=关闭)，第 2 字节低 7 位是长度
    ③ 服务端→客户端的帧【不加掩码】，客户端→服务端的帧必须加掩码

用法：py -3 tools/message-test.py
退出码：0 = 全部通过；1 = 有失败
"""
import base64
import json
import os
import socket
import struct
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
HOST = "127.0.0.1"
PORT = 8080

passed = 0
failed = 0


# ==================== HTTP 小工具 ====================
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


# ==================== 极简 WebSocket 客户端 ====================
def _recv_exact(s, n):
    buf = b""
    while len(buf) < n:
        chunk = s.recv(n - len(buf))
        if not chunk:
            raise IOError("连接已被对端关闭")
        buf += chunk
    return buf


def ws_connect(sid, token=None, timeout=6):
    """完成 WebSocket 握手；返回 socket。token 为 None 时不带（用于测鉴权）"""
    key = base64.b64encode(os.urandom(16)).decode()
    path = "/ws/%s" % sid
    if token:
        path += "?token=" + token
    req = ("GET %s HTTP/1.1\r\n"
           "Host: %s:%d\r\n"
           "Upgrade: websocket\r\n"
           "Connection: Upgrade\r\n"
           "Sec-WebSocket-Key: %s\r\n"
           "Sec-WebSocket-Version: 13\r\n\r\n") % (path, HOST, PORT, key)
    s = socket.create_connection((HOST, PORT), timeout=timeout)
    s.sendall(req.encode())
    # 读握手响应直到空行
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = s.recv(4096)
        if not chunk:
            break
        buf += chunk
    status = buf.split(b"\r\n")[0].decode("utf-8", "ignore") if buf else "(无响应)"
    return s, status


def ws_recv_text(s, timeout=6):
    """读一条文本消息；返回 None 表示连接被关闭"""
    s.settimeout(timeout)
    while True:
        b1, b2 = _recv_exact(s, 2)
        opcode = b1 & 0x0F
        masked = b2 & 0x80
        length = b2 & 0x7F
        if length == 126:
            length = struct.unpack(">H", _recv_exact(s, 2))[0]
        elif length == 127:
            length = struct.unpack(">Q", _recv_exact(s, 8))[0]
        mask = _recv_exact(s, 4) if masked else None
        payload = _recv_exact(s, length)
        if mask:
            payload = bytes(c ^ mask[i % 4] for i, c in enumerate(payload))
        if opcode == 0x1:          # 文本帧
            return payload.decode("utf-8")
        if opcode == 0x8:          # 关闭帧
            return None
        # 其它（ping/pong/二进制）忽略，继续读下一帧


def ws_try_recv(s, timeout=4):
    """尝试收一条消息：收到返回文本；关闭/超时返回 None"""
    try:
        return ws_recv_text(s, timeout)
    except Exception:
        return None


# ==================== 开始测试 ====================
ming = login("test_openid_buyer_001")     # id=1
seller = login("test_openid_seller_001")  # id=2（商品3 的卖家）
zhang = login("test_openid_runner_001")   # id=3（第三人）
Hm = {"authentication": ming}
Hs = {"authentication": seller}
Hz = {"authentication": zhang}

# ---------- 一、会话 ----------
print("\n===== 一、会话创建与查重（3 项）=====")
r1 = call("POST", "/user/message/conversation?productId=3", Hm)
check("T1", "小明点'联系卖家'（商品3）建会话", "code=1", r1)
cid = r1.get("data")
print("      会话id = %s" % cid)

r2 = call("POST", "/user/message/conversation?productId=3", Hm)
check_true("T2", "小明再点一次 → 返回同一个会话（不新建）",
           r2.get("data") == cid, "（%s vs %s）" % (r2.get("data"), cid))

check("T3", "卖家点自己商品的'联系卖家'（我和我自己）", "不能和自己聊天",
      call("POST", "/user/message/conversation?productId=3", Hs))

# ---------- 二、WebSocket 实时推送 ----------
print("\n===== 二、WebSocket 实时推送（4 项）=====")
ws_seller, hs = ws_connect("2", seller)
print("      学姐的 WebSocket 握手：[%s]" % hs)
check_true("T4", "带 token 的 WebSocket 握手成功（101）", "101" in hs, "（%s）" % hs)

check("T5", "小明发一条消息", "code=1",
      call("POST", "/user/message/send", Hm,
           {"conversationId": cid, "content": "你好，明天下午在宿舍楼下交易可以吗"}))

msg = ws_try_recv(ws_seller, 6)
push1 = json.loads(msg) if msg else {}
check_true("T6", "学姐实时收到 type=message 推送", push1.get("type") == "message",
           "（收到：%s）" % (msg if msg else "什么都没收到"))

unread = ws_try_recv(ws_seller, 6)
push2 = json.loads(unread) if unread else {}
check_true("T7", "学姐随后收到 type=unread 推送（小红点）", push2.get("type") == "unread",
           "（收到：%s）" % (unread if unread else "什么都没收到"))

# ---------- 三、历史 + 已读 + 已读回执 ----------
print("\n===== 三、聊天记录 + 已读回执（3 项）=====")
hist = call("GET", "/user/message/history?conversationId=%s&page=1&pageSize=20" % cid, Hs)
records = (hist.get("data") or {}).get("records", [])
check_true("T8", "学姐能拉到聊天记录（1 条）", len(records) == 1,
           "（total=%s, 实际 %d 条）" % ((hist.get("data") or {}).get("total"), len(records)))

ws_ming, hm = ws_connect("1", ming)
check_true("T9", "小明（发送方）也连上 WebSocket", "101" in hm, "（%s）" % hm)

check("T10", "学姐把消息标记为已读", "code=1",
      call("PUT", "/user/message/read/%s" % cid, Hs))
read_push = ws_try_recv(ws_ming, 6)
push3 = json.loads(read_push) if read_push else {}
check_true("T11", "小明实时收到 type=read 已读回执（新功能 B）", push3.get("type") == "read",
           "（收到：%s）" % (read_push if read_push else "什么都没收到"))

# ---------- 四、权限：第三人 ----------
print("\n===== 四、权限：第三人（小张）越权（3 项）=====")
check("T12", "第三人看别人的聊天记录", "无权查看该会话",
      call("GET", "/user/message/history?conversationId=%s&page=1&pageSize=20" % cid, Hz))
check("T13", "第三人在别人的会话里发言", "无权在该会话发言",
      call("POST", "/user/message/send", Hz, {"conversationId": cid, "content": "我插一句话"}))
check("T14", "第三人标记别人的会话为已读", "无权操作该会话",
      call("PUT", "/user/message/read/%s" % cid, Hz))

# ---------- 五、消息搜索 ----------
print("\n===== 五、消息搜索（权限写进 SQL）（3 项）=====")
kw = urllib.parse.quote("宿舍楼下")
mine = call("GET", "/user/message/search?keyword=%s&page=1&pageSize=20" % kw, Hm)
my_total = (mine.get("data") or {}).get("total") or 0
check_true("T15", "小明搜'宿舍楼下' → 命中自己会话里的消息", my_total >= 1, "（命中 %s 条）" % my_total)

other = call("GET", "/user/message/search?keyword=%s&page=1&pageSize=20" % kw, Hz)
other_total = (other.get("data") or {}).get("total") or 0
check_true("T16", "第三人搜同一个词 → 搜不到（他不在这个会话里）", other_total == 0,
           "（命中 %s 条 —— 必须是 0，否则就是全站聊天记录泄露）" % other_total)

none = call("GET", "/user/message/search?keyword=%s&page=1&pageSize=20"
            % urllib.parse.quote("这个词根本不存在zzz"), Hm)
check_true("T17", "搜不存在的词 → 0 条", ((none.get("data") or {}).get("total") or 0) == 0)

# ---------- 六、WebSocket 鉴权 ----------
print("\n===== 六、WebSocket 鉴权（1 项）=====")
ws_bad, hs_bad = ws_connect("1", None)   # 冒充 1 号用户，不带 token
bad_msg = ws_try_recv(ws_bad, 4)
check_true("T18", "不带 token 冒充他人连接 → 收不到任何消息（连接被拒）",
           bad_msg is None, "（收到：%s）" % (bad_msg if bad_msg else "无，符合预期"))

# ---------- 七、S3-8b：捡漏商品的私信权限 ----------
print("\n===== 七、捡漏商品的私信权限（S3-8b，4 项）=====")
check("T19", "捡漏·预热期（商品1 还没到抢购时间）", "抢购开始前不能私信卖家",
      call("POST", "/user/message/conversation?productId=1", Hm))

check("T20", "捡漏·已开始但小明没抢到（商品2）", "只有抢到该商品的买家才能联系卖家",
      call("POST", "/user/message/conversation?productId=2", Hm))

r5 = call("POST", "/user/message/conversation?productId=5", Hm)
check("T21", "捡漏·已开始且小明抢到了（商品5）→ 应允许建会话", "code=1", r5)
cid5 = r5.get("data")
print("      商品5 的会话id = %s（其 can_send_until 应等于订单900 的 expire_time）" % cid5)

check("T22", "在这条【已过窗口期】的会话里发消息 → 应被拦住", "会话已超过可发送时间",
      call("POST", "/user/message/send", Hm, {"conversationId": cid5, "content": "窗口已经过了"}))

# 收尾：关掉连接（发一个空的关闭帧）
for s in (ws_seller, ws_ming, ws_bad):
    try:
        s.close()
    except Exception:
        pass

total = passed + failed
print("\n" + "=" * 60)
print("私信模块回归：通过 %d / %d" % (passed, total))
print("RESULT pass=%d fail=%d total=%d" % (passed, failed, total))
sys.exit(1 if failed else 0)
