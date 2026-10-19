/* ==========================================================================
   用户端 H5 · 单页应用（零依赖，hash 路由）
   --------------------------------------------------------------------------
   入口： http://localhost:8080/ui/user/index.html
   说明：用户端用【authentication】请求头（和后端双 token 设计一致），
        管理端用的是 token —— 这就是为什么这里有一套自己的请求封装 uapi()。
   ========================================================================== */

const U = {
    TK: 'campus.userToken',
    UI: 'campus.userInfo',
    token() { return localStorage.getItem(this.TK) || ''; },
    info() {
        try { return JSON.parse(localStorage.getItem(this.UI) || '{}'); } catch (e) { return {}; }
    },
    set(token, info) {
        localStorage.setItem(this.TK, token);
        if (info) localStorage.setItem(this.UI, JSON.stringify(info));
    },
    clear() { localStorage.removeItem(this.TK); localStorage.removeItem(this.UI); },
    ok() { return !!this.token(); }
};

/* ---------- 用户端请求封装 ---------- */
async function uapi(method, url, body, opts) {
    opts = opts || {};
    const headers = {};
    if (U.token()) headers['authentication'] = U.token();

    let payload;
    if (body !== undefined && body !== null && method !== 'GET' && method !== 'DELETE') {
        headers['Content-Type'] = 'application/json; charset=utf-8';
        payload = JSON.stringify(body);
    }

    let res;
    try {
        res = await fetch(url, { method, headers, body: payload });
    } catch (e) {
        if (!opts.quiet) toast('网络异常：' + e.message, 'err');
        throw e;
    }

    if (res.status === 401) {
        U.clear();
        toast('请先登录', 'err');
        location.hash = '#/login';
        renderAll();
        throw new Error('unauthorized');
    }

    let data;
    try { data = await res.json(); } catch (e) {
        if (!opts.quiet) toast('返回不是 JSON（HTTP ' + res.status + '）', 'err');
        throw e;
    }
    if (data.code !== 1) {
        if (!opts.quiet) toast(data.msg || '请求失败', 'err');
        const e = new Error(data.msg || 'fail');
        e.bizCode = data.code;
        throw e;
    }
    return data.data;
}

const app = document.getElementById('app');

/* ======================= 登录 ======================= */
const DEMO_USERS = [
    { openid: 'test_openid_buyer_001', name: '小明（买家）' },
    { openid: 'test_openid_seller_001', name: '学姐（卖家）' },
    { openid: 'test_openid_runner_001', name: '小张（跑腿员）' }
];

function renderLogin() {
    app.innerHTML =
        '<div class="login-page">' +
        '  <div class="login-box">' +
        '    <h1>校园二手交易平台</h1>' +
        '    <p>用户端 · 测试登录（不需要微信）</p>' +
        '    <div class="field"><label>openid</label>' +
        '      <input id="openid" placeholder="test_openid_buyer_001"></div>' +
        '    <button class="btn primary block" id="go">登录</button>' +
        '    <div style="margin-top:20px;font-size:12px;color:#8f98a6">快速体验：</div>' +
        DEMO_USERS.map(u =>
            '<button class="btn block" style="margin-top:7px;justify-content:flex-start" ' +
            'data-openid="' + u.openid + '">' + u.name + '</button>').join('') +
        '    <p style="margin:18px 0 0;text-align:center"><a href="../index.html">← 返回入口</a></p>' +
        '  </div>' +
        '</div>';

    const doLogin = async (openid) => {
        if (!openid) return toast('请输入 openid', 'err');
        try {
            const d = await uapi('GET', '/user/user/testLogin?openid=' + encodeURIComponent(openid), null, { quiet: true });
            U.set(d.token, { id: d.id, nickname: d.nickname, role: d.role, openid: openid });
            toast('欢迎，' + (d.nickname || '同学'), 'ok');
            location.hash = '#/home';
            renderAll();
        } catch (e) { toast(e.message || '登录失败', 'err'); }
    };

    document.getElementById('go').onclick = () => doLogin(document.getElementById('openid').value.trim());
    document.getElementById('openid').onkeydown = (e) => { if (e.key === 'Enter') doLogin(e.target.value.trim()); };
    app.querySelectorAll('[data-openid]').forEach(b =>
        b.onclick = () => doLogin(b.getAttribute('data-openid')));
}

/* ======================= 框架：tabbar ======================= */
let unreadTotal = 0;

async function refreshUnread() {
    if (!U.ok()) { unreadTotal = 0; return; }
    try {
        const n = await uapi('GET', '/user/message/unread', null, { quiet: true });
        unreadTotal = Number(n) || 0;
    } catch (e) { unreadTotal = 0; }
}

