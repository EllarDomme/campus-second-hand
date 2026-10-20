/* ==========================================================================
   校园二手交易平台 · 前端公共脚本（零依赖）
   --------------------------------------------------------------------------
   提供 5 样东西：
     1) api(method, url, body) → 统一走后端 /admin 或 /user 接口，自动带 token、统一处理 code
     2) auth → token 存取（localStorage）、登录态判断、退出
     3) toast(msg, type) → 轻提示
     4) modal({title, body, onOk}) → 弹窗（返回 Promise）
     5) esc / money / fmtTime / starText → 展示格式化
   ========================================================================== */

/* ---------------- 1. 请求 ---------------- */
const Auth = {
    KEY: 'campus.token',
    NAME: 'campus.adminName',
    get() { return localStorage.getItem(this.KEY) || ''; },
    set(t, name) {
        localStorage.setItem(this.KEY, t);
        if (name) localStorage.setItem(this.NAME, name);
    },
    name() { return localStorage.getItem(this.NAME) || ''; },
    clear() {
        localStorage.removeItem(this.KEY);
        localStorage.removeItem(this.NAME);
    },
    logged() { return !!this.get(); }
};

/**
 * 统一请求
 * @param {'GET'|'POST'|'PUT'|'DELETE'} method
 * @param {string} url  以 /admin 或 /user 开头
 * @param {object} [body] JSON 体（GET/DELETE 时忽略）
 * @param {object} [opts] { quiet:true 不弹错误提示 }
 */
async function api(method, url, body, opts) {
    opts = opts || {};
    const headers = {};
    const token = Auth.get();
    if (token) headers['token'] = token;

    let payload;
    if (body !== undefined && body !== null && method !== 'GET' && method !== 'DELETE') {
        headers['Content-Type'] = 'application/json; charset=utf-8';
        payload = JSON.stringify(body);
    }

    let res;
    try {
        res = await fetch(url, { method, headers, body: payload });
    } catch (e) {
        if (!opts.quiet) toast('网络请求失败：' + e.message, 'err');
        throw e;
    }

    if (res.status === 401) {
        Auth.clear();
        toast('登录已过期，请重新登录', 'err');
        setTimeout(() => { location.hash = '#/login'; location.reload(); }, 800);
        throw new Error('unauthorized');
    }

    let data;
    try {
        data = await res.json();
    } catch (e) {
        if (!opts.quiet) toast('返回内容不是 JSON（HTTP ' + res.status + '）', 'err');
        throw e;
    }

    if (data.code !== 1) {
        const msg = data.msg || ('请求失败（code=' + data.code + '）');
        if (!opts.quiet) toast(msg, 'err');
        const err = new Error(msg);
        err.bizCode = data.code;
        throw err;
    }
    return data.data;
}

/** 拼查询串（跳过空值） */
function qs(obj) {
    const parts = [];
    Object.keys(obj || {}).forEach(k => {
        const v = obj[k];
        if (v !== undefined && v !== null && v !== '') {
            parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
        }
    });
    return parts.length ? ('?' + parts.join('&')) : '';
}

/* ---------------- 2. 提示 ---------------- */
function toast(msg, type) {
    let wrap = document.getElementById('toast-wrap');
    if (!wrap) {
        wrap = document.createElement('div');
        wrap.id = 'toast-wrap';
        document.body.appendChild(wrap);
    }
    const el = document.createElement('div');
    el.className = 'toast' + (type === 'err' ? ' err' : type === 'ok' ? ' ok' : '');
    el.textContent = msg;
    wrap.appendChild(el);
    setTimeout(() => {
        el.style.transition = 'opacity .2s';
        el.style.opacity = '0';
        setTimeout(() => el.remove(), 220);
    }, type === 'err' ? 3200 : 2000);
}

/* ---------------- 3. 弹窗 ---------------- */
/**
 * modal({title, body(HTML字符串), okText, cancelText, danger, onOk})
 * onOk 返回 false 时不关闭；抛异常时也不关闭。
 */
