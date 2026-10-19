/* ==========================================================================
   管理后台 · 单页应用（零依赖，hash 路由）
   --------------------------------------------------------------------------
   7 个模块：看板 / 用户 / 商品审核 / 捡漏活动 / 跑腿单 / 投诉工单 / 评价管理
   入口： http://localhost:8080/ui/admin/index.html
   ========================================================================== */

const NAV = [
    { key: 'dashboard', name: '数据看板' },
    { key: 'users', name: '用户管理' },
    { key: 'audit', name: '商品审核' },
    { key: 'flashsale', name: '捡漏活动' },
    { key: 'delivery', name: '跑腿单' },
    { key: 'complaint', name: '投诉工单' },
    { key: 'review', name: '评价管理' }
];

const app = document.getElementById('app');

/* ======================= 登录 ======================= */
function renderLogin() {
    app.innerHTML =
        '<div class="login-page">' +
        '  <div class="login-box">' +
        '    <h1>校园二手交易平台</h1>' +
        '    <p>管理后台 · 请使用管理员账号登录</p>' +
        '    <div class="field"><label>用户名</label><input id="u" placeholder="zhangsan" autocomplete="username"></div>' +
        '    <div class="field"><label>密码</label><input id="p" type="password" placeholder="123456" autocomplete="current-password"></div>' +
        '    <button class="btn primary block" id="go">登录</button>' +
        '    <p style="margin:16px 0 0;text-align:center"><a href="../index.html">← 返回入口</a></p>' +
        '  </div>' +
        '</div>';

    const doLogin = async () => {
        const username = document.getElementById('u').value.trim();
        const password = document.getElementById('p').value.trim();
        if (!username || !password) return toast('请输入用户名和密码', 'err');
        const btn = document.getElementById('go');
        btn.disabled = true;
        try {
            const d = await api('POST', '/admin/employee/login', { username, password });
            Auth.set(d.token, d.name || username);
            toast('登录成功', 'ok');
            location.hash = '#/dashboard';
            renderLayout();
        } catch (e) { /* api 已提示 */ } finally { btn.disabled = false; }
    };

    document.getElementById('go').onclick = doLogin;
    document.getElementById('p').onkeydown = (e) => { if (e.key === 'Enter') doLogin(); };
}

/* ======================= 主框架 ======================= */
let todoCounts = null;   // 侧边栏的待办角标

async function loadTodo() {
    try {
        const d = await api('GET', '/admin/dashboard', null, { quiet: true });
        todoCounts = {
            audit: d.pendingAuditProducts || 0,
            complaint: d.pendingComplaints || 0,
            delivery: d.waitingDeliveries || 0
        };
    } catch (e) { todoCounts = null; }
}

function renderLayout() {
    const cur = (location.hash || '#/dashboard').replace('#/', '').split('?')[0] || 'dashboard';

    const navHTML = NAV.map(n => {
        const cnt = todoCounts && todoCounts[n.key];
        return '<a href="#/' + n.key + '" class="' + (n.key === cur ? 'active' : '') + '">' +
            '<span>' + n.name + '</span>' +
            (cnt ? '<span class="dot">' + cnt + '</span>' : '') +
            '</a>';
    }).join('');

    app.innerHTML =
        '<div class="layout">' +
        '  <aside class="sidebar">' +
        '    <div class="logo">校园二手交易平台<small>管理后台</small></div>' +
        '    <nav>' + navHTML + '</nav>' +
        '    <div class="foot">' +
        '      <div>当前账号：' + esc(Auth.name() || '-') + '</div>' +
        '      <button class="btn sm" id="logout">退出登录</button>' +
        '    </div>' +
        '  </aside>' +
        '  <main class="main" id="view"><div class="loading">加载中…</div></main>' +
        '</div>';

    document.getElementById('logout').onclick = () => {
        Auth.clear();
        location.hash = '#/login';
        renderLogin();
    };

    renderView(cur);
}

/* ======================= 路由 ======================= */
const VIEWS = {
    dashboard: viewDashboard,
    users: viewUsers,
    audit: viewAudit,
    flashsale: viewFlashSale,
    delivery: viewDelivery,
    complaint: viewComplaint,
    review: viewReview
};

let currentKey = '';

function renderView(key) {
    if (!VIEWS[key]) key = 'dashboard';
    currentKey = key;
    const el = document.getElementById('view');
    if (!el) return;
    el.innerHTML = '<div class="loading">加载中…</div>';
    VIEWS[key](el).catch(e => {
        el.innerHTML = '<div class="card"><div class="empty">加载失败：' + esc(e.message || e) + '</div></div>';
    });
}

