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
    var detectLanguageHint = function (text, parent) {
        var tagged = parent && parent.closest ? parent.closest('[lang]') : null;
        var tag = tagged ? (tagged.getAttribute('lang') || '') : '';
        var rootTag = !!tagged && (tagged === document.documentElement || tagged === document.body);
        if (tag && !rootTag) {
            return tag;
        }
        if (/[\u3040-\u30ff]/.test(text)) return 'ja';
        if (/[\uac00-\ud7af]/.test(text)) return 'ko';
        if (/[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff]/.test(text)) return 'ar';
        if (/[\u0590-\u05ff]/.test(text)) return 'he';
        if (/[\u0e00-\u0e7f]/.test(text)) return 'th';
        if (/[\u0900-\u097f]/.test(text)) return 'hi';
        if (/[\u0370-\u03ff]/.test(text)) return 'el';
        if (/[\u4e00-\u9fff]/.test(text)) return 'zh';
        return rootTag ? '' : tag;
    };
    if (!S) {
        first = true;
        S = window.__coaraTr = {
            nodes: [],
            indexMap: new WeakMap(),
            orig: new WeakMap(),
            tr: new WeakMap(),
            done: new WeakSet(),
            active: true,
            dirty: 0,
            observers: [],
            observedRoots: new WeakSet(),
            sk: 'script,style,noscript,textarea,code,pre,kbd,samp,svg,math,select,[translate="no"],.notranslate,[contenteditable]:not([contenteditable="false"])'
        };
        S.apply = function (base, arr) {
            var indexes = [];
            for (var i = 0; i < arr.length; i++) {
                indexes.push(base + i);
            }
            S.applyIndexes(indexes, arr);
        };
        S.applyIndexes = function (indexes, arr) {
            var i, n, t, o, lead, trail, v;
            for (i = 0; i < arr.length; i++) {
                n = S.nodes[indexes[i]];
                t = arr[i];
                if (!n || t == null || !n.isConnected) {
                    continue;
                }
                o = n.nodeValue;
                lead = o.match(/^\s*/)[0];
                trail = o.match(/\s*$/)[0];
                v = lead + t + trail;
                S.orig.set(n, o);
                S.tr.set(n, v);
                S.done.add(n);
                n.nodeValue = v;
            }
        };
        S.markDoneIndexes = function (indexes) {
            for (var i = 0; i < indexes.length; i++) {
                var node = S.nodes[indexes[i]];
                if (node && node.isConnected) {
                    S.done.add(node);
                }
            }
        };
        S.compact = function () {
            var next = [];
            var indexes = new WeakMap();
            var seen = new WeakSet();
            var pending = false;
            for (var i = 0; i < S.nodes.length; i++) {
                var node = S.nodes[i];
                if (node && node.isConnected && !seen.has(node)) {
                    seen.add(node);
                    indexes.set(node, next.length);
                    next.push(node);
                    if (!S.done.has(node)) {
                        pending = true;
                    }
                }
            }
            S.nodes = next;
            S.indexMap = indexes;
            if (pending) {
                S.dirty = 1;
            }
            return next.length;
        };
    }
    if (!S.indexMap) {
        S.indexMap = new WeakMap();
        for (var index = 0; index < S.nodes.length; index++) {
            if (S.nodes[index]) {
                S.indexMap.set(S.nodes[index], index);
            }
        }
    }
    if (!S.markDoneIndexes) {
        S.markDoneIndexes = function (indexes) {
            for (var index = 0; index < indexes.length; index++) {
                var node = S.nodes[indexes[index]];
                if (node && node.isConnected) {
                    S.done.add(node);
                }
            }
        };
    }
    if (!S.observers) {
        S.observers = [];
    }
    if (!S.observedRoots) {
        S.observedRoots = new WeakSet();
    }
    S.mayContainText = function (root) {
        if (!root) {
            return false;
        }
        if (root.nodeType === 3) {
            return !!root.nodeValue && re.test(root.nodeValue);
        }
        if (root.nodeType !== 1 && root.nodeType !== 11) {
            return false;
        }
        if (root.nodeType === 1 && (root.matches(S.sk) || root.closest(S.sk))) {
            return false;
        }
        var tree = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var current;
        var checked = 0;
        while ((current = tree.nextNode()) && checked < 48) {
            checked++;
            if (current.nodeValue && re.test(current.nodeValue)) {
                var parent = current.parentElement;
                if (parent && !parent.closest(S.sk)) {
                    return true;
                }
            }
        }
        return false;
    };
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
                    for (var a = 0; a < m.addedNodes.length; a++) {
                        if (S.mayContainText(m.addedNodes[a])) {
                            S.dirty = 1;
                            break;
                        }
                    }
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
    var langs = [];
    var indexes = [];
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
        var nodeIndex = S.indexMap.get(node);
        if (typeof nodeIndex !== 'number') {
            nodeIndex = S.nodes.length;
            S.nodes.push(node);
            S.indexMap.set(node, nodeIndex);
        }
        indexes.push(nodeIndex);
        out.push(t);
        p = node.parentElement;
        langs.push(detectLanguageHint(t, p));
    }
    return {indexes: indexes, texts: out, langs: langs, lang: document.documentElement.lang || '', first: first};
})()
