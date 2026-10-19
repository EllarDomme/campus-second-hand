# -*- coding: utf-8 -*-
"""
生成「演示数据」SQL（让系统一打开就像在运行）

设计要点：
  1. 用独立的 ID 段（user/product 1000+，trade_order 2000+），
     绝不碰回归测试的夹具 ID（user 1~19、product 100~107、order 900+）
  2. 脚本开头先 DELETE 自己的 ID 段 → 可以反复执行（幂等）
  3. 时间用 NOW() - INTERVAL 生成，看起来是"最近一个月真实发生的"
  4. 覆盖全部业务：所有状态都有，看板和列表不会是空的

用法：py -3 tools/gen-demo-data.py  → 输出 sql/seed-demo-data.sql
"""
import os
from datetime import datetime

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "sql", "seed-demo-data.sql")

L = []
def w(s=""):
    L.append(s)

# ---------------------------------------------------------------- 用户
USERS = [
    # id, 昵称, 手机, 学号, 角色, 信用分
    (1000, "林小满", "13800001000", "2021010101", 0, 128),
    (1001, "陈知行", "13800001001", "2021010102", 0, 115),
    (1002, "苏念", "13800001002", "2021010103", 0, 106),
    (1003, "周予安", "13800001003", "2021020104", 0, 100),
    (1004, "何清越", "13800001004", "2021020105", 0, 132),
    (1005, "郑亦", "13800001005", "2021030106", 0, 98),
    (1006, "吴星野", "13800001006", "2021030107", 0, 110),
    (1007, "许南乔", "13800001007", "2022010108", 0, 120),
    (1008, "沈书言", "13800001008", "2022010109", 0, 104),
    (1009, "韩风", "13800001009", "2022020110", 1, 140),
    (1010, "李跑跑", "13800001010", "2022020111", 1, 126),
    (1011, "王大力", "13800001011", "2022030112", 1, 112),
    (1012, "赵一诺", "13800001012", "2023010113", 0, 100),
    (1013, "钱屿", "13800001013", "2023010114", 0, 96),
    (1014, "孙照", "13800001014", "2023020115", 0, 135),
    (1015, "李明", "13800001015", "2023020116", 0, 108),
    (1016, "顾清", "13800001016", "2023030117", 0, 118),
    (1017, "唐婉", "13800001017", "2023030118", 0, 102),
    (1018, "谢言", "13800001018", "2024010119", 0, 62),    # 信用分偏低，看板能命中
    (1019, "罗宇", "13800001019", "2024010120", 0, 100),
]

