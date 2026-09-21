(function () {
  'use strict';

  const ASSET_BASE = '/game/';
  const MAP1_SRC = ASSET_BASE + 'map1.png';
  const MAP2_SRC = ASSET_BASE + 'map2.png';
  const MAP3_SRC = ASSET_BASE + 'map3.jpg';
  const MAP4_SRC = ASSET_BASE + 'map4.jpg';
  const PLAYER_SRC = ASSET_BASE + 'TheCar_amsm.png';
  const OBSTACLE_DEFS = [
    { src: ASSET_BASE + 'car1.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car2.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car3.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car4.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'car5.png', w: 40, h: 70 },
    { src: ASSET_BASE + 'barrel.png', w: 50, h: 50 },
    { src: ASSET_BASE + 'roadblock.png', w: 60, h: 50 }
  ];

  const STAGE_DESIGN = { w: 420, h: 500 };
  const PLAYER_START_Y = 430;
  const PLAYER_MIN_Y_FRAC = 0.55;
  const SPAWN_INTERVAL_START = 80;
  const SPAWN_INTERVAL_MIN = 45;
  const FALL_SPEED_START = 4;
  const FALL_SPEED_MAX = 7;
  const MAP2_AT_SCORE = 1000;
  const MAP3_AT_SCORE = 2000;
  const MAP4_AT_SCORE = 3000;
  const DIFFICULTY_RAMP_FRAMES = 2000;
  const NEAR_MISS_PAD = 28;
  const NEAR_MISS_BONUS = 25;
  const MULTIPLIER_STEP = 0.1;
  const MULTIPLIER_MAX = 2;
  const FOCUS_FLASH_FRAMES = 45;

  const dto = window.GAME_CONFIG || {};
  const playsRemainingInitial = typeof dto.playsRemaining === 'number' ? dto.playsRemaining : -1;

  let sessionId = null;
  let playsRemaining = playsRemainingInitial;
  let runStartedAt = 0;

  const state = {
    phase: 'booting', // booting | waiting | playing | over
    frameNo: 0,
    score: 0,
    bonusScore: 0,
    multiplier: 1,
    streak: 0,
    spawnCooldown: 0,
    focusFlash: 0,
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
    const w = Math.max(280, Math.floor(rect.width) || el.clientWidth || STAGE_DESIGN.w);
    const h = Math.max(400, Math.floor(rect.height) || el.clientHeight || STAGE_DESIGN.h);
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
    const urls = [MAP1_SRC, MAP2_SRC, MAP3_SRC, MAP4_SRC, PLAYER_SRC].concat(
      OBSTACLE_DEFS.map(function (o) {
        return o.src;
      })
    );
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
    return (v / STAGE_DESIGN.w) * canvas.width;
  }

  function scaleY(v) {
    return (v / STAGE_DESIGN.h) * canvas.height;
  }

  function difficultyT() {
    return Math.min(1, state.frameNo / DIFFICULTY_RAMP_FRAMES);
  }

  function currentSpawnInterval() {
    const t = difficultyT();
    return Math.round(SPAWN_INTERVAL_START + (SPAWN_INTERVAL_MIN - SPAWN_INTERVAL_START) * t);
  }

  function currentFallSpeed() {
    const t = difficultyT();
    return FALL_SPEED_START + (FALL_SPEED_MAX - FALL_SPEED_START) * t;
  }

  function currentMapSrc() {
    if (state.score >= MAP4_AT_SCORE) return MAP4_SRC;
    if (state.score >= MAP3_AT_SCORE) return MAP3_SRC;
    if (state.score >= MAP2_AT_SCORE) return MAP2_SRC;
    return MAP1_SRC;
  }

  function playerMinY() {
    return canvas.height * PLAYER_MIN_Y_FRAC;
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
      '<p id="rf-overlay-best" style="margin-top:0.35rem;font-size:1.05rem;font-family:Fira Sans Condensed,sans-serif;' +
      'font-weight:700;color:#FFD200;display:none"></p>' +
      '<p id="rf-overlay-rank" style="margin-top:0.35rem;font-size:0.95rem;color:rgba(255,255,255,0.75)"></p>' +
      '<button id="rf-overlay-btn" type="button" style="margin-top:1.25rem;border:0;border-radius:0.5rem;' +
      'padding:0.75rem 1.5rem;font-family:Fira Sans Condensed,sans-serif;font-weight:700;text-transform:uppercase;' +
      'background:#FFD200;color:#0A0A0A;cursor:pointer"></button>';
    overlayEls = {
      root: root,
      title: document.getElementById('rf-overlay-title'),
      score: document.getElementById('rf-overlay-score'),
      best: document.getElementById('rf-overlay-best'),
      rank: document.getElementById('rf-overlay-rank'),
      btn: document.getElementById('rf-overlay-btn')
    };
    overlayEls.btn.addEventListener('click', onOverlayAction);
  }

  function showWaitingOverlay() {
    if (!overlayEls.root) return;
    overlayEls.title.textContent = 'Road Fighter';
    overlayEls.score.textContent = 'Притиснете Space / ↑ или допрете за старт';
    if (overlayEls.best) {
      overlayEls.best.style.display = 'none';
      overlayEls.best.textContent = '';
    }
    overlayEls.rank.textContent = 'Стрелки · ← → ↑ ↓ за возење';
    overlayEls.btn.textContent = 'СТАРТ';
    overlayEls.root.style.display = 'flex';
  }

  function showGameOverOverlay(result) {
    if (!overlayEls.root) return;
    overlayEls.title.textContent = 'КРАЈ';
    overlayEls.score.textContent = 'Резултат: ' + (result.score || 0);
    if (overlayEls.best) {
      if (result.personalBest) {
        overlayEls.best.textContent = 'НОВ РЕКОРД';
        overlayEls.best.style.display = 'block';
      } else {
        overlayEls.best.textContent = '';
        overlayEls.best.style.display = 'none';
      }
    }
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
    state.bonusScore = 0;
    state.multiplier = 1;
    state.streak = 0;
    state.spawnCooldown = currentSpawnInterval();
    state.focusFlash = 0;
    endRequested = false;
    player = {
      x: scaleX(200),
      y: scaleY(PLAYER_START_Y),
      w: scaleX(40),
      h: scaleY(70),
      speedX: 0,
      speedY: 0
    };
    clampPlayer();
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

  function nearMissProbe(a, b, pad) {
    const myleft = a.x - pad;
    const myright = a.x + a.w + pad;
    const mytop = a.y - pad;
    const mybottom = a.y + a.h + pad;
    const otherleft = b.x;
    const otherright = b.x + b.w;
    const othertop = b.y;
    const otherbottom = b.y + b.h;
    return !(mybottom < othertop || mytop > otherbottom || myright < otherleft || myleft > otherright);
  }

  function registerNearMiss() {
    state.streak += 1;
    state.multiplier = Math.min(MULTIPLIER_MAX, 1 + state.streak * MULTIPLIER_STEP);
    const bonus = Math.round(NEAR_MISS_BONUS * state.multiplier);
    state.bonusScore += bonus;
    state.focusFlash = FOCUS_FLASH_FRAMES;
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
      h: scaleY(def.h),
      wasClose: false,
      scored: false
    });
  }

  function clampPlayer() {
    const minY = playerMinY();
    player.x = Math.max(0, Math.min(canvas.width - player.w, player.x));
    player.y = Math.max(minY, Math.min(canvas.height - player.h, player.y));
  }

  function updatePlaying() {
    for (let i = 0; i < obstacles.length; i++) {
      if (crashWith(player, obstacles[i])) {
        state.streak = 0;
        state.multiplier = 1;
        gameOver();
        return;
      }
    }

    state.frameNo += 1;
    state.score = state.frameNo + state.bonusScore;

    if (state.focusFlash > 0) state.focusFlash -= 1;

    state.spawnCooldown -= 1;
    if (state.spawnCooldown <= 0) {
      spawnObstacle();
      state.spawnCooldown = currentSpawnInterval();
    }

    const speed = scaleY(currentFallSpeed());
    const pad = scaleX(NEAR_MISS_PAD);
    for (let i = 0; i < obstacles.length; i++) {
      const o = obstacles[i];
      o.y += speed;
      if (!o.scored && nearMissProbe(player, o, pad)) {
        o.wasClose = true;
      }
      if (!o.scored && o.wasClose && o.y > player.y + player.h) {
        o.scored = true;
        registerNearMiss();
        state.score = state.frameNo + state.bonusScore;
      }
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
    const map = images[currentMapSrc()];
    if (!map) {
      ctx.fillStyle = '#2a2a2a';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      return;
    }
    const scroll = (state.frameNo * scaleY(8)) % canvas.height;
    ctx.drawImage(map, 0, scroll - canvas.height, canvas.width, canvas.height);
    ctx.drawImage(map, 0, scroll, canvas.width, canvas.height);
  }

  function roundRect(x, y, w, h, r) {
    const radius = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + w, y, x + w, y + h, radius);
    ctx.arcTo(x + w, y + h, x, y + h, radius);
    ctx.arcTo(x, y + h, x, y, radius);
    ctx.arcTo(x, y, x + w, y, radius);
    ctx.closePath();
  }

  function drawHud() {
    if (state.phase !== 'playing' && state.phase !== 'over') return;

    const showStreak = state.phase === 'playing' && (state.streak > 0 || state.multiplier > 1);
    const scoreText = String(state.score);
    const labelSize = Math.max(10, Math.floor(scaleX(12)));
    const scoreSize = Math.max(22, Math.floor(scaleX(34)));
    const streakSize = Math.max(11, Math.floor(scaleX(15)));
    const padX = scaleX(18);
    const padY = scaleY(10);
    const accentW = Math.max(3, scaleX(5));

    ctx.save();
    ctx.font = '700 ' + scoreSize + 'px "Fira Sans Condensed", Impact, sans-serif';
    const scoreW = ctx.measureText(scoreText).width;
    ctx.font = '600 ' + labelSize + 'px "Fira Sans Condensed", sans-serif';
    const labelW = ctx.measureText('РЕЗУЛТАТ').width;

    let streakW = 0;
    let streakText = '';
    if (showStreak) {
      streakText = '×' + state.multiplier.toFixed(1) + '  ·  ' + state.streak + ' фокус';
      ctx.font = '600 ' + streakSize + 'px "Fira Sans Condensed", sans-serif';
      streakW = ctx.measureText(streakText).width;
    }

    const contentW = Math.max(labelW, scoreW, streakW);
    const pillW = accentW + padX * 2 + contentW;
    const pillH = padY * 2 + labelSize + scoreSize + (showStreak ? streakSize + scaleY(6) : 0) + scaleY(4);
    const pillX = (canvas.width - pillW) / 2;
    const pillY = scaleY(16);

    roundRect(pillX, pillY, pillW, pillH, scaleX(10));
    ctx.fillStyle = 'rgba(10,10,10,0.72)';
    ctx.fill();

    ctx.fillStyle = '#FFD200';
    ctx.fillRect(pillX, pillY + scaleY(6), accentW, pillH - scaleY(12));

    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.shadowColor = 'rgba(0,0,0,0.45)';
    ctx.shadowBlur = 4;
    ctx.shadowOffsetY = 1;

    const textX = pillX + accentW + padX;
    let textY = pillY + padY;

    ctx.fillStyle = 'rgba(255,255,255,0.75)';
    ctx.font = '600 ' + labelSize + 'px "Fira Sans Condensed", sans-serif';
    ctx.fillText('РЕЗУЛТАТ', textX, textY);
    textY += labelSize + scaleY(2);

    ctx.fillStyle = '#FFD200';
    ctx.font = '700 ' + scoreSize + 'px "Fira Sans Condensed", Impact, sans-serif';
    ctx.fillText(scoreText, textX, textY);
    textY += scoreSize + scaleY(4);

    if (showStreak) {
      ctx.fillStyle = 'rgba(255,255,255,0.9)';
      ctx.font = '600 ' + streakSize + 'px "Fira Sans Condensed", sans-serif';
      ctx.fillText(streakText, textX, textY);
    }

    ctx.shadowColor = 'transparent';
    ctx.shadowBlur = 0;
    ctx.shadowOffsetY = 0;

    if (state.focusFlash > 0) {
      const alpha = Math.min(1, state.focusFlash / 15);
      ctx.fillStyle = 'rgba(255,210,0,' + alpha + ')';
      ctx.font = '700 ' + Math.max(18, Math.floor(scaleX(28))) + 'px "Fira Sans Condensed", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('ФОКУС!', canvas.width / 2, pillY + pillH + scaleY(28));
    }

    ctx.restore();
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

    drawHud();
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
    const code = e.keyCode;
    const isGameKey =
      code === 32 ||
      code === 37 ||
      code === 38 ||
      code === 39 ||
      code === 40 ||
      code === 65 ||
      code === 68 ||
      code === 83 ||
      code === 87;
    if (isGameKey && state.phase !== 'booting') {
      e.preventDefault();
    }
    state.keys[code] = true;
    if (state.phase === 'waiting' && (code === 32 || code === 38)) {
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
