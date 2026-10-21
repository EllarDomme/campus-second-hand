# -*- coding: utf-8 -*-
"""
修 message-test.py 的 T6/T7：推送帧的【顺序不保证】，测试却假设了固定顺序

原来：
    msg    = ws_try_recv(ws_seller, 6)     # 期望第 1 帧是 message
    unread = ws_try_recv(ws_seller, 6)     # 期望第 2 帧是 unread
服务端发消息后会推两帧（message + unread），但两帧走的是不同的发送路径，
到达顺序没有保证。顺序反过来时，T6 拿到 unread、T7 拿到 message —— 两项一起挂。

改成：收集最多 2 帧（8 秒内），断言"两种 type 都出现过"，与顺序无关。
"""
import io

PATH = r"E:\DeskTop\campus-second-hand\tools\message-test.py"

with io.open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

OLD = '''msg = ws_try_recv(ws_seller, 6)
push1 = json.loads(msg) if msg else {}
check_true("T6", "学姐实时收到 type=message 推送", push1.get("type") == "message",
           "（收到：%s）" % (msg if msg else "什么都没收到"))

unread = ws_try_recv(ws_seller, 6)
push2 = json.loads(unread) if unread else {}
check_true("T7", "学姐随后收到 type=unread 推送（小红点）", push2.get("type") == "unread",
           "（收到：%s）" % (unread if unread else "什么都没收到"))'''

NEW = '''# 服务端会推两帧：type=message（聊天内容）和 type=unread（小红点）。
# 这两帧走不同的发送路径，【到达顺序没有保证】——
# 所以不能"第 1 帧断言 message、第 2 帧断言 unread"，否则顺序反过来就双双失败。
# 正确做法：把 8 秒内收到的帧都收集起来，只断言"两种类型都出现过"。
push_frames = []
deadline = time.time() + 8
while len(push_frames) < 2 and time.time() < deadline:
    remaining = max(0.5, deadline - time.time())
    frame = ws_try_recv(ws_seller, remaining)
    if not frame:
        break
    try:
        push_frames.append(json.loads(frame))
    except Exception:
        pass

push_types = [p.get("type") for p in push_frames]
seen = "（收到 %d 帧：%s）" % (len(push_frames), push_types if push_types else "什么都没收到")

check_true("T6", "学姐实时收到 type=message 推送", "message" in push_types, seen)
check_true("T7", "学姐随后收到 type=unread 推送（小红点）", "unread" in push_types, seen)'''

if OLD not in src:
    raise SystemExit("!! 没匹配到 T6/T7 的代码")

src = src.replace(OLD, NEW, 1)

# 确保 import time 存在
if "\nimport time" not in src:
    src = src.replace("import socket", "import socket\nimport time", 1)
    print("[2] 补了 import time")

with io.open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print('[1] T6/T7 已改成不看顺序只看两帧' )

# 语法检查
import py_compile
py_compile.compile(PATH, doraise=True)
print("[3] 语法检查通过")