# ---------------------------------------------------------------- 商品
# (id, sellerId, categoryId, 标题, 价格, 成色, 捡漏?, 状态, 审核, 审核备注, 天数前, 描述)
PRODUCTS = [
    (1000, 1000, 1, "考研数学复习全书（2025版·数一）", 35.00, 2, 0, 0, 1, None, 24,
     "九成新，只做了前三章的基础题，其余干净。附赠同系列习题册。"),
    (1001, 1001, 1, "线性代数辅导讲义（李永乐）", 18.00, 2, 0, 0, 1, None, 21,
     "大二下用过一学期，有少量铅笔笔记，可以用橡皮擦掉。"),
    (1002, 1002, 2, "iPad Air 4 64G 天空蓝", 1750.00, 3, 0, 1, 1, None, 18,
     "自用两年，屏幕无划痕，电池健康 89%。带原装保护壳，不含笔。"),
    (1003, 1003, 2, "罗技 MX Master 3 无线鼠标", 320.00, 2, 0, 0, 1, None, 16,
     "用了一年不到，滚轮和侧键都正常。附充电线。"),
    (1004, 1004, 2, "Kindle Paperwhite 4 8G", 380.00, 2, 0, 2, 1, None, 15,
     "看书神器，换了新机器所以出。屏幕有轻微压痕，不影响阅读。"),
    (1005, 1005, 3, "宿舍折叠桌（可床上用）", 45.00, 3, 0, 0, 1, None, 14,
     "毕业带不走。桌面有一处小磕碰，不影响使用。"),
    (1006, 1006, 3, "小米台灯 Pro（可调色温）", 89.00, 2, 0, 0, 1, None, 13,
     "无级调光，考研期间买的，现在用不上了。"),
    (1007, 1007, 3, "保温杯 500ml 不锈钢", 25.00, 1, 0, 0, 1, None, 12,
     "全新未拆封，买多了闲置。"),
    (1008, 1008, 4, "尤克里里 23寸 桃花芯", 168.00, 2, 0, 0, 1, None, 11,
     "社团活动时买的，会弹几首简单曲子。附琴包和调音器。"),
    (1009, 1009, 4, "吉他变调夹 + 拨片套装", 20.00, 1, 0, 0, 1, None, 10,
     "全新，买琴送多了。"),
    (1010, 1010, 5, "羽毛球拍 尤尼克斯 双拍套装", 130.00, 3, 0, 0, 1, None, 9,
     "体育课用过一学期，拍面完好，握把胶换过。"),
    (1011, 1011, 5, "瑜伽垫 10mm 加厚防滑", 35.00, 2, 0, 0, 1, None, 9,
     "用了几次，一直卷着收着，很干净。"),
    (1012, 1012, 6, "高等数学 同济第七版 上下册", 28.00, 3, 0, 0, 1, None, 8,
     "书角有磨损，笔记比较多，适合不介意的人。"),
    (1013, 1013, 6, "大学物理实验报告册（全新）", 12.00, 1, 0, 0, 1, None, 7,
     "买多了，一页没写过。"),
    (1014, 1014, 7, "充电宝 20000mAh 双向快充", 78.00, 2, 0, 0, 1, None, 6,
     "出差用过几次，容量还很好。带双口。"),
    (1015, 1015, 7, "USB-C 数据线 1m（两条）", 15.00, 1, 0, 0, 1, None, 5,
     "全新未拆，多买了。"),
    (1016, 1016, 8, "机械键盘 87键 红轴", 199.00, 2, 0, 0, 1, None, 5,
     "键帽无油，手感很好，送一套备用键帽。"),
    (1017, 1017, 8, "显示器支架 单臂 可升降", 95.00, 2, 0, 0, 1, None, 4,
     "装了两个月，换了双屏所以出。附安装螺丝。"),
    (1018, 1018, 1, "考研英语真题（2010-2024）", 22.00, 3, 0, 0, 1, None, 3,
     "做过一部分，答案页有铅笔记号。"),
    (1019, 1019, 2, "AirPods 2 代（右耳单只）", 120.00, 3, 0, 0, 1, None, 3,
     "左耳丢了只剩右耳，功能正常。价格按单只算。"),
    # 待审核的（管理员有事可做）
    (1020, 1000, 3, "多功能收纳盒（三层）", 30.00, 2, 0, 0, 0, None, 1,
     "宿舍整理用，很好用。"),
    (1021, 1001, 5, "篮球 斯伯丁 7号", 88.00, 3, 0, 0, 0, None, 1,
     "打了一学期，气足，皮面有磨损。"),
    (1022, 1002, 4, "电子琴 61键（含支架）", 260.00, 2, 0, 0, 0, None, 0,
     "搬家带不走，音色正常，附支架和琴包。"),
    # 已驳回的
    (1023, 1003, 7, "全新原装充电器（来源不明）", 999.00, 1, 0, 0, 2,
     "图片与描述不符，且价格明显异常，请补充实拍图与购买凭证", 2,
     "朋友送的，没有发票。"),
    # 捡漏商品：已开抢 + 未开抢
    (1024, 1004, 2, "【捡漏】小米移动电源 10000mAh", 29.00, 2, 1, 0, 1, None, 2,
     "捡漏专场，手慢无。功能一切正常，只是外壳有轻微划痕。"),
    (1025, 1005, 1, "【捡漏】四六级词汇书（未拆封）", 9.90, 1, 1, 0, 1, None, 1,
     "全新未拆封，捡漏价出。"),
    (1026, 1006, 3, "【捡漏】宿舍衣架 20 个装", 12.00, 2, 1, 0, 1, None, 1,
     "搬宿舍剩的，捡漏价打包出。"),
    (1027, 1007, 8, "【捡漏】Type-C 扩展坞（七合一）", 45.00, 2, 1, 0, 1, None, 1,
     "支持 HDMI + 读卡 + 三个 USB，一直用在笔记本上。"),
    # 已售罄 / 已下架
    (1028, 1008, 6, "概率论与数理统计（浙大版）", 20.00, 3, 0, 2, 1, None, 20,
     "考完试就出，笔记多但很工整。"),
    (1029, 1009, 5, "跳绳（计数款）", 15.00, 3, 0, 3, 1, None, 19,
     "把手有点磨损，能正常用。已下架。"),
]