window.addEventListener('hashchange', () => {
    if (!Auth.logged()) return renderLogin();
    loadTodo().then(() => renderLayout());
});

/* ======================= 1. 数据看板 ======================= */
async function viewDashboard(el) {
    const d = await api('GET', '/admin/dashboard?lowCredit=80');
    const t = d.trade || {}, u = d.user || {}, p = d.product || {};

    const stat = (k, v, cls, unit) =>
        '<div class="stat ' + (cls || '') + '"><div class="k">' + k + '</div>' +
        '<div class="v">' + v + (unit ? '<small>' + unit + '</small>' : '') + '</div></div>';

    el.innerHTML =
        '<div class="head"><div><h2>数据看板</h2>' +
        '<div class="sub">进入后台先看三个待办数字，再往下看平台整体情况</div></div></div>' +

        '<h3 style="font-size:14px;margin:0 0 10px">待办（需要我处理的）</h3>' +
        '<div class="stat-grid" style="margin-bottom:20px">' +
        stat('待审核商品', d.pendingAuditProducts || 0, 'todo', '件') +
        stat('待处理投诉', d.pendingComplaints || 0, 'todo', '单') +
        stat('待接单跑腿', d.waitingDeliveries || 0, 'todo', '单') +
        '</div>' +

        '<h3 style="font-size:14px;margin:0 0 10px">交易</h3>' +
        '<div class="stat-grid" style="margin-bottom:20px">' +
        stat('今日成交单数', t.todayOrders || 0, 'good', '单') +
        stat('今日成交额', money(t.todayAmount), 'good') +
        stat('本月成交单数', t.monthOrders || 0, '', '单') +
        stat('本月成交额', money(t.monthAmount), '') +
        stat('累计成交额', money(t.totalAmount), '') +
        '</div>' +

        '<h3 style="font-size:14px;margin:0 0 10px">用户</h3>' +
        '<div class="stat-grid" style="margin-bottom:20px">' +
        stat('用户总数', u.totalUsers || 0, '', '人') +
        stat('跑腿员', u.totalRunners || 0, '', '人') +
        stat('已封禁', u.bannedUsers || 0, u.bannedUsers ? 'todo' : '', '人') +
        stat('低信用分（&lt;80）', u.lowCreditUsers || 0, u.lowCreditUsers ? 'todo' : '', '人') +
        '</div>' +

        '<h3 style="font-size:14px;margin:0 0 10px">商品</h3>' +
        '<div class="stat-grid">' +
        stat('在售', p.onSaleProducts || 0, '', '件') +
        stat('交易中', p.inTradeProducts || 0, '', '件') +
        stat('已售罄', p.soldOutProducts || 0, '', '件') +
        stat('待审核', p.pendingAuditProducts || 0, p.pendingAuditProducts ? 'todo' : '', '件') +
        '</div>' +
        '<div class="card" style="margin-top:18px;padding:13px 18px;color:#8f98a6;font-size:12.5px">' +
        '说明：交易的时间口径按【完成时间】统计 —— 一个月前下单、今天才收货，这笔成交算今天。' +
        '</div>';
}

/* ======================= 2. 用户管理 ======================= */
const userState = { page: 1, pageSize: 10, keyword: '', status: '', role: '' };

async function viewUsers(el) {
    el.innerHTML =
        '<div class="head"><div><h2>用户管理</h2>' +
        '<div class="sub">封禁 / 跑腿员认证 / 手动调整信用分</div></div></div>' +
        '<div class="card">' +
        '  <div class="filters">' +
        '    <div class="field grow"><label>搜索（昵称 / 手机 / 学号）</label>' +
        '      <input id="f-kw" value="' + esc(userState.keyword) + '" placeholder="输入关键词后回车"></div>' +
        '    <div class="field"><label>状态</label><select id="f-st">' +
        '      <option value="">全部</option><option value="0">正常</option><option value="1">已封禁</option>' +
        '    </select></div>' +
        '    <div class="field"><label>角色</label><select id="f-role">' +
        '      <option value="">全部</option><option value="0">普通用户</option><option value="1">跑腿员</option>' +
        '    </select></div>' +
        '    <button class="btn primary" id="f-go">查询</button>' +
        '    <button class="btn" id="f-reset">重置</button>' +
        '  </div>' +
        '  <div id="user-table"><div class="loading">加载中…</div></div>' +
        '</div>';

    document.getElementById('f-st').value = userState.status;
    document.getElementById('f-role').value = userState.role;
    document.getElementById('f-go').onclick = () => {
        userState.keyword = document.getElementById('f-kw').value.trim();
        userState.status = document.getElementById('f-st').value;
        userState.role = document.getElementById('f-role').value;
        userState.page = 1;
        loadUsers();
    };
    document.getElementById('f-reset').onclick = () => {
        userState.keyword = ''; userState.status = ''; userState.role = ''; userState.page = 1;
        viewUsers(el);
    };
    document.getElementById('f-kw').onkeydown = (e) => { if (e.key === 'Enter') document.getElementById('f-go').click(); };

    await loadUsers();
}

