/* ==========================================================================
   管理后台 · 单页应用（零依赖，hash 路由）
   --------------------------------------------------------------------------
   7 个模块：看板 / 用户 / 商品审核 / 捡漏活动 / 跑腿单 / 投诉工单 / 评价管理
   入口： http://localhost:8080/ui/admin/index.html
   ========================================================================== */

const NAV = [
    { group: '概览', key: 'dashboard', name: '数据看板', icon: 'dashboard' },
    { group: '内容', key: 'audit', name: '商品审核', icon: 'box' },
    { group: '交易', key: 'flashsale', name: '捡漏活动', icon: 'bolt' },
    { group: '交易', key: 'delivery', name: '跑腿单', icon: 'truck' },
    { group: '治理', key: 'users', name: '用户管理', icon: 'users' },
    { group: '治理', key: 'complaint', name: '投诉工单', icon: 'flag' },
    { group: '治理', key: 'review', name: '评价管理', icon: 'star' }
];

const app = document.getElementById('app');

/* ======================= 登录 ======================= */
function renderLogin() {
    app.innerHTML =
        '<div class="login-page">' +
        '  <div class="login-box">' +
        '    <div class="brand">' +
        '      <span class="mark">' + icon('box', 20) + '</span>' +
        '      <div><h1>校园二手交易平台</h1><p class="sub">管理后台 · 请使用管理员账号登录</p></div>' +
        '    </div>' +
        '    <div class="field"><label>用户名</label><input id="u" placeholder="zhangsan" autocomplete="username"></div>' +
        '    <div class="field"><label>密码</label><input id="p" type="password" placeholder="123456" autocomplete="current-password"></div>' +
        '    <button class="btn primary block" id="go" style="height:42px;margin-top:4px">登录</button>' +
        '    <p style="margin:18px 0 0;text-align:center;font-size:12.5px"><a href="../index.html">← 返回入口</a></p>' +
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
    const curNav = NAV.find(n => n.key === cur) || NAV[0];

    // 按 group 分组渲染导航
    let navHTML = '', lastGroup = '';
    NAV.forEach(n => {
        if (n.group !== lastGroup) {
            navHTML += '<div class="group">' + n.group + '</div>';
            lastGroup = n.group;
        }
        const cnt = todoCounts && todoCounts[n.key];
        navHTML += '<a href="#/' + n.key + '" class="' + (n.key === cur ? 'active' : '') + '">' +
            icon(n.icon) + '<span class="txt">' + n.name + '</span>' +
            (cnt ? '<span class="dot">' + cnt + '</span>' : '') + '</a>';
    });

    const name = Auth.name() || '管理员';
    const today = new Date();
    const dateStr = today.getFullYear() + '-' +
        String(today.getMonth() + 1).padStart(2, '0') + '-' +
        String(today.getDate()).padStart(2, '0');

    app.innerHTML =
        '<div class="layout">' +
        '  <aside class="sidebar">' +
        '    <div class="logo">' +
        '      <span class="mark">' + icon('box', 15) + '</span>' +
        '      <span>校园二手<small>管理后台</small></span>' +
        '    </div>' +
        '    <nav>' + navHTML + '</nav>' +
        '    <div class="foot">' +
        '      <div class="who"><span class="avatar">' + esc(name.substring(0, 1)) + '</span>' +
        '        <span>' + esc(name) + '</span></div>' +
        '      <button class="btn sm" id="logout">' + icon('close', 13) + ' 退出</button>' +
        '    </div>' +
        '  </aside>' +
        '  <div class="main">' +
        '    <div class="topbar">' +
        '      <div class="crumb"><b>' + curNav.name + '</b>' +
        '        <span class="sep">/</span><span>' + dateStr + '</span></div>' +
        '      <div class="right"><span>数据实时读库</span></div>' +
        '    </div>' +
        '    <main class="page" id="view"><div class="loading">加载中…</div></main>' +
        '  </div>' +
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

/** 纯 SVG 环形图（不引任何图表库） */
function donutSVG(segments, centerText, centerSub) {
    const R = 52, W = 16, C = 2 * Math.PI * R;
    const total = segments.reduce((s, x) => s + (Number(x.value) || 0), 0);
    let acc = 0;
    const rings = segments.map(s => {
        const v = Number(s.value) || 0;
        const len = total ? (v / total) * C : 0;
        const el = '<circle cx="70" cy="70" r="' + R + '" fill="none" stroke="' + s.color + '"' +
            ' stroke-width="' + W + '" stroke-dasharray="' + len.toFixed(2) + ' ' + (C - len).toFixed(2) + '"' +
            ' stroke-dashoffset="' + (-acc).toFixed(2) + '" transform="rotate(-90 70 70)"' +
            ' stroke-linecap="butt"></circle>';
        acc += len;
        return el;
    }).join('');

    return '<svg viewBox="0 0 140 140" style="width:140px;height:140px;flex:none">' +
        '<circle cx="70" cy="70" r="' + R + '" fill="none" stroke="#f1f5f9" stroke-width="' + W + '"></circle>' +
        rings +
        '<text x="70" y="66" text-anchor="middle" font-size="20" font-weight="650" fill="#0f172a">' +
        (centerText === undefined ? total : centerText) + '</text>' +
        '<text x="70" y="85" text-anchor="middle" font-size="10" fill="#94a3b8">' + (centerSub || '合计') + '</text>' +
        '</svg>';
}

/** 横向条形 */
function barRow(label, value, max, color, suffix) {
    const pct = max > 0 ? Math.max(2, (Number(value) / max) * 100) : 0;
    return '<div class="bar-item">' +
        '<div class="bar-top"><span>' + esc(label) + '</span><b>' + (value || 0) + (suffix || '') + '</b></div>' +
        '<div class="bar-track"><div class="bar-fill" style="width:' + pct.toFixed(1) + '%;background:' + color + '"></div></div>' +
        '</div>';
}

async function viewDashboard(el) {
    const d = await api('GET', '/admin/dashboard?lowCredit=80');
    const t = d.trade || {}, u = d.user || {}, p = d.product || {};

    const stat = (ico, k, v, cls, unit, foot) =>
        '<div class="stat ' + (cls || '') + '">' +
        '<span class="ico-badge">' + icon(ico) + '</span>' +
        '<div class="k">' + k + '</div>' +
        '<div class="v">' + v + (unit ? '<small>' + unit + '</small>' : '') + '</div>' +
        (foot ? '<div class="foot">' + foot + '</div>' : '') +
        '</div>';

    const pendingTotal = (d.pendingAuditProducts || 0) + (d.pendingComplaints || 0) + (d.waitingDeliveries || 0);

    // 商品状态占比
    const segs = [
        { label: '在售', value: p.onSaleProducts || 0, color: '#10b981' },
        { label: '交易中', value: p.inTradeProducts || 0, color: '#4f46e5' },
        { label: '已售罄', value: p.soldOutProducts || 0, color: '#94a3b8' },
        { label: '待审核', value: p.pendingAuditProducts || 0, color: '#f59e0b' }
    ];
    const maxTrade = Math.max(t.monthOrders || 0, t.todayOrders || 0, 1);

    el.innerHTML =
        '<div class="head"><div><h2>数据看板</h2>' +
        '<div class="sub">先看待办（需要人工处理的），再看平台整体。交易按【完成时间】统计 —— ' +
        '一个月前下单、今天才收货，算今天成交。</div></div>' +
        '<button class="btn" id="dash-refresh">' + icon('clock', 14) + ' 刷新</button></div>' +

        '<div class="stat-grid" style="margin-bottom:18px">' +
        stat('box', '待审核商品', d.pendingAuditProducts || 0, 'todo', '件', '审核通过后才对外展示') +
        stat('flag', '待处理投诉', d.pendingComplaints || 0, 'todo', '单', '建议当天处理完毕') +
        stat('truck', '待接单跑腿', d.waitingDeliveries || 0, 'todo', '单', '太久没人接要主动介入') +
        stat('chart', '待办合计', pendingTotal, pendingTotal > 5 ? 'warn' : 'good', '项',
            pendingTotal === 0 ? '当前无待办 ' : '处理完再看别处') +
        '</div>' +

        '<div class="chart-row" style="margin-bottom:18px">' +
        '  <div class="card" style="margin:0">' +
        '    <h3>' + icon('chart', 15) + ' 交易概况</h3>' +
        '    <div class="bars">' +
        barRow('今日成交单数', t.todayOrders || 0, maxTrade, 'linear-gradient(90deg,#818cf8,#4f46e5)', ' 单') +
        barRow('本月成交单数', t.monthOrders || 0, maxTrade, 'linear-gradient(90deg,#34d399,#10b981)', ' 单') +
        '    </div>' +
        '    <div class="legend">' +
        '      <span style="margin-left:0">今日成交额 <b>' + money(t.todayAmount) + '</b></span>' +
        '      <span>本月成交额 <b>' + money(t.monthAmount) + '</b></span>' +
        '      <span>累计成交额 <b>' + money(t.totalAmount) + '</b></span>' +
        '    </div>' +
        '  </div>' +
        '  <div class="card" style="margin:0">' +
        '    <h3>' + icon('box', 15) + ' 商品状态占比</h3>' +
        '    <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">' +
        donutSVG(segs, undefined, '在架商品') +
        '      <div class="bars" style="flex:1;min-width:120px;gap:9px">' +
        segs.map(s =>
            '<div class="bar-item"><div class="bar-top"><span>' + s.label + '</span><b>' + s.value + '</b></div>' +
            '<div class="bar-track"><div class="bar-fill" style="width:' +
            (segs.reduce((a, b) => a + b.value, 0) ? Math.max(2, s.value / segs.reduce((a, b) => a + b.value, 0) * 100) : 0).toFixed(1) +
            '%;background:' + s.color + '"></div></div></div>').join('') +
        '      </div>' +
        '    </div>' +
        '  </div>' +
        '</div>' +

        '<div class="card">' +
        '  <h3>' + icon('users', 15) + ' 用户与商品<span class="count">实时读库</span></h3>' +
        '  <div class="stat-grid">' +
        stat('users', '用户总数', u.totalUsers || 0, '', '人') +
        stat('truck', '跑腿员', u.totalRunners || 0, '', '人') +
        stat('close', '已封禁', u.bannedUsers || 0, u.bannedUsers ? 'todo' : '', '人') +
        stat('flag', '低信用分（&lt;80）', u.lowCreditUsers || 0, u.lowCreditUsers ? 'warn' : '', '人') +
        '  </div>' +
        '</div>';

    const rf = document.getElementById('dash-refresh');
    if (rf) rf.onclick = () => { loadTodo().then(renderLayout); };
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

/**
 * images 字段 → 图片地址数组
 * 兼容两种格式：JSON 数组字符串（库里的老数据）/ 逗号分隔（早期前端写的）
 * 只按逗号拆会把 JSON 数组拆坏，导致图片全部加载失败 —— 这个坑踩过。
 */
function splitImages(images) {
    if (!images) return [];
    const raw = String(images).trim();
    let arr = null;
    if (raw.startsWith('[')) {
        try { arr = JSON.parse(raw); } catch (e) { arr = null; }
    }
    if (!Array.isArray(arr)) arr = raw.split(',');
    return arr.map(x => String(x).trim().replace(/^["'\s]+|["'\s]+$/g, ''))
        .filter(Boolean)
        .map(x => /^https?:/.test(x) ? x : '/' + x.replace(/^\/+/, ''));
}

/**
 * 商品详情抽屉（审核用）
 * 为什么是抽屉而不是弹窗：审核要看的东西多（多图 + 长描述 + 卖家信息），
 * 抽屉能占满右侧高度，滚动更舒服；而且关掉后列表还在原位置。
 */
async function openProductDrawer(id, onDone) {
    let p;
    try {
        p = await api('GET', '/admin/product/' + id, null, { quiet: true });
    } catch (e) {
        toast(e.message || '加载失败', 'err');
        return;
    }
    const imgs = splitImages(p.images);

    const mask = document.createElement('div');
    mask.className = 'drawer-mask';
    mask.innerHTML =
        '<div class="drawer">' +
        '  <div class="d-head"><span>商品详情 · #' + p.id + '</span>' +
        '    <button class="x" data-close>&times;</button></div>' +
        '  <div class="d-body">' +

        (imgs.length
            ? '<div class="img-grid">' + imgs.map(src =>
                '<img src="' + esc(src) + '" onerror="this.style.opacity=.2">').join('') + '</div>'
            : '<div class="desc-box" style="text-align:center;color:#94a3b8">卖家没有上传图片</div>') +

        '    <div style="margin:16px 0 10px;font-size:17px;font-weight:600">' + esc(p.title || '') + '</div>' +
        '    <div style="color:#e11d48;font-size:22px;font-weight:700;margin-bottom:14px">' + money(p.price) + '</div>' +

        '    <dl class="kv" style="margin:0 0 16px">' +
        '      <dt>分类</dt><dd>' + esc(p.categoryName || '-') + '</dd>' +
        '      <dt>成色</dt><dd>' + (p.conditionLevel ? badge(({ 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '有瑕疵' })[p.conditionLevel] || ('成色' + p.conditionLevel), 'gray') : '-') + '</dd>' +
        '      <dt>卖家</dt><dd>' + esc(p.sellerNickname || ('#' + p.sellerId)) + '</dd>' +
        '      <dt>商品状态</dt><dd>' + tag('productStatus', p.status) + '</dd>' +
        '      <dt>审核状态</dt><dd>' + tag('auditStatus', p.auditStatus) + '</dd>' +
        '      <dt>热度</dt><dd>' + (p.viewCount || 0) + ' 次浏览 · ' + (p.favoriteCount || 0) + ' 人收藏</dd>' +
        '      <dt>捡漏</dt><dd>' + (p.isFlashSale === 1
            ? badge('是', 'orange') + ' <span class="muted">开抢 ' + fmtTime(p.flashStartTime) + '</span>'
            : '<span class="muted">否</span>') + '</dd>' +
        (p.auditNote ? '      <dt>驳回原因</dt><dd style="color:#dc2626">' + esc(p.auditNote) + '</dd>' : '') +
        '    </dl>' +

        '    <div style="font-size:12.5px;color:#64748b;margin-bottom:6px">商品描述</div>' +
        '    <div class="desc-box">' + (p.description ? esc(p.description) : '<span class="muted">卖家没有填写描述</span>') + '</div>' +

        (p.auditStatus === 0
            ? '<div style="display:flex;gap:9px;margin-top:20px">' +
              '  <button class="btn danger" style="flex:1;height:40px" id="d-reject">驳回</button>' +
              '  <button class="btn primary" style="flex:1;height:40px" id="d-pass">通过审核</button>' +
              '</div>'
            : '<div class="hint muted" style="margin-top:18px">这件商品已经处理过了（' +
              (p.auditStatus === 1 ? '已通过' : '已驳回') + '）</div>') +

        '  </div>' +
        '</div>';

    document.body.appendChild(mask);
    const close = () => { mask.remove(); if (onDone) onDone(); };
    mask.querySelector('[data-close]').onclick = () => { mask.remove(); };
    mask.onclick = (e) => { if (e.target === mask) { mask.remove(); } };

    const passBtn = mask.querySelector('#d-pass');
    if (passBtn) passBtn.onclick = async () => {
        await api('POST', '/admin/product/audit' + qs({ id: p.id, auditStatus: 1 }));
        toast('已通过审核', 'ok');
        close();
    };

    const rejectBtn = mask.querySelector('#d-reject');
    if (rejectBtn) rejectBtn.onclick = async () => {
        const note = prompt('驳回原因（会展示给卖家，必填）：');
        if (!note || !note.trim()) return toast('驳回必须填写原因', 'err');
        await api('POST', '/admin/product/audit' + qs({ id: p.id, auditStatus: 2, auditNote: note.trim() }));
        toast('已驳回', 'ok');
        close();
    };
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
            '<button class="btn sm" data-act="detail" data-id="' + p.id + '">详情</button>' +
            (p.auditStatus === 0
                ? '<button class="btn sm primary" data-act="pass" data-id="' + p.id + '">通过</button>' +
                  '<button class="btn sm danger" data-act="reject" data-id="' + p.id + '">驳回</button>'
                : '') +
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
        const act = b.getAttribute('data-act');
        if (act === 'detail') {
            return openProductDrawer(id, loadAudit);
        }
        if (act === 'pass') {
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