# ---------------------------------------------------------------- 订单
# (id, orderNo, sellerId, buyerId, productId, realPrice, dealType, source, status, payStatus,
#  天数前, 交付地点/地址)
ORDERS = [
    # 已完成（本月）—— 让"本月成交"有数字
    (2000, "DM20261010001", 1000, 1004, 1000, 35.00, 0, 1, 2, 1, 17, "3号宿舍楼下"),
    (2001, "DM20261011002", 1001, 1007, 1001, 18.00, 0, 1, 2, 1, 16, "图书馆门口"),
    (2002, "DM20261012003", 1002, 1012, 1003, 320.00, 1, 1, 2, 1, 15, None),
    (2003, "DM20261013004", 1003, 1008, 1005, 45.00, 0, 1, 2, 1, 14, "5号宿舍楼下"),
    (2004, "DM20261014005", 1004, 1014, 1006, 89.00, 0, 1, 2, 1, 13, "教学楼A座"),
    (2005, "DM20261015006", 1006, 1016, 1008, 168.00, 1, 1, 2, 1, 11, None),
    (2006, "DM20261016007", 1007, 1005, 1009, 20.00, 0, 1, 2, 1, 10, "南门快递点"),
    (2007, "DM20261017008", 1010, 1000, 1010, 130.00, 0, 1, 2, 1, 8, "体育馆门口"),
    (2008, "DM20261018009", 1011, 1001, 1011, 35.00, 0, 1, 2, 1, 6, "4号宿舍楼下"),
    (2009, "DM20261019010", 1012, 1002, 1012, 28.00, 0, 1, 2, 1, 4, "图书馆门口"),
    (2010, "DM20261020011", 1014, 1003, 1014, 78.00, 1, 1, 2, 1, 2, None),
    (2011, "DM20261021012", 1016, 1009, 1016, 199.00, 0, 1, 2, 1, 1, "6号宿舍楼下"),
    # 今日成交（让"今日成交"有数字）
    (2012, "DM20261022013", 1017, 1010, 1017, 95.00, 1, 1, 2, 1, 0, None),
    (2013, "DM20261022014", 1015, 1011, 1015, 15.00, 0, 1, 2, 3, 0, "北门超市门口"),

    # 待交易（已支付，买卖双方已确认，等收货）
    (2014, "DM20261022015", 1005, 1013, 1024, 29.00, 0, 3, 1, 1, 0, "3号宿舍楼下"),
    (2015, "DM20261022016", 1006, 1017, 1026, 12.00, 0, 3, 1, 1, 0, "1号宿舍楼下"),

    # 待确认（刚下单，还没人确认）
    (2016, "DM20261022017", 1008, 1018, 1027, 45.00, 0, 3, 0, 1, 0, "4号宿舍楼下"),
    (2017, "DM20261022018", 1013, 1004, 1013, 12.00, 0, 1, 0, 1, 0, "图书馆门口"),

    # 已取消
    (2018, "DM20261018019", 1009, 1015, 1018, 22.00, 0, 1, 3, 2, 5, "南门"),
    (2019, "DM20261017020", 1019, 1006, 1019, 120.00, 0, 1, 3, 2, 7, "北门"),
    (2020, "DM20261015021", 1007, 1016, 1025, 9.90, 0, 3, 3, 2, 9, "2号宿舍楼下"),

    # 超时取消（买家超时未确认，扣了信用分）
    (2021, "DM20261014022", 1011, 1019, 1020, 30.00, 0, 1, 4, 0, 12, "5号楼下"),
    (2022, "DM20261013023", 1012, 1013, 1021, 88.00, 0, 1, 4, 0, 14, "体育馆"),
    (2023, "DM20261012024", 1014, 1006, 1022, 260.00, 0, 1, 4, 0, 15, "6号楼下"),
]