async function loadUsers() {
    const box = document.getElementById('user-table');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const d = await api('GET', '/admin/user/page' + qs({
        page: userState.page, pageSize: userState.pageSize,
        keyword: userState.keyword, status: userState.status, role: userState.role
    }));
    const rows = d.records || [];

    box.innerHTML =
        '<div class="table-wrap"><table>' +
        '<thead><tr><th>ID</th><th>用户</th><th>角色</th><th>状态</th><th>信用分</th>' +
        '<th>好评/差评</th><th>注册时间</th><th>操作</th></tr></thead><tbody>' +
        (rows.length ? rows.map(u =>
            '<tr>' +
            '<td class="num">' + u.id + '</td>' +
            '<td><div class="t"><b>' + esc(u.nickname || '-') + '</b>' +
            '<small class="muted">' + esc(u.phone || u.studentNo || '') + '</small></div></td>' +
            '<td>' + (u.roleName ? badge(u.roleName, u.role === 1 ? 'blue' : 'gray') : tag('userRole', u.role)) + '</td>' +
            '<td>' + (u.statusName ? badge(u.statusName, u.status === 1 ? 'red' : 'green') : tag('userStatus', u.status)) + '</td>' +
            '<td class="num"><b>' + (u.creditScore === null ? '-' : u.creditScore) + '</b></td>' +
            '<td class="num">' + (u.goodReviewCount || 0) + ' / ' + (u.badReviewCount || 0) + '</td>' +
            '<td class="muted">' + fmtTime(u.createTime) + '</td>' +
            '<td class="td-actions">' +
            (u.status === 1
                ? '<button class="btn sm" data-act="unban" data-id="' + u.id + '">解封</button>'
                : '<button class="btn sm danger" data-act="ban" data-id="' + u.id + '">封禁</button>') +
            (u.role === 1
                ? '<button class="btn sm" data-act="demote" data-id="' + u.id + '">取消跑腿员</button>'
                : '<button class="btn sm" data-act="promote" data-id="' + u.id + '">设为跑腿员</button>') +
            '<button class="btn sm" data-act="credit" data-id="' + u.id + '" data-name="' + esc(u.nickname || '') + '">调分</button>' +
            '</td></tr>'
        ).join('') : emptyRow(8, '没有符合条件的用户')) +
        '</tbody></table></div>' +
        pagerHTML(userState.page, userState.pageSize, d.total);

    box.querySelectorAll('[data-page]').forEach(b => b.onclick = () => {
        const p = Number(b.getAttribute('data-page'));
        if (p >= 1) { userState.page = p; loadUsers(); }
    });
    box.querySelectorAll('[data-act]').forEach(b => b.onclick = () => userAction(b.getAttribute('data-act'), b));
}

async function userAction(act, btn) {
    const id = btn.getAttribute('data-id');
    const name = btn.getAttribute('data-name') || '该用户';

    if (act === 'ban') {
        if (!await confirmBox('封禁用户', '封禁后该用户将无法登录和下单，确定封禁？')) return;
        await api('PUT', '/admin/user/status/1?id=' + id);
        toast('已封禁', 'ok');
        return loadUsers();
    }
    if (act === 'unban') {
        await api('PUT', '/admin/user/status/0?id=' + id);
        toast('已解封', 'ok');
        return loadUsers();
    }
    if (act === 'promote') {
        await api('PUT', '/admin/user/role/1?id=' + id);
        toast('已设为跑腿员', 'ok');
        return loadUsers();
    }
    if (act === 'demote') {
        if (!await confirmBox('取消跑腿员资格',
            '如果该跑腿员手上还有进行中的跑腿单，系统会拒绝这次操作 —— 因为那几单就没人送了。')) return;
        await api('PUT', '/admin/user/role/0?id=' + id);
        toast('已取消跑腿员资格', 'ok');
        return loadUsers();
    }
    if (act === 'credit') {
        await modal({
            title: '手动调整信用分 · ' + name,
            body:
                '<div class="field"><label>调整分值（正数加分，负数扣分）</label>' +
                '<input id="m-score" type="number" placeholder="例如 5 或 -5">' +
                '<div class="hint">单次幅度不能超过 ±20；扣到 0 就停住，不会变负数</div></div>' +
                '<div class="field"><label>调整理由（必填）</label>' +
                '<textarea id="m-remark" placeholder="例如：经核实该差评系报复性评价，补回分数"></textarea>' +
                '<div class="hint">信用分是奖励/处罚，必须能说清"为什么"</div></div>',
            okText: '提交调整',
            onOk: async () => {
                const score = Number(document.getElementById('m-score').value);
                const remark = document.getElementById('m-remark').value.trim();
                if (!score) return toast('请填写调整分值（不能为 0）', 'err'), false;
                if (!remark) return toast('必须填写调整理由', 'err'), false;
                await api('PUT', '/admin/user/credit', { userId: Number(id), changeScore: score, remark });
                toast('已调整，可在用户端"我的信用分"看到流水', 'ok');
                loadUsers();
            }
        });
    }
}

