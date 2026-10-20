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
/* 演示账号（都是 demo_openid_，各自带商品/订单/私信/议价，登进去不会空空如也） */
const DEMO_USERS = [
    { openid: 'demo_openid_1000', name: '林小满（有 2 件在卖 · 8 条私信）' },
    { openid: 'demo_openid_1001', name: '陈知行（有在卖 · 有人向他议价）' },
    { openid: 'demo_openid_1009', name: '韩风（跑腿员 · 可以抢单）' }
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
        { k: 'home', n: '首页', i: 'home' },
        { k: 'flash', n: '捡漏', i: 'bolt' },
        { k: 'msg', n: '消息', i: 'message' },
        { k: 'me', n: '我的', i: 'user' }
    ];
    return '<div class="tabbar">' + t.map(x =>
        '<a href="#/' + x.k + '" class="' + (x.k === cur ? 'active' : '') + '">' +
        icon(x.i) + '<span>' + x.n + '</span>' +
        (x.k === 'msg' && unreadTotal ? '<span class="badge-dot">' + (unreadTotal > 99 ? '99+' : unreadTotal) + '</span>' : '') +
        '</a>').join('') + '</div>';
}

function topBar(title, showBack) {
    return '<div class="h5-top">' +
        (showBack ? '<span class="back" onclick="history.back()">' + icon('arrow') + '</span>' : '') +
        '<h2>' + esc(title) + '</h2>' +
        (U.ok() ? '<span class="who">' + esc(U.info().nickname || '同学') + '</span>' : '') +
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
    if (key === 'publish') return viewPublish(arg);
    if (key === 'mine') return viewMine();
    if (key === 'bargain') return viewBargain();
    location.hash = '#/home';
}

window.addEventListener('hashchange', renderAll);

/* ======================= 首页 ======================= */
let currentCat = '';
let searchKw = '';

async function viewHome() {
    app.innerHTML =
        topBar('校园二手') +
        '<div class="h5-body">' +
        '  <div class="hero">' +
        '    <h1>把旧物交给需要的人</h1>' +
        '    <p>毕业季的旧书、旧电脑，同校交易更靠谱</p>' +
        '    <div class="search">' + icon('search') +
        '      <input id="kw" placeholder="搜商品标题，回车搜索">' +
        '    </div>' +
        '    <div class="quick">' +
        '      <a href="#/publish">' + icon('plus') + ' 发布二手</a>' +
        '      <a href="#/orders">' + icon('box') + ' 我的订单</a>' +
        '      <a href="#/bargain">' + icon('money') + ' 我的议价</a>' +
        '    </div>' +
        '  </div>' +
        // 热门榜：买家看"大家都在买"，卖家看"最近什么好卖"
        '  <div id="hot-home" style="display:none"></div>' +
        '  <div class="sec-head">' +
        '    <span class="sec-title">' + icon('box', 15) + ' 按分类逛</span>' +
        '    <span class="sec-hint">点一下只看这个分类</span>' +
        '  </div>' +
        '  <div class="chips" id="cats"><span class="chip on" data-cat="">全部</span></div>' +
        '  <div id="goods"><div class="loading">加载中…</div></div>' +
        '</div>' + tabbar('home');

    loadHotHome();

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
        searchKw = '';
        viewHome();
    });

    // 搜索：后端 list 接口一次返回全部在售商品（不分页），所以在前端过滤
    const kwInput = document.getElementById('kw');
    if (kwInput) {
        kwInput.value = searchKw;
        kwInput.onkeydown = (e) => {
            if (e.key === 'Enter') {
                searchKw = kwInput.value.trim();
                loadGoods();
            }
        };
    }

    await loadGoods();
}

async function loadGoods() {
    const box = document.getElementById('goods');
    if (!box) return;
    box.innerHTML = '<div class="sk-card"><div class="sk-line skeleton" style="width:70%"></div>' +
        '<div class="sk-line skeleton" style="width:40%"></div></div>';
    let list = await uapi('GET', '/user/product/list' + qs({ categoryId: currentCat }));
    if (searchKw) {
        const k = searchKw.toLowerCase();
        list = (list || []).filter(g => (g.title || '').toLowerCase().includes(k) ||
            (g.description || '').toLowerCase().includes(k));
    }
    if (!list || !list.length) {
        box.innerHTML = '<div class="h5-empty"><div class="art">' + icon('empty') + '</div>' +
            '<b>' + (searchKw ? '没有搜到" ' + esc(searchKw) + ' "' : '这个分类下还没有在售商品') + '</b>' +
            '<div>换个关键词，或者发一件试试</div></div>';
        return;
    }
    box.innerHTML = '<div class="goods-grid">' + list.map(goodsCard).join('') + '</div>';
    box.querySelectorAll('[data-pid]').forEach(b =>
        b.onclick = () => { location.hash = '#/detail/' + b.getAttribute('data-pid'); });
}

