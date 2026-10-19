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
        '<span>共 ' + (total || 0) + ' 条，第 ' + page + ' / ' + pages + ' 页</span>' +
        '<span class="btns">' +
        '<button class="btn sm" data-page="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>' +
        '<button class="btn sm" data-page="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + '>下一页</button>' +
        '</span></div>';
}
