# -*- coding: utf-8 -*-
"""删掉"我的"页面里那张多余的信用分规则卡片（它排在"退出登录"菜单之后）"""
import io, sys

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\static\ui\user\app.js"

with io.open(PATH, "r", encoding="utf-8") as f:
    src = f.read()
before = len(src)

# 1) 定位 logout 菜单
logout = src.find('id="logout"')
if logout < 0:
    print("!! 找不到 logout 菜单"); sys.exit(1)

# 2) logout 之后出现的那张 rule-card
card = src.find("'<div class=\"rule-card\">' +", logout)
if card < 0:
    print("!! logout 之后没有 rule-card，可能已经删过了"); sys.exit(0)

# 3) 卡片结束标志：紧随其后的 `'</div>';`（viewMe 的 innerHTML 结尾）
endmark = "'</div>';"
end = src.find(endmark, card)
if end < 0:
    print("!! 找不到卡片结束标志"); sys.exit(1)

removed = src[card:end]
# 保留下 ";": 把卡片前面那个 `'</div>' +` 的 + 换成 ;
prev = src.rfind("'</div>' +", logout, card)
if prev > 0:
    src = src[:prev] + "'</div>';" + src[end + len(endmark):]
    note = "并把前面的 + 换成 ;"
else:
    src = src[:card] + src[end + len(endmark):]
    note = ""

with io.open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("已删除卡片 %d 字符，%s" % (len(removed), note))
print("文件：%d -> %d 字符" % (before, len(src)))