# ---------------------------------------------------------------- 跑腿单（对应 deal_type=1 的订单）
# (tradeOrderId, runnerId, pick, receive, fee, status, 天数前)
DELIVERIES = [
    (2002, 1009, "3号宿舍楼下", "教学楼A座", 5.00, 3, 15),
    (2005, 1010, "图书馆门口", "8号宿舍楼下", 7.00, 3, 11),
    (2010, 1011, "5号宿舍楼下", "教学楼B座", 5.00, 3, 2),
    (2012, 1009, "2号宿舍楼下", "实验楼", 6.00, 2, 0),      # 取货中
    (2014, None, "3号宿舍楼下", "图书馆门口", 5.00, 0, 0),  # 待接单
    (2015, None, "1号宿舍楼下", "教学楼A座", 5.00, 0, 0),   # 待接单
    (2018, 1010, "南门", "4号宿舍楼下", 5.00, 4, 5),        # 已取消
]

# ---------------------------------------------------------------- 评价
# (orderId, productId, fromUserId, toUserId, star, 内容, 天数前)
REVIEWS = [
    (2000, 1000, 1004, 1000, 5, "书很新，卖家还送了笔记，非常满意！", 16),
    (2001, 1001, 1007, 1001, 5, "讲义很干净，见面也很准时。", 15),
    (2003, 1005, 1008, 1003, 4, "桌子基本没问题，就是有点灰，擦一下就好。", 13),
    (2004, 1006, 1014, 1004, 5, "台灯很好用，卖家描述准确。", 12),
    (2006, 1009, 1005, 1007, 5, "拨片套装齐全，态度也好。", 9),
    (2007, 1010, 1000, 1010, 4, "球拍还行，握把胶有点旧。", 7),
    (2008, 1011, 1001, 1011, 5, "瑜伽垫很新，价格实在。", 5),
    (2009, 1012, 1002, 1012, 2, "笔记比描述的多很多，书页还有撕裂，有点失望。", 3),
    (2011, 1016, 1009, 1016, 5, "键盘手感很好，键帽确实没油。", 0),
]

# ---------------------------------------------------------------- 议价
# (productId, buyerId, sellerId, buyerOffer, sellerCounter, status, 天数前)
BARGAINS = [
    (1002, 1005, 1002, 1600.00, None, 0, 1),        # 待卖家回应
    (1003, 1006, 1003, 280.00, 300.00, 2, 2),       # 卖家已还价
    (1014, 1012, 1014, 60.00, None, 4, 3),          # 已拒绝
    (1016, 1008, 1016, 180.00, None, 5, 6),         # 已过期
    (1017, 1007, 1017, 80.00, None, 3, 2),          # 已接受
    # 下面两条挂在演示账号上，保证"H5 快捷登录后能立刻看到议价"
    (1003, 1000, 1001, 300.00, None, 0, 0),         # 林小满 出价 陈知行 的鼠标（待卖家回应）
    (1000, 1001, 1000, 30.00, 32.00, 2, 1),         # 陈知行 出价 林小满 的书（卖家已还价）
]

# ---------------------------------------------------------------- 会话与消息
CONVERSATIONS = [
    (1000, 1000, 1002, 1005, "在的，1600 可以吗？", 1),
    (1001, 1001, 1003, 1006, "290 行不行，我今天就能来拿", 2),
    (1002, 1002, 1014, 1012, "充电宝还有吗", 3),
    (1003, 1003, 1016, 1008, "键盘能便宜点吗", 5),
    # 下面两条挂在演示账号上（H5 快捷登录后能立刻看到聊天）
    (1004, 1003, 1000, 1001, "300 可以的话我下午就来拿", 0),   # 林小满 ↔ 陈知行（鼠标）
    (1005, 1000, 1001, 1000, "32 的话我今天就拍", 1),           # 陈知行 ↔ 林小满（高数书）
]
MESSAGES = [
    (1000, 1005, 1002, "你好，iPad 还在吗？", 3),
    (1000, 1002, 1005, "在的，有意可以约时间看货。", 3),
    (1000, 1005, 1002, "1600 可以吗？可以我今天就来。", 2),
    (1000, 1002, 1005, "在的，1600 可以吗？", 1),
    (1001, 1006, 1003, "鼠标还有吗？", 2),
    (1001, 1003, 1006, "有的，成色很好。", 2),
    (1001, 1006, 1003, "290 行不行，我今天就能来拿", 2),
    (1002, 1012, 1014, "充电宝还有吗", 3),
    (1002, 1014, 1012, "在，容量很足。", 3),
    (1003, 1008, 1016, "键盘能便宜点吗", 5),
    (1003, 1016, 1008, "190 最低了，键帽都是新的。", 5),
    # 演示会话 1004：林小满 找 陈知行 买鼠标
    (1004, 1000, 1001, "你好，鼠标还在吗？成色怎么样？", 1),
    (1004, 1001, 1000, "在的，用了一年不到，滚轮和侧键都正常。", 1),
    (1004, 1000, 1001, "能便宜点吗，我出 300。", 0),
    (1004, 1001, 1000, "300 可以的话我下午就来拿", 0),
    # 演示会话 1005：陈知行 找 林小满 买高数书
    (1005, 1001, 1000, "高数书还有吗？我看到上面笔记挺多的。", 1),
    (1005, 1000, 1001, "有的，笔记是我自己整理的，重点都标出来了。", 1),
    (1005, 1001, 1000, "那 30 包邮行吗？", 1),
    (1005, 1000, 1001, "32 吧，我给你送到宿舍楼下。", 1),
    (1005, 1001, 1000, "32 的话我今天就拍", 1),
]