/* ======================= 3. 商品审核 ======================= */
const auditState = { page: 1, pageSize: 10, auditStatus: 0, title: '' };

async function viewAudit(el) {
    el.innerHTML =
        '<div class="head"><div><h2>商品审核</h2>' +
        '<div class="sub">二手商品必须有内容审核 —— 审核是平台职责，上下架是卖家意愿，两件事</div></div></div>' +
        '<div class="card">' +
        '  <div class="filters">' +
        '    <div class="field grow"><label>商品标题</label><input id="a-kw" value="' + esc(auditState.title) + '" placeholder="输入关键词后回车"></div>' +
        '    <div class="field"><label>审核状态</label><select id="a-st">' +
        '      <option value="0">待审核</option><option value="1">已通过</option>' +
        '      <option value="2">已驳回</option><option value="">全部</option>' +
        '    </select></div>' +
        '    <button class="btn primary" id="a-go">查询</button>' +
        '  </div>' +
        '  <div id="audit-table"><div class="loading">加载中…</div></div>' +
        '</div>';

    document.getElementById('a-st').value = String(auditState.auditStatus);
    document.getElementById('a-go').onclick = () => {
        auditState.title = document.getElementById('a-kw').value.trim();
        auditState.auditStatus = document.getElementById('a-st').value;
        auditState.page = 1;
        loadAudit();
    };
    document.getElementById('a-kw').onkeydown = (e) => { if (e.key === 'Enter') document.getElementById('a-go').click(); };
    await loadAudit();
}

function imgCell(images, title) {
    let src = '';
    if (images) {
        const first = String(images).split(',')[0].trim();
        if (first) src = /^https?:/.test(first) ? first : '/' + first.replace(/^\/+/, '');
    }
    const thumb = src
        ? '<img class="thumb" src="' + esc(src) + '" onerror="this.style.display=\'none\'">'
        : '<span class="thumb-ph">📦</span>';
    return '<div class="cell-main">' + thumb + '<div class="t">' + esc(title || '(无标题)') + '</div></div>';
}

async function loadAudit() {
    const box = document.getElementById('audit-table');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const d = await api('GET', '/admin/product/page' + qs({
        page: auditState.page, pageSize: auditState.pageSize,
        auditStatus: auditState.auditStatus, title: auditState.title
    }));
    const rows = d.records || [];

    box.innerHTML =
        '<div class="table-wrap"><table>' +
        '<thead><tr><th>商品</th><th>价格</th><th>分类</th><th>商品状态</th><th>审核</th><th>操作</th></tr></thead><tbody>' +
        (rows.length ? rows.map(p =>
            '<tr>' +
            '<td>' + imgCell(p.images, p.title) + '</td>' +
            '<td class="num">' + money(p.price) + '</td>' +
            '<td class="muted">' + esc(p.categoryName || '-') + '</td>' +
            '<td>' + tag('productStatus', p.status) + '</td>' +
            '<td>' + tag('auditStatus', p.auditStatus) +
            (p.auditNote ? '<div class="muted" style="font-size:11.5px;margin-top:3px">' + esc(p.auditNote) + '</div>' : '') + '</td>' +
            '<td class="td-actions">' +
            (p.auditStatus === 0
                ? '<button class="btn sm primary" data-act="pass" data-id="' + p.id + '">通过</button>' +
                  '<button class="btn sm danger" data-act="reject" data-id="' + p.id + '">驳回</button>'
                : '<span class="muted">已处理</span>') +
            '</td></tr>'
        ).join('') : emptyRow(6, '没有符合条件的商品')) +
        '</tbody></table></div>' +
        pagerHTML(auditState.page, auditState.pageSize, d.total);

    box.querySelectorAll('[data-page]').forEach(b => b.onclick = () => {
        const p = Number(b.getAttribute('data-page'));
        if (p >= 1) { auditState.page = p; loadAudit(); }
    });
    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const id = b.getAttribute('data-id');
        if (b.getAttribute('data-act') === 'pass') {
            await api('POST', '/admin/product/audit' + qs({ id: id, auditStatus: 1 }));
            toast('已通过审核', 'ok');
            return loadAudit();
        }
        await modal({
            title: '驳回商品',
            body: '<div class="field"><label>驳回原因（会展示给卖家）</label>' +
                '<textarea id="m-note" placeholder="例如：商品图片不清晰 / 疑似违禁品 / 描述与实际不符"></textarea>' +
                '<div class="hint">驳回必须填原因，否则卖家不知道该怎么改</div></div>',
            danger: true, okText: '确认驳回',
            onOk: async () => {
                const note = document.getElementById('m-note').value.trim();
                if (!note) return toast('驳回必须填写原因', 'err'), false;
                await api('POST', '/admin/product/audit' + qs({ id: id, auditStatus: 2, auditNote: note }));
                toast('已驳回', 'ok');
                loadAudit();
            }
        });
    });
}

