// ==UserScript==
// @name         Foodie Brain Recorder
// @namespace    https://github.com/ElgaVigrieze/foodieApp
// @version      1.2.0
// @description  Automatically records Human Benchmark reaction time and processing speed results in Foodie.
// @author       Foodie
// @updateURL    https://foodie-app.fly.dev/js/brain-recorder.user.js
// @downloadURL  https://foodie-app.fly.dev/js/brain-recorder.user.js
// @match        https://humanbenchmark.com/tests/reactiontime*
// @match        https://www.humanbenchmark.com/tests/reactiontime*
// @match        https://humanbenchmark.now/tests/processing-speed*
// @match        https://www.humanbenchmark.now/tests/processing-speed*
// @match        https://foodie-app.fly.dev/brain*
// @match        http://localhost/brain*
// @match        http://127.0.0.1/brain*
// @include      /^http:\/\/(localhost|127\.0\.0\.1):\d+\/brain.*$/
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_registerMenuCommand
// @grant        GM_xmlhttpRequest
// @connect      *
// @run-at       document-idle
// @noframes
// ==/UserScript==

(function () {
    'use strict';

    const STORE_API = 'foodieBrainApi';
    const STORE_TOKEN = 'foodieBrainToken';
    const HINT_KEY = 'foodieBrainHintShown';

    const isReactionPage =
        /(^|\.)humanbenchmark\.com$/i.test(location.hostname) &&
        location.pathname.startsWith('/tests/reactiontime');

    const isProcessingPage =
        /(^|\.)humanbenchmark\.now$/i.test(location.hostname) &&
        location.pathname.startsWith('/tests/processing-speed');

    const isFoodieBrainPage =
        location.pathname === '/brain' &&
        document.getElementById('brain-token') !== null;

    if (!isReactionPage && !isProcessingPage && !isFoodieBrainPage) {
        return;
    }

    const pageName = isReactionPage ? 'reaction' : (isProcessingPage ? 'processing' : 'brain');

    // ── Config storage ───────────────────────────────────────────────────────

    // The token is kept in the userscript manager's private storage. The page's own
    // localStorage is readable by every script on that site (ads, analytics), so it is
    // used only when GM storage is unavailable.
    const hasGmStorage =
        typeof GM_getValue === 'function' && typeof GM_setValue === 'function';

    function storageGet(key) {
        if (hasGmStorage) {
            try {
                return GM_getValue(key, '') || '';
            } catch (ignored) {
                return '';
            }
        }
        try {
            return localStorage.getItem('foodie.' + key) || '';
        } catch (ignored) {
            return '';
        }
    }

    function storageSet(key, value) {
        if (hasGmStorage) {
            try {
                GM_setValue(key, value);
            } catch (ignored) {
                // ignore
            }
            return;
        }
        try {
            localStorage.setItem('foodie.' + key, value);
        } catch (ignored) {
            // ignore
        }
    }

    const TRUSTED_HOSTS = ['foodie-app.fly.dev', 'localhost', '127.0.0.1'];

    function isTrustedApi(api) {
        try {
            const url = new URL(api);
            const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
            return TRUSTED_HOSTS.indexOf(url.hostname) !== -1 &&
                (url.protocol === 'https:' || (local && url.protocol === 'http:'));
        } catch (ignored) {
            return false;
        }
    }

    function getConfig() {
        const api = storageGet(STORE_API).trim().replace(/\/+$/, '');
        const token = storageGet(STORE_TOKEN).trim();
        return api && token ? { api, token } : null;
    }

    /**
     * The Foodie Brain page opens the test with a #foodie=<json> fragment.
     * Reading it here pairs the recorder without any copy/paste.
     */
    function consumePairingHash() {
        const match = location.hash.match(/[#&]foodie=([^&]+)/);
        if (!match) return false;

        try {
            const config = JSON.parse(decodeURIComponent(match[1]));
            if (!config || !config.a || !config.t) return false;

            const api = String(config.a).trim().replace(/\/+$/, '');
            const existing = getConfig();

            // A link on a third-party site must not silently redirect results elsewhere.
            if (!isTrustedApi(api) && !(existing && existing.api === api)) {
                const ok = confirm(
                    'Foodie Brain recorder\n\nA link is asking to send your results to:\n' + api +
                    '\n\nOnly continue if this is your own Foodie server.');
                if (!ok) {
                    history.replaceState(null, '', location.pathname + location.search);
                    return false;
                }
            }

            storageSet(STORE_API, api);
            storageSet(STORE_TOKEN, String(config.t).trim());
            history.replaceState(null, '', location.pathname + location.search);
            toast('Foodie recorder connected. Results will save automatically.', 'ok', 6000);
            return true;
        } catch (error) {
            console.warn('[Foodie] Could not read pairing link', error);
            return false;
        }
    }

    function configureManually() {
        const current = getConfig();
        const api = prompt(
            'Foodie URL (for example https://your-app.fly.dev)',
            current ? current.api : 'https://'
        );
        if (api === null) return;

        const token = prompt(
            'Recorder token — copy it from Foodie → Brain → Automatic recorder',
            current ? current.token : ''
        );
        if (token === null) return;

        storageSet(STORE_API, api.trim().replace(/\/+$/, ''));
        storageSet(STORE_TOKEN, token.trim());
        toast('Recorder settings saved.', 'ok');
    }

    function showStatus() {
        const config = getConfig();
        alert(
            'Foodie Brain recorder\n\n' +
            'Page: ' + pageName + '\n' +
            'Paired: ' + (config ? 'yes' : 'no') + '\n' +
            'Foodie URL: ' + (config ? config.api : '(not set)') + '\n' +
            'Token: ' + (config ? config.token.slice(0, 8) + '…' : '(not set)')
        );
    }

    if (typeof GM_registerMenuCommand === 'function') {
        GM_registerMenuCommand('Foodie Brain: connect / change settings', configureManually);
        GM_registerMenuCommand('Foodie Brain: show recorder status', showStatus);
    }

    // ── Persistent status badge ───────────────────────────────────────────────

    let badge = null;
    let badgeResetTimer = null;

    function setBadge(state, detail) {
        try {
            if (!badge) {
                badge = document.createElement('div');
                badge.id = 'foodie-brain-status';
                badge.style.cssText =
                    'position:fixed;left:12px;bottom:12px;z-index:2147483646;' +
                    'background:#166534;color:#fff;padding:6px 10px;border-radius:999px;' +
                    'font:600 11px/1.2 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;' +
                    'box-shadow:0 4px 14px rgba(0,0,0,.3);pointer-events:none;opacity:.95;' +
                    'max-width:70vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;';
                (document.body || document.documentElement).appendChild(badge);
            }

            const styles = {
                connected: { color: '#166534', text: '🍽️ Foodie: connected' },
                unpaired: { color: '#b45309', text: '🍽️ Foodie: not paired' },
                saving: { color: '#1d4ed8', text: '🍽️ Foodie: saving…' },
                saved: { color: '#166534', text: '🍽️ Foodie: saved' },
                error: { color: '#b91c1c', text: '🍽️ Foodie: error' }
            };
            const style = styles[state] || styles.connected;

            if (badgeResetTimer) {
                clearTimeout(badgeResetTimer);
                badgeResetTimer = null;
            }

            badge.textContent = style.text;
            badge.style.background = style.color;
            badge.title = detail || '';
        } catch (ignored) {
            // The badge must never break the test page.
        }
    }

    function setBadgeThenConnected(detail, delay) {
        setBadge('saved', detail);
        badgeResetTimer = setTimeout(() => {
            setBadge('connected', detail);
        }, delay || 7000);
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    async function heartbeat() {
        const config = getConfig();
        if (!config) return;

        try {
            const data = await postResult(
                config.api + '/api/brain/heartbeat?page=' + encodeURIComponent(pageName),
                config.token,
                {}
            );
            const user = data.user || 'Foodie user';
            console.info('[Foodie] recorder heartbeat accepted for', user, 'on', pageName);
            setBadge('connected', 'Paired with ' + config.api + ' as ' + user + '.');
        } catch (error) {
            console.warn('[Foodie] recorder heartbeat failed', error);
            if (error.status === 401) {
                setBadge('unpaired', 'Token not recognised. Open the test from Foodie → Brain.');
                toast(
                    'Foodie token is no longer valid. Open this test from Foodie → Brain to reconnect.',
                    'warn',
                    9000
                );
            } else {
                setBadge('error', 'Heartbeat failed: ' + error.message);
            }
        }
    }

    // ── Result submission ────────────────────────────────────────────────────

    function localDate() {
        const now = new Date();
        const pad = (value) => String(value).padStart(2, '0');
        return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
    }

    function describe(payload) {
        if (payload.testType === 'REACTION_TIME') {
            return payload.reactionMs + ' ms average';
        }
        return payload.processingAvgMs + ' ms · ' + payload.processingAccuracy + '% accuracy';
    }

    /**
     * Prefer the userscript manager's request API: it bypasses page CSP and CORS.
     * Plain fetch is the fallback (the Foodie API also sends CORS headers).
     */
    function postResult(url, token, payload) {
        const body = JSON.stringify(payload);
        const headers = {
            'Content-Type': 'application/json',
            'X-Brain-Token': token
        };

        const gmRequest = (typeof GM_xmlhttpRequest === 'function')
            ? GM_xmlhttpRequest
            : (typeof GM !== 'undefined' && typeof GM.xmlHttpRequest === 'function'
                ? GM.xmlHttpRequest.bind(GM)
                : null);

        if (gmRequest) {
            return new Promise((resolve, reject) => {
                gmRequest({
                    method: 'POST',
                    url: url,
                    headers: headers,
                    data: body,
                    timeout: 15000,
                    onload: (response) => {
                        if (response.status >= 200 && response.status < 300) {
                            try {
                                resolve(JSON.parse(response.responseText));
                            } catch (error) {
                                resolve({});
                            }
                        } else {
                            reject(responseError(response.status, response.responseText));
                        }
                    },
                    onerror: () => reject(new Error('network error')),
                    ontimeout: () => reject(new Error('request timed out'))
                });
            });
        }

        return fetch(url, { method: 'POST', headers: headers, body: body })
            .then(async (response) => {
                if (!response.ok) {
                    throw responseError(response.status, await response.text().catch(() => ''));
                }
                return response.json();
            });
    }

    function responseError(status, bodyText) {
        let detail = '';
        try {
            detail = JSON.parse(bodyText).error || '';
        } catch (ignored) {
            // body was not JSON
        }
        const error = new Error(status === 401
            ? 'token not recognised — open a test from Foodie → Brain to reconnect'
            : (detail ? detail + ' (' + status + ')' : 'server returned ' + status));
        error.status = status;
        return error;
    }

    function isRetryable(error) {
        return !error.status || error.status >= 500;
    }

    async function submit(payload) {
        const config = getConfig();
        if (!config) {
            setBadge('unpaired', 'Open this test from Foodie → Brain to pair the recorder.');
            toast(
                'Foodie recorder is not connected. Open this test from Foodie → Brain to pair it.',
                'warn',
                9000
            );
            return;
        }

        payload.date = localDate();
        payload.source = 'userscript';
        payload.clientKey = (crypto.randomUUID
            ? crypto.randomUUID()
            : Date.now() + '-' + Math.random().toString(36).slice(2));

        const url = config.api + '/api/brain/ingest';

        setBadge('saving', 'Sending ' + describe(payload) + ' to Foodie…');

        try {
            const data = await postResult(url, config.token, payload);
            console.info('[Foodie] result saved', payload);
            setBadgeThenConnected(describe(payload), 7000);
            toast(
                (data.duplicate ? 'Already saved — ' : 'Saved to Foodie — ') + describe(payload),
                'ok'
            );
        } catch (firstError) {
            if (!isRetryable(firstError)) {
                console.warn('[Foodie] Could not save result', firstError);
                setBadge('error', firstError.message);
                toast('Foodie save failed: ' + firstError.message, 'err', 9000);
                return;
            }

            // One retry is safe because the same clientKey is sent again.
            try {
                await new Promise((resolve) => setTimeout(resolve, 1500));
                const data = await postResult(url, config.token, payload);
                console.info('[Foodie] result saved after retry', payload);
                setBadgeThenConnected(describe(payload), 7000);
                toast(
                    (data.duplicate ? 'Already saved — ' : 'Saved to Foodie — ') + describe(payload),
                    'ok'
                );
            } catch (secondError) {
                console.warn('[Foodie] Could not save result after retry', secondError);
                setBadge('error', secondError.message);
                toast('Foodie save failed: ' + secondError.message, 'err', 9000);
            }
        }
    }

    // ── Reaction time (humanbenchmark.com) ───────────────────────────────────

    function startReactionWatcher() {
        const attempts = [];
        let lastResultText = null;
        let scoreHandled = false;
        let unreadableSince = 0;

        function scan() {
            const score = document.querySelector('.view-score');
            if (score) {
                if (scoreHandled) return;

                const scoreText = (score.querySelector('h1') || score).innerText || '';
                const match = scoreText.match(/(\d+(?:\.\d+)?)\s*ms/i);
                const average = match ? Math.round(parseFloat(match[1])) : NaN;
                if (!Number.isFinite(average) || average <= 0) {
                    // Wait briefly for React to render the number, then say so if it never does.
                    unreadableSince = unreadableSince || Date.now();
                    if (Date.now() - unreadableSince > 3000) {
                        setBadge('error', 'Score screen found but the score could not be read; the site layout may have changed.');
                    }
                    return;
                }
                unreadableSince = 0;

                scoreHandled = true;
                const cleanAttempts = attempts.filter(
                    (value) => Number.isFinite(value) && value > 0 && value <= 60000
                );
                const best = cleanAttempts.length ? Math.min.apply(null, cleanAttempts) : null;

                submit({
                    testType: 'REACTION_TIME',
                    reactionMs: average,
                    bestReactionMs: best,
                    attempts: cleanAttempts
                });
                return;
            }

            // The score screen is gone, so the next run (if any) starts fresh.
            if (scoreHandled) {
                scoreHandled = false;
                attempts.length = 0;
                lastResultText = null;
            }

            const result = document.querySelector('.view-result');
            if (result) {
                const text = (result.innerText || '').trim();
                if (text && text !== lastResultText) {
                    const match = text.match(/(\d+(?:\.\d+)?)\s*ms/i);
                    if (match) {
                        const value = Math.round(parseFloat(match[1]));
                        if (Number.isFinite(value) && value > 0 && value <= 60000) {
                            attempts.push(value);
                        }
                    }
                    lastResultText = text;
                }
            } else {
                lastResultText = null;
            }
        }

        watch(scan);
    }

    // ── Processing speed (humanbenchmark.now) ────────────────────────────────

    function startProcessingWatcher() {
        let lastCompletion = null;
        let unreadableSince = 0;

        function scan() {
            const stage = document.getElementById('ps-stage');
            if (!stage) return;

            const text = stage.innerText || '';
            if (!/All 3 Levels Complete!/i.test(text)) {
                lastCompletion = null;
                return;
            }

            const avgMatch = text.match(/(\d+)\s*ms/i);
            const accuracyMatch = text.match(/(\d+)\s*%/);
            const avgMs = avgMatch ? parseInt(avgMatch[1], 10) : NaN;
            const accuracy = accuracyMatch ? parseInt(accuracyMatch[1], 10) : NaN;
            if (!Number.isFinite(avgMs) || !Number.isFinite(accuracy)) {
                unreadableSince = unreadableSince || Date.now();
                if (Date.now() - unreadableSince > 3000) {
                    setBadge('error', 'Results screen found but the scores could not be read; the site layout may have changed.');
                }
                return;
            }
            unreadableSince = 0;

            const roundsTotalElement = document.getElementById('ps-rounds-total');
            const roundsPerLevel = parseInt(
                roundsTotalElement ? roundsTotalElement.innerText : '10',
                10
            ) || 10;

            const completion = avgMs + '|' + accuracy + '|' + roundsPerLevel;
            if (lastCompletion === completion) return;
            lastCompletion = completion;

            submit({
                testType: 'PROCESSING_SPEED',
                processingAvgMs: avgMs,
                processingAccuracy: accuracy,
                levels: 3,
                roundsPerLevel: roundsPerLevel
            });
        }

        watch(scan);
    }

    // ── Shared DOM watcher ───────────────────────────────────────────────────

    function watch(scan) {
        let scheduled = false;

        function schedule() {
            if (scheduled) return;
            scheduled = true;
            setTimeout(() => {
                scheduled = false;
                try {
                    scan();
                } catch (error) {
                    console.warn('[Foodie] scan failed', error);
                }
            }, 120);
        }

        const observer = new MutationObserver(schedule);
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            characterData: true
        });

        // MutationObserver alone is not enough if a view is rendered by canvas
        // or updated without a DOM mutation we can see.
        setInterval(schedule, 1000);
        schedule();
    }

    // ── Small toast UI ───────────────────────────────────────────────────────

    function toast(message, kind, duration) {
        try {
            let host = document.getElementById('foodie-brain-toasts');
            if (!host) {
                host = document.createElement('div');
                host.id = 'foodie-brain-toasts';
                host.style.cssText =
                    'position:fixed;right:16px;bottom:16px;z-index:2147483647;' +
                    'display:flex;flex-direction:column;gap:8px;align-items:flex-end;' +
                    'pointer-events:none;';
                (document.body || document.documentElement).appendChild(host);
            }

            const colors = {
                ok: '#166534',
                warn: '#b45309',
                err: '#b91c1c'
            };

            const item = document.createElement('div');
            item.textContent = '🍽️ ' + message;
            item.style.cssText =
                'pointer-events:auto;max-width:340px;background:' + (colors[kind] || colors.ok) + ';' +
                'color:#fff;padding:10px 14px;border-radius:10px;' +
                'font:600 13px/1.4 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;' +
                'box-shadow:0 8px 24px rgba(0,0,0,.35);opacity:0;' +
                'transform:translateY(8px);transition:opacity .2s ease,transform .2s ease;';
            host.appendChild(item);

            requestAnimationFrame(() => {
                item.style.opacity = '1';
                item.style.transform = 'translateY(0)';
            });

            setTimeout(() => {
                item.style.opacity = '0';
                item.style.transform = 'translateY(8px)';
                setTimeout(() => item.remove(), 250);
            }, duration || 4500);
        } catch (ignored) {
            // A toast must never break the test page.
        }
    }

    // ── Boot ─────────────────────────────────────────────────────────────────

    if (isFoodieBrainPage) {
        // Running on Foodie's own Brain page: read the token from the page and
        // pair this browser immediately. This removes the need to pair through
        // a test link and confirms the userscript is installed in this browser.
        const tokenInput = document.getElementById('brain-token');
        const token = tokenInput ? tokenInput.value.trim() : '';
        if (token) {
            storageSet(STORE_API, location.origin);
            storageSet(STORE_TOKEN, token);
            setBadge('connected', 'Recorder installed in this browser and paired with ' + location.origin + '.');
            console.info('[Foodie] recorder auto-paired from the Foodie Brain page');
            heartbeat();
        } else {
            setBadge('unpaired', 'No recorder token found on the Brain page.');
        }
        return;
    }

    consumePairingHash();

    const bootConfig = getConfig();
    if (bootConfig) {
        setBadge('connected', 'Paired with ' + bootConfig.api + '. Checking recorder…');
        console.info('[Foodie] recorder active on', pageName, 'page; paired with', bootConfig.api);
        heartbeat();
    } else {
        setBadge('unpaired', 'Open this test from Foodie → Brain to pair the recorder.');
        console.warn('[Foodie] recorder is not paired on', pageName, 'page');

        let shown = false;
        try {
            shown = sessionStorage.getItem(HINT_KEY) === '1';
            sessionStorage.setItem(HINT_KEY, '1');
        } catch (ignored) {
            // ignore
        }
        if (!shown) {
            toast(
                'Foodie recorder is not connected yet. Open this test from Foodie → Brain.',
                'warn',
                8000
            );
        }
    }

    if (isReactionPage) {
        startReactionWatcher();
    }
    if (isProcessingPage) {
        startProcessingWatcher();
    }
})();