function tabbar(cur) {
    const t = [
        { k: 'home', n: '首页', i: '🏠' },
        { k: 'flash', n: '捡漏', i: '⚡' },
        { k: 'msg', n: '消息', i: '💬' },
        { k: 'me', n: '我的', i: '👤' }
    ];
    return '<div class="tabbar">' + t.map(x =>
        '<a href="#/' + x.k + '" class="' + (x.k === cur ? 'active' : '') + '">' +
        '<span class="ico">' + x.i + '</span><span>' + x.n + '</span>' +
        (x.k === 'msg' && unreadTotal ? '<span class="badge-dot">' + (unreadTotal > 99 ? '99+' : unreadTotal) + '</span>' : '') +
        '</a>').join('') + '</div>';
}

function topBar(title, showBack) {
    return '<div class="h5-top">' +
        (showBack ? '<span class="back" onclick="history.back()">‹</span>' : '') +
        '<h2>' + esc(title) + '</h2>' +
        (U.ok() ? '<a href="#/me" style="font-size:12px;color:#8f98a6">' + esc(U.info().nickname || '') + '</a>' : '') +
        '</div>';
}

/* ======================= 路由 ======================= */
function parseHash() {
    const h = (location.hash || '#/home').replace(/^#\/?/, '');
    const parts = h.split('/');
    return { key: parts[0] || 'home', arg: parts[1] || '' };
}

async function renderAll() {
    if (!U.ok()) return renderLogin();
    const { key, arg } = parseHash();

    if (key !== 'chat') await refreshUnread();

    if (key === 'home') return viewHome();
    if (key === 'flash') return viewFlash();
    if (key === 'msg') return viewMsg();
    if (key === 'me') return viewMe();
    if (key === 'detail') return viewDetail(arg);
    if (key === 'chat') return viewChat(arg);
    if (key === 'orders') return viewOrders();
    if (key === 'fav') return viewFav();
    if (key === 'runner') return viewRunner();
    location.hash = '#/home';
}

window.addEventListener('hashchange', renderAll);

/* ======================= 首页 ======================= */
let currentCat = '';

async function viewHome() {
    app.innerHTML =
        topBar('校园二手') +
        '<div class="h5-body">' +
        '  <div class="chips" id="cats"><span class="chip on" data-cat="">全部</span></div>' +
        '  <div id="goods"><div class="loading">加载中…</div></div>' +
        '</div>' + tabbar('home');

    // 分类
    try {
        const cats = await uapi('GET', '/user/category/list', null, { quiet: true });
        const box = document.getElementById('cats');
        if (box && cats && cats.length) {
            box.innerHTML = '<span class="chip' + (currentCat === '' ? ' on' : '') + '" data-cat="">全部</span>' +
                cats.map(c => '<span class="chip' + (String(currentCat) === String(c.id) ? ' on' : '') +
                    '" data-cat="' + c.id + '">' + esc(c.name) + '</span>').join('');
        }
    } catch (e) { }

    app.querySelectorAll('[data-cat]').forEach(b => b.onclick = () => {
        currentCat = b.getAttribute('data-cat');
        viewHome();
    });

    await loadGoods();
}

async function loadGoods() {
    const box = document.getElementById('goods');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const list = await uapi('GET', '/user/product/list' + qs({ categoryId: currentCat }));
    if (!list || !list.length) {
        box.innerHTML = '<div class="h5-empty">这个分类下还没有在售商品</div>';
        return;
    }
    box.innerHTML = '<div class="goods-grid">' + list.map(goodsCard).join('') + '</div>';
    box.querySelectorAll('[data-pid]').forEach(b =>
        b.onclick = () => { location.hash = '#/detail/' + b.getAttribute('data-pid'); });
}

function firstImg(images) {
    if (!images) return '';
    const f = String(images).split(',')[0].trim();
    if (!f) return '';
    return /^https?:/.test(f) ? f : '/' + f.replace(/^\/+/, '');
}

function goodsCard(p) {
    const img = firstImg(p.images);
    return '<div class="goods" data-pid="' + p.id + '">' +
        '<div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '📦') + '</div>' +
        '<div class="info">' +
        '<div class="t">' + esc(p.title || '') + '</div>' +
        '<div class="p"><small>¥</small>' + Number(p.price || 0).toFixed(2) + '</div>' +
        '<div class="tags">' + (p.isFlashSale === 1 ? badge('捡漏', 'orange') : '') +
        (p.conditionLevel ? badge(conditionText(p.conditionLevel), 'gray') : '') + '</div>' +
        '</div></div>';
}

function conditionText(lv) {
    return ({ 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '有瑕疵' })[lv] || ('成色' + lv);
}

/* ======================= 商品详情 ======================= */
async function viewDetail(id) {
    app.innerHTML = topBar('商品详情', true) + '<div class="h5-body"><div class="loading">加载中…</div></div>';
    let p;
    try {
        p = await uapi('GET', '/user/product/' + id);
    } catch (e) {
        app.querySelector('.h5-body').innerHTML = '<div class="h5-empty">商品不存在或已下架</div>';
        return;
    }
    const img = firstImg(p.images);
    const isMine = Number(p.sellerId) === Number(U.info().id);

    app.innerHTML =
        topBar('商品详情', true) +
        '<div class="h5-body">' +
        '  <div class="detail-pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '📦') + '</div>' +
        '  <div class="detail-price"><small>¥</small>' + Number(p.price || 0).toFixed(2) + '</div>' +
        '  <div class="detail-title">' + esc(p.title || '') + '</div>' +
        '  <div style="margin-top:9px;display:flex;gap:6px;flex-wrap:wrap">' +
        tag('productStatus', p.status) +
        (p.isFlashSale === 1 ? badge('限时捡漏', 'orange') : '') +
        (p.conditionLevel ? badge(conditionText(p.conditionLevel), 'gray') : '') +
        '</div>' +
        '  <div class="row" style="margin-top:14px">' +
        '    <div class="r-head"><span><b>' + esc(p.sellerNickname || p.sellerName || '卖家') + '</b></span>' +
        '      <span class="muted" style="font-size:12px">' + esc(p.categoryName || '') + '</span></div>' +
        '    <div style="color:#5a6472;font-size:13px;line-height:1.7">' + esc(p.description || '（卖家没有填写描述）') + '</div>' +
        '  </div>' +
        '  <div style="margin-top:14px"><b style="font-size:13.5px">商品评价</b><div id="revs" style="margin-top:8px"></div></div>' +
        '  ' + (isMine ? '<div class="h5-empty">这是你自己发布的商品</div>' : '<div style="height:56px"></div>') +
        '</div>' +
        (isMine ? tabbar('home') :
            '<div class="actionbar">' +
            '  <button class="btn" id="fav">收藏</button>' +
            '  <button class="btn" id="bargain">议价</button>' +
            '  <button class="btn" id="chat">私信</button>' +
            '  <button class="btn primary" id="buy">立即购买</button>' +
            '</div>');

    // 评价
    try {
        const revs = await uapi('GET', '/user/review/product/' + id, null, { quiet: true });
        setHTML('revs', (revs && revs.length)
            ? revs.slice(0, 5).map(r =>
                '<div class="row" style="padding:10px 12px"><div class="r-head">' +
                '<span style="font-size:13px"><b>' + esc(r.fromNickname || '用户') + '</b> ' + stars(r.star) + '</span>' +
                '<span class="muted" style="font-size:11px">' + fmtTime(r.createTime) + '</span></div>' +
                '<div style="font-size:13px;color:#5a6472">' + esc(r.content || '（未填写内容）') + '</div></div>').join('')
            : '<div class="h5-empty" style="padding:16px 0">还没有人评价</div>');
    } catch (e) { setHTML('revs', ''); }

    const favBtn = document.getElementById('fav');
    if (favBtn) {
        favBtn.onclick = async () => {
            try {
                await uapi('POST', '/user/favorite?productId=' + id);
                toast('已收藏', 'ok');
            } catch (e) {
                try { await uapi('DELETE', '/user/favorite?productId=' + id); toast('已取消收藏', 'ok'); }
                catch (e2) { }
            }
        };
    }

    const chatBtn = document.getElementById('chat');
    if (chatBtn) chatBtn.onclick = async () => {
        const cid = await uapi('POST', '/user/message/conversation?productId=' + id);
        location.hash = '#/chat/' + cid;
    };

    const bargainBtn = document.getElementById('bargain');
    if (bargainBtn) bargainBtn.onclick = () => openBargain(p);

    const buyBtn = document.getElementById('buy');
    if (buyBtn) buyBtn.onclick = () => openBuy(p);
}

/* ---------- 议价 ---------- */
function openBargain(p) {
    const mask = document.createElement('div');
    mask.className = 'sheet-mask';
    mask.innerHTML =
        '<div class="sheet">' +
        '  <h3>我要议价</h3>' +
        '  <div class="field"><label>我的出价（卖家要价 ¥' + Number(p.price || 0).toFixed(2) + '）</label>' +
        '    <input id="offer" type="number" placeholder="输入你愿意出的价格"></div>' +
        '  <div class="hint" style="font-size:12px;color:#8f98a6;margin-bottom:14px">' +
        '    出价不能低于原价的 50%；卖家 24 小时内没回应会自动过期（不扣信用分）。</div>' +
        '  <div style="display:flex;gap:9px">' +
        '    <button class="btn" style="flex:1" data-close>取消</button>' +
        '    <button class="btn primary" style="flex:1" id="send">提交出价</button>' +
        '  </div></div>';
    document.body.appendChild(mask);
    mask.querySelector('[data-close]').onclick = () => mask.remove();
    mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
    mask.querySelector('#send').onclick = async () => {
        const offer = Number(mask.querySelector('#offer').value);
        if (!offer) return toast('请输入出价', 'err');
        await uapi('POST', '/user/bargain', { productId: p.id, buyerOffer: offer });
        toast('出价已发送，等卖家回应', 'ok');
        mask.remove();
    };
}

/* ---------- 下单 ---------- */
function openBuy(p) {
    const mask = document.createElement('div');
    mask.className = 'sheet-mask';
    mask.innerHTML =
        '<div class="sheet">' +
        '  <h3>确认下单</h3>' +
        '  <div class="field"><label>交付方式</label>' +
        '    <select id="deal"><option value="0">线下自提（免费）</option>' +
        '      <option value="1">校园跑腿帮送（收跑腿费）</option></select></div>' +
        '  <div class="field" id="self-box"><label>自提地点</label>' +
        '    <input id="pickupPlace" placeholder="例如：3 号宿舍楼下"></div>' +
        '  <div id="dlv-box" style="display:none">' +
        '    <div class="field"><label>取货地址</label><input id="pickAddress" placeholder="例如：3 号宿舍楼下"></div>' +
        '    <div class="field"><label>送达地址</label><input id="receiveAddress" placeholder="例如：教学楼 B 座"></div>' +
        '    <div class="field"><label>距离档位</label><select id="dist">' +
        '      <option value="1">校园内近（约 1 km，跑腿费 ¥5）</option>' +
        '      <option value="2">中等（约 5 km，跑腿费 ¥7）</option>' +
        '      <option value="3">较远（约 10 km，跑腿费 ¥12）</option>' +
        '    </select></div>' +
        '  </div>' +
        '  <div class="field"><label>备注（选填）</label><input id="remark" placeholder="例如：晚上 7 点后都可以"></div>' +
        '  <div class="hint" style="font-size:12px;color:#8f98a6;margin-bottom:14px">' +
        '    下单后商品会锁定为"交易中"；30 分钟内卖家不确认会自动取消（这条规则由定时任务兜底）。</div>' +
        '  <div style="display:flex;gap:9px">' +
        '    <button class="btn" style="flex:1" data-close>取消</button>' +
        '    <button class="btn primary" style="flex:1" id="ok">提交并支付</button>' +
        '  </div></div>';
    document.body.appendChild(mask);
    const $ = (s) => mask.querySelector(s);
    $('[data-close]').onclick = () => mask.remove();
    mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
    $('#deal').onchange = () => {
        const isDlv = $('#deal').value === '1';
        $('#self-box').style.display = isDlv ? 'none' : '';
        $('#dlv-box').style.display = isDlv ? '' : 'none';
    };

    $('#ok').onclick = async () => {
        const dealType = Number($('#deal').value);
        const body = {
            productId: p.id,
            dealType: dealType,
            remark: $('#remark').value.trim()
        };
        if (dealType === 0) {
            body.pickupPlace = $('#pickupPlace').value.trim();
            if (!body.pickupPlace) return toast('请填写自提地点', 'err');
        } else {
            body.pickAddress = $('#pickAddress').value.trim();
            body.receiveAddress = $('#receiveAddress').value.trim();
            body.distanceLevel = Number($('#dist').value);
            if (!body.pickAddress || !body.receiveAddress) return toast('请填写取货和送达地址', 'err');
        }
        const btn = $('#ok');
        btn.disabled = true;
        try {
            const vo = await uapi('POST', '/user/order/submit', body);
            mask.remove();
            toast('下单成功，正在支付…', 'ok');
            await uapi('POST', '/user/order/pay?orderNo=' + encodeURIComponent(vo.orderNo));
            toast('支付成功（模拟通道）', 'ok');
            location.hash = '#/orders';
        } catch (e) { btn.disabled = false; }
    };
}

/* ======================= 捡漏 ======================= */
async function viewFlash() {
    app.innerHTML = topBar('限时捡漏') +
        '<div class="h5-body">' +
        '  <div style="background:#fff8ec;border:1px solid #f7e2c0;border-radius:10px;padding:11px 12px;font-size:12.5px;color:#a06a1f;margin-bottom:12px;line-height:1.6">' +
        '    ⚡ 捡漏商品每人只能抢一次。抢购用 Redis 预扣库存 + 数据库兜底 —— 同一秒几十个人抢，也只有一个人能成功。' +
        '  </div>' +
        '  <div id="flash-list"><div class="loading">加载中…</div></div>' +
        '</div>' + tabbar('flash');

    const box = document.getElementById('flash-list');
    const list = await uapi('GET', '/user/flashSale/list');
    if (!list || !list.length) {
        box.innerHTML = '<div class="h5-empty">现在没有正在捡漏的商品</div>';
        return;
    }
    box.innerHTML = list.map(f => {
        const img = firstImg(f.images);
        const started = !f.flashStartTime || new Date(String(f.flashStartTime).replace(' ', 'T')) <= new Date();
        const sold = f.status === 1;
        return '<div class="row">' +
            '<div class="r-main">' +
            '  <div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '⚡') + '</div>' +
            '  <div class="t"><b>' + esc(f.title || '') + '</b>' +
            '    <small>' + esc(f.sellerName || f.sellerNickname || '') + ' · ' + conditionText(f.conditionLevel) + '</small>' +
            '    <div style="color:#e5484d;font-weight:600;font-size:15px;margin-top:3px">¥' + Number(f.price || 0).toFixed(2) + '</div>' +
            '  </div>' +
            '</div>' +
            '<div class="r-actions">' +
            (sold ? badge('已被抢走', 'gray') :
                !started ? badge('未开始', 'orange') :
                    '<button class="btn primary" data-grab="' + f.id + '">立即抢</button>') +
            '</div></div>';
    }).join('');

    box.querySelectorAll('[data-grab]').forEach(b => b.onclick = async () => {
        const pid = b.getAttribute('data-grab');
        b.disabled = true;
        try {
            await uapi('POST', '/user/flashSale/' + pid);
            toast('抢到了！去订单列表看看', 'ok');
            location.hash = '#/orders';
        } catch (e) { b.disabled = false; }
    });
}

/* ======================= 消息 ======================= */
async function viewMsg() {
    app.innerHTML = topBar('消息') + '<div class="h5-body"><div id="convs"><div class="loading">加载中…</div></div></div>' + tabbar('msg');
    const box = document.getElementById('convs');
    const list = await uapi('GET', '/user/message/conversation/list');

    if (!list || !list.length) {
        box.innerHTML = '<div class="h5-empty">还没有会话<br><small>去商品详情页点"私信"就能开始聊</small></div>';
        return;
    }
    box.innerHTML = list.map(c =>
        '<div class="row" data-cid="' + c.id + '" style="cursor:pointer">' +
        '<div class="r-main">' +
        '  <div class="pic">' + esc((c.otherNickname || '?').substring(0, 1)) + '</div>' +
        '  <div class="t"><b>' + esc(c.otherNickname || '对方') + '</b>' +
        '    <small>' + esc(c.productTitle || '') + '</small>' +
        '    <div class="muted" style="font-size:12px;margin-top:3px">' + esc(c.lastMessage || '（暂无消息）') + '</div>' +
        '  </div>' +
        '  <div style="text-align:right;font-size:11px;color:#8f98a6">' +
        '    <div>' + fmtTime(c.lastMessageTime) + '</div>' +
        (c.unreadCount ? '<div style="margin-top:5px">' + badge(c.unreadCount + ' 条未读', 'red') + '</div>' : '') +
        '  </div>' +
        '</div></div>').join('');

    box.querySelectorAll('[data-cid]').forEach(b =>
        b.onclick = () => { location.hash = '#/chat/' + b.getAttribute('data-cid'); });
}

let chatTimer = null;

async function viewChat(cid) {
    clearInterval(chatTimer);
    app.innerHTML =
        topBar('聊天', true) +
        '<div class="h5-body" style="padding-bottom:64px"><div id="msgs" class="chat-wrap"><div class="loading">加载中…</div></div></div>' +
        '<div class="chat-input"><input id="txt" placeholder="说点什么…"><button class="btn primary" id="send">发送</button></div>';

    const load = async () => {
        try {
            const d = await uapi('GET', '/user/message/history' + qs({ conversationId: cid, page: 1, pageSize: 50 }), null, { quiet: true });
            const rows = (d && d.records) ? d.records.slice().reverse() : [];
            const me = Number(U.info().id);
            setHTML('msgs', rows.length ? rows.map(m =>
                '<div class="bubble ' + (Number(m.fromUserId) === me ? 'me' : 'other') + '">' +
                esc(m.content || '') + '<span class="tm">' + fmtTime(m.createTime) + '</span></div>').join('')
                : '<div class="h5-empty">还没有消息，打个招呼吧</div>');
            const wrap = document.getElementById('msgs');
            if (wrap) window.scrollTo(0, document.body.scrollHeight);
            await uapi('PUT', '/user/message/read/' + cid, null, { quiet: true });
        } catch (e) { }
    };

    document.getElementById('send').onclick = async () => {
        const el = document.getElementById('txt');
        const content = el.value.trim();
        if (!content) return;
        el.value = '';
        try {
            await uapi('POST', '/user/message/send', { conversationId: Number(cid), content });
            load();
        } catch (e) { el.value = content; }
    };
    document.getElementById('txt').onkeydown = (e) => { if (e.key === 'Enter') document.getElementById('send').click(); };

    await load();
    chatTimer = setInterval(load, 3000);   // 简易轮询（后端也支持 WebSocket 推送）
}

/* ======================= 我的 ======================= */
async function viewMe() {
    const info = U.info();
    app.innerHTML = topBar('我的') + '<div class="h5-body"><div id="me-box"><div class="loading">加载中…</div></div></div>' + tabbar('me');

    let credit = {};
    try { credit = await uapi('GET', '/user/credit/my', null, { quiet: true }) || {}; } catch (e) { }

    const box = document.getElementById('me-box');
    box.innerHTML =
        '<div class="me-card">' +
        '  <div class="n">' + esc(info.nickname || '同学') + '</div>' +
        '  <div class="s">用户 id：' + esc(info.id || '-') + '</div>' +
        '  <div class="credit">' +
        '    <div><b>' + (credit.creditScore === undefined ? '-' : credit.creditScore) + '</b><span>信用分</span></div>' +
        '    <div><b>' + (credit.goodReviewCount || 0) + '</b><span>好评</span></div>' +
        '    <div><b>' + (credit.badReviewCount || 0) + '</b><span>差评</span></div>' +
        '    <div><b>' + (credit.todayAdded || 0) + '/' + (credit.dailyAddLimit || 10) + '</b><span>今日已加</span></div>' +
        '  </div>' +
        '</div>' +

        '<div class="menu-list" style="margin-bottom:12px">' +
        '  <a href="#/orders">我的订单 <span class="chev">›</span></a>' +
        '  <a href="#/fav">我的收藏 <span class="chev">›</span></a>' +
        '  <a href="#/runner">跑腿大厅（抢单）<span class="chev">›</span></a>' +
        '  <a href="#" id="credit-log">信用分流水 <span class="chev">›</span></a>' +
        '</div>' +

        '<div class="menu-list">' +
        '  <div class="item" id="logout" style="color:#e5484d">退出登录</div>' +
        '</div>' +
        '<div class="h5-empty" style="padding:22px 0;font-size:12px">' +
        '  信用分规则：成交 +2 · 好评 +1 · 差评 -2 · 超时爽约 -5 · 投诉成立 -10<br>' +
        '  每日加分上限 10 分（补偿性加分不受限）' +
        '</div>';

    document.getElementById('logout').onclick = () => {
        U.clear();
        toast('已退出', 'ok');
        location.hash = '#/login';
        renderAll();
    };
    document.getElementById('credit-log').onclick = async (e) => {
        e.preventDefault();
        const logs = await uapi('GET', '/user/credit/logs', null, { quiet: true }) || [];
        showSheet('信用分流水', logs.length
            ? logs.map(l => '<div class="row" style="padding:10px 12px">' +
                '<div class="r-head"><span style="font-size:13px">' + esc(l.reasonName || l.reasonType) + '</span>' +
                '<span style="color:' + (l.changeScore > 0 ? '#16a34a' : '#e5484d') + ';font-weight:600">' +
                (l.changeScore > 0 ? '+' : '') + l.changeScore + '</span></div>' +
                '<div class="muted" style="font-size:11.5px">' + fmtTime(l.createTime) +
                (l.remark ? ' · ' + esc(l.remark) : '') + '</div></div>').join('')
            : '<div class="h5-empty">还没有流水</div>');
    };
}

function showSheet(title, html) {
    const mask = document.createElement('div');
    mask.className = 'sheet-mask';
    mask.innerHTML = '<div class="sheet"><h3>' + esc(title) + '</h3>' +
        '<div style="max-height:60vh;overflow:auto">' + html + '</div>' +
        '<button class="btn block" style="margin-top:14px" data-close>关闭</button></div>';
    document.body.appendChild(mask);
    mask.querySelector('[data-close]').onclick = () => mask.remove();
    mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
}

/* ======================= 订单 ======================= */
let orderView = 'buyer';

async function viewOrders() {
    app.innerHTML = topBar('我的订单', true) +
        '<div class="h5-body">' +
        '  <div class="chips" id="ov">' +
        '    <span class="chip' + (orderView === 'buyer' ? ' on' : '') + '" data-v="buyer">我买的</span>' +
        '  </div>' +
        '  <div id="order-list"><div class="loading">加载中…</div></div>' +
        '</div>';

    // 卖家视角也放进来
    document.getElementById('ov').innerHTML =
        '<span class="chip' + (orderView === 'buyer' ? ' on' : '') + '" data-v="buyer">我买的</span>' +
        '<span class="chip' + (orderView === 'seller' ? ' on' : '') + '" data-v="seller">我卖的</span>';

    app.querySelectorAll('[data-v]').forEach(b => b.onclick = () => {
        orderView = b.getAttribute('data-v');
        viewOrders();
    });
    await loadOrders();
}

async function loadOrders() {
    const box = document.getElementById('order-list');
    if (!box) return;
    box.innerHTML = '<div class="loading">加载中…</div>';
    const d = await uapi('GET', '/user/order/page' + qs({ page: 1, pageSize: 30, view: orderView }));
    const rows = (d && d.records) || [];
    if (!rows.length) {
        box.innerHTML = '<div class="h5-empty">还没有订单</div>';
        return;
    }
    const me = Number(U.info().id);
    box.innerHTML = rows.map(o => {
        const st = o.status;
        const img = firstImg(o.productImages);
        let actions = '';
        if (orderView === 'seller' && st === 0) {
            actions = '<button class="btn primary" data-act="confirm" data-id="' + o.id + '">确认交易</button>' +
                '<button class="btn" data-act="reject" data-id="' + o.id + '">拒绝</button>';
        }
        if (orderView === 'buyer' && st === 0) {
            actions = '<button class="btn danger" data-act="cancel" data-id="' + o.id + '">取消订单</button>';
        }
        if (orderView === 'buyer' && st === 1) {
            actions = '<button class="btn primary" data-act="complete" data-id="' + o.id + '">确认收货</button>' +
                '<button class="btn danger" data-act="cancel" data-id="' + o.id + '">取消</button>';
        }
        if (st === 2) {
            actions = '<button class="btn" data-act="review" data-id="' + o.id + '">评价</button>';
        }
        return '<div class="row">' +
            '<div class="r-head"><span class="no">' + esc(o.orderNo || ('#' + o.id)) + '</span>' +
            tag('productStatus', st) + '</div>' +
            '<div class="r-main">' +
            '  <div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '📦') + '</div>' +
            '  <div class="t"><b>' + esc(o.productTitle || ('商品 #' + o.productId)) + '</b>' +
            '    <small>' + (orderView === 'buyer' ? '卖家：' + esc(o.sellerNickname || '-') : '买家：' + esc(o.buyerNickname || '-')) + '</small>' +
            '    <div style="color:#e5484d;font-weight:600;margin-top:3px">¥' + Number(o.realPrice || 0).toFixed(2) + '</div>' +
            '  </div>' +
            '</div>' +
            (actions ? '<div class="r-actions">' + actions + '</div>' : '') +
            '</div>';
    }).join('');

    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const act = b.getAttribute('data-act');
        const id = b.getAttribute('data-id');
        try {
            if (act === 'confirm') { await uapi('PUT', '/user/order/confirm/' + id); toast('已确认，等买家收货', 'ok'); }
            else if (act === 'complete') { await uapi('PUT', '/user/order/complete/' + id); toast('已确认收货，去给对方评价吧', 'ok'); }
            else if (act === 'reject') {
                await uapi('PUT', '/user/order/reject', { id: Number(id), reason: '卖家拒绝交易' });
                toast('已拒绝', 'ok');
            } else if (act === 'cancel') {
                if (!confirm('确定取消这笔订单？')) return;
                await uapi('PUT', '/user/order/cancel', { id: Number(id), reason: '买家主动取消' });
                toast('已取消', 'ok');
            } else if (act === 'review') {
                return openReview(id);
            }
            loadOrders();
            refreshUnread();
        } catch (e) { }
    });
}