# ---------------------------------------------------------------- 投诉
COMPLAINTS = [
    # (productId, orderId, 投诉人, 被投诉人, 类型, 原因, 内容, 状态, 结果, 备注, 天数前)
    (1012, 2009, 1002, 1012, 1, 1, "书页有多处撕裂，和描述里说的'笔记比较多'完全不是一回事。", 2, 1,
     "经核实商品损坏情况属实，投诉成立，已扣被投诉人 10 分。", 2),
    (1006, 2004, 1014, 1004, 1, 4, "约好晚上七点见面，卖家一直没出现，也联系不上。", 2, 2,
     "被投诉人提供了当日聊天记录，系双方时间沟通有误，投诉不成立。", 10),
    (1024, 2014, 1013, 1005, 0, 2, "捡漏商品的图和实物差距比较大，怀疑用了网图。", 0, None, None, 0),
]

# ---------------------------------------------------------------- 收藏
FAVORITES = [
    (1000, 1002), (1000, 1003), (1000, 1014), (1000, 1016),
    (1001, 1000), (1001, 1002), (1001, 1024),
    (1004, 1003), (1004, 1016), (1004, 1017),
    (1005, 1002), (1005, 1006), (1005, 1024),
    (1006, 1000), (1006, 1003), (1006, 1008),
    (1007, 1002), (1007, 1025),
    (1008, 1003), (1008, 1016), (1008, 1027),
    (1012, 1006), (1012, 1014),
    (1013, 1002), (1013, 1017),
    (1014, 1003), (1014, 1008), (1014, 1016),
    (1015, 1010), (1015, 1024),
    (1016, 1002), (1016, 1026),
    (1017, 1016), (1017, 1019),
    (1018, 1024), (1018, 1025), (1018, 1026),
    (1019, 1003),
]

# ---------------------------------------------------------------- 信用分流水
# (userId, 变动, 变动后, 原因类型, refId, 备注, 天数前)
CREDIT_LOGS = [
    (1000, 2, 128, 3, 2000, "交易完成", 17),
    (1004, 2, 132, 3, 2003, "交易完成", 14),
    (1004, 1, 133, 3, 2003, "收到好评", 13),
    (1014, 2, 130, 3, 2004, "交易完成", 13),
    (1007, 2, 120, 3, 2001, "交易完成", 16),
    (1012, -2, 106, 4, 2009, "收到差评", 3),
    (1012, -10, 62, 6, 1000, "投诉成立", 2),
    (1019, -5, 100, 5, 2019, "交易超时未确认", 7),
    (1013, -5, 96, 5, 2021, "交易超时未确认", 12),
    (1006, -5, 110, 5, 2023, "交易超时未确认", 15),
    (1018, -5, 62, 5, 2017, "交易超时未确认", 6),
]


