(function () {
    "use strict";
    var G = window;
    if (G.__coaraAgent) {
        return;
    }
    var APP = "coaraPage";
    var isTop = false;
    try {
        isTop = G.top === G;
    } catch (e) {
        isTop = false;
    }
    var port = null;
    var outbox = [];
    var styles = {};
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
            default:
                break;
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


    send({ t: "hello", top: isTop, url: String(location.href) });

    document.addEventListener("DOMContentLoaded", function () {
        sendIcon();
        watchIcons();
    }, { once: true });
    G.addEventListener("load", function () {
        sendIcon();
    }, { once: true });
})();
