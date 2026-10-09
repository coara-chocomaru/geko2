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
    var darkWanted = false;
    var darkApplied = false;
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

    function luminance(color) {
        var m = /rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+))?/.exec(color || "");
        if (!m) {
            return -1;
        }
        var a = m[4] === undefined ? 1 : parseFloat(m[4]);
        if (a < 0.05) {
            return -1;
        }
        return (0.2126 * parseInt(m[1], 10) + 0.7152 * parseInt(m[2], 10) + 0.0722 * parseInt(m[3], 10)) / 255;
    }

    function pageIsLight() {
        try {
            var meta = document.querySelector("meta[name=color-scheme]");
            if (meta && /dark/i.test(meta.getAttribute("content") || "")) {
                return false;
            }
            var targets = [document.body, document.documentElement];
            for (var i = 0; i < targets.length; i++) {
                if (!targets[i]) {
                    continue;
                }
                var l = luminance(getComputedStyle(targets[i]).backgroundColor);
                if (l >= 0) {
                    return l > 0.55;
                }
            }
            return true;
        } catch (e) {
            return true;
        }
    }

    function evalDark() {
        if (!darkWanted) {
            if (darkApplied) {
                removeCss("__dark");
                darkApplied = false;
            }
            return;
        }
        if (!isTop) {
            return;
        }
        var light = pageIsLight();
        if (light && !darkApplied) {
            setCss("__dark", "html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important;}img,picture,video,canvas,svg image,iframe{filter:invert(1) hue-rotate(180deg)!important;}");
            darkApplied = true;
        } else if (!light && darkApplied) {
            removeCss("__dark");
            darkApplied = false;
        }
    }

    function setDark(on) {
        darkWanted = on;
        evalDark();
    }

    function sendIcon() {
        if (!isTop) {
            return;
        }
        try {
            var best = "";
            var nodes = document.querySelectorAll("link[rel~='icon'],link[rel='shortcut icon'],link[rel~='apple-touch-icon']");
            for (var i = 0; i < nodes.length; i++) {
                var href = nodes[i].href;
                if (href) {
                    best = href;
                    if (/icon/i.test(nodes[i].getAttribute("rel") || "") && !/apple/i.test(nodes[i].getAttribute("rel") || "")) {
                        break;
                    }
                }
            }
            if (!best) {
                best = location.origin + "/favicon.ico";
            }
            if (best !== iconSent && /^https?:/i.test(best)) {
                iconSent = best;
                send({ t: "icon", href: best });
            }
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
            case "dark":
                setDark(!!m.on);
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

    if (location.protocol === "moz-extension:") {
        G.Android = {
            executeCommand: function (command) {
                send({ t: "call", fn: "executeCommand", a: [String(command)] });
            },
            stopCommand: function () {
                send({ t: "call", fn: "stopCommand", a: [] });
            }
        };
    }

    send({ t: "hello", top: isTop, url: String(location.href) });

    document.addEventListener("DOMContentLoaded", function () {
        sendIcon();
        evalDark();
    }, { once: true });
    G.addEventListener("load", function () {
        sendIcon();
        evalDark();
    }, { once: true });
})();
