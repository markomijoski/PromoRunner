(function () {
  'use strict';

  const ASSET_BASE = '/game/';
  const MAP_SRC = ASSET_BASE + 'map1.png';
  const PLAYER_SRC = ASSET_BASE + 'car6.png';
  const OBSTACLE_DEFS = [
    { src: ASSET_BASE + 'car1.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car2.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car3.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car4.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car5.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'barrel.png', w: 50, h: 50 },
    { src: ASSET_BASE + 'roadblock.png', w: 60, h: 50 }
  ];

  const dto = window.GAME_CONFIG || {};
  const playsRemainingInitial = typeof dto.playsRemaining === 'number' ? dto.playsRemaining : -1;

  let sessionId = null;
  let playsRemaining = playsRemainingInitial;
  let runStartedAt = 0;

  const state = {
    phase: 'booting', // booting | waiting | playing | over
    frameNo: 0,
    score: 0,
    result: null,
    keys: Object.create(null),
    touchDir: { x: 0, y: 0 }
  };

  let parentEl = null;
  let canvas = null;
  let ctx = null;
  let loopId = null;
  let resizeObserver = null;
  let player = null;
  let obstacles = [];
  let images = {};
  let endRequested = false;
  let pendingStart = false;
  let overlayEls = {};

  function showGate() {
    const el = document.getElementById('gate-overlay');
    if (el) el.classList.remove('hidden');
  }

  function hideGate() {
    const el = document.getElementById('gate-overlay');
    if (el) el.classList.add('hidden');
  }

  function showError(msg) {
    const box = document.getElementById('game-error');
    const text = document.getElementById('game-error-msg');
    if (text) text.textContent = msg || 'Обидете се повторно.';
    if (box) box.classList.remove('hidden');
  }

  function measureParent(el) {
    const rect = el.getBoundingClientRect();
    const w = Math.max(280, Math.floor(rect.width) || el.clientWidth || 420);
    const h = Math.max(360, Math.floor(rect.height) || el.clientHeight || 500);
    return { w: w, h: h };
  }

  function loadImage(src) {
    return new Promise(function (resolve, reject) {
      const img = new Image();
      img.onload = function () {
        resolve(img);
      };
      img.onerror = function () {
        reject(new Error('Не се вчита: ' + src));
      };
      img.src = src;
    });
  }

  function loadAssets() {
    const urls = [MAP_SRC, PLAYER_SRC].concat(OBSTACLE_DEFS.map(function (o) {
      return o.src;
    }));
    const unique = Array.from(new Set(urls));
    return Promise.all(
      unique.map(function (src) {
        return loadImage(src).then(function (img) {
          images[src] = img;
        });
      })
    );
  }

  function scaleX(v) {
    return (v / 420) * canvas.width;
  }

  function scaleY(v) {
    return (v / 500) * canvas.height;
  }

  function startSession() {
    const device = window.matchMedia('(pointer:coarse)').matches ? 'mobile' : 'desktop';
    if (typeof window.amsmFetch !== 'function') {
      return Promise.reject(new Error('amsmFetch недостапен'));
    }

    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = controller
      ? setTimeout(function () {
          controller.abort();
        }, 4000)
      : null;

    return window
      .amsmFetch('/api/game/session/start?deviceType=' + device, {
        method: 'POST',
        signal: controller ? controller.signal : undefined
      })
      .then(function (res) {
        return res.json().catch(function () {
          return {};
        });
      })
      .then(function (data) {
        if (timer) clearTimeout(timer);
        if (!data.canPlay) {
          playsRemaining = 0;
          return false;
        }
        sessionId = data.sessionId;
        playsRemaining = data.playsRemaining;
        return true;
      })
      .catch(function (err) {
        if (timer) clearTimeout(timer);
        if (err && err.name === 'AbortError') {
          throw new Error('Истече времето за поврзување (4с).');
        }
        throw new Error('Неуспешно поврзување со серверот.');
      });
  }

  function createOverlay() {
    let root = document.getElementById('rf-overlay');
    if (!root) {
      root = document.createElement('div');
      root.id = 'rf-overlay';
      root.style.cssText =
        'position:absolute;inset:0;z-index:10;display:none;align-items:center;justify-content:center;' +
        'flex-direction:column;text-align:center;padding:1.5rem;background:rgba(10,10,10,0.82);color:#fff;';
      parentEl.appendChild(root);
    }
    root.innerHTML =
      '<p id="rf-overlay-title" style="font-family:Fira Sans Condensed,sans-serif;font-size:2rem;font-weight:800;margin:0"></p>' +
      '<p id="rf-overlay-score" style="margin-top:0.75rem;font-size:1.25rem"></p>' +
      '<p id="rf-overlay-rank" style="margin-top:0.35rem;font-size:0.95rem;color:rgba(255,255,255,0.75)"></p>' +
      '<button id="rf-overlay-btn" type="button" style="margin-top:1.25rem;border:0;border-radius:0.5rem;' +
      'padding:0.75rem 1.5rem;font-family:Fira Sans Condensed,sans-serif;font-weight:700;text-transform:uppercase;' +
      'background:#FFD200;color:#0A0A0A;cursor:pointer"></button>';
    overlayEls = {
      root: root,
      title: document.getElementById('rf-overlay-title'),
      score: document.getElementById('rf-overlay-score'),
      rank: document.getElementById('rf-overlay-rank'),
      btn: document.getElementById('rf-overlay-btn')
    };
    overlayEls.btn.addEventListener('click', onOverlayAction);
  }

  function showWaitingOverlay() {
    if (!overlayEls.root) return;
    overlayEls.title.textContent = 'Road Fighter';
    overlayEls.score.textContent = 'Притиснете Space / ↑ или допрете за старт';
    overlayEls.rank.textContent = 'Стрелки · ← → ↑ ↓ за возење';
    overlayEls.btn.textContent = 'СТАРТ';
    overlayEls.root.style.display = 'flex';
  }

  function showGameOverOverlay(result) {
    if (!overlayEls.root) return;
    overlayEls.title.textContent = 'КРАЈ';
    overlayEls.score.textContent = 'Резултат: ' + (result.score || 0);
    overlayEls.rank.textContent = result.rank ? 'Ранг #' + result.rank : '';
    overlayEls.btn.textContent = 'ИГРАЈ ПОВТОРНО';
    overlayEls.root.style.display = 'flex';
  }

  function hideOverlay() {
    if (overlayEls.root) overlayEls.root.style.display = 'none';
  }

  function onOverlayAction() {
    if (state.phase === 'waiting') {
      beginRun();
      return;
    }
    if (state.phase === 'over') {
      if (playsRemaining === 0) {
        showGate();
        const root = document.querySelector('[x-data]');
        if (root && window.Alpine) Alpine.$data(root).openConsent();
        return;
      }
      prepareWaiting();
    }
  }

  function resetEntities() {
    obstacles = [];
    state.frameNo = 0;
    state.score = 0;
    endRequested = false;
    player = {
      x: scaleX(200),
      y: scaleY(400),
      w: scaleX(40),
      h: scaleY(70),
      speedX: 0,
      speedY: 0
    };
  }

  function prepareWaiting() {
    state.phase = 'waiting';
    state.result = null;
    sessionId = null;
    pendingStart = false;
    resetEntities();
    showWaitingOverlay();
    startSession()
      .then(function (ok) {
        if (!ok) {
          pendingStart = false;
          showGate();
          hideOverlay();
          state.phase = 'over';
          return;
        }
        if (pendingStart) beginRun();
      })
      .catch(function (err) {
        pendingStart = false;
        showError(err.message || 'Грешка');
      });
  }

  function beginRun() {
    if (state.phase !== 'waiting') return;
    if (sessionId == null) {
      pendingStart = true;
      return;
    }
    pendingStart = false;
    state.phase = 'playing';
    hideOverlay();
    runStartedAt = performance.now();
    resetEntities();
  }

  function crashWith(a, b) {
    const myleft = a.x;
    const myright = a.x + a.w;
    const mytop = a.y;
    const mybottom = a.y + a.h;
    const otherleft = b.x;
    const otherright = b.x + b.w;
    const othertop = b.y;
    const otherbottom = b.y + b.h;
    if (mybottom < othertop || mytop > otherbottom - scaleY(20) || myright < otherleft || myleft > otherright) {
      return false;
    }
    return true;
  }

  function everyInterval(n) {
    return state.frameNo === 1 || state.frameNo % n === 0;
  }

  function spawnObstacle() {
    const def = OBSTACLE_DEFS[Math.floor(Math.random() * OBSTACLE_DEFS.length)];
    const minX = scaleX(100);
    const maxX = Math.max(minX + 1, canvas.width - scaleX(120));
    const x = minX + Math.random() * (maxX - minX);
    obstacles.push({
      src: def.src,
      x: x,
      y: -scaleY(def.h),
      w: scaleX(def.w),
      h: scaleY(def.h)
    });
  }

  function clampPlayer() {
    player.x = Math.max(0, Math.min(canvas.width - player.w, player.x));
    player.y = Math.max(0, Math.min(canvas.height - player.h, player.y));
  }

  function updatePlaying() {
    for (let i = 0; i < obstacles.length; i++) {
      if (crashWith(player, obstacles[i])) {
        gameOver();
        return;
      }
    }

    state.frameNo += 1;
    state.score = state.frameNo;

    if (everyInterval(80)) spawnObstacle();

    const speed = scaleY(4);
    for (let i = 0; i < obstacles.length; i++) {
      obstacles[i].y += speed;
    }
    obstacles = obstacles.filter(function (o) {
      return o.y < canvas.height + o.h;
    });

    const moveX = scaleX(3);
    const moveY = scaleY(3);
    player.speedX = 0;
    player.speedY = 0;

    if (state.keys[37] || state.keys[65] || state.touchDir.x < 0) player.speedX = -moveX;
    if (state.keys[39] || state.keys[68] || state.touchDir.x > 0) player.speedX = moveX;
    if (state.keys[38] || state.keys[87] || state.touchDir.y < 0) player.speedY = -moveY;
    if (state.keys[40] || state.keys[83] || state.touchDir.y > 0) player.speedY = moveY;

    player.x += player.speedX;
    player.y += player.speedY;
    clampPlayer();
  }

  function drawRoadBackground() {
    const map = images[MAP_SRC];
    if (!map) {
      ctx.fillStyle = '#2a2a2a';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      return;
    }
    const scroll = (state.frameNo * scaleY(8)) % canvas.height;
    ctx.drawImage(map, 0, scroll - canvas.height, canvas.width, canvas.height);
    ctx.drawImage(map, 0, scroll, canvas.width, canvas.height);
  }

  function draw() {
    if (!ctx) return;
    drawRoadBackground();

    if (player && images[PLAYER_SRC]) {
      ctx.drawImage(images[PLAYER_SRC], player.x, player.y, player.w, player.h);
    }

    for (let i = 0; i < obstacles.length; i++) {
      const o = obstacles[i];
      const img = images[o.src];
      if (img) ctx.drawImage(img, o.x, o.y, o.w, o.h);
    }

    if (state.phase === 'playing' || state.phase === 'over') {
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold ' + Math.max(16, Math.floor(scaleX(28))) + 'px Impact, Fira Sans Condensed, sans-serif';
      ctx.fillText('SCORE: ' + state.score, scaleX(140), scaleY(40));
    }
  }

  function gameOver() {
    if (state.phase !== 'playing' || endRequested) return;
    endRequested = true;
    state.phase = 'over';

    const durationMs = Math.max(0, Math.round(performance.now() - runStartedAt));
    const payload = { score: state.score, durationMs: durationMs };
    let result = { score: state.score, personalBest: false, rank: null, totalPlayers: 0 };

    const finish = function (r) {
      state.result = r;
      showGameOverOverlay(r);
    };

    if (sessionId == null) {
      finish(result);
      return;
    }

    window
      .amsmFetch('/api/game/session/' + sessionId + '/end', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      .then(function (res) {
        if (res.ok) return res.json();
        return result;
      })
      .then(finish)
      .catch(function () {
        finish(result);
      });
  }

  function tick() {
    if (state.phase === 'playing') updatePlaying();
    draw();
  }

  function onKeyDown(e) {
    state.keys[e.keyCode] = true;
    if (state.phase === 'waiting' && (e.keyCode === 32 || e.keyCode === 38)) {
      e.preventDefault();
      beginRun();
    }
  }

  function onKeyUp(e) {
    state.keys[e.keyCode] = false;
  }

  function updateTouchFromPoint(clientX, clientY) {
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    const cx = rect.width / 2;
    const cy = rect.height / 2;
    state.touchDir.x = x < cx * 0.85 ? -1 : x > cx * 1.15 ? 1 : 0;
    state.touchDir.y = y < cy * 0.85 ? -1 : y > cy * 1.15 ? 1 : 0;
  }

  function onPointerDown(e) {
    if (state.phase === 'waiting') {
      beginRun();
      return;
    }
    if (state.phase !== 'playing') return;
    updateTouchFromPoint(e.clientX, e.clientY);
  }

  function onPointerMove(e) {
    if (state.phase !== 'playing') return;
    if (e.buttons === 0 && e.pointerType === 'mouse') return;
    updateTouchFromPoint(e.clientX, e.clientY);
  }

  function onPointerUp() {
    state.touchDir.x = 0;
    state.touchDir.y = 0;
  }

  function resizeCanvas() {
    if (!parentEl || !canvas) return;
    const size = measureParent(parentEl);
    const prevW = canvas.width || size.w;
    const prevH = canvas.height || size.h;
    canvas.width = size.w;
    canvas.height = size.h;
    if (player && prevW > 0 && prevH > 0) {
      player.x = (player.x / prevW) * size.w;
      player.y = (player.y / prevH) * size.h;
      player.w = scaleX(40);
      player.h = scaleY(70);
      clampPlayer();
    }
  }

  function bindInput() {
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    canvas.style.touchAction = 'none';
  }

  function bootGame() {
    parentEl = document.getElementById('game-canvas');
    if (!parentEl) {
      showError('Нема game-canvas елемент.');
      return;
    }

    if (playsRemainingInitial === 0) {
      showGate();
      return;
    }

    canvas = document.createElement('canvas');
    canvas.setAttribute('aria-label', 'Road Fighter');
    parentEl.innerHTML = '';
    parentEl.appendChild(canvas);
    ctx = canvas.getContext('2d');
    resizeCanvas();
    createOverlay();
    bindInput();

    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(function () {
        resizeCanvas();
      });
      resizeObserver.observe(parentEl);
    } else {
      window.addEventListener('resize', resizeCanvas);
    }

    loadAssets()
      .then(function () {
        prepareWaiting();
        loopId = setInterval(tick, 20);
        window.__amsmGame = { canvas: canvas, getPhase: function () { return state.phase; } };
      })
      .catch(function (err) {
        showError(err.message || 'Неуспешно вчитување на играта.');
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      requestAnimationFrame(bootGame);
    });
  } else {
    requestAnimationFrame(bootGame);
  }
})();