function firstImg(images) {
    const list = imgList(images);
    return list.length ? list[0] : '';
}

/**
 * images 字段 → 图片地址数组
 *
 * ⚠️ 这里要兼容两种历史格式（踩过坑）：
 *   ① JSON 数组字符串： ["https://a.jpg","https://b.jpg"]   ← 数据库里的老数据 / 建表注释约定的格式
 *   ② 逗号分隔：        https://a.jpg,https://b.jpg          ← 早期前端发布时写的格式
 * 只按逗号拆的话，JSON 数组会被拆成 `["https://a.jpg"` 这种残缺串，图片全部加载失败。
 * 所以：先尝试 JSON.parse，失败再退回逗号拆分。
 */
function imgList(images) {
    if (!images) return [];
    const raw = String(images).trim();
    let arr = null;
    if (raw.startsWith('[')) {
        try { arr = JSON.parse(raw); } catch (e) { arr = null; }
    }
    if (!Array.isArray(arr)) {
        arr = raw.split(',');
    }
    return arr.map(x => String(x).trim().replace(/^["'\s]+|["'\s]+$/g, ''))
        .filter(Boolean)
        .map(x => /^https?:/.test(x) ? x : '/' + x.replace(/^\/+/, ''));
}

/** 多图轮播（横滑 + 指示点）。只有一张图时不显示指示点 */
function carouselHTML(images) {
    const list = imgList(images);
    if (!list.length) {
        return '<div class="carousel"><div class="slide">' + icon('box') + '</div></div>';
    }
    return '<div class="carousel">' +
        '<div class="track" id="car-track">' +
        list.map(src => '<div class="slide"><img src="' + esc(src) + '" onerror="this.style.display=\'none\'"></div>').join('') +
        '</div>' +
        (list.length > 1
            ? '<div class="dots" id="car-dots">' + list.map((_, i) => '<i' + (i === 0 ? ' class="on"' : '') + '></i>').join('') + '</div>'
            : '') +
        '</div>';
}

/** 绑定轮播的指示点跟随（横滑时高亮当前图） */
function bindCarousel() {
    const track = document.getElementById('car-track');
    const dots = document.getElementById('car-dots');
    if (!track || !dots) return;
    const marks = Array.from(dots.children);
    track.onscroll = () => {
        const idx = Math.round(track.scrollLeft / track.clientWidth);
        marks.forEach((m, i) => { m.className = i === idx ? 'on' : ''; });
    };
}

function goodsCard(p) {
    const img = firstImg(p.images);
    const views = p.viewCount || 0;
    const favs = p.favoriteCount || 0;
    return '<div class="goods" data-pid="' + p.id + '">' +
        (p.isFlashSale === 1 ? '<span class="flash-flag">⚡ 捡漏</span>' : '') +
        '<div class="pic">' + (img
            ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">'
            : icon('box')) + '</div>' +
        '<div class="info">' +
        '<div class="t">' + esc(p.title || '') + '</div>' +
        '<div class="p"><small>¥</small>' + Number(p.price || 0).toFixed(2) + '</div>' +
        '<div class="tags">' + (p.conditionLevel ? badge(conditionText(p.conditionLevel), 'gray') : '') +
        (p.status === 1 ? badge('交易中', 'blue') : '') + '</div>' +
        // 热度：让买家一眼看出"这件东西有没有人关注"
        '<div class="card-heat">' +
        '<span>' + icon('user', 12) + ' ' + views + '</span>' +
        '<span>' + icon('heart', 12) + ' ' + favs + '</span>' +
        '</div>' +
        '</div></div>';
}

function conditionText(lv) {
    return ({ 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '有瑕疵' })[lv] || ('成色' + lv);
}

/* ---------- 时间小工具：倒计时 / 开抢时间 ---------- */
function parseTime(v) {
    if (!v) return null;
    const s = String(v).replace('T', ' ').replace(/-/g, '/');
    const d = new Date(s);
    return isNaN(d.getTime()) ? null : d;
}

/** 距离某个时间还有多久（中文，粒度到分钟） */
function untilText(target) {
    const t = parseTime(target);
    if (!t) return '';
    const ms = t.getTime() - Date.now();
    if (ms <= 0) return '';
    const min = Math.floor(ms / 60000);
    if (min < 1) return '不到 1 分钟';
    if (min < 60) return min + ' 分钟';
    const hour = Math.floor(min / 60);
    if (hour < 24) return hour + ' 小时' + (min % 60 ? (min % 60) + ' 分钟' : '');
    return Math.floor(hour / 24) + ' 天' + (hour % 24 ? (hour % 24) + ' 小时' : '');
}

/** 捡漏开抢状态：未开始 / 已开抢 */
function flashState(f) {
    const t = parseTime(f.flashStartTime);
    if (!t) return { started: true, text: '已开抢', cls: 'green' };
    if (t.getTime() <= Date.now()) return { started: true, text: '已开抢', cls: 'green' };
    return { started: false, text: '未开始', cls: 'orange' };
}

/** 详情页的捡漏横幅：开抢时间 + 倒计时 */
function flashBanner(p) {
    const st = flashState(p);
    const t = parseTime(p.flashStartTime);
    const timeStr = t ? fmtTime(p.flashStartTime) : '待定';
    if (st.started) {
        return '<div class="flash-banner on">' + icon('bolt', 16) +
            '<div><b>捡漏已开抢</b><span>每人限抢一次，先到先得</span></div></div>';
    }
    return '<div class="flash-banner wait">' + icon('clock', 16) +
        '<div><b>距开抢还有 ' + untilText(p.flashStartTime) + '</b>' +
        '<span>开抢时间：' + timeStr + '</span></div></div>';
}

/** 首页热门榜（买家看"大家都在买"，卖家看"最近什么好卖"） */
async function loadHotHome() {
    const box = document.getElementById('hot-home');
    if (!box) return;
    let data;
    try {
        data = await uapi('GET', '/user/hot/categories?topCategories=3&topProducts=2', null, { quiet: true });
    } catch (e) { return; }              // 接口没接通就整块不显示，不干扰首页
    if (!data || !data.length) return;

    box.style.display = '';
    box.innerHTML =
        '<div class="hot-home">' +
        '  <div class="hot-home-head">' +
        '    <span class="hot-home-title">🔥 大家都在买</span>' +
        '    <span class="hot-home-sub">最近哪些品类卖得最快</span>' +
        '  </div>' +
        data.map((c, i) => {
            const prods = (c.products || []).map(p => {
                const tag = p.sold === 1
                    ? '<span class="hh-sold">已卖出' + (p.elapsedDays === null ? '' : ' · ' + p.elapsedDays + ' 天') + '</span>'
                    : '<span class="hh-on">在售</span>';
                return '<div class="hh-item" data-hp="' + p.productId + '">' +
                    '<span class="hh-name">' + esc(p.title || '') + '</span>' + tag +
                    '</div>';
            }).join('');
            return '<div class="hot-home-cat">' +
                '<div class="hh-cat-head">' +
                '<span class="hh-rank">' + (i + 1) + '</span>' +
                '<b>' + esc(c.categoryName || '') + '</b>' +
                '<span class="hh-count">成交 ' + (c.soldCount || 0) + ' 件</span>' +
                '</div>' + prods + '</div>';
        }).join('') +
        '  <div class="hot-home-foot">卖家可参考：卖得好的品类，说明需求旺</div>' +
        '</div>';

    box.querySelectorAll('[data-hp]').forEach(el => el.onclick = () => {
        location.hash = '#/detail/' + el.getAttribute('data-hp');
    });
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
        '  ' + carouselHTML(p.images) +
        '  <div class="detail-price"><small>¥</small>' + Number(p.price || 0).toFixed(2) + '</div>' +
        '  <div class="detail-title">' + esc(p.title || '') + '</div>' +
        '  <div style="margin-top:9px;display:flex;gap:6px;flex-wrap:wrap;align-items:center">' +
        tag('productStatus', p.status) +
        (p.isFlashSale === 1 ? badge('限时捡漏', 'orange') : '') +
        (p.conditionLevel ? badge(conditionText(p.conditionLevel), 'gray') : '') +
        '</div>' +
        // 热度行：浏览 / 收藏（这两个数字让买家判断"这件东西有多少人关注"）
        '  <div class="heat-row">' +
        '    <span>' + icon('user', 14) + ' <b>' + (p.viewCount || 0) + '</b> 人浏览</span>' +
        '    <span>' + icon('heart', 14) + ' <b>' + (p.favoriteCount || 0) + '</b> 人收藏</span>' +
        '    <span class="muted" style="font-size:12px">' + esc(p.categoryName || '') + '</span>' +
        '  </div>' +
        // 捡漏倒计时 / 开抢时间
        (p.isFlashSale === 1 ? flashBanner(p) : '') +
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

    bindCarousel();   // 多图轮播：让指示点跟着滑动走

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
        const st = flashState(f);
        const sold = f.status === 1;
        const timeText = f.flashStartTime
            ? (st.started ? '开抢时间：' + fmtTime(f.flashStartTime) : '开抢时间：' + fmtTime(f.flashStartTime) + '（还有 ' + untilText(f.flashStartTime) + '）')
            : '开抢时间：待定';
        return '<div class="row">' +
            '<div class="r-main">' +
            '  <div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : icon('bolt')) + '</div>' +
            '  <div class="t"><b>' + esc(f.title || '') + '</b>' +
            '    <small>' + esc(f.sellerName || f.sellerNickname || '') + ' · ' + conditionText(f.conditionLevel) + '</small>' +
            '    <div style="color:#e11d48;font-weight:700;font-size:15.5px;margin-top:3px">¥' + Number(f.price || 0).toFixed(2) + '</div>' +
            '    <div class="muted" style="font-size:11.5px;margin-top:4px">' + icon('clock', 12) + ' ' + timeText + '</div>' +
            '  </div>' +
            '</div>' +
            '<div class="r-actions">' +
            (sold ? badge('已被抢走', 'gray') :
                !st.started ? badge('未开始 · 还有 ' + untilText(f.flashStartTime), 'orange') :
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
        '  <a href="#/publish">发布二手 <span class="chev">›</span></a>' +
        '  <a href="#/mine">我的发布 <span class="chev">›</span></a>' +
        '  <a href="#/orders">我的订单 <span class="chev">›</span></a>' +
        '  <a href="#/bargain">我的议价 <span class="chev">›</span></a>' +
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
            actions = '<button class="btn" data-act="review" data-id="' + o.id + '">评价</button>' +
                '<button class="btn" data-act="complain" data-id="' + o.id + '" data-pid="' + (o.productId || '') + '">投诉</button>';
        }
        // 未支付的订单（比如议价被接受后生成的单）要能补支付
        if (o.payStatus === 0 && (st === 0 || st === 1)) {
            actions = '<button class="btn primary" data-act="pay" data-no="' + esc(o.orderNo || '') + '">去支付</button>' + actions;
        }
        return '<div class="row">' +
            '<div class="r-head"><span class="no">' + esc(o.orderNo || ('#' + o.id)) + '</span>' +
            tag('productStatus', st) + '</div>' +
            '<div class="r-main">' +
            '  <div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '📦') + '</div>' +
            '  <div class="t"><b>' + esc(o.productTitle || ('商品 #' + o.productId)) + '</b>' +
            '    <small>' + (orderView === 'buyer'
                ? '卖家 #' + (o.sellerId || '-')
                : '买家 #' + (o.buyerId || '-')) +
            ' · ' + (o.dealType === 1 ? '跑腿帮送' : '线下自提') + '</small>' +
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
            if (act === 'pay') {
                await uapi('POST', '/user/order/pay?orderNo=' + encodeURIComponent(b.getAttribute('data-no')));
                toast('支付成功（模拟通道）', 'ok');
            }
            else if (act === 'confirm') { await uapi('PUT', '/user/order/confirm/' + id); toast('已确认，等买家收货', 'ok'); }
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
            } else if (act === 'complain') {
                return openComplaint({ orderId: Number(id), productId: Number(b.getAttribute('data-pid')) || null });
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

/* ---------- 图片上传（multipart，不能用 JSON 那套封装） ---------- */
async function uploadImage(file) {
    const fd = new FormData();
    fd.append('file', file);
    const headers = {};
    if (U.token()) headers['authentication'] = U.token();
    // ⚠️ 不要手动设 Content-Type —— 浏览器要自己带 boundary
    const res = await fetch('/user/upload', { method: 'POST', headers, body: fd });
    if (res.status === 401) { U.clear(); location.hash = '#/login'; renderAll(); throw new Error('unauthorized'); }
    const data = await res.json();
    if (data.code !== 1) throw new Error(data.msg || '上传失败');
    return data.data;   // 形如 /upload/2026/10/xxxx.png
}

/* ======================= 发布 / 编辑商品 ======================= */
async function viewPublish(pid) {
    const isEdit = !!pid;
    let p = {};
    if (isEdit) {
        try { p = await uapi('GET', '/user/product/' + pid); } catch (e) { }
    }
    let cats = [];
    try { cats = await uapi('GET', '/user/category/list', null, { quiet: true }) || []; } catch (e) { }

    app.innerHTML = topBar(isEdit ? '编辑商品' : '发布二手', true) +
        '<div class="h5-body">' +
        '  <div class="field"><label>商品标题</label><input id="p-title" value="' + esc(p.title || '') + '" placeholder="例如：考研数学复习全书（2025版）"></div>' +
        '  <div class="field"><label>价格（元）</label><input id="p-price" type="number" value="' + (p.price || '') + '" placeholder="例如 35"></div>' +
        '  <div class="field"><label>分类</label><select id="p-cat">' +
        cats.map(c => '<option value="' + c.id + '"' + (Number(p.categoryId) === Number(c.id) ? ' selected' : '') + '>' + esc(c.name) + '</option>').join('') +
        '  </select></div>' +
        '  <div class="field"><label>成色</label><select id="p-cond">' +
        [1, 2, 3, 4, 5].map(l => '<option value="' + l + '"' + (Number(p.conditionLevel) === l ? ' selected' : '') + '>' + conditionText(l) + '</option>').join('') +
        '  </select></div>' +
        '  <div class="field"><label>商品描述</label><textarea id="p-desc" placeholder="用法说明、有没有笔记、哪里有磨损…">' + esc(p.description || '') + '</textarea></div>' +
        '  <div class="field"><label>商品图片（最多 6 张）</label>' +
        '    <div class="up-grid" id="up-grid"></div>' +
        '    <input type="file" id="up-input" accept="image/*" multiple hidden>' +
        '    <button class="btn block" id="up-btn" type="button" style="margin-top:9px">📷 拍照 / 从相册选择</button>' +
        '    <div class="hint" style="font-size:11.5px;color:#8f98a6">支持 jpg / png / webp，单张不超过 5MB；第一张会作为封面</div></div>' +
        '  <div class="field"><label style="display:flex;align-items:center;gap:8px">' +
        '    <input id="p-flash" type="checkbox" style="width:auto"' + (p.isFlashSale === 1 ? ' checked' : '') + '> 同时参加限时捡漏</label>' +
        '  </div>' +
        '  <div class="field" id="flash-box" style="' + (p.isFlashSale === 1 ? '' : 'display:none') + '">' +
        '    <label>抢购开始时间</label><input id="p-flashtime" type="datetime-local">' +
        '    <div class="hint" style="font-size:11.5px;color:#8f98a6">捡漏商品会出现在"捡漏"专区，每人只能抢一次</div></div>' +
        '  <div class="hint" style="font-size:12px;color:#8f98a6;margin:14px 0">' +
        '    发布后进入【待审核】，管理员通过后才会在首页出现 —— 二手平台需要人工把关违禁品。</div>' +
        '  <button class="btn primary block" id="p-save">' + (isEdit ? '保存修改' : '提交发布') + '</button>' +
        '</div>';

    document.getElementById('p-flash').onchange = (e) => {
        document.getElementById('flash-box').style.display = e.target.checked ? '' : 'none';
    };

    // ---------- 图片上传 ----------
    // pubImages 是"已上传成功的图片地址数组"，提交时 JSON.stringify 后存库
    let pubImages = imgList(p.images);

    const renderUpGrid = () => {
        const grid = document.getElementById('up-grid');
        if (!grid) return;
        grid.innerHTML = pubImages.map((src, i) =>
            '<div class="up-item">' +
            '<img src="' + esc(src) + '" onerror="this.style.opacity=.25">' +
            (i === 0 ? '<span class="up-cover">封面</span>' : '') +
            '<button type="button" class="up-del" data-del="' + i + '">×</button>' +
            '</div>').join('') +
            (pubImages.length >= 6 ? '' : '<div class="up-item up-empty">＋</div>');
        grid.querySelectorAll('[data-del]').forEach(b => b.onclick = () => {
            pubImages.splice(Number(b.getAttribute('data-del')), 1);
            renderUpGrid();
        });
    };

    const upInput = document.getElementById('up-input');
    document.getElementById('up-btn').onclick = () => upInput.click();

    upInput.onchange = async () => {
        const files = Array.from(upInput.files || []);
        upInput.value = '';                       // 允许再次选同一个文件
        const room = 6 - pubImages.length;
        if (room <= 0) return toast('最多 6 张图片', 'err');
        const picked = files.slice(0, room);
        const btn = document.getElementById('up-btn');
        btn.disabled = true;
        let ok = 0;
        for (let i = 0; i < picked.length; i++) {
            btn.textContent = '上传中 ' + (i + 1) + '/' + picked.length + ' …';
            try {
                const url = await uploadImage(picked[i]);
                pubImages.push(url);
                ok++;
                renderUpGrid();
            } catch (e) {
                toast(e.message || '上传失败', 'err');
            }
        }
        btn.disabled = false;
        btn.textContent = '📷 拍照 / 从相册选择';
        if (ok) toast('已上传 ' + ok + ' 张', 'ok');
    };

    renderUpGrid();

    document.getElementById('p-save').onclick = async () => {
        const title = document.getElementById('p-title').value.trim();
        const price = Number(document.getElementById('p-price').value);
        const imagesStr = pubImages.length ? JSON.stringify(pubImages) : '';
        const isFlash = document.getElementById('p-flash').checked ? 1 : 0;
        const ft = document.getElementById('p-flashtime').value;
        if (!title) return toast('请填写标题', 'err');
        if (!price || price <= 0) return toast('请填写正确的价格', 'err');

        const body = {
            categoryId: Number(document.getElementById('p-cat').value) || null,
            title: title,
            price: price,
            images: imagesStr,
            description: document.getElementById('p-desc').value.trim(),
            conditionLevel: Number(document.getElementById('p-cond').value),
            isFlashSale: isFlash,
            flashStartTime: isFlash && ft ? ft.replace('T', ' ') + ':00' : null
        };
        const btn = document.getElementById('p-save');
        btn.disabled = true;
        try {
            if (isEdit) {
                body.id = Number(pid);
                await uapi('PUT', '/user/product', body);
                toast('已保存', 'ok');
            } else {
                await uapi('POST', '/user/product', body);
                toast('已提交，等待管理员审核', 'ok');
            }
            location.hash = '#/mine';
        } catch (e) { btn.disabled = false; }
    };
}

/* ======================= 我的发布 ======================= */
async function viewMine() {
    app.innerHTML = topBar('我的发布', true) +
        '<div class="h5-body">' +
        '  <a class="btn primary block" href="#/publish" style="margin-bottom:12px">+ 发布新商品</a>' +
        '  <div id="mine-list"><div class="loading">加载中…</div></div>' +
        '</div>';

    const box = document.getElementById('mine-list');
    const d = await uapi('GET', '/user/product/mine?page=1&pageSize=30');
    const rows = (d && d.records) || [];
    if (!rows.length) {
        box.innerHTML = '<div class="h5-empty">还没有发布过商品<br><small>点上面的按钮发布第一件</small></div>';
        return;
    }
    box.innerHTML = rows.map(p => {
        const img = firstImg(p.images);
        // 自动下架的商品要给用户一句人话，否则他不知道为什么不见了
        const offlineHint = (p.status === 3 && p.offlineReason === 2)
            ? '<div class="offline-hint">挂了 ' +
              (p.daysOnline === null || p.daysOnline === undefined ? '较长时间' : p.daysOnline + ' 天') +
              ' 还没卖出去，系统已自动下架。<br>想继续卖请点「重新上架」（会重新计时 30 天）。</div>'
            : '';
        return '<div class="row">' +
            '<div class="r-head"><span class="no">' + (p.auditStatus === 0 ? '待审核' : p.auditStatus === 2 ? '已驳回' : '已通过') + '</span>' +
            tag('productStatus', p.status) + '</div>' +
            '<div class="r-main" data-pid="' + p.id + '" style="cursor:pointer">' +
            '  <div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '📦') + '</div>' +
            '  <div class="t"><b>' + esc(p.title || '') + '</b>' +
            '    <small>¥' + Number(p.price || 0).toFixed(2) + (p.auditNote ? ' · 驳回原因：' + esc(p.auditNote) : '') + '</small>' +
            '  </div></div>' +
            offlineHint +
            '<div class="r-actions">' +
            '<button class="btn sm" data-act="edit" data-id="' + p.id + '">编辑</button>' +
            (p.status === 0
                ? '<button class="btn sm" data-act="off" data-id="' + p.id + '">下架</button>'
                : (p.status === 3 ? '<button class="btn sm" data-act="on" data-id="' + p.id + '">重新上架</button>' : '')) +
            '<button class="btn sm danger" data-act="del" data-id="' + p.id + '">删除</button>' +
            '</div></div>';
    }).join('');

    box.querySelectorAll('[data-pid]').forEach(b =>
        b.onclick = () => { location.hash = '#/detail/' + b.getAttribute('data-pid'); });
    box.querySelectorAll('[data-act]').forEach(b => b.onclick = async () => {
        const id = b.getAttribute('data-id');
        const act = b.getAttribute('data-act');
        try {
            if (act === 'edit') return location.hash = '#/publish/' + id;
            if (act === 'off') { await uapi('POST', '/user/product/status/3?id=' + id); toast('已下架', 'ok'); }
            else if (act === 'on') { await uapi('POST', '/user/product/status/0?id=' + id); toast('已重新上架（若需重新审核请编辑后保存）', 'ok'); }
            else if (act === 'del') {
                if (!confirm('删除后无法恢复，确定删除？')) return;
                await uapi('DELETE', '/user/product?id=' + id);
                toast('已删除', 'ok');
            }
            viewMine();
        } catch (e) { }
    });
}

/* ======================= 议价 ======================= */
let bargainView = 'buyer';

const BARGAIN_STATUS = { 0: ['待回应', 'orange'], 1: ['买家出价', 'blue'], 2: ['卖家还价', 'blue'], 3: ['已接受', 'green'], 4: ['已拒绝', 'gray'], 5: ['已过期', 'gray'] };

async function viewBargain() {
    app.innerHTML = topBar('我的议价', true) +
        '<div class="h5-body">' +
        '  <div class="chips" id="bv">' +
        '    <span class="chip' + (bargainView === 'buyer' ? ' on' : '') + '" data-v="buyer">我出的价</span>' +
        '    <span class="chip' + (bargainView === 'seller' ? ' on' : '') + '" data-v="seller">别人对我的出价</span>' +
        '  </div>' +
        '  <div id="bargain-list"><div class="loading">加载中…</div></div>' +
        '</div>';

    app.querySelectorAll('[data-v]').forEach(b => b.onclick = () => {
        bargainView = b.getAttribute('data-v');
        viewBargain();
    });

    const box = document.getElementById('bargain-list');
    const d = await uapi('GET', '/user/bargain/list' + qs({ page: 1, pageSize: 30, view: bargainView }));
    const rows = (d && d.records) || [];
    if (!rows.length) {
        box.innerHTML = '<div class="h5-empty">' +
            (bargainView === 'buyer' ? '还没出过价<br><small>去商品详情页点"议价"</small>' : '还没有人跟你议价') + '</div>';
        return;
    }

    box.innerHTML = rows.map(b => {
        const st = BARGAIN_STATUS[b.bargainStatus] || ['未知', 'gray'];
        const img = firstImg(b.productImages);
        const canOperate = bargainView === 'seller' && (b.bargainStatus <= 2);
        return '<div class="row">' +
            '<div class="r-head"><span class="no">' + fmtTime(b.createTime) + '</span>' + badge(st[0], st[1]) + '</div>' +
            '<div class="r-main">' +
            '  <div class="pic">' + (img ? '<img src="' + esc(img) + '" onerror="this.style.display=\'none\'">' : '📦') + '</div>' +
            '  <div class="t"><b>' + esc(b.productTitle || '') + '</b>' +
            '    <small>原价 ¥' + Number(b.productPrice || 0).toFixed(2) +
            ' · ' + (bargainView === 'buyer' ? '卖家 ' + esc(b.sellerName || '') : '买家 ' + esc(b.buyerName || '')) + '</small>' +
            '    <div style="margin-top:5px;font-size:13px">' +
            '      <span style="color:#e5484d;font-weight:600">出价 ¥' + Number(b.buyerOffer || 0).toFixed(2) + '</span>' +
            (b.sellerCounterOffer ? ' <span style="color:#2f6bff">卖家还价 ¥' + Number(b.sellerCounterOffer).toFixed(2) + '</span>' : '') +
            '    </div>' +
            '    <div class="muted" style="font-size:11px;margin-top:3px">' + fmtTime(b.expireTime) + ' 前有效</div>' +
            '  </div></div>' +
            (canOperate ? '<div class="r-actions">' +
                '<button class="btn sm primary" data-act="accept" data-id="' + b.id + '">接受</button>' +
                '<button class="btn sm" data-act="counter" data-id="' + b.id + '">还价</button>' +
                '<button class="btn sm danger" data-act="reject" data-id="' + b.id + '">拒绝</button>' +
                '</div>' : '') +
            '</div>';
    }).join('');

    box.querySelectorAll('[data-act]').forEach(btn => btn.onclick = async () => {
        const id = btn.getAttribute('data-id');
        const act = btn.getAttribute('data-act');
        try {
            if (act === 'accept') {
                if (!confirm('接受这个价格？系统会立刻生成一笔订单，买家再去支付。')) return;
                await uapi('PUT', '/user/bargain/accept/' + id);
                toast('已接受，订单已生成（买家需在订单里支付）', 'ok');
            } else if (act === 'reject') {
                await uapi('PUT', '/user/bargain/reject/' + id);
                toast('已拒绝', 'ok');
            } else if (act === 'counter') {
                const v = prompt('你想卖多少钱？');
                if (!v) return;
                await uapi('PUT', '/user/bargain/counter', { id: Number(id), counterOffer: Number(v) });
                toast('已还价', 'ok');
            }
            viewBargain();
        } catch (e) { }
    });
}

/* ======================= 发起投诉 ======================= */
function openComplaint(opts) {
    const mask = document.createElement('div');
    mask.className = 'sheet-mask';
    mask.innerHTML =
        '<div class="sheet"><h3>发起投诉</h3>' +
        '<div class="field"><label>投诉类型</label><select id="c-type">' +
        '  <option value="1"' + (opts.orderId ? ' selected' : '') + '>交易投诉</option>' +
        '  <option value="0"' + (opts.productId && !opts.orderId ? ' selected' : '') + '>商品投诉</option>' +
        '</select></div>' +
        '<div class="field"><label>投诉原因</label><select id="c-reason">' +
        '  <option value="1">与描述不符</option><option value="2">虚假描述</option>' +
        '  <option value="3">卖家/买家失联</option><option value="4">交付纠纷</option>' +
        '  <option value="9">其他</option></select></div>' +
        '<div class="field"><label>详细说明</label><textarea id="c-content" placeholder="把事情经过写清楚，管理员据此裁决"></textarea></div>' +
        '<div class="field"><label>证据图片地址（每行一个，可留空）</label><textarea id="c-imgs"></textarea></div>' +
        '<div class="hint" style="font-size:12px;color:#8f98a6;margin-bottom:12px">' +
        '  投诉经管理员查实成立后，被投诉人会被扣 10 分信用分。</div>' +
        '<div style="display:flex;gap:9px"><button class="btn" style="flex:1" data-close>取消</button>' +
        '<button class="btn primary" style="flex:1" id="c-ok">提交投诉</button></div></div>';
    document.body.appendChild(mask);
    mask.querySelector('[data-close]').onclick = () => mask.remove();
    mask.onclick = (e) => { if (e.target === mask) mask.remove(); };
    mask.querySelector('#c-ok').onclick = async () => {
        const content = mask.querySelector('#c-content').value.trim();
        if (!content) return toast('请写清投诉内容', 'err');
        const body = {
            complaintType: Number(mask.querySelector('#c-type').value),
            complaintReason: Number(mask.querySelector('#c-reason').value),
            content: content,
            images: (function () {
                const list = mask.querySelector('#c-imgs').value.split('\n').map(x => x.trim()).filter(Boolean);
                return list.length ? JSON.stringify(list) : '';
            })(),
            productId: opts.productId || null,
            orderId: opts.orderId || null
        };
        await uapi('POST', '/user/complaint', body);
        toast('已提交，管理员会尽快处理', 'ok');
        mask.remove();
    };
}

/* ======================= 启动 ======================= */
renderAll();