/* ======================= 4. 捡漏活动 ======================= */
async function viewFlashSale(el) {
    el.innerHTML =
        '<div class="head"><div><h2>捡漏活动</h2>' +
        '<div class="sub">Redis 库存 + 参与人数 + 抢到人数，一眼看清热度</div></div>' +
        '<button class="btn" id="f-reload">刷新</button></div>' +
        '<div class="card"><div id="fs-table"><div class="loading">加载中…</div></div>' +
        '<div class="hint muted" style="margin-top:10px;font-size:12px">' +
        '库存显示"(未初始化)"表示这台商品的 Redis key 还没建过 —— 第一次有人抢时会按初始库存自动建。</div>' +
        '</div>';

    document.getElementById('f-reload').onclick = () => loadFlashSale();
    await loadFlashSale();
}

async function loadFlashSale() {
    const box = document.getElementById('fs-table');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const rows = await api('GET', '/admin/flashSale/list');

    box.innerHTML =
        '<div class="table-wrap"><table>' +
        '<thead><tr><th>商品</th><th>价格</th><th>抢购开始</th><th>商品状态</th>' +
        '<th>Redis 库存</th><th>参与/抢到</th><th>操作</th></tr></thead><tbody>' +
        ((rows && rows.length) ? rows.map(r =>
            '<tr>' +
            '<td><b>#' + r.productId + '</b> ' + esc(r.title || '-') +
            (r.sellerNickname ? '<small class="muted" style="display:block">卖家：' + esc(r.sellerNickname) + '</small>' : '') + '</td>' +
            '<td class="num">' + money(r.price) + '</td>' +
            '<td class="muted">' + fmtTime(r.flashStartTime) + '</td>' +
            '<td>' + badge(r.statusName || '未知', r.status === 0 ? 'green' : r.status === 1 ? 'blue' : 'gray') + '</td>' +
            '<td class="num">' + (r.redisStock === null || r.redisStock === undefined ? '<span class="muted">(未初始化)</span>' : '<b>' + esc(r.redisStock) + '</b>') + '</td>' +
            '<td class="num">' + (r.participants || 0) + ' / ' + (r.grabbed || 0) + '</td>' +
            '<td class="td-actions">' +
            '<button class="btn sm" data-act="reset" data-id="' + r.productId + '">重置库存</button>' +
            '<button class="btn sm danger" data-act="end" data-id="' + r.productId + '">结束活动</button>' +
            '</td></tr>'
        ).join('') : emptyRow(7, '当前没有捡漏商品')) +
        '</tbody></table></div>';

    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const id = b.getAttribute('data-id');
        if (b.getAttribute('data-act') === 'reset') {
            if (!await confirmBox('重置 Redis 库存',
                '会清掉这台商品的库存 key，下次抢购按初始库存重建。\n只有"在售"状态才能重置 —— 已经卖掉的商品不允许（否则等于让 Redis 说谎）。')) return;
            await api('POST', '/admin/flashSale/resetStock/' + id);
            toast('已重置库存', 'ok');
            return loadFlashSale();
        }
        if (!await confirmBox('结束捡漏活动',
            '商品会变回普通商品（不再出现在捡漏专区），并清空 Redis 库存。\n如果它已经被抢走，已有订单不受影响 —— 活动归活动，订单归订单。')) return;
        await api('PUT', '/admin/flashSale/end/' + id);
        toast('已结束活动', 'ok');
        loadFlashSale();
    });
}

