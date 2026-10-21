# -*- coding: utf-8 -*-
"""S6-3 用户主页：加前端页面 + 路由 + 从商品详情进入"""

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\static\ui\user\app.js"

with open(PATH, "r", encoding="utf-8") as f:
    src = f.read()
before = len(src)

# ---------- 1. 路由 ----------
anchor = "    if (key === 'contact') return viewContact();"
if anchor in src:
    src = src.replace(anchor, anchor + "\n    if (key === 'profile') return viewProfile(arg);", 1)
    print("[1] 路由已加 #/profile/{userId}")
else:
    print("!! 没找到路由锚点")

# ---------- 2. 页面函数 ----------
PAGE = r'''
/* ======================= 用户主页（S6-3）======================= */
/**
 * 看别人的主页：信用分、评价统计、在卖什么、加入多久
 *
 * 数据结构来自 GET /user/user/profile/{userId}；评价来自 GET /user/review/user/{userId}。
 * 注意这个页面只显示"能公开的字段" —— 对方的学号/手机号/学生证照片不该出现在这里。
 */
async function viewProfile(userId) {
    if (!userId) { location.hash = '#/me'; return; }
    app.innerHTML = topBar('用户主页', true) +
        '<div class="h5-body"><div class="loading">加载中…</div></div>';

    let p;
    try {
        p = await uapi('GET', '/user/user/profile/' + userId);
    } catch (e) {
        app.querySelector('.h5-body').innerHTML =
            '<div class="h5-empty"><div class="art">' + icon('user') + '</div>' +
            '<b>看不到这个用户</b><div>' + esc(e.message || '可能不存在') + '</div></div>';
        return;
    }

    const isMe = Number(p.userId) === Number(U.info().id);
    const trust = p.goodReviewCount > p.badReviewCount ? '信誉良好' :
                  (p.badReviewCount > 0 ? '有差评记录' : '暂无评价');
    const trustCls = p.goodReviewCount > p.badReviewCount ? 'ok' : (p.badReviewCount > 0 ? 'warn' : 'flat');

    app.innerHTML = topBar('用户主页', true) +
        '<div class="h5-body">' +

        // 顶部：头像 + 昵称 + 角色 + 信用分
        '<div class="me-card">' +
        '  <div class="n">' + (p.avatar
            ? '<img src="' + esc(String(p.avatar).replace(/^\/+/, '/')) + '" style="width:48px;height:48px;border-radius:50%;vertical-align:middle;margin-right:10px;border:2px solid rgba(255,255,255,.6)" onerror="imgFallback(this)">'
            : '') + esc(p.nickname || '同学') + '</div>' +
        '  <div class="s">' +
        (p.role === 1 ? '🛵 跑腿员 · ' : '') +
        '加入平台 ' + (p.joinedDays || 0) + ' 天' +
        '</div>' +
        '  <div class="credit">' +
        '    <div><b>' + (p.creditScore === undefined ? '-' : p.creditScore) + '</b><span>信用分</span></div>' +
        '    <div><b>' + (p.goodReviewCount || 0) + '</b><span>好评</span></div>' +
        '    <div><b>' + (p.badReviewCount || 0) + '</b><span>差评</span></div>' +
        '    <div><b>' + (p.onSaleCount || 0) + '</b><span>在卖</span></div>' +
        '  </div>' +
        '</div>' +

        // 信誉小结
        '<div class="rule-card" style="margin-bottom:12px">' +
        '  <div class="rule-head">' + icon('star', 15) + ' 交易信誉</div>' +
        '  <div class="rule-body" style="border-top:none">' +
        '    <div class="rule-row"><span>综合判断</span><b class="' + trustCls + '">' + trust + '</b></div>' +
        '    <div class="rule-row"><span>已卖出</span><b>' + (p.soldCount || 0) + ' 件</b></div>' +
        '    <div class="rule-row"><span>正在卖</span><b>' + (p.onSaleCount || 0) + ' 件</b></div>' +
        '  </div>' +
        '  <div class="rule-foot">' +
        '    信用分是所有交易行为的累积：成交 +2、好评 +1、差评 -2、超时爽约 -5、投诉成立 -10。' +
        '  </div>' +
        '</div>' +

        // 评价列表
        '<div class="rule-card" style="margin-bottom:12px">' +
        '  <div class="rule-head">' + icon('message', 15) + ' TA 收到的评价</div>' +
        '  <div id="prof-revs"><div class="loading">加载中…</div></div>' +
        '</div>' +

        (isMe ? '<div class="h5-empty" style="padding:10px 0">这是你自己的主页</div>' : '') +
        '</div>';

    // 评价
    try {
        const revs = await uapi('GET', '/user/review/user/' + userId, null, { quiet: true }) || [];
        setHTML('prof-revs', revs.length
            ? revs.slice(0, 20).map(r =>
                '<div class="row" style="padding:10px 12px;margin-bottom:8px">' +
                '<div class="r-head">' +
                '<span style="font-size:12.5px">' + stars(r.star) + '</span>' +
                '<span class="muted" style="font-size:11px">' + fmtTime(r.createTime) + '</span></div>' +
                '<div style="font-size:12.5px;color:#5a6472;line-height:1.6">' +
                esc(r.content || '（未填写内容）') + '</div>' +
                '<div class="muted" style="font-size:11px;margin-top:4px">' +
                '来自 ' + esc(r.fromNickname || '用户') + ' · ' + esc(r.productTitle || '') + '</div>' +
                '</div>').join('')
            : '<div class="h5-empty" style="padding:18px 0">还没有收到评价</div>');
    } catch (e) { setHTML('prof-revs', '<div class="h5-empty" style="padding:16px 0">评价加载失败</div>'); }
}
'''

anchor2 = "/* ======================= \u542f\u52a8 ======================= */"
if anchor2 in src:
    src = src.replace(anchor2, PAGE + "\n" + anchor2, 1)
    print("[2] 已追加 viewProfile")
else:
    src += PAGE
    print("[2] 追加到文件末尾")

# ---------- 3. 商品详情的卖家名字 → 可点进主页 ----------
old_seller = """
        '  <div class="row" style="margin-top:14px">' +
        '    <div class="r-head"><span><b>' + esc(p.sellerNickname || p.sellerName || '卖家') + '</b></span>' +
"""
new_seller = """
        '  <div class="row" style="margin-top:14px">' +
        '    <div class="r-head"><span style="cursor:pointer" data-seller="' + p.sellerId + '"><b>' + esc(p.sellerNickname || p.sellerName || '卖家') + '</b> <span class="muted" style="font-size:11px">›</span></span>' +
"""
if old_seller in src:
    src = src.replace(old_seller, new_seller, 1)
    print("[3] 卖家名字已可点击")
else:
    print("!! 没匹配到商品详情的卖家条")

# 绑定点击（在 detail 的 bindCarousel 之后加一段）
old_bind = "    bindCarousel();   // 多图轮播：让指示点跟着滑动走"
new_bind = """    bindCarousel();   // 多图轮播：让指示点跟着滑动走

    // 点卖家名字 → 看 TA 的主页（信用分 / 评价 / 在卖什么）
    const sellerLink = document.querySelector('[data-seller]');
    if (sellerLink) sellerLink.onclick = () => { location.hash = '#/profile/' + sellerLink.getAttribute('data-seller'); };
"""
if old_bind in src:
    src = src.replace(old_bind, new_bind, 1)
    print("[4] 已绑定卖家点击")
else:
    print("!! 没匹配到 bindCarousel")

with open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("完成：%d -> %d 字符（%+d）" % (before, len(src), len(src) - before))
