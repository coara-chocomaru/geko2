(function (G) {
    "use strict";
    var state = {
        on: false,
        hosts: Object.create(null),
        paths: Object.create(null),
        prefixes: [],
        tokens: [],
        second: Object.create(null),
        tabHosts: Object.create(null),
        tabCount: 0,
        count: 0,
        reported: 0,
        timer: 0,
        script: "",
        handle: null,
        onCount: null
    };

    var TRUSTED = {
        "youtube.com": 1,
        "google.com": 1,
        "google.co.jp": 1,
        "youtube-nocookie.com": 1,
        "gstatic.com": 1,
        "ytimg.com": 1
    };

    function lower(s) {
        return s ? String(s).toLowerCase() : "";
    }

    function endsWithDot(host, matched) {
        return host === matched || (host.length > matched.length && host.substring(host.length - matched.length - 1) === "." + matched);
    }

    function matchHost(host) {
        var h = host;
        while (true) {
            if (state.hosts[h] === 1) {
                return h;
            }
            var dot = h.indexOf(".");
            if (dot < 0) {
                return null;
            }
            h = h.substring(dot + 1);
            if (h.indexOf(".") < 0) {
                return state.hosts[h] === 1 ? h : null;
            }
        }
    }

    function matchPath(host, path) {
        var h = host;
        while (true) {
            var rules = state.paths[h];
            if (rules) {
                for (var i = 0; i < rules.length; i++) {
                    var r = rules[i];
                    var c = r.charAt(0);
                    if (c === "*") {
                        if (path.indexOf(r.substring(1)) >= 0) {
                            return true;
                        }
                    } else if (c === "=") {
                        if (path === r.substring(1)) {
                            return true;
                        }
                    } else if (path.indexOf(r) === 0) {
                        return true;
                    }
                }
            }
            var dot = h.indexOf(".");
            if (dot < 0 || h.indexOf(".", dot + 1) < 0) {
                return false;
            }
            h = h.substring(dot + 1);
        }
    }

    function registrable(host) {
        var parts = host.split(".");
        if (parts.length < 2) {
            return host;
        }
        var tail2 = parts.slice(-2).join(".");
        if (state.second[tail2] === 1 && parts.length >= 3) {
            return parts.slice(-3).join(".");
        }
        return tail2;
    }

    function isThirdParty(host, pageHost) {
        if (!pageHost || host === pageHost) {
            return false;
        }
        return registrable(host) !== registrable(pageHost);
    }

    function adHostPrefix(host) {
        if (host.split(".").length < 3) {
            return false;
        }
        for (var i = 0; i < state.prefixes.length; i++) {
            if (host.indexOf(state.prefixes[i]) === 0) {
                return true;
            }
        }
        return false;
    }

    function adPathToken(path) {
        if (!path || path.length < 4) {
            return false;
        }
        var p = path.toLowerCase();
        for (var i = 0; i < state.tokens.length; i++) {
            if (p.indexOf(state.tokens[i]) >= 0) {
                return true;
            }
        }
        return false;
    }

    function shouldBlock(host, path, pageHost) {
        if (!host) {
            return false;
        }
        var matched = matchHost(host);
        if (matched) {
            return !(pageHost && endsWithDot(pageHost, matched));
        }
        if (matchPath(host, path)) {
            return !pageHost || !endsWithDot(pageHost, host);
        }
        if (isThirdParty(host, pageHost) && TRUSTED[registrable(pageHost)] !== 1) {
            return adHostPrefix(host) || adPathToken(path);
        }
        return false;
    }

    function hostOfUrl(u) {
        try {
            return lower(new URL(u).hostname);
        } catch (e) {
            return "";
        }
    }

    function rememberTab(tabId, host) {
        if (tabId === undefined || tabId < 0) {
            return;
        }
        if (state.tabHosts[tabId] === undefined) {
            if (state.tabCount > 256) {
                state.tabHosts = Object.create(null);
                state.tabCount = 0;
            }
            state.tabCount++;
        }
        state.tabHosts[tabId] = host;
    }

    function scheduleReport() {
        if (state.timer || !state.onCount) {
            return;
        }
        state.timer = setTimeout(function () {
            state.timer = 0;
            if (state.count !== state.reported) {
                state.reported = state.count;
                try {
                    state.onCount(state.count);
                } catch (e) {
                }
            }
        }, 1000);
    }

    function onBeforeRequest(details) {
        if (!state.on) {
            return undefined;
        }
        var url;
        try {
            url = new URL(details.url);
        } catch (e) {
            return undefined;
        }
        if (url.protocol !== "http:" && url.protocol !== "https:") {
            return undefined;
        }
        var host = lower(url.hostname);
        if (details.type === "main_frame") {
            rememberTab(details.tabId, host);
            return undefined;
        }
        var pageHost = state.tabHosts[details.tabId];
        if (!pageHost) {
            pageHost = hostOfUrl(details.originUrl || details.documentUrl || "");
        }
        if (!shouldBlock(host, url.pathname, pageHost)) {
            return undefined;
        }
        state.count++;
        scheduleReport();
        return { cancel: true };
    }

    function listen(on) {
        var api = browser.webRequest && browser.webRequest.onBeforeRequest;
        if (!api) {
            return;
        }
        if (on) {
            if (!api.hasListener(onBeforeRequest)) {
                api.addListener(onBeforeRequest, { urls: ["http://*/*", "https://*/*"] }, ["blocking"]);
            }
        } else if (api.hasListener(onBeforeRequest)) {
            api.removeListener(onBeforeRequest);
        }
    }

    function toSet(list) {
        var o = Object.create(null);
        for (var i = 0; i < list.length; i++) {
            o[list[i]] = 1;
        }
        return o;
    }

    function toMap(value) {
        var out = Object.create(null);
        if (value && typeof value === "object") {
            var keys = Object.keys(value);
            for (var i = 0; i < keys.length; i++) {
                out[keys[i]] = value[keys[i]];
            }
        }
        return out;
    }

    function dropCosmetic() {
        var h = state.handle;
        state.handle = null;
        if (!h) {
            return Promise.resolve();
        }
        return Promise.resolve(h.unregister()).catch(function () {
        });
    }

    function applyCosmetic() {
        return dropCosmetic().then(function () {
            if (!state.on || !state.script) {
                return null;
            }
            return browser.contentScripts.register({
                matches: ["http://*/*", "https://*/*"],
                js: [{ code: state.script }],
                runAt: "document_start",
                allFrames: true,
                matchAboutBlank: false
            }).then(function (handle) {
                state.handle = handle;
            });
        }).catch(function () {
        });
    }

    function configure(m) {
        var on = !!m.on;
        if (on && m.rules) {
            var r = m.rules;
            state.hosts = toSet(r.hosts || []);
            state.paths = toMap(r.paths);
            state.prefixes = r.prefixes || [];
            state.tokens = r.tokens || [];
            state.second = toSet(r.second || []);
            state.script = typeof m.script === "string" ? m.script : "";
        }
        state.on = on && Object.keys(state.hosts).length > 0;
        listen(state.on);
        return applyCosmetic();
    }

    G.CoaraAdBlock = {
        configure: configure,
        setCountListener: function (fn) {
            state.onCount = fn;
        },
        count: function () {
            return state.count;
        }
    };
})(self);
