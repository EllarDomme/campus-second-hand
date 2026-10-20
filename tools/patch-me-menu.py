# -*- coding: utf-8 -*-
"""
把"跑腿员资格 / 信用分规则 / 联系我们"三张大卡片改成独立的菜单页面

做三件事：
  1. 删掉"我的"页面里那三张 rule-card
  2. 路由里注册三个新页
  3. 追加三个页面函数
"""
import io, re, sys

PATH = r"E:\DeskTop\campus-second-hand\campus-server\src\main\resources\static\ui\user\app.js"

with io.open(PATH, "r", encoding="utf-8") as f:
    src = f.read()

orig_len = len(src)

# ---------- 1. 删掉三张卡片 ----------
start_mark = "// \u8dd1\u817f\u5458\u8d44\u683c\u8bf4\u660e"      # // 跑腿员资格说明
si = src.find(start_mark)
if si < 0:
    print("!! 没找到卡片起始标记")
    sys.exit(1)

# 从起点往后找第一个 menu-list（那就是"退出登录"那个菜单）
ei = src.find('\'<div class="menu-list">\' +', si)
if ei < 0:
    print("!! 没找到卡片结束边界")
    sys.exit(1)

removed = src[si:ei]
src = src[:si] + src[ei:]
print("[1] 已删除三张卡片：%d 字符" % len(removed))

# ---------- 2. 路由注册 ----------
anchor = "    if (key === 'bargain') return viewBargain();"
if anchor in src:
    new_routes = (anchor + "\n"
                  "    if (key === 'runner-guide') return viewRunnerGuide();\n"
                  "    if (key === 'credit-rules') return viewCreditRules();\n"
                  "    if (key === 'contact') return viewContact();")
    src = src.replace(anchor, new_routes, 1)
    print("[2] 路由已注册 3 个新页")
else:
    print("!! 没找到路由注册点")

