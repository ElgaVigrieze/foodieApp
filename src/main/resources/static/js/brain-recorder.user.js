// ==UserScript==
// @name         Foodie Brain Recorder
// @namespace    https://github.com/ElgaVigrieze/foodieApp
// @version      1.0.0
// @description  Automatically records Human Benchmark reaction time and processing speed results in Foodie.
// @author       Foodie
// @match        https://humanbenchmark.com/tests/reactiontime*
// @match        https://www.humanbenchmark.com/tests/reactiontime*
// @match        https://humanbenchmark.now/tests/processing-speed*
// @match        https://www.humanbenchmark.now/tests/processing-speed*
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

    if (!isReactionPage && !isProcessingPage) {
        return;
    }

    // ── Config storage ───────────────────────────────────────────────────────

    function storageGet(key) {
        try {
            const value = GM_getValue(key, '');
            if (value) return value;
        } catch (ignored) {
            // fall through to localStorage
        }
        try {
            return localStorage.getItem('foodie.' + key) || '';
        } catch (ignored) {
            return '';
        }
    }

    function storageSet(key, value) {
        try {
            GM_setValue(key, value);
        } catch (ignored) {
            // ignore
        }
        try {
            localStorage.setItem('foodie.' + key, value);
        } catch (ignored) {
            // ignore
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

            storageSet(STORE_API, String(config.a).trim().replace(/\/+$/, ''));
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

    if (typeof GM_registerMenuCommand === 'function') {
        GM_registerMenuCommand('Foodie Brain: connect / change settings', configureManually);
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
                            reject(responseError(response.status));
                        }
                    },
                    onerror: () => reject(new Error('network error')),
                    ontimeout: () => reject(new Error('request timed out'))
                });
            });
        }

        return fetch(url, { method: 'POST', headers: headers, body: body })
            .then((response) => {
                if (!response.ok) {
                    throw responseError(response.status);
                }
                return response.json();
            });
    }

    function responseError(status) {
        const error = new Error(status === 401
            ? 'token not recognised — open a test from Foodie → Brain to reconnect'
            : 'server returned ' + status);
        error.status = status;
        return error;
    }

    function isRetryable(error) {
        return !error.status || error.status >= 500;
    }

    async function submit(payload) {
        const config = getConfig();
        if (!config) {
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

        try {
            const data = await postResult(url, config.token, payload);
            toast(
                (data.duplicate ? 'Already saved — ' : 'Saved to Foodie — ') + describe(payload),
                'ok'
            );
        } catch (firstError) {
            if (!isRetryable(firstError)) {
                console.warn('[Foodie] Could not save result', firstError);
                toast('Foodie save failed: ' + firstError.message, 'err', 9000);
                return;
            }

            // One retry is safe because the same clientKey is sent again.
            try {
                await new Promise((resolve) => setTimeout(resolve, 1500));
                const data = await postResult(url, config.token, payload);
                toast(
                    (data.duplicate ? 'Already saved — ' : 'Saved to Foodie — ') + describe(payload),
                    'ok'
                );
            } catch (secondError) {
                console.warn('[Foodie] Could not save result after retry', secondError);
                toast('Foodie save failed: ' + secondError.message, 'err', 9000);
            }
        }
    }

    // ── Reaction time (humanbenchmark.com) ───────────────────────────────────

    function startReactionWatcher() {
        const attempts = [];
        let lastResultText = null;
        let scoreHandled = false;

        function scan() {
            const score = document.querySelector('.view-score');
            if (score) {
                if (scoreHandled) return;

                const scoreText = (score.querySelector('h1') || score).innerText || '';
                const match = scoreText.match(/(\d+(?:\.\d+)?)\s*ms/i);
                if (!match) return; // wait until React renders the number

                const average = Math.round(parseFloat(match[1]));
                if (!Number.isFinite(average) || average <= 0) return;

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
            if (!avgMatch || !accuracyMatch) return;

            const avgMs = parseInt(avgMatch[1], 10);
            const accuracy = parseInt(accuracyMatch[1], 10);
            if (!Number.isFinite(avgMs) || !Number.isFinite(accuracy)) return;

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

    consumePairingHash();

    if (!getConfig()) {
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
