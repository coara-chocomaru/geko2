(function () {
    "use strict";
    var APP = "coaraBg";
    var port = null;
    var registered = { plugins: [], css: {} };
    var queue = Promise.resolve();

    function enqueue(job) {
        queue = queue.then(job, job);
        return queue;
    }

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
            return;
        }
        try {
            port.postMessage(msg);
        } catch (e) {
            port = null;
        }
    }

    function unregisterAll(list) {
        var jobs = [];
        for (var i = 0; i < list.length; i++) {
            try {
                jobs.push(list[i].unregister());
            } catch (e) {
            }
        }
        return Promise.all(jobs.map(function (p) {
            return Promise.resolve(p).catch(function () {
            });
        }));
    }

    function registerPlugins(list, seq) {
        return enqueue(function () {
            return doRegisterPlugins(list, seq);
        });
    }

    function doRegisterPlugins(list, seq) {
        var previous = registered.plugins;
        registered.plugins = [];
        return unregisterAll(previous).then(function () {
            var jobs = [];
            for (var i = 0; i < list.length; i++) {
                (function (spec) {
                    var matches = spec.matches && spec.matches.length ? spec.matches : ["<all_urls>"];
                    var job = browser.contentScripts.register({
                        matches: matches,
                        js: [{ code: String(spec.code) }],
                        runAt: "document_start",
                        allFrames: !!spec.allFrames,
                        matchAboutBlank: false
                    }).then(function (handle) {
                        registered.plugins.push(handle);
                    }).catch(function (e) {
                        send({ t: "plugins.error", id: spec.id, m: String(e && e.message ? e.message : e) });
                    });
                    jobs.push(job);
                })(list[i]);
            }
            return Promise.all(jobs);
        }).catch(function () {
        }).then(function () {
            send({ t: "plugins.done", n: registered.plugins.length, seq: seq });
        });
    }

    function registerCss(key, css) {
        return enqueue(function () {
            return doRegisterCss(key, css);
        });
    }

    function doRegisterCss(key, css) {
        var previous = registered.css[key];
        delete registered.css[key];
        var chain = previous ? unregisterAll([previous]) : Promise.resolve();
        return chain.then(function () {
            if (typeof css !== "string" || css.length === 0) {
                return null;
            }
            return browser.contentScripts.register({
                matches: ["http://*/*", "https://*/*"],
                css: [{ code: css }],
                runAt: "document_start",
                allFrames: true
            }).then(function (handle) {
                registered.css[key] = handle;
            });
        }).catch(function () {
        });
    }

    function serializeCookie(c) {
        return {
            name: c.name,
            value: c.value,
            domain: c.domain,
            path: c.path,
            secure: !!c.secure,
            httpOnly: !!c.httpOnly,
            session: !!c.session,
            sameSite: c.sameSite || "no_restriction",
            expirationDate: c.expirationDate === undefined ? 0 : c.expirationDate,
            hostOnly: !!c.hostOnly,
            storeId: c.storeId || ""
        };
    }

    function cookiesGet(rid, filter) {
        browser.cookies.getAll(filter || {}).then(function (list) {
            send({ t: "cookies.res", rid: rid, ok: true, list: list.map(serializeCookie) });
        }).catch(function (e) {
            send({ t: "cookies.res", rid: rid, ok: false, list: [], m: String(e && e.message ? e.message : e) });
        });
    }

    function cookiesSet(rid, items) {
        var jobs = [];
        for (var i = 0; i < items.length; i++) {
            var c = items[i];
            var scheme = c.secure ? "https://" : "http://";
            var host = String(c.domain || "").replace(/^\./, "");
            if (!host) {
                continue;
            }
            var details = {
                url: scheme + host + (c.path || "/"),
                name: c.name,
                value: c.value,
                path: c.path || "/",
                secure: !!c.secure,
                httpOnly: !!c.httpOnly
            };
            if (!c.hostOnly) {
                details.domain = c.domain;
            }
            if (!c.session && c.expirationDate) {
                details.expirationDate = c.expirationDate;
            }
            jobs.push(browser.cookies.set(details).catch(function () {
                return null;
            }));
        }
        Promise.all(jobs).then(function (res) {
            var n = 0;
            for (var k = 0; k < res.length; k++) {
                if (res[k]) {
                    n++;
                }
            }
            send({ t: "cookies.res", rid: rid, ok: true, count: n });
        });
    }

    function cookiesClear(rid) {
        browser.cookies.getAll({}).then(function (list) {
            var jobs = list.map(function (c) {
                var host = String(c.domain || "").replace(/^\./, "");
                return browser.cookies.remove({
                    url: (c.secure ? "https://" : "http://") + host + c.path,
                    name: c.name,
                    storeId: c.storeId
                }).catch(function () {
                    return null;
                });
            });
            return Promise.all(jobs);
        }).then(function () {
            send({ t: "cookies.res", rid: rid, ok: true });
        }).catch(function () {
            send({ t: "cookies.res", rid: rid, ok: false });
        });
    }

    function onNative(m) {
        if (!m || typeof m !== "object") {
            return;
        }
        switch (m.t) {
            case "plugins":
                registerPlugins(m.list || [], m.seq);
                break;
            case "css":
                registerCss(String(m.key), m.css);
                break;
            case "cssoff":
                registerCss(String(m.key), "");
                break;
            case "cookies.get":
                cookiesGet(m.rid, m.filter);
                break;
            case "cookies.set":
                cookiesSet(m.rid, m.list || []);
                break;
            case "cookies.clear":
                cookiesClear(m.rid);
                break;
            case "adblock":
                CoaraAdBlock.configure(m);
                break;
            case "ping":
                send({ t: "pong" });
                break;
            default:
                break;
        }
    }

    var tpSeen = Object.create(null);
    var tpCount = 0;

    function topOf(details) {
        var a = details.frameAncestors;
        if (a && a.length) {
            return String(a[a.length - 1].url || "");
        }
        return String(details.documentUrl || details.originUrl || "");
    }

    function hostOf(u) {
        try {
            return new URL(u).hostname;
        } catch (e) {
            return "";
        }
    }

    function hasSetCookie(headers) {
        if (!headers) {
            return false;
        }
        for (var i = 0; i < headers.length; i++) {
            if (String(headers[i].name).toLowerCase() === "set-cookie") {
                return true;
            }
        }
        return false;
    }

    try {
        browser.webRequest.onHeadersReceived.addListener(function (details) {
            try {
                if (!details.thirdParty || details.tabId < 0 || !hasSetCookie(details.responseHeaders)) {
                    return;
                }
                var top = topOf(details);
                var topHost = hostOf(top);
                var host = hostOf(details.url);
                if (!topHost || !host || topHost === host) {
                    return;
                }
                var key = topHost + ">" + host;
                if (tpSeen[key]) {
                    return;
                }
                if (tpCount >= 1024) {
                    tpSeen = Object.create(null);
                    tpCount = 0;
                }
                tpSeen[key] = 1;
                tpCount++;
                send({ t: "tp.cookie", top: top, url: details.url });
            } catch (e) {
            }
        }, { urls: ["http://*/*", "https://*/*"] }, ["responseHeaders"]);
    } catch (e) {
    }

    CoaraAdBlock.setCountListener(function (n) {
        send({ t: "adblock.count", n: n });
    });

    connect();
    send({ t: "hello" });
})();