function modal(cfg) {
    return new Promise((resolve) => {
        const mask = document.createElement('div');
        mask.className = 'mask';
        mask.innerHTML =
            '<div class="modal">' +
            '  <div class="m-head"><span>' + esc(cfg.title || '提示') + '</span>' +
            '    <button class="x" data-close>&times;</button></div>' +
            '  <div class="m-body">' + (cfg.body || '') + '</div>' +
            '  <div class="m-foot">' +
            '    <button class="btn" data-close>' + esc(cfg.cancelText || '取消') + '</button>' +
            '    <button class="btn ' + (cfg.danger ? 'danger' : 'primary') + '" data-ok>' +
            esc(cfg.okText || '确定') + '</button>' +
            '  </div>' +
            '</div>';

        const close = (val) => {
            mask.remove();
            resolve(val);
        };

        mask.querySelectorAll('[data-close]').forEach(b => b.onclick = () => close(false));
        mask.querySelector('[data-ok]').onclick = async () => {
            const okBtn = mask.querySelector('[data-ok]');
            okBtn.disabled = true;
            try {
                const r = cfg.onOk ? await cfg.onOk(mask) : true;
                if (r !== false) close(true);
            } catch (e) {
                /* onOk 内部已 toast，这里只恢复按钮 */
            } finally {
                okBtn.disabled = false;
            }
        };
        mask.onclick = (e) => { if (e.target === mask) close(false); };
        document.body.appendChild(mask);
        const first = mask.querySelector('input, textarea');
        if (first) setTimeout(() => first.focus(), 50);
    });
}

/** 二次确认 */
function confirmBox(title, text, okText) {
    return modal({
        title: title,
        body: '<div style="line-height:1.7">' + esc(text) + '</div>',
        danger: true,
        okText: okText || '确认执行'
    });
}

/* ---------------- 4. 格式化 ---------------- */
function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function money(v) {
    if (v === null || v === undefined || v === '') return '-';
    return '¥' + Number(v).toFixed(2);
}

function fmtTime(v) {
    if (!v) return '-';
    let s = String(v).replace('T', ' ');
    return s.length >= 16 ? s.substring(0, 16) : s;
}

function stars(n) {
    n = Number(n) || 0;
    return '<span class="stars">' + '★'.repeat(n) + '<span style="color:#ddd">' + '★'.repeat(5 - n) + '</span></span>';
}

/** 状态徽章：cls ∈ gray/blue/green/red/orange */
function badge(text, cls) {
    return '<span class="badge ' + (cls || 'gray') + '">' + esc(text) + '</span>';
}

/* ---------------- 5. 常用映射（和后端常量保持一致） ---------------- */
const MAP = {
    // 商品状态 0在售 1交易中 2已售罄 3已下架
    productStatus: { 0: ['在售', 'green'], 1: ['交易中', 'blue'], 2: ['已售罄', 'gray'], 3: ['已下架', 'gray'] },
    // 审核状态 0待审 1通过 2驳回
    auditStatus: { 0: ['待审核', 'orange'], 1: ['已通过', 'green'], 2: ['已驳回', 'red'] },
    // 用户状态 0正常 1封禁
    userStatus: { 0: ['正常', 'green'], 1: ['已封禁', 'red'] },
    // 用户角色 0普通 1跑腿员
    userRole: { 0: ['普通用户', 'gray'], 1: ['跑腿员', 'blue'] },
    // 跑腿单 0待接单 1已接单 2取货中 3已送达 4已取消
    deliveryStatus: { 0: ['待接单', 'orange'], 1: ['已接单', 'blue'], 2: ['取货中', 'blue'], 3: ['已送达', 'green'], 4: ['已取消', 'gray'] },
    // 投诉 0待处理 1处理中 2已完结
    complaintStatus: { 0: ['待处理', 'orange'], 1: ['处理中', 'blue'], 2: ['已完结', 'green'] },
    // 投诉结果 1成立 2不成立
    complaintResult: { 1: ['成立', 'red'], 2: ['不成立', 'gray'] },
    // 投诉类型 0商品 1交易
    complaintType: { 0: ['商品投诉', 'gray'], 1: ['交易投诉', 'gray'] }
};