def esc(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def d(days_ago):
    """几天前的某个时间点（做成"下午或晚上"，更像真人操作）"""
    hours = [10, 11, 13, 14, 15, 16, 19, 20, 21][days_ago % 9]
    return "DATE_ADD(DATE_SUB(CURDATE(), INTERVAL %d DAY), INTERVAL %d HOUR)" % (days_ago, hours)


w("-- ============================================================")
w("-- 演示数据（让系统一打开就像在运行）")
w("-- 由 tools/gen-demo-data.py 生成，可重复执行")
w("-- ID 段：user/product 1000+，trade_order 2000+，绝不碰测试夹具")
w("-- ============================================================")
w("USE campus_second_hand;")
w("SET NAMES utf8mb4;")
w("")
w("-- ---------- 0. 先清掉自己的 ID 段（保证可重复执行）----------")
w("DELETE FROM message        WHERE conversation_id >= 1000;")
w("DELETE FROM conversation   WHERE id >= 1000;")
w("DELETE FROM credit_log     WHERE id >= 1000;")
w("DELETE FROM favorite       WHERE id >= 1000;")
w("DELETE FROM review         WHERE id >= 1000;")
w("DELETE FROM complaint      WHERE id >= 1000;")
w("DELETE FROM flash_sale_record WHERE id >= 1000;")
w("DELETE FROM delivery_order WHERE id >= 1000;")
w("DELETE FROM payment_record WHERE id >= 1000;")
w("DELETE FROM trade_order    WHERE id >= 2000;")
w("DELETE FROM bargain        WHERE id >= 1000;")
w("DELETE FROM product        WHERE id >= 1000;")
w("DELETE FROM user           WHERE id >= 1000;")
w("")

# ---------- 用户 ----------
w("-- ---------- 1. 用户（20 位同学，含 3 个跑腿员、1 个低信用）----------")
vals = []
for uid, nick, phone, sno, role, credit in USERS:
    vals.append("(%d, %s, %s, %s, %s, %d, 0, 100, %s, %s)" % (
        uid, esc("demo_openid_%d" % uid), esc(nick), esc(phone), esc(sno), role,
        d(30), d(30)))
w("INSERT INTO user (id, openid, nickname, phone, student_no, role, status, credit_score, create_time, update_time) VALUES")
w(",\n".join(vals) + ";")
w("")
w("-- 单独修正几个人的信用分（体现「有高有低」）")
for uid, nick, phone, sno, role, credit in USERS:
    if credit != 100:
        w("UPDATE user SET credit_score = %d WHERE id = %d;" % (credit, uid))
w("")

# ---------- 商品 ----------
w("-- ---------- 2. 商品（30 件，覆盖全部状态与捡漏）----------")
vals = []
for (pid, seller, cat, title, price, cond, flash, status, audit, note, days, desc) in PRODUCTS:
    imgs = '[\\"ui/img/%d.svg\\", \\"ui/img/%d.svg\\", \\"ui/img/%d.svg\\"]' % (
        (pid % 8) + 1, ((pid + 3) % 8) + 1, ((pid + 5) % 8) + 1)
    imgs = imgs.replace('\\\\"', '\\"')
    flash_start = "DATE_ADD(NOW(), INTERVAL 2 HOUR)" if (flash and pid % 2 == 0) else (
        "DATE_SUB(NOW(), INTERVAL 3 HOUR)" if flash else None)
    vals.append("(%d, %d, %d, %s, %.2f, %s, %s, %d, %d, %s, %s, %s, %d, %d, %s)" % (
        pid, seller, cat, esc(title), price,
        "'" + imgs + "'", esc(desc), cond, flash,
        (flash_start if flash_start else "NULL"),
        audit, esc(note), status,
        (pid * 17) % 180 + 20,
        d(days)))
w("INSERT INTO product (id, seller_id, category_id, title, price, images, description, condition_level,")
w("                    is_flash_sale, flash_start_time, audit_status, audit_note, status, view_count, create_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 订单 ----------
w("-- ---------- 3. 交易订单（24 笔，5 种状态都有）----------")
def sv(v):
    """可空值的 SQL 字面量：None → NULL"""
    return "NULL" if v is None else str(v)

vals = []
for (oid, ono, seller, buyer, pid, price, deal, src, status, pay, days, place) in ORDERS:
    pay_no = esc("DEMOPAY%d" % oid) if pay in (1, 2) else "NULL"
    pay_time = d(days) if pay in (1, 2) else "NULL"
    finish = d(days) if status == 2 else "NULL"
    if status == 3:
        cancel = esc("买家主动取消")
    elif status == 4:
        cancel = esc("超时未确认，系统自动取消")
    else:
        cancel = "NULL"
    expire = "DATE_ADD(%s, INTERVAL 30 MINUTE)" % d(days)
    # 自提填写地点；跑腿的地址不在这张表（存在 delivery_order 里）
    place_sql = esc(place) if (place and deal == 0) else "NULL"
    remark = esc("跑腿帮送，麻烦送到宿舍楼下") if deal == 1 else "NULL"
    vals.append("(%s, %s, %d, %d, %d, %.2f, %d, %d, %d, %d, %s, %s, %s, %s, %s, %s, %s, %s, %s)" % (
        sv(oid), esc(ono), seller, buyer, pid, price, deal, src, status, pay,
        pay_no, pay_time, place_sql, remark, expire, finish, cancel, d(days), d(days)))
w("INSERT INTO trade_order (id, order_no, seller_id, buyer_id, product_id, real_price, deal_type, order_source,")
w("                        status, pay_status, pay_no, pay_time, pickup_place, remark,")
w("                        expire_time, finish_time, cancel_reason, create_time, update_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 支付流水 ----------
w("-- ---------- 4. 支付流水 ----------")
vals = []
pid_auto = 1000
for (oid, ono, seller, buyer, pid, price, deal, src, status, pay, days, place) in ORDERS:
    if pay in (1, 2):
        vals.append("(%d, %d, %s, %s, %.2f, 1, 1, %s, %s)" % (
            pid_auto, oid, esc(ono), esc("DEMOPAY%d" % oid), price,
            esc("模拟支付成功"), d(days)))
        pid_auto += 1
    if pay == 2:      # 退过款的，再补一条退款流水
        vals.append("(%d, %d, %s, %s, %.2f, 2, 1, %s, %s)" % (
            pid_auto, oid, esc(ono), esc("DEMOREFUND%d" % oid), price,
            esc("订单取消退款"), d(days)))
        pid_auto += 1
if vals:
    w("INSERT INTO payment_record (id, order_id, order_no, pay_no, amount, pay_type, status, remark, create_time) VALUES")
    w(",\n".join(vals) + ";")
w("")

# ---------- 跑腿单 ----------
w("-- ---------- 5. 跑腿单（待接单 / 已接单 / 取货中 / 已送达 / 已取消）----------")
vals = []
did = 1000
for (oid, runner, pick, recv, fee, status, days) in DELIVERIES:
    accept = d(days) if runner and status >= 1 else None
    finish = d(days) if status == 3 else None
    vals.append("(%d, %d, %s, %s, %s, %.2f, %d, %s, %s, %s, %s)" % (
        did, oid, esc(runner), esc(pick), esc(recv), fee, status,
        (accept if accept else "NULL"), (finish if finish else "NULL"), d(days), d(days)))
    did += 1
w("INSERT INTO delivery_order (id, trade_order_id, runner_user_id, pick_address, receive_address,")
w("                             delivery_fee, status, accept_time, finish_time, create_time, update_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 评价 ----------
w("-- ---------- 6. 评价 ----------")
vals = []
rid = 1000
for (oid, pid, frm, to, star, content, days) in REVIEWS:
    vals.append("(%d, %d, %d, %d, %d, %d, %s, 0, %s)" % (rid, oid, pid, frm, to, star, esc(content), d(days)))
    rid += 1
w("INSERT INTO review (id, order_id, product_id, from_user_id, to_user_id, star, content, is_default, create_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 议价 ----------
w("-- ---------- 7. 议价（待回应 / 卖家还价 / 已接受 / 已拒绝 / 已过期）----------")
vals = []
bid = 1000
for (pid, buyer, seller, offer, counter, status, days) in BARGAINS:
    vals.append("(%d, %d, %d, %d, %.2f, %s, %d, NULL, %s, %s, %s)" % (
        bid, pid, buyer, seller, offer,
        ("%.2f" % counter if counter else "NULL"), status,
        "DATE_ADD(%s, INTERVAL 1 DAY)" % d(days), d(days), d(days)))
    bid += 1
w("INSERT INTO bargain (id, product_id, buyer_id, seller_id, buyer_offer, seller_counter_offer,")
w("                      bargain_status, order_id, expire_time, create_time, update_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 会话与消息 ----------
w("-- ---------- 8. 私信会话与消息 ----------")
w("-- 注意：conversation 表没有 last_message 字段（列表里的最后一条是 join message 表取出来的）")
vals = []
for (cid, pid, buyer, seller, last, days) in CONVERSATIONS:
    vals.append("(%d, %d, %d, %d, NULL, 0, NULL, %s, %s, %s)" % (
        cid, pid, buyer, seller, d(days), d(days), d(days)))
w("INSERT INTO conversation (id, product_id, buyer_id, seller_id, order_id, status, can_send_until,")
w("                           last_message_time, create_time, update_time) VALUES")
w(",\n".join(vals) + ";")
w("")
vals = []
mid = 1000
for (cid, frm, to, content, days) in MESSAGES:
    vals.append("(%d, %d, %d, %d, %s, 1, 0, %s)" % (mid, cid, frm, to, esc(content), d(days)))
    mid += 1
w("INSERT INTO message (id, conversation_id, from_user_id, to_user_id, content, msg_type, is_read, create_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 投诉 ----------
w("-- ---------- 9. 投诉工单（1 待处理 / 1 成立 / 1 不成立）----------")
vals = []
cid = 1000
for (pid, oid, comp, resp, ctype, reason, content, status, result, note, days) in COMPLAINTS:
    vals.append("(%d, %d, %d, %d, %d, %d, %d, %s, NULL, %d, %s, %s, %s, %s)" % (
        cid, pid, oid, comp, resp, ctype, reason, esc(content), status,
        (str(result) if result else "NULL"), esc(note), d(days), d(days)))
    cid += 1
w("INSERT INTO complaint (id, product_id, order_id, complainant_user_id, respondent_user_id,")
w("                        complaint_type, complaint_reason, content, images, handle_status,")
w("                        handle_result, handle_note, create_time, update_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 收藏 ----------
w("-- ---------- 10. 收藏（让商品卡片的「收藏人数」有数字）----------")
vals = []
fid = 1000
for (uid, pid) in FAVORITES:
    vals.append("(%d, %d, %d, %s)" % (fid, uid, pid, d(10)))
    fid += 1
w("INSERT INTO favorite (id, user_id, product_id, create_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 信用分流水 ----------
w("-- ---------- 11. 信用分流水 ----------")
vals = []
clid = 1000
for (uid, change, after, rtype, ref, remark, days) in CREDIT_LOGS:
    vals.append("(%d, %d, %d, %d, %d, %s, %s, %s)" % (
        clid, uid, change, after, rtype, (ref if ref else "NULL"), esc(remark), d(days)))
    clid += 1
w("INSERT INTO credit_log (id, user_id, change_score, score_after, reason_type, ref_id, remark, create_time) VALUES")
w(",\n".join(vals) + ";")
w("")

# ---------- 捡漏记录（让参与人数有数字）----------
w("-- ---------- 12. 捡漏抢购记录（让「参与/抢到」有数字）----------")
vals = []
fsid = 1000
for pid, buyers in [(1024, [1013, 1018, 1005, 1019]), (1025, [1018, 1004, 1016]), (1026, [1015, 1017]), (1027, [1008])]:
    for i, b in enumerate(buyers):
        vals.append("(%d, %d, %d, %d, %s)" % (fsid, pid, b, 1 if i == 0 else 0, d(1)))
        fsid += 1
w("INSERT INTO flash_sale_record (id, product_id, user_id, is_success, create_time) VALUES")
w(",\n".join(vals) + ";")
w("")
w("SELECT '演示数据已写入' AS 结果;")
w("SELECT CONCAT('用户 ', (SELECT COUNT(*) FROM user), ' / 商品 ', (SELECT COUNT(*) FROM product),")
w("              ' / 订单 ', (SELECT COUNT(*) FROM trade_order), ' / 跑腿 ', (SELECT COUNT(*) FROM delivery_order),")
w("              ' / 评价 ', (SELECT COUNT(*) FROM review), ' / 投诉 ', (SELECT COUNT(*) FROM complaint),")
w("              ' / 收藏 ', (SELECT COUNT(*) FROM favorite)) AS 数据量;")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("已生成：", OUT)
print("行数：", len(L))