/* ======================= 5. 跑腿单 ======================= */
const delState = { page: 1, pageSize: 10, status: '' };

async function viewDelivery(el) {
    el.innerHTML =
        '<div class="head"><div><h2>跑腿单</h2>' +
        '<div class="sub">全局视角：谁手上压着单子、哪些单子卡住了</div></div></div>' +
        '<div class="card">' +
        '  <div class="filters">' +
        '    <div class="field"><label>状态</label><select id="d-st">' +
        '      <option value="">全部</option><option value="0">待接单</option><option value="1">已接单</option>' +
        '      <option value="2">取货中</option><option value="3">已送达</option><option value="4">已取消</option>' +
        '    </select></div>' +
        '    <button class="btn primary" id="d-go">查询</button>' +
        '  </div>' +
        '  <div id="del-table"><div class="loading">加载中…</div></div>' +
        '</div>';

    document.getElementById('d-st').value = delState.status;
    document.getElementById('d-go').onclick = () => {
        delState.status = document.getElementById('d-st').value;
        delState.page = 1;
        loadDelivery();
    };
    await loadDelivery();
}

async function loadDelivery() {
    const box = document.getElementById('del-table');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const d = await api('GET', '/admin/delivery/page' + qs({
        page: delState.page, pageSize: delState.pageSize, status: delState.status
    }));
    const rows = d.records || [];

    box.innerHTML =
        '<div class="table-wrap"><table>' +
        '<thead><tr><th>单号</th><th>商品</th><th>取货 → 送达</th><th>跑腿员</th><th>费用</th><th>状态</th><th>操作</th></tr></thead><tbody>' +
        (rows.length ? rows.map(r =>
            '<tr>' +
            '<td class="num">#' + r.id + '<small class="muted" style="display:block">交易单 #' + (r.tradeOrderId || '-') + '</small></td>' +
            '<td>' + esc(r.productTitle || '-') + '</td>' +
            '<td class="muted" style="font-size:12px">' + esc(r.pickAddress || '-') + '<br>→ ' + esc(r.receiveAddress || '-') + '</td>' +
            '<td>' + (r.runnerUserId ? '#' + r.runnerUserId : '<span class="muted">未接单</span>') + '</td>' +
            '<td class="num">' + money(r.deliveryFee) + '</td>' +
            '<td>' + tag('deliveryStatus', r.status) + '</td>' +
            '<td class="td-actions">' +
            ((r.status === 3 || r.status === 4)
                ? '<span class="muted">已结束</span>'
                : '<button class="btn sm danger" data-act="cancel" data-id="' + r.id + '">强制取消</button>') +
            '</td></tr>'
        ).join('') : emptyRow(7, '没有符合条件的跑腿单')) +
        '</tbody></table></div>' +
        pagerHTML(delState.page, delState.pageSize, d.total);

    box.querySelectorAll('[data-page]').forEach(b => b.onclick = () => {
        const p = Number(b.getAttribute('data-page'));
        if (p >= 1) { delState.page = p; loadDelivery(); }
    });
    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const id = b.getAttribute('data-id');
        await modal({
            title: '强制取消跑腿单 #' + id,
            body: '<div class="field"><label>取消原因（必填，会写进审计日志）</label>' +
                '<textarea id="m-reason" placeholder="例如：跑腿员接单后失联，联系不上，已安排其他人送"></textarea>' +
                '<div class="hint">管理员的强制操作必须留痕到"谁、为什么" —— 否则事后用户投诉时无法举证</div></div>' +
                '<div class="hint muted">注意：只取消跑腿单，不联动交易订单 —— 货没送到，交易要不要取消是另一个决定。</div>',
            danger: true, okText: '确认强制取消',
            onOk: async () => {
                const reason = document.getElementById('m-reason').value.trim();
                if (!reason) return toast('强制取消必须填写原因', 'err'), false;
                await api('PUT', '/admin/delivery/cancel/' + id + qs({ reason: reason }));
                toast('已强制取消', 'ok');
                loadDelivery();
            }
        });
    });
}

/* ======================= 6. 投诉工单 ======================= */
const cmpState = { page: 1, pageSize: 10, handleStatus: '', complaintType: '' };

