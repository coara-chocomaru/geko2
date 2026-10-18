(function () {
    if (window.__coaraTouchpadNative) {
        return;
    }

    var d = document;
    var _vw = 0;
    var _vh = 0;
    var cx = 0;
    var cy = 0;
    var layer = null;
    var cur = null;
    var hov = null;
    var nativeActive = false;
    var wantActive = false;
    var nativePress = null;
    var fadeTimer = 0;
    var fadeAt = 0;
    var resizeBound = false;
    var lastTg = null;
    var keyXf = false;

    function measure() {
        _vw = window.innerWidth || (d.documentElement && d.documentElement.clientWidth) || 360;
        _vh = window.innerHeight || (d.documentElement && d.documentElement.clientHeight) || 640;
    }

    function clamp(v, lo, hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    function host(kind, a, b) {
        try {
            if (window.AndroidBridge && typeof window.AndroidBridge.touchpadNative === "function") {
                window.AndroidBridge.touchpadNative(kind, a, b);
                return true;
            }
        } catch (e) {}
        return false;
    }

    function hostPos(kind) {
        measure();
        return host(kind, clamp(cx / _vw, 0, 1), clamp(cy / _vh, 0, 1));
    }

    function arrowUri() {
        return "url(\"data:image/svg+xml;utf8," + encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24'><path d='M3 2v17.5l4.6-4.3 3.2 7 2.9-1.3-3.1-6.9H17.5z' fill='#ffffff' stroke='#000000' stroke-width='1.5' stroke-linejoin='round'/></svg>") + "\")";
    }

    function styleCursor() {
        if (!cur) {
            return;
        }
        var sz = 26;
        cur.style.cssText = "position:fixed;left:0;top:0;width:" + sz + "px;height:" + sz + "px;margin:" + (-sz * 2 / 24) + "px 0 0 " + (-sz * 3 / 24) + "px;background:" + arrowUri() + " 0 0/100% 100% no-repeat;pointer-events:none;will-change:transform;transition:opacity .25s;opacity:1;transform:translate3d(" + cx + "px," + cy + "px,0);display:" + (nativeActive ? "block" : "none");
    }

    function hostRoot() {
        var fs = d.fullscreenElement || d.webkitFullscreenElement || null;
        if (fs && fs.nodeType === 1 && !/^(VIDEO|CANVAS|IMG|IFRAME|FRAME|EMBED|OBJECT|INPUT|TEXTAREA|SELECT|AUDIO)$/i.test(fs.tagName)) {
            return fs;
        }
        return d.documentElement;
    }

    function ensure() {
        var root = hostRoot();
        if (!root) {
            return false;
        }
        if (layer && layer.parentNode === root) {
            return true;
        }
        var fresh = !layer;
        if (fresh) {
            measure();
            cx = clamp(Math.round(cx || _vw / 2), 0, _vw - 1);
            cy = clamp(Math.round(cy || _vh / 2), 0, _vh - 1);
            layer = d.createElement("div");
            layer.setAttribute("aria-hidden", "true");
            layer.style.cssText = "position:fixed;left:0;top:0;width:0;height:0;overflow:visible;pointer-events:none;z-index:2147483647;margin:0;padding:0;border:0";
            cur = d.createElement("div");
            layer.appendChild(cur);
        }
        try {
            root.appendChild(layer);
        } catch (e) {
            return false;
        }
        if (fresh) {
            styleCursor();
        } else if (cur) {
            cur.style.display = nativeActive ? "block" : "none";
        }
        if (!resizeBound) {
            resizeBound = true;
            window.addEventListener("resize", function () {
                measure();
                cx = clamp(cx, 0, _vw - 1);
                cy = clamp(cy, 0, _vh - 1);
                styleCursor();
            }, { passive: true });
            var fsChange = function () {
                ensure();
            };
            d.addEventListener("fullscreenchange", fsChange, true);
            d.addEventListener("webkitfullscreenchange", fsChange, true);
        }
        return true;
    }

    function fadeCheck() {
        fadeTimer = 0;
        var left = fadeAt - Date.now();
        if (left > 30) {
            fadeTimer = setTimeout(fadeCheck, left);
            return;
        }
        if (cur) {
            cur.style.opacity = ".55";
        }
    }

    function showCursor() {
        if (!cur) {
            return;
        }
        if (cur.style.opacity !== "1") {
            cur.style.opacity = "1";
        }
        fadeAt = Date.now() + 2500;
        if (!fadeTimer) {
            fadeTimer = setTimeout(fadeCheck, 2500);
        }
    }

    function hit(doc, x, y) {
        var el = doc.elementFromPoint(x, y);
        return el;
    }

    function pick(x, y) {
        var ox = 0;
        var oy = 0;
        var xf = false;
        var el;
        try {
            el = hit(d, x, y);
        } catch (e) {
            return null;
        }
        if (!el) {
            return null;
        }
        for (var g = 0; g < 8; g++) {
            var nx = null;
            xf = false;
            try {
                if (el.shadowRoot && el.shadowRoot.elementFromPoint) {
                    var s1 = el.shadowRoot.elementFromPoint(x - ox, y - oy);
                    if (s1 && s1 !== el) {
                        nx = s1;
                    }
                } else if (el.tagName === "IFRAME" || el.tagName === "FRAME") {
                    var cd = null;
                    try {
                        cd = el.contentDocument;
                    } catch (e0) {
                        cd = null;
                    }
                    if (cd) {
                        var r = el.getBoundingClientRect();
                        var bx = r.left + (el.clientLeft || 0);
                        var by = r.top + (el.clientTop || 0);
                        var s2 = cd.elementFromPoint(x - ox - bx, y - oy - by);
                        if (s2) {
                            ox += bx;
                            oy += by;
                            nx = s2;
                        }
                    } else {
                        xf = true;
                    }
                }
            } catch (e) {
                nx = null;
            }
            if (!nx) {
                break;
            }
            el = nx;
        }
        return { el: el, ox: ox, oy: oy, xf: xf };
    }

    function fire(tg, type, o) {
        o = o || {};
        var el = tg.el;
        var doc = el.ownerDocument;
        var w = (doc && doc.defaultView) || window;
        var ptr = type.indexOf("pointer") === 0;
        var nb = /enter$|leave$/.test(type);
        var x = cx - tg.ox;
        var y = cy - tg.oy;
        var init = {
            bubbles: !nb,
            cancelable: !nb,
            composed: true,
            view: w,
            clientX: x,
            clientY: y,
            screenX: x,
            screenY: y,
            button: o.button || 0,
            buttons: o.buttons || 0,
            detail: o.detail || 0,
            movementX: o.movementX || 0,
            movementY: o.movementY || 0,
            relatedTarget: o.related || null
        };
        var ev;
        try {
            if (ptr) {
                if (!w.PointerEvent) {
                    return true;
                }
                init.pointerId = 1;
                init.pointerType = "mouse";
                init.isPrimary = true;
                init.width = 1;
                init.height = 1;
                init.pressure = init.buttons ? 0.5 : 0;
                ev = new w.PointerEvent(type, init);
            } else {
                ev = new w.MouseEvent(type, init);
            }
            return el.dispatchEvent(ev);
        } catch (e) {
            return true;
        }
    }

    function windowOf(el) {
        var doc = el && el.ownerDocument;
        return (doc && doc.defaultView) || window;
    }

    function touchOk(w) {
        return typeof w.Touch === "function" && typeof w.TouchEvent === "function";
    }

    function fireTouch(tg, type) {
        var el = tg.el;
        var w = windowOf(el);
        if (!touchOk(w)) {
            return true;
        }
        var x = cx - tg.ox;
        var y = cy - tg.oy;
        try {
            var t = new w.Touch({
                identifier: 1,
                target: el,
                clientX: x,
                clientY: y,
                screenX: x,
                screenY: y,
                pageX: x + (w.pageXOffset || 0),
                pageY: y + (w.pageYOffset || 0),
                radiusX: 1,
                radiusY: 1,
                rotationAngle: 0,
                force: 1
            });
            var ending = type === "touchend" || type === "touchcancel";
            var list = ending ? [] : [t];
            var ev = new w.TouchEvent(type, {
                bubbles: true,
                cancelable: true,
                composed: true,
                view: w,
                touches: list,
                targetTouches: list,
                changedTouches: [t]
            });
            return el.dispatchEvent(ev);
        } catch (e) {
            return true;
        }
    }

    function parentOrHost(n) {
        var p = n.parentNode;
        if (p && p.nodeType === 11) {
            return p.host || null;
        }
        return p;
    }

    function chain(el) {
        var a = [];
        var n = el;
        while (n && n.nodeType === 1) {
            a.push(n);
            n = parentOrHost(n);
        }
        return a;
    }

    function crossing(oldT, nu) {
        var oc = oldT ? chain(oldT.el) : [];
        var nc = chain(nu.el);
        var oi = oc.length - 1;
        var ni = nc.length - 1;
        while (oi >= 0 && ni >= 0 && oc[oi] === nc[ni]) {
            oi--;
            ni--;
        }
        var i;
        if (oldT) {
            fire(oldT, "pointerout", { related: nu.el });
            fire(oldT, "mouseout", { related: nu.el });
            for (i = 0; i <= oi; i++) {
                fire({ el: oc[i], ox: oldT.ox, oy: oldT.oy }, "pointerleave", { related: nu.el });
                fire({ el: oc[i], ox: oldT.ox, oy: oldT.oy }, "mouseleave", { related: nu.el });
            }
        }
        fire(nu, "pointerover", { related: oldT ? oldT.el : null });
        fire(nu, "mouseover", { related: oldT ? oldT.el : null });
        for (i = ni; i >= 0; i--) {
            fire({ el: nc[i], ox: nu.ox, oy: nu.oy }, "pointerenter", { related: oldT ? oldT.el : null });
            fire({ el: nc[i], ox: nu.ox, oy: nu.oy }, "mouseenter", { related: oldT ? oldT.el : null });
        }
    }

    function syncHover(tg) {
        if (!hov || hov.el !== tg.el) {
            crossing(hov, tg);
            hov = tg;
        }
        return tg;
    }

    function focusFor(el) {
        var f = null;
        try {
            f = el.closest("input,textarea,select,button,a[href],[tabindex],[contenteditable=''],[contenteditable='true'],summary");
        } catch (e) {
            f = null;
        }
        if (f && f.disabled) {
            f = null;
        }
        if (f) {
            try {
                f.focus({ preventScroll: true });
            } catch (e) {
                try {
                    f.focus();
                } catch (e2) {}
            }
            return;
        }
        var doc = el.ownerDocument;
        var a = doc && doc.activeElement;
        if (a && a !== doc.body && typeof a.blur === "function" && (/^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName) || a.isContentEditable)) {
            try {
                a.blur();
            } catch (e) {}
        }
    }

    function openPicker(el) {
        if (typeof el.showPicker === "function") {
            try {
                el.showPicker();
            } catch (e) {}
        }
    }

    function caretAt(doc, x, y) {
        if (!doc) {
            return null;
        }
        try {
            if (doc.caretRangeFromPoint) {
                return doc.caretRangeFromPoint(x, y);
            }
            if (doc.caretPositionFromPoint) {
                var c = doc.caretPositionFromPoint(x, y);
                if (c) {
                    var r = doc.createRange();
                    r.setStart(c.offsetNode, c.offset);
                    r.collapse(true);
                    return r;
                }
            }
        } catch (e) {}
        return null;
    }

    function selectionFor(doc) {
        if (!doc) {
            return null;
        }
        try {
            if (typeof doc.getSelection === "function") {
                return doc.getSelection();
            }
            var w = doc.defaultView;
            return w && typeof w.getSelection === "function" ? w.getSelection() : null;
        } catch (e) {
            return null;
        }
    }

    function afterClick(tg) {
        var el = tg.el;
        var tag = el.tagName;
        if (tag === "SELECT") {
            openPicker(el);
        } else if (tag === "INPUT") {
            var ty = (el.type || "").toLowerCase();
            if (ty === "range") {
                var r = el.getBoundingClientRect();
                if (r.width) {
                    var min = parseFloat(el.min);
                    var max = parseFloat(el.max);
                    var step = parseFloat(el.step);
                    if (isNaN(min)) min = 0;
                    if (isNaN(max)) max = 100;
                    if (isNaN(step) || step <= 0) step = 1;
                    var ratio = clamp((cx - tg.ox - r.left) / r.width, 0, 1);
                    var v = min + ratio * (max - min);
                    v = Math.round((v - min) / step) * step + min;
                    v = clamp(v, min, max);
                    if (String(v) !== el.value) {
                        el.value = String(v);
                        var w = el.ownerDocument.defaultView || window;
                        try {
                            el.dispatchEvent(new w.Event("input", { bubbles: true }));
                            el.dispatchEvent(new w.Event("change", { bubbles: true }));
                        } catch (e) {}
                    }
                }
            } else if (/^(date|time|datetime-local|month|week|color)$/.test(ty)) {
                openPicker(el);
            }
        } else if ((tag === "VIDEO" || tag === "AUDIO") && el.controls) {
            try {
                if (el.paused) {
                    var pr = el.play();
                    if (pr && pr.catch) {
                        pr.catch(function () {});
                    }
                } else {
                    el.pause();
                }
            } catch (e) {}
        } else if (el.isContentEditable) {
            var r2 = caretAt(el.ownerDocument, cx - tg.ox, cy - tg.oy);
            if (r2) {
                try {
                    var s = selectionFor(el.ownerDocument);
                    if (s) {
                        s.removeAllRanges();
                        s.addRange(r2);
                    }
                } catch (e) {}
            }
        }
    }

    function clearSelection(tg) {
        try {
            var s = (tg.el.ownerDocument.defaultView || window).getSelection();
            if (s && s.type === "Range" && !tg.el.isContentEditable && !/^(INPUT|TEXTAREA)$/.test(tg.el.tagName)) {
                s.removeAllRanges();
            }
        } catch (e) {}
    }

    function targetAt() {
        return pick(cx, cy);
    }

    function activate() {
        if (nativeActive) {
            return true;
        }
        if (!wantActive) {
            return false;
        }
        return start();
    }

    function move(dx, dy) {
        if (!isFinite(dx) || !isFinite(dy) || (dx === 0 && dy === 0)) {
            return;
        }
        if (!nativeActive && !activate()) {
            return;
        }
        if (!layer || !layer.parentNode) {
            ensure();
        }
        cx = clamp(cx + dx, 0, _vw - 1);
        cy = clamp(cy + dy, 0, _vh - 1);
        if (cur) {
            cur.style.transform = "translate3d(" + cx + "px," + cy + "px,0)";
        }
        showCursor();
        var tg = targetAt();
        if (!tg) {
            return;
        }
        var p = nativePress;
        if (p && p.xf) {
            hostPos(3);
            return;
        }
        if (tg.xf) {
            hostPos(1);
            return;
        }
        syncHover(tg);
        var buttons = p ? p.buttons : 0;
        fire(tg, "pointermove", { buttons: buttons, movementX: dx, movementY: dy });
        if (p && p.touch) {
            fireTouch(p.tg, "touchmove");
        }
        if (!(p && p.tprev)) {
            fire(tg, "mousemove", { buttons: buttons, movementX: dx, movementY: dy });
        }
    }

    function down(button) {
        if (button !== 0 && button !== 2) {
            return;
        }
        if (!nativeActive && !activate()) {
            return;
        }
        ensure();
        showCursor();
        var tg = targetAt();
        if (!tg) {
            return;
        }
        if (nativePress && nativePress.button === button) {
            return;
        }
        if (nativePress) {
            up(nativePress.button);
        }
        var bit = button === 2 ? 2 : 1;
        if (tg.xf) {
            nativePress = { button: button, buttons: bit, tg: tg, sx: cx, sy: cy, xf: true, touch: false, tprev: false };
            if (button === 0) {
                hostPos(2);
            }
            return;
        }
        syncHover(tg);
        nativePress = { button: button, buttons: bit, tg: tg, sx: cx, sy: cy, xf: false, touch: false, tprev: false };
        lastTg = tg;
        fire(tg, "pointerdown", { button: button, buttons: bit, detail: 1 });
        if (button === 0 && touchOk(windowOf(tg.el))) {
            nativePress.touch = true;
            nativePress.tprev = !fireTouch(tg, "touchstart");
        }
        if (!nativePress.tprev) {
            var md = fire(tg, "mousedown", { button: button, buttons: bit, detail: 1 });
            if (md) {
                focusFor(tg.el);
            }
        }
    }

    function up(button) {
        var p = nativePress;
        if (!p || p.button !== button) {
            return;
        }
        nativePress = null;
        if (p.xf) {
            if (button === 0) {
                hostPos(4);
            }
            return;
        }
        var tg = targetAt() || p.tg;
        fire(tg, "pointerup", { button: button, buttons: 0, detail: 1 });
        var endPrevented = false;
        if (p.touch) {
            endPrevented = !fireTouch(p.tg, "touchend");
        }
        if (p.tprev) {
            return;
        }
        fire(tg, "mouseup", { button: button, buttons: 0, detail: 1 });
        if (button === 2) {
            fire(tg, "contextmenu", { button: 2, buttons: 0, detail: 1 });
            fire(tg, "auxclick", { button: 2, buttons: 0, detail: 1 });
        } else if (!endPrevented && tg.el === p.tg.el && Math.hypot(cx - p.sx, cy - p.sy) < 8) {
            var ce = fire(tg, "click", { button: 0, buttons: 0, detail: 1 });
            if (ce) {
                afterClick(tg);
            }
            clearSelection(tg);
        }
    }

    function click(button) {
        down(button);
        up(button);
    }

    function cancel() {
        if (!nativePress) {
            return;
        }
        var p = nativePress;
        nativePress = null;
        if (p.xf) {
            if (p.button === 0) {
                hostPos(4);
            }
            return;
        }
        var tg = targetAt() || p.tg;
        fire(tg, "pointerup", { button: p.button, buttons: 0, detail: 1 });
        if (p.touch) {
            fireTouch(p.tg, "touchcancel");
        }
        if (!p.tprev) {
            fire(tg, "mouseup", { button: p.button, buttons: 0, detail: 1 });
        }
    }

    var KEYS = {
        w: {
            u: { id: "wu", key: "w", code: "KeyW", kc: 87, ch: 119, ak: 51 },
            d: { id: "wd", key: "s", code: "KeyS", kc: 83, ch: 115, ak: 47 },
            l: { id: "wl", key: "a", code: "KeyA", kc: 65, ch: 97, ak: 29 },
            r: { id: "wr", key: "d", code: "KeyD", kc: 68, ch: 100, ak: 32 }
        },
        a: {
            u: { id: "au", key: "ArrowUp", code: "ArrowUp", kc: 38, ch: 0, ak: 19 },
            d: { id: "ad", key: "ArrowDown", code: "ArrowDown", kc: 40, ch: 0, ak: 20 },
            l: { id: "al", key: "ArrowLeft", code: "ArrowLeft", kc: 37, ch: 0, ak: 21 },
            r: { id: "ar", key: "ArrowRight", code: "ArrowRight", kc: 39, ch: 0, ak: 22 }
        }
    };
    var SPACE = { id: "sp", key: " ", code: "Space", kc: 32, ch: 32, ak: 62 };
    var DIRS = ["u", "d", "l", "r"];
    var stMode = 0;
    var stX = 0;
    var stY = 0;
    var stOn = false;
    var stRaf = 0;
    var stRafType = 0;
    var stLast = 0;
    var stDirActive = false;
    var stAimed = false;
    var lookRx = 0;
    var lookRy = 0;
    var dirs = { u: false, d: false, l: false, r: false };
    var held = {};
    var pad = null;
    var padOn = false;
    var padWins = [];
    var aimOn = false;
    var aimTg = null;

    function usesWasd() {
        return stMode === 0 || stMode === 1;
    }

    function usesArrows() {
        return stMode === 0 || stMode === 2;
    }

    function usesPad() {
        return stMode === 0 || stMode === 3;
    }

    function nowMs() {
        return (window.performance && performance.now) ? performance.now() : Date.now();
    }

    function keyTarget() {
        keyXf = false;
        var doc = d;
        var el = d.activeElement;
        for (var i = 0; i < 6 && el && (el.tagName === "IFRAME" || el.tagName === "FRAME"); i++) {
            var cd = null;
            try {
                cd = el.contentDocument;
            } catch (e) {
                cd = null;
            }
            if (!cd) {
                keyXf = true;
                return null;
            }
            doc = cd;
            el = cd.activeElement;
        }
        if (el && el !== doc.body && el !== doc.documentElement && el.tagName !== "IFRAME" && el.tagName !== "FRAME") {
            return el;
        }
        if (lastTg && lastTg.el && lastTg.el.isConnected && lastTg.el.ownerDocument === doc) {
            return lastTg.el;
        }
        var tg = pick(cx, cy);
        if (tg && tg.el && tg.el.ownerDocument === doc) {
            return tg.el;
        }
        return doc.body || doc.documentElement;
    }

    function emitKey(def, type, repeat, xf) {
        if (xf) {
            if (type === "keypress" || repeat) {
                return true;
            }
            host(type === "keydown" ? 5 : 6, def.ak, 0);
            return true;
        }
        var t = keyTarget();
        if (!t) {
            return true;
        }
        var doc = t.ownerDocument || d;
        var w = doc.defaultView || window;
        var press = type === "keypress";
        var code = press ? def.ch : def.kc;
        var init = {
            key: def.key,
            code: def.code,
            keyCode: code,
            which: code,
            charCode: press ? def.ch : 0,
            bubbles: true,
            cancelable: true,
            composed: true,
            view: w,
            repeat: !!repeat,
            location: 0
        };
        var ev;
        try {
            ev = new w.KeyboardEvent(type, init);
        } catch (e) {
            return true;
        }
        try {
            if (ev.keyCode !== code) {
                Object.defineProperty(ev, "keyCode", { get: function () { return code; } });
            }
            if (ev.which !== code) {
                Object.defineProperty(ev, "which", { get: function () { return code; } });
            }
            if (press && ev.charCode !== def.ch) {
                Object.defineProperty(ev, "charCode", { get: function () { return def.ch; } });
            }
        } catch (e) {}
        try {
            return t.dispatchEvent(ev);
        } catch (e) {
            return true;
        }
    }

    function setKey(def, on, t) {
        var h = held[def.id];
        if (on) {
            if (h) {
                return;
            }
            keyTarget();
            var xf = keyXf;
            held[def.id] = { def: def, next: t + 450, xf: xf };
            var ok = emitKey(def, "keydown", false, xf);
            if (ok && def.ch && !xf) {
                emitKey(def, "keypress", false, false);
            }
        } else {
            if (!h) {
                return;
            }
            delete held[def.id];
            emitKey(def, "keyup", false, h.xf);
        }
    }

    function applyKeys() {
        var t = nowMs();
        var w = usesWasd();
        var a = usesArrows();
        for (var i = 0; i < 4; i++) {
            var dir = DIRS[i];
            setKey(KEYS.w[dir], w && dirs[dir], t);
            setKey(KEYS.a[dir], a && dirs[dir], t);
        }
    }

    function releaseKeys() {
        var ids = [];
        for (var id in held) {
            if (Object.prototype.hasOwnProperty.call(held, id)) {
                ids.push(id);
            }
        }
        for (var i = 0; i < ids.length; i++) {
            var h = held[ids[i]];
            if (h) {
                delete held[ids[i]];
                emitKey(h.def, "keyup", false, h.xf);
            }
        }
    }

    function repeatKeys(t) {
        for (var id in held) {
            if (Object.prototype.hasOwnProperty.call(held, id)) {
                var h = held[id];
                if (t >= h.next) {
                    h.next = t + 40;
                    emitKey(h.def, "keydown", true, h.xf);
                }
            }
        }
    }

    function decode(x, y) {
        var m = Math.sqrt(x * x + y * y);
        if (m < (stDirActive ? 0.18 : 0.28)) {
            stDirActive = false;
            dirs.u = dirs.d = dirs.l = dirs.r = false;
            return;
        }
        stDirActive = true;
        var nx = x / m;
        var ny = y / m;
        dirs.r = nx >= (dirs.r ? 0.34 : 0.42);
        dirs.l = nx <= -(dirs.l ? 0.34 : 0.42);
        dirs.d = ny >= (dirs.d ? 0.34 : 0.42);
        dirs.u = ny <= -(dirs.u ? 0.34 : 0.42);
    }

    function makePad() {
        var btns = [];
        for (var i = 0; i < 17; i++) {
            btns.push({ pressed: false, touched: false, value: 0 });
        }
        return {
            id: "Coara Virtual Gamepad (STANDARD GAMEPAD Vendor: 0000 Product: 0000)",
            index: 0,
            connected: true,
            mapping: "standard",
            axes: [0, 0, 0, 0],
            buttons: btns,
            timestamp: nowMs(),
            vibrationActuator: null,
            hapticActuators: []
        };
    }

    function padEvent(w, type) {
        try {
            var ev;
            try {
                ev = new w.GamepadEvent(type, { gamepad: pad, bubbles: false, cancelable: false });
            } catch (e) {
                ev = new w.Event(type);
                Object.defineProperty(ev, "gamepad", { value: pad });
            }
            w.dispatchEvent(ev);
        } catch (e) {}
    }

    function patchPad(w) {
        try {
            if (w.__coaraPadPatched) {
                return false;
            }
            var nav = w.navigator;
            var orig = w.Navigator && w.Navigator.prototype && w.Navigator.prototype.getGamepads;
            if (!nav) {
                return false;
            }
            var fn = function () {
                var real = null;
                try {
                    real = typeof orig === "function" ? orig.call(nav) : null;
                } catch (e) {
                    real = null;
                }
                var out = [];
                var n = real ? real.length : 0;
                var i;
                for (i = 0; i < n; i++) {
                    out.push(real[i]);
                }
                if (padOn && pad) {
                    var slot = 0;
                    while (slot < out.length && out[slot]) {
                        slot++;
                    }
                    pad.index = slot;
                    out[slot] = pad;
                }
                while (out.length < 4) {
                    out.push(null);
                }
                return out;
            };
            Object.defineProperty(nav, "getGamepads", { value: fn, configurable: true, writable: true });
            w.__coaraPadPatched = true;
            padWins.push(w);
            return true;
        } catch (e) {
            return false;
        }
    }

    function scanWins(w, depth, list) {
        list.push(w);
        if (depth > 3) {
            return;
        }
        var frames = null;
        try {
            frames = w.document.querySelectorAll("iframe,frame");
        } catch (e) {
            frames = null;
        }
        if (!frames) {
            return;
        }
        for (var i = 0; i < frames.length; i++) {
            var cw = null;
            try {
                cw = frames[i].contentWindow;
                if (cw && cw.document) {
                    scanWins(cw, depth + 1, list);
                }
            } catch (e) {}
        }
    }

    function padEnable() {
        if (!pad) {
            pad = makePad();
        }
        var list = [];
        scanWins(window, 0, list);
        var fresh = !padOn;
        padOn = true;
        for (var i = 0; i < list.length; i++) {
            if (patchPad(list[i]) || fresh) {
                padEvent(list[i], "gamepadconnected");
            }
        }
    }

    function padDisable() {
        if (!padOn) {
            return;
        }
        padOn = false;
        var wins = padWins;
        padWins = [];
        for (var i = 0; i < wins.length; i++) {
            var w = wins[i];
            try {
                padEvent(w, "gamepaddisconnected");
                delete w.navigator.getGamepads;
                w.__coaraPadPatched = false;
            } catch (e) {}
        }
        if (pad) {
            pad.axes[0] = 0;
            pad.axes[1] = 0;
            for (var b = 12; b < 16; b++) {
                pad.buttons[b].pressed = false;
                pad.buttons[b].touched = false;
                pad.buttons[b].value = 0;
            }
        }
    }

    function applyPad(x, y) {
        if (!padOn || !pad) {
            return;
        }
        var m = Math.sqrt(x * x + y * y);
        var ax = 0;
        var ay = 0;
        if (m > 0.1) {
            var k = Math.min(1, (m - 0.1) / 0.9) / m;
            ax = x * k;
            ay = y * k;
        }
        pad.axes[0] = ax;
        pad.axes[1] = ay;
        var b = pad.buttons[12];
        b.pressed = dirs.u;
        b.touched = dirs.u;
        b.value = dirs.u ? 1 : 0;
        b = pad.buttons[13];
        b.pressed = dirs.d;
        b.touched = dirs.d;
        b.value = dirs.d ? 1 : 0;
        b = pad.buttons[14];
        b.pressed = dirs.l;
        b.touched = dirs.l;
        b.value = dirs.l ? 1 : 0;
        b = pad.buttons[15];
        b.pressed = dirs.r;
        b.touched = dirs.r;
        b.value = dirs.r ? 1 : 0;
        pad.timestamp = nowMs();
    }

    function pointerLockTarget() {
        try {
            if (d.pointerLockElement) {
                return { el: d.pointerLockElement, ox: 0, oy: 0 };
            }
            var frames = d.querySelectorAll("iframe,frame");
            for (var i = 0; i < frames.length; i++) {
                var cd = null;
                try {
                    cd = frames[i].contentDocument;
                } catch (e) {
                    cd = null;
                }
                if (cd && cd.pointerLockElement) {
                    var r = frames[i].getBoundingClientRect();
                    return { el: cd.pointerLockElement, ox: r.left + (frames[i].clientLeft || 0), oy: r.top + (frames[i].clientTop || 0) };
                }
            }
        } catch (e) {}
        return null;
    }

    function look(dx, dy) {
        var pl = pointerLockTarget();
        if (pl) {
            fire(pl, "pointermove", { buttons: nativePress ? nativePress.buttons : 0, movementX: dx, movementY: dy });
            fire(pl, "mousemove", { buttons: nativePress ? nativePress.buttons : 0, movementX: dx, movementY: dy });
            return;
        }
        move(dx, dy);
    }

    function aimTouch(type) {
        var tg = type === "touchstart" ? targetAt() : aimTg;
        if (!tg || tg.xf) {
            return;
        }
        if (!touchOk(windowOf(tg.el))) {
            return;
        }
        if (type === "touchstart") {
            aimTg = tg;
            aimOn = true;
        } else if (!aimOn) {
            return;
        }
        fireTouch(tg, type);
        if (type === "touchend") {
            aimOn = false;
            aimTg = null;
        }
    }

    function aim(x, y) {
        if (!nativeActive && !activate()) {
            return;
        }
        measure();
        var r = Math.min(_vw, _vh) * 0.42;
        var m = Math.sqrt(x * x + y * y);
        var px = _vw / 2;
        var py = _vh / 2;
        var releasing = false;
        if (m >= 0.1) {
            var s = m > 1 ? 1 / m : 1;
            px += x * s * r;
            py += y * s * r;
            stAimed = true;
        } else if (!stAimed) {
            return;
        } else {
            stAimed = false;
            releasing = true;
        }
        move(clamp(px, 0, _vw - 1) - cx, clamp(py, 0, _vh - 1) - cy);
        if (releasing) {
            aimTouch("touchend");
        } else if (aimOn) {
            aimTouch("touchmove");
        } else {
            aimTouch("touchstart");
        }
    }

    function stickTick(ts) {
        stRaf = 0;
        if (!stOn) {
            return;
        }
        var t = ts || nowMs();
        var dt = stLast ? Math.min(0.05, Math.max(0.001, (t - stLast) / 1000)) : 0.016;
        stLast = t;
        if (stMode === 4) {
            var m = Math.sqrt(stX * stX + stY * stY);
            if (m > 0.12) {
                var k = (m - 0.12) / 0.88;
                if (k > 1) {
                    k = 1;
                }
                var speed = 900 * k * (0.35 + 0.65 * k) * dt;
                lookRx += (stX / m) * speed;
                lookRy += (stY / m) * speed;
                var ix = lookRx < 0 ? Math.ceil(lookRx) : Math.floor(lookRx);
                var iy = lookRy < 0 ? Math.ceil(lookRy) : Math.floor(lookRy);
                lookRx -= ix;
                lookRy -= iy;
                if (ix !== 0 || iy !== 0) {
                    look(ix, iy);
                }
            }
        } else {
            repeatKeys(nowMs());
        }
        scheduleTick();
    }

    function scheduleTick() {
        if (stRaf) {
            return;
        }
        if (typeof window.requestAnimationFrame === "function") {
            stRaf = window.requestAnimationFrame(stickTick);
            stRafType = 1;
        } else {
            stRaf = setTimeout(stickTick, 16);
            stRafType = 2;
        }
    }

    function cancelTick() {
        if (!stRaf) {
            return;
        }
        try {
            if (stRafType === 1) {
                window.cancelAnimationFrame(stRaf);
            } else {
                clearTimeout(stRaf);
            }
        } catch (e) {}
        stRaf = 0;
    }

    function releaseStick() {
        var was = stOn;
        stOn = false;
        stX = 0;
        stY = 0;
        stLast = 0;
        lookRx = 0;
        lookRy = 0;
        cancelTick();
        decode(0, 0);
        releaseKeys();
        applyPad(0, 0);
        if (was && stMode === 5) {
            aim(0, 0);
        }
        if (aimOn) {
            aimTouch("touchend");
        }
    }

    function applyStick() {
        decode(stX, stY);
        if (usesWasd() || usesArrows()) {
            applyKeys();
        }
        if (usesPad()) {
            applyPad(stX, stY);
        }
        if (stMode === 5) {
            aim(stX, stY);
        }
    }

    function stick(x, y) {
        if (!isFinite(x) || !isFinite(y)) {
            return;
        }
        if (!nativeActive && !activate()) {
            return;
        }
        x = clamp(x, -1, 1);
        y = clamp(y, -1, 1);
        var m = Math.sqrt(x * x + y * y);
        if (m > 1) {
            x /= m;
            y /= m;
        }
        if (x === 0 && y === 0) {
            releaseStick();
            return;
        }
        stX = x;
        stY = y;
        if (!stOn) {
            stOn = true;
            stLast = 0;
            if (usesPad()) {
                padEnable();
            }
            if (stMode !== 5) {
                scheduleTick();
            }
        }
        applyStick();
    }

    function setStickMode(mode) {
        mode = mode | 0;
        if (mode < 0 || mode > 5 || mode === stMode) {
            return;
        }
        var x = stX;
        var y = stY;
        var engaged = stOn;
        releaseStick();
        padDisable();
        stMode = mode;
        if (engaged) {
            stick(x, y);
        }
    }

    function batch(mx, my, hs, tx, ty) {
        if (mx !== 0 || my !== 0) {
            try {
                move(mx, my);
            } catch (e) {}
        }
        if (hs === 1) {
            try {
                stick(tx, ty);
            } catch (e) {}
        }
    }

    function stickTap() {
        if (!nativeActive && !activate()) {
            return;
        }
        if (stMode === 4) {
            click(0);
            return;
        }
        var t = nowMs();
        if (usesWasd() || usesArrows() || stMode === 5) {
            setKey(SPACE, true, t);
            setTimeout(function () {
                setKey(SPACE, false, nowMs());
            }, 90);
        }
        if (usesPad()) {
            if (!padOn) {
                padEnable();
            }
            if (pad) {
                var b = pad.buttons[0];
                b.pressed = true;
                b.touched = true;
                b.value = 1;
                pad.timestamp = nowMs();
                setTimeout(function () {
                    b.pressed = false;
                    b.touched = false;
                    b.value = 0;
                    pad.timestamp = nowMs();
                }, 110);
            }
        }
    }

    function start() {
        wantActive = true;
        if (!ensure()) {
            return false;
        }
        nativeActive = true;
        styleCursor();
        showCursor();
        return true;
    }

    function stop() {
        releaseStick();
        padDisable();
        cancel();
        wantActive = false;
        nativeActive = false;
        hov = null;
        if (cur) {
            cur.style.display = "none";
        }
    }

    function setPointerLocked() {
    }

    measure();
    cx = Math.round(_vw / 2);
    cy = Math.round(_vh / 2);

    window.__coaraTouchpadNative = {
        start: start,
        stop: stop,
        move: move,
        down: down,
        up: up,
        click: click,
        cancel: cancel,
        stick: stick,
        stickMode: setStickMode,
        stickTap: stickTap,
        batch: batch,
        setPointerLocked: setPointerLocked
    };
})();
