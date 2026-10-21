# -*- coding: utf-8 -*-
"""
演示数据补全：
  1. 给空掉的三个分类（交通工具 5 / 服饰鞋包 6 / 其他 8）各补 3 件真实商品
  2. 把回归测试夹具（product 100~107）的 audit_status 改成 2（驳回），
     这样它们不会出现在热门榜/首页的演示数据里 ——
     但 sql/test-reset*.sql 会把它们改回 1，所以回归测试不受影响
"""
import io

GEN = r"E:\DeskTop\campus-second-hand\tools\gen-demo-data.py"

with io.open(GEN, "r", encoding="utf-8") as f:
    src = f.read()

# ---------- 1. 在 PRODUCTS 列表末尾追加 9 件 ----------
anchor = '''    (1027, 1007, 8, "【捡漏】Type-C 扩展坞（七合一）", 45.00, 2, 1, 0, 1, None, 1,
     "支持 HDMI + 读卡 + 三个 USB，一直用在笔记本上。"),
'''
add = anchor + '''    # --- 补全空分类：交通工具 / 服饰鞋包 / 其他 ---
    (1030, 1002, 5, "捷安特山地车 26 寸（前刹刚换）", 320.00, 3, 0, 0, 1, None, 9,
     "骑了两年，上个月刚换的前刹车皮，链条也上过油。带车锁。"),
    (1031, 1005, 5, "折叠自行车 20 寸 通勤用", 260.00, 2, 0, 0, 1, None, 6,
     "宿舍楼下停车太挤所以出，折叠起来能塞进柜子。"),
    (1032, 1007, 5, "【捡漏】电动滑板车（续航 20km）", 480.00, 2, 1, 0, 1, None, 1,
     "毕业带不走，捡漏价出。电池健康，充一次能跑 20 公里。"),
    (1033, 1002, 6, "羽绒服 男款 L 码（九成新）", 220.00, 2, 0, 0, 1, None, 12,
     "去年冬天买的，穿了不到十次，洗过一次。"),
    (1034, 1001, 6, "Nike 运动鞋 42 码", 180.00, 3, 0, 0, 1, None, 8,
     "跑步用的，鞋底磨损不大。介意二手鞋的别拍。"),
    (1035, 1006, 6, "单肩帆布包（可放 14 寸电脑）", 35.00, 2, 0, 0, 1, None, 5,
     "买大了一号，用过两次，很能装。"),
    (1036, 1003, 8, "多肉植物 3 盆（含花盆）", 25.00, 1, 0, 0, 1, None, 7,
     "宿舍阳台养不下了，三盆带盆一起出，好养活。"),
    (1037, 1004, 8, "手办 黏土人（拆封未摆）", 160.00, 2, 0, 0, 1, None, 10,
     "拆封检查过没摆出来，盒子还在。"),
    (1038, 1009, 8, "懒人沙发 可拆洗", 90.00, 2, 0, 0, 1, None, 3,
     "搬宿舍嫌占地方，套子可以拆下来洗。"),
'''
if anchor in src:
    src = src.replace(anchor, add, 1)
    print("[1] 已补 9 件商品（交通工具 3 / 服饰鞋包 3 / 其他 3）")
else:
    print("!! 没找到插入点")
    raise SystemExit(1)

# ---------- 2. 把测试夹具从演示数据里藏掉 ----------
anchor2 = 'w("UPDATE user SET avatar = CONCAT(\'https://picsum.photos/seed/avatar\', id, \'/200/200\');")'
add2 = anchor2 + '''
# 回归测试夹具（product 100~107）不要出现在演示数据里：
# 它们由 sql/test-reset*.sql 维护，会自己改回 audit_status = 1，所以回归测试不受影响
w("-- 隐藏回归测试夹具（回归脚本会自己改回来）")
w("UPDATE product SET audit_status = 2, audit_note = '测试夹具，运行时由回归脚本恢复'")
w(" WHERE id BETWEEN 100 AND 107;")'''
if anchor2 in src:
    src = src.replace(anchor2, add2, 1)
    print("[2] 已加：把 product 100~107 藏掉")
else:
    print("  (没找到头像 UPDATE 的锚点，跳过)")

with io.open(GEN, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("生成器已更新")