async function viewComplaint(el) {
    el.innerHTML =
        '<div class="head"><div><h2>投诉工单</h2>' +
        '<div class="sub">处理用"抢处理权"（条件更新）—— 两个管理员同时点，只会有一个成功</div></div></div>' +
        '<div class="card">' +
        '  <div class="filters">' +
        '    <div class="field"><label>处理状态</label><select id="c-st">' +
        '      <option value="">全部</option><option value="0">待处理</option>' +
        '      <option value="1">处理中</option><option value="2">已完结</option>' +
        '    </select></div>' +
        '    <div class="field"><label>投诉类型</label><select id="c-type">' +
        '      <option value="">全部</option><option value="0">商品投诉</option><option value="1">交易投诉</option>' +
        '    </select></div>' +
        '    <button class="btn primary" id="c-go">查询</button>' +
        '  </div>' +
        '  <div id="cmp-table"><div class="loading">加载中…</div></div>' +
        '</div>';

    document.getElementById('c-st').value = cmpState.handleStatus;
    document.getElementById('c-type').value = cmpState.complaintType;
    document.getElementById('c-go').onclick = () => {
        cmpState.handleStatus = document.getElementById('c-st').value;
        cmpState.complaintType = document.getElementById('c-type').value;
        cmpState.page = 1;
        loadComplaint();
    };
    await loadComplaint();
}

async function loadComplaint() {
    const box = document.getElementById('cmp-table');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const d = await api('GET', '/admin/complaint/page' + qs({
        handleStatus: cmpState.handleStatus, complaintType: cmpState.complaintType,
        page: cmpState.page, pageSize: cmpState.pageSize
    }));
    const rows = d.records || [];

    box.innerHTML =
        '<div class="table-wrap"><table>' +
        '<thead><tr><th>工单</th><th>类型/原因</th><th>投诉人 → 被投诉人</th><th>内容</th><th>状态</th><th>操作</th></tr></thead><tbody>' +
        (rows.length ? rows.map(c =>
            '<tr>' +
            '<td class="num">#' + c.id +
            '<small class="muted" style="display:block">' + fmtTime(c.createTime) + '</small></td>' +
            '<td>' + (c.complaintTypeName ? badge(c.complaintTypeName, 'gray') : tag('complaintType', c.complaintType)) +
            '<small class="muted" style="display:block;margin-top:3px">' + esc(c.complaintReasonName || '') + '</small></td>' +
            '<td style="font-size:12.5px">' + esc(c.complainantNickname || '-') + ' → ' + esc(c.respondentNickname || '-') + '</td>' +
            '<td style="max-width:300px;font-size:12.5px">' + esc(c.content || '') +
            (c.handleNote ? '<small class="muted" style="display:block;margin-top:4px">处理：' + esc(c.handleNote) + '</small>' : '') + '</td>' +
            '<td>' + (c.handleStatusName ? badge(c.handleStatusName, c.handleStatus === 2 ? 'green' : 'orange') : tag('complaintStatus', c.handleStatus)) +
            (c.handleResult ? '<small style="display:block;margin-top:3px">' + tag('complaintResult', c.handleResult) + '</small>' : '') + '</td>' +
            '<td class="td-actions">' +
            (c.handleStatus === 2
                ? '<span class="muted">已完结</span>'
                : '<button class="btn sm primary" data-act="handle" data-id="' + c.id + '">处理</button>') +
            '</td></tr>'
        ).join('') : emptyRow(6, '没有符合条件的工单')) +
        '</tbody></table></div>' +
        pagerHTML(cmpState.page, cmpState.pageSize, d.total);

    box.querySelectorAll('[data-page]').forEach(b => b.onclick = () => {
        const p = Number(b.getAttribute('data-page'));
        if (p >= 1) { cmpState.page = p; loadComplaint(); }
    });
    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const id = Number(b.getAttribute('data-id'));
        await modal({
            title: '处理投诉 #' + id,
            body:
                '<div class="field"><label>处理结果</label><select id="m-result">' +
                '  <option value="1">成立（被投诉人扣 10 分）</option>' +
                '  <option value="2">不成立（不扣分）</option>' +
                '</select></div>' +
                '<div class="field"><label>处理说明</label>' +
                '<textarea id="m-note" placeholder="例如：经核实商品与描述严重不符，投诉成立，已扣被投诉人 10 分"></textarea>' +
                '</div>' +
                '<div class="hint muted">处理用条件更新抢处理权：如果另一个管理员刚好先处理了，你会看到"该工单已被处理"。</div>',
            okText: '提交处理',
            onOk: async () => {
                const handleResult = Number(document.getElementById('m-result').value);
                const handleNote = document.getElementById('m-note').value.trim();
                if (!handleNote) return toast('请填写处理说明', 'err'), false;
                await api('PUT', '/admin/complaint/handle', { id, handleResult, handleNote });
                toast('已处理', 'ok');
                loadComplaint();
            }
        });
    });
}