# ---------- 3. 追加三个页面 ----------
views = '''

/* ======================= 跑腿员资格 ======================= */
async function viewRunnerGuide() {
    const info = U.info();
    const isRunner = Number(info.role) === 1;
    app.innerHTML = topBar('跑腿员资格', true) +
        '<div class="h5-body">' +
        (isRunner
            ? '<div class="rule-card" style="border-color:#bbf7d0;background:#f0fdf4">' +
              '  <div class="rule-head" style="color:#059669">' + icon('check', 15) + ' 你已经是跑腿员</div>' +
              '  <div class="rule-foot" style="border-top:none;padding-top:0;margin-top:0;color:#047857">' +
              '    可以去「跑腿大厅」抢单，跑腿费按距离计费、计入个人收入。</div>' +
              '</div>'
            : '<div class="rule-card" style="border-color:#fde68a;background:#fffbeb">' +
              '  <div class="rule-head" style="color:#b45309">' + icon('truck', 15) + ' 你还不是跑腿员</div>' +
              '  <div class="rule-foot" style="border-top:none;padding-top:0;margin-top:0">' +
              '    想接单赚跑腿费，需要先开通资格。开通方式是<b>联系平台管理员</b>' +
              '（管理员在后台「用户管理」里点「设为跑腿员」即可），开通后立刻生效、无需重新登录。</div>' +
              '</div>') +

        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('money', 15) + ' 跑腿费怎么算</div>' +
        '  <div class="rule-body">' +
        '    <div class="rule-row"><span>校园内近（约 1 km）</span><b>¥5</b></div>' +
        '    <div class="rule-row"><span>中等（约 5 km）</span><b>¥7</b></div>' +
        '    <div class="rule-row"><span>较远（约 10 km）</span><b>¥12</b></div>' +
        '  </div>' +
        '  <div class="rule-foot">起步价 5 元（含 3 公里），超出部分每公里加 1 元，向上取整。<br>' +
        '    跑腿费在下单时就<b>固定写进订单</b>，以后改计费规则也不影响历史单。</div>' +
        '</div>' +

        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('clock', 15) + ' 接单流程</div>' +
        '  <div class="rule-body">' +
        '    <div class="rule-row"><span>① 在跑腿大厅抢单</span><b class="flat">先到先得</b></div>' +
        '    <div class="rule-row"><span>② 取到货点「已取货」</span><b class="flat">取货中</b></div>' +
        '    <div class="rule-row"><span>③ 送到后点「已送达」</span><b class="up">跑腿费入账</b></div>' +
        '  </div>' +
        '  <div class="rule-foot">多人同时抢同一单时，只有一个人能成功（数据库条件更新保证，不需要分布式锁）。</div>' +
        '</div>' +
        '</div>';
}

/* ======================= 信用分规则 ======================= */
async function viewCreditRules() {
    let credit = {};
    try { credit = await uapi('GET', '/user/credit/my', null, { quiet: true }) || {}; } catch (e) { }
    app.innerHTML = topBar('信用分规则', true) +
        '<div class="h5-body">' +
        '<div class="me-card" style="padding:16px 18px">' +
        '  <div class="n">当前信用分</div>' +
        '  <div style="font-size:34px;font-weight:750;line-height:1.2;margin-top:4px">' +
        (credit.creditScore === undefined ? '-' : credit.creditScore) + '</div>' +
        '  <div class="s">好评 ' + (credit.goodReviewCount || 0) + ' · 差评 ' + (credit.badReviewCount || 0) +
        ' · 今日已加 ' + (credit.todayAdded || 0) + '/' + (credit.dailyAddLimit || 10) + '</div>' +
        '</div>' +

        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('star', 15) + ' 加分明细</div>' +
        '  <div class="rule-body">' +
        '    <div class="rule-row"><span>交易完成</span><b class="up">+2</b></div>' +
        '    <div class="rule-row"><span>收到好评（4~5 星）</span><b class="up">+1</b></div>' +
        '    <div class="rule-row"><span>收到中评（3 星）</span><b class="flat">0</b></div>' +
        '    <div class="rule-row"><span>收到差评（1~2 星）</span><b class="down">-2</b></div>' +
        '    <div class="rule-row"><span>超时未确认（爽约）</span><b class="down">-5</b></div>' +
        '    <div class="rule-row"><span>被投诉且成立</span><b class="down">-10</b></div>' +
        '  </div>' +
        '</div>' +

        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('flag', 15) + ' 边界规则</div>' +
        '  <div class="rule-foot" style="border-top:none;padding-top:0;margin-top:0">' +
        '    <b>每日加分上限 10 分</b> —— 只限加分，扣分不设限（否则恶意用户等于"每天最多被扣 10 分"）<br><br>' +
        '    <b>分数下限 0 分</b> —— 扣到底也不会变负数<br><br>' +
        '    <b>补偿性加分不受上限限制</b> —— 比如管理员删掉一条违规差评要把分还给你，' +
        '那属于"撤销处罚"，不是刷分，不该被每日上限吞掉<br><br>' +
        '    <b>管理员手动调整单次不超过 ±20 分</b>，且必须填写理由（用规则约束裁量权）' +
        '  </div>' +
        '</div>' +

        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('chart', 15) + ' 查看明细</div>' +
        '  <div class="rule-foot" style="border-top:none;padding-top:0;margin-top:0">' +
        '    每一笔加减分都有流水记录（谁、什么时候、因为什么）。' +
        '在「我的」→「信用分流水」里可以看到全部记录。</div>' +
        '</div>' +
        '</div>';
}

/* ======================= 联系我们 ======================= */
async function viewContact() {
    app.innerHTML = topBar('联系我们', true) +
        '<div class="h5-body">' +
        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('message', 15) + ' 联系方式</div>' +
        '  <div class="rule-body">' +
        '    <div class="rule-row"><span>平台邮箱</span><b style="font-weight:500">campus-secondhand@example.edu</b></div>' +
        '    <div class="rule-row"><span>客服 QQ 群</span><b style="font-weight:500">123 456 789</b></div>' +
        '    <div class="rule-row"><span>处理时效</span><b style="font-weight:500;color:#059669">工作日 24 小时内</b></div>' +
        '  </div>' +
        '</div>' +

        '<div class="rule-card">' +
        '  <div class="rule-head">' + icon('flag', 15) + ' 遇到问题怎么办</div>' +
        '  <div class="rule-body">' +
        '    <div class="rule-row"><span>交易纠纷</span><b class="flat">订单里发起「投诉」</b></div>' +
        '    <div class="rule-row"><span>账号被封</span><b class="flat">邮箱联系我们</b></div>' +
        '    <div class="rule-row"><span>想接单跑腿</span><b class="flat">申请开通跑腿员资格</b></div>' +
        '  </div>' +
        '  <div class="rule-foot">' +
        '    <b>纠纷优先走「投诉」</b> —— 会在管理端生成工单，管理员能看到商品、订单、双方信息和证据图片，' +
        '处理比邮件快很多；而且裁决结果会联动信用分（投诉成立扣被投诉人 10 分）。' +
        '  </div>' +
        '</div>' +
        '</div>';
}
'''

# 插到"启动"那一节之前
start_mark2 = "/* ======================= \u542f\u52a8 ======================= */"   # /* ===== 启动 ===== */
if start_mark2 in src:
    src = src.replace(start_mark2, views + "\n" + start_mark2, 1)
    print("[3] 已追加 3 个页面函数")
else:
    print("!! 没找到插入点，追加到文件末尾")
    src += views

with io.open(PATH, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("完成：%d -> %d 字符（%+d）" % (orig_len, len(src), len(src) - orig_len))
