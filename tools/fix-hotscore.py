# -*- coding: utf-8 -*-
"""修 calcHotScore 的 A1（两个 if 打架）+ 顺手把 A2 的注释改成和代码一致"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\java\com\campus\service\impl\ReportServiceImpl.java"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()
before = len(src)

# ---------- 1. A1：删掉"两个分支都赋 0"的 if/else ----------
old_a1 = """        int soldScore;
        if(sold != null && sold == 1){
            soldScore = 50;
        }
        if(sold == null || sold == 0){
            soldScore = 0;
        }else{
            soldScore = 0;
        }"""

new_a1 = """        // sold == 1 → 50 分，否则 0 分（默认值就是 0，不需要再写一个 if 去赋 0）
        // ⚠️ 用 Integer.valueOf(1).equals(sold) 而不是 sold == 1：
        //    后者会触发自动拆箱，sold 为 null 时直接 NPE —— 而 null 恰恰是可能出现的。
        int soldScore = Integer.valueOf(1).equals(sold) ? 50 : 0;"""

if old_a1 in src:
    src = src.replace(old_a1, new_a1, 1)
    print("[1] A1 已修：删掉死分支，改成一行三元表达式")
else:
    print("!! A1 没匹配到，需人工检查")
    import sys
    sys.exit(1)

# ---------- 2. A2 的注释：和代码对齐 ----------
old_note = "        //   · elapsedDays 可能为 null（老数据没有 online_time），当 0 处理"
new_note = """        //   · elapsedDays 为 null 表示"不知道挂了多久"（老数据没有 online_time）
        //     → 保守给 0 分，不能因为"不知道"就奖励它，否则脏数据会刷上榜"""
if old_note in src:
    src = src.replace(old_note, new_note, 1)
    print("[2] A2 注释已对齐代码（null → 0 分）")
else:
    print("  (A2 注释没匹配到，跳过)")

# ---------- 3. A2 的 null 分支加个说明 ----------
old_null = """        double timeScore;
        if(elapsedDays == null){
            timeScore = 0;
        }else{"""
new_null = """        double timeScore;
        if(elapsedDays == null){
            timeScore = 0;      // 见上面：不知道挂了多久 → 保守 0 分
        }else{"""
if old_null in src:
    src = src.replace(old_null, new_null, 1)
    print("[3] A2 的 null 分支加了说明")

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("完成：%d -> %d 字符" % (before, len(src)))