function tag(mapName, value) {
    const m = MAP[mapName] && MAP[mapName][value];
    if (!m) return badge('未知(' + esc(value) + ')', 'gray');
    return badge(m[0], m[1]);
}

/* ---------------- 6. 小工具 ---------------- */
/** 渲染进容器 */
function setHTML(id, html) {
    const el = typeof id === 'string' ? document.getElementById(id) : id;
    if (el) el.innerHTML = html;
}

/** 表格空态 */
function emptyRow(cols, text) {
    return '<tr><td colspan="' + cols + '"><div class="empty">' + esc(text || '暂无数据') + '</div></td></tr>';
}

/** 分页条 HTML */
function pagerHTML(page, pageSize, total) {
    const pages = Math.max(1, Math.ceil((total || 0) / pageSize));
    return '<div class="pager">' +
        '<span>共 <b>' + (total || 0) + '</b> 条 · 第 <b>' + page + '</b> / ' + pages + ' 页</span>' +
        '<span class="btns">' +
        '<button class="btn sm" data-page="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>' +
        '<button class="btn sm" data-page="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + '>下一页</button>' +
        '</span></div>';
}

/* ---------------- 7. 内联 SVG 图标（不依赖图标字体/CDN） ---------------- */
const ICONS = {
    dashboard: '<path d="M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z"/>',
    users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/>',
    box: '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><path d="M3.3 7L12 12l8.7-5M12 22V12"/>',
    bolt: '<path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>',
    truck: '<path d="M1 3h15v13H1zM16 8h4l3 3v5h-7z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>',
    flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1zM4 22v-7"/>',
    star: '<path d="M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z"/>',
    home: '<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/>',
    message: '<path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8z"/>',
    user: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    search: '<circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>',
    heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1L12 21l7.7-7.6 1.1-1a5.5 5.5 0 0 0 0-7.8z"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    chart: '<path d="M18 20V10M12 20V4M6 20v-6"/>',
    check: '<path d="M20 6L9 17l-5-5"/>',
    close: '<path d="M18 6L6 18M6 6l12 12"/>',
    clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
    money: '<path d="M12 1v22M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>',
    empty: '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><path d="M3.3 7L12 12l8.7-5"/>',
    arrow: '<path d="M15 18l-6-6 6-6"/>',
    bell: '<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>'
};

/**
 * 生成内联 SVG 图标
 * @param {string} name ICONS 的 key
 * @param {number} [size] 像素尺寸（不传则用 CSS 控制）
 */
function icon(name, size) {
    const p = ICONS[name] || ICONS.empty;
    const s = size ? ' width="' + size + '" height="' + size + '"' : '';
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
        'stroke-linecap="round" stroke-linejoin="round"' + s + '>' + p + '</svg>';
}

/** 空态（带图标） */
function emptyBox(title, desc) {
    return '<div class="empty"><div class="art">' + icon('empty') + '</div>' +
        '<b>' + esc(title || '暂无数据') + '</b>' +
        (desc ? '<div>' + esc(desc) + '</div>' : '') + '</div>';
}

/**
 * 图片加载失败的兜底
 *
 * 为什么要有它：以前是 onerror="this.style.display='none'" —— 图片挂了就静默隐藏，
 * 页面上只剩一个空盒子，完全看不出是"网络不通"还是"链接错了"。
 * 现在改成：换成一张本地兜底图 + 左下角标一个"图裂"，一眼能判断是网络问题。
 *
 * 判断方法：
 *   · 显示兜底图 + 「图裂」→ 外网图没加载成功（网络/图床问题）
 *   · 正常显示照片        → 外网通，加载成功
 */
function imgFallback(img) {
    if (img.dataset.fallback) return;      // 防止死循环
    img.dataset.fallback = '1';
    img.src = '/ui/img/cat_other_a.svg';
    img.title = '图片加载失败（外网不通或链接失效）';
    img.style.background = '#f1f5f9';
    var p = img.parentElement;
    if (p && !p.querySelector('.img-broken')) {
        var tip = document.createElement('span');
        tip.className = 'img-broken';
        tip.textContent = '图裂';
        p.appendChild(tip);
    }
}