/* ======================= 7. 评价管理 ======================= */
const revState = { page: 1, pageSize: 10, star: '', toUserId: '' };

async function viewReview(el) {
    el.innerHTML =
        '<div class="head"><div><h2>评价管理</h2>' +
        '<div class="sub">删除违规评价时【自动回退信用分】—— 删差评就把扣的分还回去</div></div></div>' +
        '<div class="card">' +
        '  <div class="filters">' +
        '    <div class="field"><label>星级</label><select id="r-star">' +
        '      <option value="">全部</option><option value="1">1 星</option><option value="2">2 星</option>' +
        '      <option value="3">3 星</option><option value="4">4 星</option><option value="5">5 星</option>' +
        '    </select></div>' +
        '    <div class="field"><label>被评价人 ID</label><input id="r-uid" value="' + esc(revState.toUserId) + '" placeholder="如 2"></div>' +
        '    <button class="btn primary" id="r-go">查询</button>' +
        '  </div>' +
        '  <div id="rev-table"><div class="loading">加载中…</div></div>' +
        '</div>';

    document.getElementById('r-star').value = revState.star;
    document.getElementById('r-go').onclick = () => {
        revState.star = document.getElementById('r-star').value;
        revState.toUserId = document.getElementById('r-uid').value.trim();
        revState.page = 1;
        loadReview();
    };
    await loadReview();
}

async function loadReview() {
    const box = document.getElementById('rev-table');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const d = await api('GET', '/admin/review/page' + qs({
        page: revState.page, pageSize: revState.pageSize,
        star: revState.star, toUserId: revState.toUserId
    }));
    const rows = d.records || [];

    box.innerHTML =
        '<div class="table-wrap"><table>' +
        '<thead><tr><th>商品</th><th>评价人 → 被评价人</th><th>星级</th><th>内容</th><th>时间</th><th>操作</th></tr></thead><tbody>' +
        (rows.length ? rows.map(r =>
            '<tr>' +
            '<td style="max-width:200px">' + esc(r.productTitle || ('#' + r.productId)) + '</td>' +
            '<td style="font-size:12.5px">' + esc(r.fromNickname || '-') + ' → ' + esc(r.toNickname || '-') + '</td>' +
            '<td>' + stars(r.star) + '</td>' +
            '<td style="max-width:320px;font-size:12.5px">' + esc(r.content || '<span class="muted">（未填写内容）</span>') + '</td>' +
            '<td class="muted">' + fmtTime(r.createTime) + '</td>' +
            '<td class="td-actions"><button class="btn sm danger" data-act="del" data-id="' + r.id + '" data-star="' + r.star + '">删除</button></td>' +
            '</tr>'
        ).join('') : emptyRow(6, '没有符合条件的评价')) +
        '</tbody></table></div>' +
        pagerHTML(revState.page, revState.pageSize, d.total);

    box.querySelectorAll('[data-page]').forEach(b => b.onclick = () => {
        const p = Number(b.getAttribute('data-page'));
        if (p >= 1) { revState.page = p; loadReview(); }
    });
    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const id = b.getAttribute('data-id');
        const star = Number(b.getAttribute('data-star'));
        const effect = star >= 4 ? '收回当初加的 1 分' : star <= 2 ? '把当初扣的 2 分还回去（+2）' : '3 星没动过分数，不回退';
        await modal({
            title: '删除违规评价 #' + id,
            body: '<div class="field"><label>删除原因（必填）</label>' +
                '<textarea id="m-reason" placeholder="例如：恶意差评，经核实为报复性评价"></textarea></div>' +
                '<div class="hint">这条是 ' + star + ' 星 → 删除后会【' + effect + '】</div>' +
                '<div class="hint muted">删除是物理删除（review 表没有 is_deleted 字段），生产环境更规范的做法是逻辑删除 + 删除原因。</div>',
            danger: true, okText: '确认删除',
            onOk: async () => {
                const reason = document.getElementById('m-reason').value.trim();
                if (!reason) return toast('删除评价必须填写原因', 'err'), false;
                await api('DELETE', '/admin/review/' + id + qs({ reason: reason }));
                toast('已删除并回退信用分', 'ok');
                loadReview();
            }
        });
    });
}

/* ======================= 启动 ======================= */
(async function bootstrap() {
    if (!Auth.logged()) {
        if (location.hash !== '#/login') location.hash = '#/login';
        return renderLogin();
    }
    await loadTodo();
    renderLayout();
})();
