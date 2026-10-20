(function () {
    var S = window.__coaraTr;
    var first = false;
    var re;
    try {
        re = new RegExp('\\p{L}', 'u');
    } catch (e) {
        re = /[A-Za-zÀ-￿]/;
    }
    var kana = /[぀-ヿ]/;
    var latin = /[A-Za-z]/;
    var skipKana = window.__coaraTrTarget === 'ja';
    if (!S) {
        first = true;
        S = window.__coaraTr = {
            nodes: [],
            orig: new WeakMap(),
            tr: new WeakMap(),
            done: new WeakSet(),
            active: true,
            dirty: 0,
            observers: [],
            observedRoots: new WeakSet(),
            sk: 'script,style,noscript,textarea,code,pre,kbd,samp,svg,math,select,[translate="no"],.notranslate,[contenteditable=""],[contenteditable="true"]'
        };
        S.apply = function (base, arr) {
            var i, n, t, o, lead, trail, v;
            for (i = 0; i < arr.length; i++) {
                n = S.nodes[base + i];
                t = arr[i];
                if (!n || !t || !n.isConnected) {
                    continue;
                }
                o = n.nodeValue;
                lead = o.match(/^\s*/)[0];
                trail = o.match(/\s*$/)[0];
                v = lead + t + trail;
                S.orig.set(n, o);
                S.tr.set(n, v);
                n.nodeValue = v;
            }
        };
    }
    if (!S.observers) {
        S.observers = [];
    }
    if (!S.observedRoots) {
        S.observedRoots = new WeakSet();
    }
    S.watchRoot = function (root) {
        if (!root || !window.MutationObserver || S.observedRoots.has(root)) {
            return;
        }
        var observer = new MutationObserver(function (ms) {
            var i, m, n;
            for (i = 0; i < ms.length; i++) {
                m = ms[i];
                if (m.type === 'characterData') {
                    n = m.target;
                    if (S.tr.has(n)) {
                        if (n.nodeValue !== S.tr.get(n)) {
                            S.tr.delete(n);
                            S.done.delete(n);
                            S.dirty = 1;
                        }
                    } else if (!S.done.has(n) && n.nodeValue && re.test(n.nodeValue)) {
                        S.dirty = 1;
                    }
                } else if (m.addedNodes && m.addedNodes.length) {
                    S.dirty = 1;
                }
            }
        });
        try {
            observer.observe(root, {childList: true, subtree: true, characterData: true});
            S.observedRoots.add(root);
            S.observers.push(observer);
        } catch (e) {
            observer.disconnect();
        }
    };
    S.restore = function () {
        var i, n;
        if (S.observers) {
            for (i = 0; i < S.observers.length; i++) {
                try {
                    S.observers[i].disconnect();
                } catch (e) {
                }
            }
            S.observers.length = 0;
        }
        if (S.obs) {
            try {
                S.obs.disconnect();
            } catch (e) {
            }
        }
        for (i = 0; i < S.nodes.length; i++) {
            n = S.nodes[i];
            if (n && n.isConnected && S.tr.has(n) && S.orig.has(n) && n.nodeValue === S.tr.get(n)) {
                n.nodeValue = S.orig.get(n);
            }
        }
        S.active = false;
        S.nodes = [];
        delete window.__coaraTr;
    };
    S.dirty = 0;
    for (var k = 0; k < S.nodes.length; k++) {
        if (S.nodes[k] && !S.nodes[k].isConnected) {
            S.nodes[k] = null;
        }
    }
    var root = document.body || document.documentElement;
    var roots = [root];
    var seenRoots = new WeakSet();
    seenRoots.add(root);
    S.watchRoot(document.documentElement);
    var vis = [];
    var rest = [];
    var vh = window.innerHeight || 800;
    var rootIndex = 0;
    var rootNode, walker, walkNode, node, v, p, r, shadow;
    while (rootIndex < roots.length) {
        rootNode = roots[rootIndex++];
        if (rootNode.nodeType === 11) {
            S.watchRoot(rootNode);
        }
        walker = document.createTreeWalker(rootNode, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, null);
        while ((walkNode = walker.nextNode())) {
            if (walkNode.nodeType === 1) {
                shadow = walkNode.shadowRoot;
                if (shadow && !seenRoots.has(shadow) && !walkNode.matches(S.sk) && !walkNode.closest(S.sk)) {
                    seenRoots.add(shadow);
                    roots.push(shadow);
                }
                continue;
            }
            if (walkNode.nodeType !== 3) {
                continue;
            }
            node = walkNode;
            if (S.done.has(node)) {
                continue;
            }
            v = node.nodeValue;
            if (!v || !re.test(v)) {
                continue;
            }
            p = node.parentElement;
            if (!p || p.closest(S.sk)) {
                continue;
            }
            r = p.getBoundingClientRect();
            if (r.bottom >= 0 && r.top <= vh * 2) {
                vis.push(node);
            } else {
                rest.push(node);
            }
        }
    }
    var all = vis.concat(rest);
    var out = [];
    var base = S.nodes.length;
    var chars = 0;
    var i, t;
    for (i = 0; i < all.length; i++) {
        node = all[i];
        t = node.nodeValue.replace(/\s+/g, ' ').trim();
        if (!t) {
            continue;
        }
        if (skipKana && kana.test(t) && !latin.test(t)) {
            S.done.add(node);
            continue;
        }
        if (out.length >= 3000 || chars + t.length > 150000) {
            S.dirty = 1;
            break;
        }
        chars += t.length;
        S.done.add(node);
        S.nodes.push(node);
        out.push(t);
    }
    return {base: base, texts: out, lang: document.documentElement.lang || '', first: first};
})()