/* ---------- 评价 ---------- */
function openReview(orderId) {
    const mask = document.createElement('div');
    mask.className = 'sheet-mask';
    mask.innerHTML =
        '<div class="sheet"><h3>评价这笔交易</h3>' +
        '<div class="field"><label>星级</label><select id="star">' +
        '<option value="5">5 星 · 非常满意（对方 +1）</option>' +
        '<option value="4">4 星 · 满意（对方 +1）</option>' +
        '<option value="3">3 星 · 一般（不加不减）</option>' +
        '<option value="2">2 星 · 不满意（对方 -2）</option>' +
        '<option value="1">1 星 · 很差（对方 -2）</option>' +
        '</select></div>' +
        '<div class="field"><label>评价内容</label><textarea id="content" placeholder="说说这次交易体验…"></textarea></div>' +
        '<div style="display:flex;gap:9px"><button class="btn" style="flex:1" data-close>取消</button>' +
        '<button class="btn primary" style="flex:1" id="ok">提交评价</button></div></div>';
    document.body.appendChild(mask);
    mask.querySelector('[data-close]').onclick = () => mask.remove();
    mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
    mask.querySelector('#ok').onclick = async () => {
        const star = Number(mask.querySelector('#star').value);
        const content = mask.querySelector('#content').value.trim();
        if (!content) return toast('写点内容吧', 'err');
        await uapi('POST', '/user/review', { orderId: Number(orderId), star, content });
        toast('评价成功，信用分已联动', 'ok');
        mask.remove();
        loadOrders();
    };
}

