(function () {
    "use strict";
    var G = window;
    if (G.__coaraAgent) {
        return;
    }

    function installYoutubePipCompatibility() {
        try {
            var host = String(location.hostname || "").toLowerCase();
            if (host !== "youtube.com" && host.slice(-12) !== ".youtube.com") {
                return;
            }
            var page = G.wrappedJSObject || G;
            if (!page || typeof exportFunction !== "function" || !page.Document
                    || G.__coaraYoutubePipCompatibilityInstalled) {
                return;
            }
            var originalWidth = null;
            var originalHeight = null;
            function patchDimension(name, screenName) {
                var descriptor = Object.getOwnPropertyDescriptor(page, name);
                if (!descriptor || typeof descriptor.get !== "function" || !descriptor.configurable) {
                    return;
                }
                var original = descriptor.get;
                if (name === "outerWidth") {
                    originalWidth = original;
                } else {
                    originalHeight = original;
                }
                var getter = exportFunction(function () {
                    var actual = original();
                    var target = screen[screenName];
                    if (actual < target / 2) {
                        return target;
                    }
                    return actual;
                }, G);
                Object.defineProperty(page, name, {
                    configurable: descriptor.configurable,
                    enumerable: descriptor.enumerable,
                    get: getter,
                    set: descriptor.set
                });
            }
            patchDimension("outerWidth", "width");
            patchDimension("outerHeight", "height");
            try {
                var proto = page.Document.prototype;
                var descriptor = Object.getOwnPropertyDescriptor(proto, "exitFullscreen");
                if (descriptor && typeof descriptor.value === "function" && descriptor.configurable
                        && originalWidth && originalHeight) {
                    var originalExitFullscreen = descriptor.value;
                    var replacement = exportFunction(function () {
                        var active = false;
                        try {
                            var pip = G.__coaraPip;
                            active = !!(pip && pip.on);
                            if (!active) {
                                active = originalWidth() < screen.width / 2
                                        && originalHeight() < screen.height / 2;
                            }
                        } catch (e) {
                        }
                        if (active) {
                            return undefined;
                        }
                        return originalExitFullscreen.apply(this);
                    }, G);
                    Object.defineProperty(proto, "exitFullscreen", {
                        configurable: descriptor.configurable,
                        enumerable: descriptor.enumerable,
                        writable: descriptor.writable,
                        value: replacement
                    });
                }
            } catch (e) {
            }
            G.__coaraYoutubePipCompatibilityInstalled = true;
        } catch (e) {
        }
    }

    installYoutubePipCompatibility();

    var APP = "coaraPage";
    var isTop = false;
    try {
        isTop = G.top === G;
    } catch (e) {
        isTop = false;
    }
    var port = null;
    var outbox = [];
    var styles = Object.create(null);
    var iconSent = "";

    function connect() {
        if (port) {
            return true;
        }
        try {
            port = browser.runtime.connectNative(APP);
            port.onMessage.addListener(onNative);
            port.onDisconnect.addListener(function () {
                port = null;
            });
            return true;
        } catch (e) {
            port = null;
            return false;
        }
    }

    function send(msg) {
        if (!connect()) {
            return false;
        }
        try {
            while (outbox.length) {
                port.postMessage(outbox.shift());
            }
            port.postMessage(msg);
            return true;
        } catch (e) {
            port = null;
            if (outbox.length < 64) {
                outbox.push(msg);
            }
            return false;
        }
    }

    function stringify(v) {
        if (v === undefined) {
            return "null";
        }
        try {
            var s = JSON.stringify(v);
            return s === undefined ? "null" : s;
        } catch (e) {
            try {
                return JSON.stringify(String(v));
            } catch (e2) {
                return "null";
            }
        }
    }

    function doEval(id, code) {
        var ok = true;
        var out = "null";
        try {
            out = stringify((0, eval)(String(code)));
        } catch (e) {
            ok = false;
            out = "null";
            send({ t: "err", m: String(e && e.message ? e.message : e), u: String(location.href) });
        }
        if (id) {
            send({ t: "res", id: id, ok: ok, v: out });
        }
    }

    function removeCss(key) {
        var entry = styles[key];
        if (!entry) {
            return;
        }
        delete styles[key];
        try {
            if (entry.sheet) {
                document.adoptedStyleSheets = Array.prototype.filter.call(document.adoptedStyleSheets, function (s) {
                    return s !== entry.sheet;
                });
            }
            if (entry.node && entry.node.parentNode) {
                entry.node.parentNode.removeChild(entry.node);
            }
        } catch (e) {
        }
    }

    function setCss(key, css) {
        removeCss(key);
        if (typeof css !== "string" || css.length === 0) {
            return;
        }
        var entry = {};
        var done = false;
        try {
            if (typeof CSSStyleSheet === "function" && ("adoptedStyleSheets" in document)) {
                var sheet = new CSSStyleSheet();
                sheet.replaceSync(css);
                document.adoptedStyleSheets = Array.prototype.concat.call(
                    Array.prototype.slice.call(document.adoptedStyleSheets), [sheet]);
                entry.sheet = sheet;
                done = true;
            }
        } catch (e) {
            entry.sheet = null;
            done = false;
        }
        if (!done) {
            var attach = function () {
                try {
                    var node = document.createElement("style");
                    node.setAttribute("data-coara", key);
                    node.textContent = css;
                    (document.head || document.documentElement).appendChild(node);
                    entry.node = node;
                } catch (e) {
                }
            };
            if (document.head || document.documentElement) {
                attach();
            } else {
                document.addEventListener("DOMContentLoaded", attach, { once: true });
            }
        }
        styles[key] = entry;
    }

    function sendIcon() {
        if (!isTop) {
            return;
        }
        try {
            var nodes = document.querySelectorAll("link[rel~='icon'],link[rel='shortcut icon'],link[rel~='apple-touch-icon'],link[rel~='apple-touch-icon-precomposed']");
            var primary = [];
            var touch = [];
            for (var i = 0; i < nodes.length; i++) {
                var href = nodes[i].href;
                if (!href || !/^(https?:|data:image\/)/i.test(href)) {
                    continue;
                }
                var rel = (nodes[i].getAttribute("rel") || "").toLowerCase();
                if (rel.indexOf("apple") >= 0) {
                    touch.push(href);
                } else {
                    primary.push(href);
                }
            }
            var list = primary.concat(touch);
            if (location.origin && /^https?:/i.test(location.origin)) {
                list.push(location.origin + "/favicon.ico");
            }
            var key = list.join("|");
            if (list.length && key !== iconSent) {
                iconSent = key;
                send({ t: "icon", hrefs: list.slice(0, 6) });
            }
        } catch (e) {
        }
    }

    var iconTimer = 0;
    function watchIcons() {
        if (!isTop || !document.head || typeof MutationObserver !== "function") {
            return;
        }
        try {
            var mo = new MutationObserver(function () {
                if (iconTimer) {
                    clearTimeout(iconTimer);
                }
                iconTimer = setTimeout(sendIcon, 300);
            });
            mo.observe(document.head, { childList: true });
            setTimeout(function () {
                mo.disconnect();
            }, 8000);
        } catch (e) {
        }
    }

    var cctChannels = Object.create(null);

    function cctOrigin(value) {
        try {
            var parsed = new URL(String(value));
            if (parsed.protocol !== "https:" || parsed.origin !== String(value)
                    || parsed.username || parsed.password) {
                return "";
            }
            return parsed.origin;
        } catch (e) {
            return "";
        }
    }

    function cctClose(id) {
        var channel = cctChannels[String(id)];
        if (!channel) {
            return;
        }
        delete cctChannels[String(id)];
        try {
            channel.port1.onmessage = null;
            channel.port1.onmessageerror = null;
            channel.port1.close();
        } catch (e) {
        }
    }

    function cctCloseAll() {
        Object.keys(cctChannels).forEach(cctClose);
    }

    function cctOpen(m) {
        if (!isTop || typeof m.id !== "string" || m.id.length < 1 || m.id.length > 128) {
            return;
        }
        var requestedOrigin = cctOrigin(m.origin);
        if (!requestedOrigin || requestedOrigin !== G.location.origin || typeof MessageChannel !== "function") {
            send({ t: "cct.channelError", id: m.id, origin: String(G.location.origin) });
            return;
        }
        cctClose(m.id);
        var channel;
        try {
            channel = new MessageChannel();
            channel.port1.onmessage = function (event) {
                if (cctChannels[m.id] !== channel || G.location.origin !== requestedOrigin) {
                    return;
                }
                var message = typeof event.data === "string" ? event.data : stringify(event.data);
                if (message.length > 16384) {
                    return;
                }
                send({ t: "cct.message", id: m.id, origin: String(G.location.origin), message: message });
            };
            channel.port1.onmessageerror = function () {
                send({ t: "cct.channelError", id: m.id, origin: String(G.location.origin) });
            };
            if (typeof channel.port1.start === "function") {
                channel.port1.start();
            }
            cctChannels[m.id] = channel;
            G.postMessage("channel", requestedOrigin, [channel.port2]);
            send({ t: "cct.channelReady", id: m.id, origin: String(G.location.origin) });
        } catch (e) {
            if (channel) {
                try { channel.port1.close(); } catch (ignored) { }
                try { channel.port2.close(); } catch (ignored) { }
            }
            delete cctChannels[m.id];
            send({ t: "cct.channelError", id: m.id, origin: String(G.location.origin) });
        }
    }

    function cctSend(m) {
        if (!isTop || !m || typeof m.id !== "string" || typeof m.message !== "string"
                || m.message.length > 16384 || cctOrigin(m.origin) !== G.location.origin) {
            return;
        }
        var channel = cctChannels[m.id];
        if (!channel) {
            return;
        }
        try {
            channel.port1.postMessage(m.message);
        } catch (e) {
            cctClose(m.id);
            send({ t: "cct.channelError", id: m.id, origin: String(G.location.origin) });
        }
    }

    G.addEventListener("pagehide", cctCloseAll, true);

    function onNative(m) {
        if (!m || typeof m !== "object") {
            return;
        }
        switch (m.t) {
            case "eval":
                doEval(m.id, m.code);
                break;
            case "css":
                setCss(String(m.key), m.css);
                break;
            case "cssoff":
                removeCss(String(m.key));
                break;
            case "ping":
                send({ t: "pong" });
                break;
            case "cct.open":
                cctOpen(m);
                break;
            case "cct.send":
                cctSend(m);
                break;
            case "cct.close":
                cctClose(m.id);
                break;
            default:
                break;
        }
    }

    var FM_MAX = 3000;
    var FM_MAX_CHARS = 8000000;
    var FM_SLICE_MS = 9;
    var fm = null;
    var FM_BLOCK = {};
    var FM_SKIP = {};
    ("DIV P LI UL OL TABLE TR TD TH TBODY THEAD TFOOT CAPTION H1 H2 H3 H4 H5 H6 SECTION ARTICLE HEADER FOOTER NAV ASIDE MAIN FORM FIELDSET LEGEND BLOCKQUOTE PRE DL DT DD HR BR FIGURE FIGCAPTION DETAILS SUMMARY ADDRESS BUTTON IFRAME FRAME VIDEO AUDIO CANVAS IMG SVG MATH HGROUP MENU DIALOG CENTER").split(" ").forEach(function (k) {
        FM_BLOCK[k] = 1;
    });
    ("SCRIPT STYLE NOSCRIPT TEMPLATE HEAD TITLE META LINK OBJECT EMBED TEXTAREA SELECT OPTION OPTGROUP DATALIST").split(" ").forEach(function (k) {
        FM_SKIP[k] = 1;
    });

    function fmNow() {
        return (G.performance && G.performance.now) ? G.performance.now() : Date.now();
    }

    function fmRegex(q) {
        var t = String(q || "").trim();
        if (!t) {
            return null;
        }
        var parts = t.split(/\s+/);
        for (var i = 0; i < parts.length; i++) {
            parts[i] = parts[i].replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        }
        try {
            return new RegExp(parts.join("\\s+"), "gi");
        } catch (e) {
            return null;
        }
    }

    function fmParentEl(n) {
        var p = n.parentNode;
        if (!p) {
            return null;
        }
        return p.nodeType === 1 ? p : (p.host || null);
    }

    function fmVisible(el, cache) {
        if (!el) {
            return false;
        }
        var v = cache.get(el);
        if (v !== undefined) {
            return v;
        }
        try {
            if (typeof el.checkVisibility === "function") {
                v = el.checkVisibility({ checkVisibilityCSS: true });
            } else {
                v = el.getClientRects().length > 0;
            }
        } catch (e) {
            v = true;
        }
        cache.set(el, v);
        return v;
    }

    function fmStop() {
        if (fm) {
            fm.stop = true;
            if (fm.timer) {
                clearTimeout(fm.timer);
            }
        }
        fm = null;
    }

    function fmDocHeight() {
        var d = document.documentElement;
        var b = document.body;
        return Math.max(1, d ? d.scrollHeight : 0, b ? b.scrollHeight : 0);
    }

    function fmFlush(s) {
        if (!s.parts.length) {
            return;
        }
        var text = s.parts.length === 1 ? s.parts[0] : s.parts.join("");
        var segs = s.segs;
        s.parts = [];
        s.segs = [];
        s.len = 0;
        s.chars += text.length;
        var re = s.re;
        re.lastIndex = 0;
        var m;
        var si = 0;
        while ((m = re.exec(text)) !== null) {
            var a = m.index;
            var b = a + m[0].length;
            if (b <= a) {
                re.lastIndex = a + 1;
                continue;
            }
            while (si + 1 < segs.length && segs[si + 1].s <= a) {
                si++;
            }
            var sj = si;
            while (sj + 1 < segs.length && segs[sj + 1].s < b) {
                sj++;
            }
            try {
                var r = document.createRange();
                r.setStart(segs[si].n, a - segs[si].s);
                r.setEnd(segs[sj].n, b - segs[sj].s);
                s.ranges.push(r);
            } catch (e) {
            }
            if (s.ranges.length >= FM_MAX) {
                s.capped = true;
                return;
            }
        }
    }

    function fmScan(s) {
        s.timer = 0;
        if (s.stop || fm !== s) {
            return;
        }
        var t0 = fmNow();
        var stack = s.stack;
        while (stack.length && !s.capped && s.chars < FM_MAX_CHARS) {
            if (fmNow() - t0 > FM_SLICE_MS) {
                s.timer = setTimeout(function () {
                    fmScan(s);
                }, 0);
                return;
            }
            var n = stack.pop();
            if (n === null) {
                fmFlush(s);
                continue;
            }
            var type = n.nodeType;
            if (type === 3) {
                var data = n.nodeValue;
                if (data && fmVisible(fmParentEl(n), s.vis)) {
                    s.segs.push({ n: n, s: s.len });
                    s.parts.push(data);
                    s.len += data.length;
                }
                continue;
            }
            if (type !== 1 && type !== 11 && type !== 9) {
                continue;
            }
            var tag = type === 1 ? n.nodeName.toUpperCase() : "";
            if (FM_SKIP[tag]) {
                continue;
            }
            var block = !!FM_BLOCK[tag];
            if (block) {
                fmFlush(s);
                stack.push(null);
            }
            var kids = n.childNodes;
            for (var i = kids.length - 1; i >= 0; i--) {
                stack.push(kids[i]);
            }
            if (type === 1) {
                var sr = null;
                try {
                    sr = n.openOrClosedShadowRoot || n.shadowRoot;
                } catch (e) {
                    sr = null;
                }
                if (sr) {
                    stack.push(sr);
                }
            }
        }
        fmFlush(s);
        s.pos = 0;
        s.rects = [];
        fmRects(s);
    }

    function fmRects(s) {
        s.timer = 0;
        if (s.stop || fm !== s) {
            return;
        }
        var t0 = fmNow();
        var list = s.ranges;
        var sy = G.pageYOffset || 0;
        while (s.pos < list.length) {
            if (fmNow() - t0 > FM_SLICE_MS) {
                s.timer = setTimeout(function () {
                    fmRects(s);
                }, 0);
                return;
            }
            var r = list[s.pos++];
            var y = -1;
            try {
                var rc = r.getClientRects();
                var box = rc.length ? rc[0] : r.getBoundingClientRect();
                if (box && (box.height > 0 || box.width > 0)) {
                    y = box.top + sy;
                }
            } catch (e) {
                y = -1;
            }
            s.rects.push(y);
        }
        var keep = [];
        var ys = [];
        for (var i = 0; i < list.length; i++) {
            if (s.rects[i] >= 0) {
                keep.push(list[i]);
                ys.push(s.rects[i]);
            }
        }
        var h = fmDocHeight();
        var f = [];
        for (var j = 0; j < ys.length; j++) {
            f.push(Math.round(Math.min(1, Math.max(0, ys[j] / h)) * 10000) / 10000);
        }
        s.ranges = keep;
        s.done = true;
        s.vis = null;
        s.stack = null;
        send({ t: "fmap", gen: s.gen, q: s.q, n: keep.length, capped: !!s.capped, f: f });
    }

    function fmStart(gen, q) {
        fmStop();
        var re = fmRegex(q);
        var root = document.body || document.documentElement;
        if (!re || !root) {
            send({ t: "fmap", gen: gen, q: String(q || ""), n: 0, capped: false, f: [] });
            return true;
        }
        var s = {
            gen: gen,
            q: String(q),
            re: re,
            stack: [root],
            vis: new Map(),
            parts: [],
            segs: [],
            len: 0,
            ranges: [],
            chars: 0,
            capped: false,
            done: false,
            stop: false,
            timer: 0
        };
        fm = s;
        s.timer = setTimeout(function () {
            fmScan(s);
        }, 0);
        return true;
    }

    function fmJump(gen, i) {
        var s = fm;
        if (!s || !s.done || s.gen !== gen) {
            return false;
        }
        var r = s.ranges[i];
        if (!r) {
            return false;
        }
        try {
            var sc = r.startContainer;
            if (!sc || !sc.isConnected) {
                return false;
            }
            var el = fmParentEl(sc);
            var box = r.getClientRects();
            var first = box.length ? box[0] : r.getBoundingClientRect();
            var vh = G.innerHeight || 0;
            if (!first || first.top < 0 || first.bottom > vh) {
                if (el && el.scrollIntoView) {
                    el.scrollIntoView({ block: "center", inline: "nearest", behavior: "instant" });
                }
                box = r.getClientRects();
                first = box.length ? box[0] : r.getBoundingClientRect();
                if (first && (first.top < 0 || first.bottom > vh)) {
                    G.scrollBy({ top: first.top - vh * 0.4, left: 0, behavior: "instant" });
                }
            }
            var sel = G.getSelection();
            if (sel) {
                sel.removeAllRanges();
                sel.addRange(r.cloneRange());
            }
            return true;
        } catch (e) {
            return false;
        }
    }

    function fmActiveY() {
        try {
            var sel = G.getSelection();
            if (!sel || sel.rangeCount < 1) {
                return -1;
            }
            var r = sel.getRangeAt(0);
            var rc = r.getClientRects();
            var box = rc.length ? rc[0] : r.getBoundingClientRect();
            if (!box || (box.width === 0 && box.height === 0)) {
                return -1;
            }
            var v = ((box.top + (G.pageYOffset || 0)) / fmDocHeight());
            return Math.round(Math.min(1, Math.max(0, v)) * 10000) / 10000;
        } catch (e) {
            return -1;
        }
    }

    var bridge = {
        touchpadNative: function (kind, a, b) {
            send({ t: "call", fn: "touchpadNative", a: [Number(kind), Number(a), Number(b)] });
        },
        onPointerLockChanged: function (locked) {
            send({ t: "call", fn: "onPointerLockChanged", a: [!!locked] });
        }
    };

    try {
        Object.defineProperty(G, "__coaraAgent", { value: { send: send }, configurable: true });
    } catch (e) {
        G.__coaraAgent = { send: send };
    }
    G.AndroidBridge = bridge;
    try {
        Object.defineProperty(G, "__coaraFind", { value: { start: fmStart, stop: fmStop, jump: fmJump, activeY: fmActiveY }, configurable: true });
    } catch (e) {
        G.__coaraFind = { start: fmStart, stop: fmStop, jump: fmJump, activeY: fmActiveY };
    }


    send({ t: "hello", top: isTop, url: String(location.href) });

    document.addEventListener("DOMContentLoaded", function () {
        sendIcon();
        watchIcons();
    }, { once: true });
    G.addEventListener("load", function () {
        sendIcon();
    }, { once: true });
})();