/* ======================= 收藏 ======================= */
async function viewFav() {
    app.innerHTML = topBar('我的收藏', true) + '<div class="h5-body"><div id="fav-list"><div class="loading">加载中…</div></div></div>';
    const box = document.getElementById('fav-list');
    const list = await uapi('GET', '/user/favorite/list');
    box.innerHTML = (list && list.length)
        ? '<div class="goods-grid">' + list.map(goodsCard).join('') + '</div>'
        : '<div class="h5-empty">还没有收藏任何商品</div>';
    box.querySelectorAll('[data-pid]').forEach(b =>
        b.onclick = () => { location.hash = '#/detail/' + b.getAttribute('data-pid'); });
}

/* ======================= 跑腿大厅 ======================= */
async function viewRunner() {
    app.innerHTML = topBar('跑腿大厅', true) +
        '<div class="h5-body">' +
        '  <div class="chips">' +
        '    <span class="chip on" id="tab-avail">可抢的单</span>' +
        '    <span class="chip" id="tab-mine">我的跑腿</span>' +
        '  </div>' +
        '  <div id="runner-list"><div class="loading">加载中…</div></div>' +
        '</div>';

    document.getElementById('tab-avail').onclick = () => { loadRunner('avail'); };
    document.getElementById('tab-mine').onclick = () => { loadRunner('mine'); };
    await loadRunner('avail');
}

async function loadRunner(which) {
    const box = document.getElementById('runner-list');
    box.innerHTML = '<div class="loading">加载中…</div>';
    const list = which === 'avail'
        ? await uapi('GET', '/user/delivery/available')
        : await uapi('GET', '/user/delivery/my');

    if (!list || !list.length) {
        box.innerHTML = '<div class="h5-empty">' +
            (which === 'avail' ? '现在没有可抢的单' : '你还没有接过单<br><small>（成为跑腿员才能抢单，可以让管理员在后台给你开权限）</small>') +
            '</div>';
        return;
    }

    box.innerHTML = list.map(d =>
        '<div class="row">' +
        '<div class="r-head"><span class="no">#' + d.id + '</span>' + tag('deliveryStatus', d.status) + '</div>' +
        '<div style="font-size:13px;line-height:1.8">' +
        '  <div>📦 ' + esc(d.pickAddress || '-') + '</div>' +
        '  <div>🏫 ' + esc(d.receiveAddress || '-') + '</div>' +
        '  <div class="muted" style="font-size:12px">' + esc(d.productTitle || '') + '</div>' +
        '</div>' +
        '<div class="r-actions">' +
        '<span style="color:#e5484d;font-weight:600;margin-right:auto">¥' + Number(d.deliveryFee || 0).toFixed(2) + '</span>' +
        (which === 'avail' ? '<button class="btn primary" data-grab="' + d.id + '">抢单</button>' :
            (d.status === 1 ? '<button class="btn primary" data-next="pickup" data-id="' + d.id + '">已取货</button>' : '') +
            (d.status === 2 ? '<button class="btn primary" data-next="finish" data-id="' + d.id + '">已送达</button>' : '')) +
        '</div></div>').join('');

    box.querySelectorAll('[data-grab]').forEach(b => b.onclick = async () => {
        b.disabled = true;
        try {
            await uapi('PUT', '/user/delivery/accept/' + b.getAttribute('data-grab'));
            toast('抢到了！', 'ok');
            loadRunner('mine');
        } catch (e) { b.disabled = false; }
    });
    box.querySelectorAll('[data-next]').forEach(b => b.onclick = async () => {
        const id = b.getAttribute('data-id');
        const step = b.getAttribute('data-next');
        b.disabled = true;
        try {
            if (step === 'pickup') { await uapi('PUT', '/user/delivery/pickup/' + id); toast('已标记取货', 'ok'); }
            else { await uapi('PUT', '/user/delivery/finish/' + id); toast('已送达，跑腿费计入收入', 'ok'); }
            loadRunner('mine');
        } catch (e) { b.disabled = false; }
    });
}

/* ======================= 启动 ======================= */
renderAll();
