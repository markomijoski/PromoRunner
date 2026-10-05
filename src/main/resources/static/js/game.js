(function () {
  'use strict';

  const ASSET_BASE = '/game/';
  // roadLeft/roadRight = fractions of canvas width (inner asphalt edges)
  const MAPS = [
    { src: ASSET_BASE + 'map1.jpg', atScore: 0, roadLeft: 0.18, roadRight: 0.82 },
    { src: ASSET_BASE + 'map2.png', atScore: 1000, roadLeft: 0.17, roadRight: 0.81 },
    { src: ASSET_BASE + 'map3.jpg', atScore: 2000, roadLeft: 0.2, roadRight: 0.78 },
    { src: ASSET_BASE + 'map4.jpg', atScore: 3000, roadLeft: 0.22, roadRight: 0.76 }
  ];
  const PLAYER_SRC = ASSET_BASE + 'TheCar_amsm.png';
  const COIN_SRC = ASSET_BASE + 'coin.png';
  const SHIELD_SRC = ASSET_BASE + 'shield.png';
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
  const SPAWN_INTERVAL_START = 90;
  const SPAWN_INTERVAL_MIN = 52;
  const FALL_SPEED_START = 3.5;
  const FALL_SPEED_MAX = 6;
  const SPAWN_ROAD_PAD = 8;
  const DIFFICULTY_RAMP_FRAMES = 3200;
  const COIN_POINTS = 50;
  const COIN_SIZE = 28;
  const SHIELD_PICKUP_SIZE = 36;
  const SHIELD_DURATION_FRAMES = 220;
  const SHIELD_FLASH_FRAMES = 20;
  const INVULN_FRAMES = 12;
  const PICKUP_SPAWN_START = 110;
  const PICKUP_SPAWN_MIN = 75;
  const PICKUP_SHIELD_CHANCE = 0.15;
  const POPUP_FRAMES = 36;

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
    spawnCooldown: 0,
    pickupCooldown: 0,
    coinsCollected: 0,
    shieldFramesLeft: 0,
    shieldFlash: 0,
    invulnFrames: 0,
    popups: [],
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
  let pickups = [];
  let images = {};
  let imageFailed = {};
  let playerSprite = null;
  let viewW = STAGE_DESIGN.w;
  let viewH = STAGE_DESIGN.h;
  let dpr = 1;
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

  function loadImageOptional(src) {
    return new Promise(function (resolve) {
      const img = new Image();
      img.onload = function () {
        images[src] = img;
        resolve(img);
      };
      img.onerror = function () {
        imageFailed[src] = true;
        resolve(null);
      };
      img.src = src;
    });
  }

  function loadAssets() {
    const required = MAPS.map(function (m) {
      return m.src;
    })
      .concat([PLAYER_SRC])
      .concat(
        OBSTACLE_DEFS.map(function (o) {
          return o.src;
        })
      );
    const uniqueRequired = Array.from(new Set(required));
    return Promise.all(
      uniqueRequired.map(function (src) {
        return loadImage(src).then(function (img) {
          images[src] = img;
        });
      })
    ).then(function () {
      return Promise.all([loadImageOptional(COIN_SRC), loadImageOptional(SHIELD_SRC)]);
    });
  }

  function scaleX(v) {
    return (v / STAGE_DESIGN.w) * viewW;
  }

  function scaleY(v) {
    return (v / STAGE_DESIGN.h) * viewH;
  }

  /** Uniform entity scale (width-based) — keeps sprites proportional on all aspects. */
  function scaleU(v) {
    return (v / STAGE_DESIGN.w) * viewW;
  }

  let audioCtx = null;
  function playTone(freq, durationMs, type, gainValue) {
    try {
      if (!audioCtx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        audioCtx = new AC();
      }
      if (audioCtx.state === 'suspended') audioCtx.resume();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = type || 'square';
      osc.frequency.value = freq;
      gain.gain.value = gainValue == null ? 0.045 : gainValue;
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      const now = audioCtx.currentTime;
      gain.gain.setValueAtTime(gain.gain.value, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + durationMs / 1000);
      osc.start(now);
      osc.stop(now + durationMs / 1000);
    } catch (e) {
      /* ignore audio failures */
    }
  }

  function sfxCoin() {
    playTone(880, 70, 'sine', 0.05);
  }
  function sfxShield() {
    playTone(520, 90, 'triangle', 0.05);
  }
  function sfxShieldBreak() {
    playTone(220, 120, 'sawtooth', 0.04);
  }
  function sfxCrash() {
    playTone(110, 180, 'sawtooth', 0.06);
  }

  function applyCanvasQuality() {
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.imageSmoothingEnabled = true;
    if ('imageSmoothingQuality' in ctx) ctx.imageSmoothingQuality = 'high';
  }

  function bakePlayerSprite() {
    const src = images[PLAYER_SRC];
    if (!src || !viewW) return;
    const drawW = scaleU(40);
    const drawH = scaleU(70);
    const targetW = Math.max(80, Math.round(drawW * dpr));
    const targetH = Math.max(140, Math.round(drawH * dpr));
    if (
      playerSprite &&
      playerSprite.width === targetW &&
      playerSprite.height === targetH
    ) {
      return;
    }
    const off = document.createElement('canvas');
    off.width = targetW;
    off.height = targetH;
    const offCtx = off.getContext('2d');
    offCtx.imageSmoothingEnabled = true;
    if ('imageSmoothingQuality' in offCtx) offCtx.imageSmoothingQuality = 'high';
    offCtx.drawImage(src, 0, 0, targetW, targetH);
    playerSprite = off;
  }

  function difficultyT() {
    return Math.min(1, state.frameNo / DIFFICULTY_RAMP_FRAMES);
  }

  function currentSpawnInterval() {
    const t = difficultyT();
    return Math.round(SPAWN_INTERVAL_START + (SPAWN_INTERVAL_MIN - SPAWN_INTERVAL_START) * t);
  }

  function currentPickupSpawnInterval() {
    const t = difficultyT();
    return Math.round(PICKUP_SPAWN_START + (PICKUP_SPAWN_MIN - PICKUP_SPAWN_START) * t);
  }

  function currentFallSpeed() {
    const t = difficultyT();
    return FALL_SPEED_START + (FALL_SPEED_MAX - FALL_SPEED_START) * t;
  }

  function currentMap() {
    let map = MAPS[0];
    for (let i = 0; i < MAPS.length; i++) {
      if (state.score >= MAPS[i].atScore) map = MAPS[i];
    }
    return map;
  }

  function currentMapSrc() {
    return currentMap().src;
  }

  function roadBounds(entityW) {
    const map = currentMap();
    const minX = viewW * map.roadLeft;
    const maxX = viewW * map.roadRight - entityW;
    return { minX: minX, maxX: Math.max(minX, maxX) };
  }

  function playerMinY() {
    return viewH * PLAYER_MIN_Y_FRAC;
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
          if (data.reason === 'CAMPAIGN_CLOSED') {
            window.location.href = '/';
            return false;
          }
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
    overlayEls.title.textContent = 'AMSM Runner';
    overlayEls.score.textContent = 'Притиснете Space / ↑ или допрете за старт';
    if (overlayEls.best) {
      overlayEls.best.style.display = 'none';
      overlayEls.best.textContent = '';
    }
    overlayEls.rank.textContent =
      'Монети = +50 · Штит = 1 спас (~4с) · Стрелки / WASD / допир';
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
    const coinLine =
      state.coinsCollected > 0 ? ' · Монети: ' + state.coinsCollected : '';
    overlayEls.rank.textContent =
      (result.rank ? 'Ранг #' + result.rank : '') + coinLine;
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
    pickups = [];
    state.frameNo = 0;
    state.score = 0;
    state.bonusScore = 0;
    state.spawnCooldown = currentSpawnInterval();
    state.pickupCooldown = currentPickupSpawnInterval();
    state.coinsCollected = 0;
    state.shieldFramesLeft = 0;
    state.shieldFlash = 0;
    state.invulnFrames = 0;
    state.popups = [];
    endRequested = false;
    player = {
      x: scaleX(200),
      y: Math.min(viewH - scaleU(70), Math.max(playerMinY(), scaleY(PLAYER_START_Y))),
      w: scaleU(40),
      h: scaleU(70),
      speedX: 0,
      speedY: 0
    };
    bakePlayerSprite();
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
    if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
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
    if (mybottom < othertop || mytop > otherbottom - scaleU(20) || myright < otherleft || myleft > otherright) {
      return false;
    }
    return true;
  }

  function spawnObstacle() {
    const def = OBSTACLE_DEFS[Math.floor(Math.random() * OBSTACLE_DEFS.length)];
    const w = scaleU(def.w);
    const h = scaleU(def.h);
    const bounds = roadBounds(w);
    const pad = scaleU(SPAWN_ROAD_PAD);
    const minX = bounds.minX + pad;
    const maxX = Math.max(minX + 1, bounds.maxX - pad);
    const x = minX + Math.random() * (maxX - minX);
    obstacles.push({
      src: def.src,
      x: x,
      y: -h,
      w: w,
      h: h
    });
  }

  function overlapRatio(a, b) {
    const left = Math.max(a.x, b.x);
    const right = Math.min(a.x + a.w, b.x + b.w);
    const top = Math.max(a.y, b.y);
    const bottom = Math.min(a.y + a.h, b.y + b.h);
    if (right <= left || bottom <= top) return 0;
    const inter = (right - left) * (bottom - top);
    const area = Math.max(1, a.w * a.h);
    return inter / area;
  }

  function aabbOverlap(a, b) {
    return !(
      a.y + a.h < b.y ||
      a.y > b.y + b.h ||
      a.x + a.w < b.x ||
      a.x > b.x + b.w
    );
  }

  function spawnPickup() {
    const isShield = Math.random() < PICKUP_SHIELD_CHANCE;
    const kind = isShield ? 'shield' : 'coin';
    const designSize = isShield ? SHIELD_PICKUP_SIZE : COIN_SIZE;
    const w = scaleU(designSize);
    const h = scaleU(designSize);
    const bounds = roadBounds(w);
    const pad = scaleU(SPAWN_ROAD_PAD);
    const minX = bounds.minX + pad;
    const maxX = Math.max(minX + 1, bounds.maxX - pad);
    const x = minX + Math.random() * (maxX - minX);
    const candidate = {
      kind: kind,
      src: isShield ? SHIELD_SRC : COIN_SRC,
      x: x,
      y: -h,
      w: w,
      h: h
    };
    if (player && overlapRatio(candidate, player) > 0.5) {
      return;
    }
    pickups.push(candidate);
  }

  function addPopup(text, x, y, color) {
    state.popups.push({
      text: text,
      x: x,
      y: y,
      color: color || '#FFD200',
      life: POPUP_FRAMES
    });
  }

  function collectPickup(p) {
    if (p.kind === 'coin') {
      state.bonusScore += COIN_POINTS;
      state.coinsCollected += 1;
      state.score = state.frameNo + state.bonusScore;
      addPopup('+' + COIN_POINTS, p.x + p.w / 2, p.y, '#FFD200');
      sfxCoin();
      return;
    }
    if (p.kind === 'shield') {
      state.shieldFramesLeft = SHIELD_DURATION_FRAMES;
      addPopup('ШТИТ', p.x + p.w / 2, p.y, '#4DA3FF');
      sfxShield();
    }
  }

  function absorbHit(obstacleIndex) {
    obstacles.splice(obstacleIndex, 1);
    state.shieldFramesLeft = 0;
    state.shieldFlash = SHIELD_FLASH_FRAMES;
    state.invulnFrames = INVULN_FRAMES;
    sfxShieldBreak();
  }

  function clampPlayer() {
    const minY = playerMinY();
    const bounds = roadBounds(player.w);
    player.x = Math.max(bounds.minX, Math.min(bounds.maxX, player.x));
    player.y = Math.max(minY, Math.min(viewH - player.h, player.y));
  }

  function updatePlaying() {
    for (let i = 0; i < obstacles.length; i++) {
      if (crashWith(player, obstacles[i])) {
        if (state.invulnFrames > 0) {
          continue;
        }
        if (state.shieldFramesLeft > 0) {
          absorbHit(i);
          break;
        }
        sfxCrash();
        gameOver();
        return;
      }
    }
    if (state.phase !== 'playing') return;

    state.frameNo += 1;
    state.score = state.frameNo + state.bonusScore;

    if (state.shieldFlash > 0) state.shieldFlash -= 1;
    if (state.shieldFramesLeft > 0) state.shieldFramesLeft -= 1;
    if (state.invulnFrames > 0) state.invulnFrames -= 1;

    for (let i = state.popups.length - 1; i >= 0; i--) {
      state.popups[i].life -= 1;
      state.popups[i].y -= scaleU(1.2);
      if (state.popups[i].life <= 0) state.popups.splice(i, 1);
    }

    state.spawnCooldown -= 1;
    if (state.spawnCooldown <= 0) {
      spawnObstacle();
      state.spawnCooldown = currentSpawnInterval();
    }

    state.pickupCooldown -= 1;
    if (state.pickupCooldown <= 0) {
      spawnPickup();
      state.pickupCooldown = currentPickupSpawnInterval();
    }

    const speed = scaleU(currentFallSpeed());
    for (let i = 0; i < obstacles.length; i++) {
      obstacles[i].y += speed;
    }
    obstacles = obstacles.filter(function (o) {
      return o.y < viewH + o.h;
    });

    for (let i = 0; i < pickups.length; i++) {
      pickups[i].y += speed;
    }
    const remaining = [];
    for (let i = 0; i < pickups.length; i++) {
      const p = pickups[i];
      if (p.y >= viewH + p.h) continue;
      if (aabbOverlap(player, p)) {
        collectPickup(p);
        continue;
      }
      remaining.push(p);
    }
    pickups = remaining;

    const move = scaleU(3);
    player.speedX = 0;
    player.speedY = 0;

    if (state.keys[37] || state.keys[65] || state.touchDir.x < 0) player.speedX = -move;
    if (state.keys[39] || state.keys[68] || state.touchDir.x > 0) player.speedX = move;
    if (state.keys[38] || state.keys[87] || state.touchDir.y < 0) player.speedY = -move;
    if (state.keys[40] || state.keys[83] || state.touchDir.y > 0) player.speedY = move;

    player.x += player.speedX;
    player.y += player.speedY;
    clampPlayer();
  }

  function drawRoadBackground() {
    const map = images[currentMapSrc()];
    if (!map) {
      ctx.fillStyle = '#2a2a2a';
      ctx.fillRect(0, 0, viewW, viewH);
      return;
    }
    const scroll = (state.frameNo * scaleU(8)) % viewH;
    ctx.drawImage(map, 0, scroll - viewH, viewW, viewH);
    ctx.drawImage(map, 0, scroll, viewW, viewH);
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

    const scoreText = String(state.score);
    const labelSize = Math.max(10, Math.floor(scaleU(12)));
    const scoreSize = Math.max(20, Math.floor(scaleU(30)));
    const metaSize = Math.max(11, Math.floor(scaleU(14)));
    const padX = scaleU(16);
    const padY = scaleU(10);
    const accentW = Math.max(3, scaleU(5));

    ctx.save();
    ctx.font = '700 ' + scoreSize + 'px "Fira Sans Condensed", Impact, sans-serif';
    const scoreW = ctx.measureText(scoreText).width;
    ctx.font = '600 ' + labelSize + 'px "Fira Sans Condensed", sans-serif';
    const labelW = ctx.measureText('РЕЗУЛТАТ').width;

    const coinText = '● ' + state.coinsCollected;
    ctx.font = '600 ' + metaSize + 'px "Fira Sans Condensed", sans-serif';
    const coinW = ctx.measureText(coinText).width;

    const contentW = Math.max(labelW, scoreW, coinW);
    const pillW = Math.min(viewW - scaleU(24), accentW + padX * 2 + contentW);
    const pillH = padY * 2 + labelSize + scoreSize + metaSize + scaleU(10);
    const pillX = (viewW - pillW) / 2;
    const pillY = scaleU(12);

    roundRect(pillX, pillY, pillW, pillH, scaleU(10));
    ctx.fillStyle = 'rgba(10,10,10,0.72)';
    ctx.fill();

    ctx.fillStyle = '#FFD200';
    ctx.fillRect(pillX, pillY + scaleU(6), accentW, pillH - scaleU(12));

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
    textY += labelSize + scaleU(2);

    ctx.fillStyle = '#FFD200';
    ctx.font = '700 ' + scoreSize + 'px "Fira Sans Condensed", Impact, sans-serif';
    ctx.fillText(scoreText, textX, textY);
    textY += scoreSize + scaleU(4);

    ctx.fillStyle = '#FFD200';
    ctx.font = '600 ' + metaSize + 'px "Fira Sans Condensed", sans-serif';
    ctx.fillText(coinText, textX, textY);

    ctx.shadowColor = 'transparent';
    ctx.shadowBlur = 0;
    ctx.shadowOffsetY = 0;

    if (state.phase === 'playing' && state.shieldFramesLeft > 0) {
      const barW = Math.min(scaleU(120), viewW * 0.4);
      const barH = Math.max(6, scaleU(8));
      const barX = (viewW - barW) / 2;
      const barY = pillY + pillH + scaleU(12);
      const frac = state.shieldFramesLeft / SHIELD_DURATION_FRAMES;
      const icon = scaleU(18);
      drawPickupSprite(SHIELD_SRC, 'shield', barX - icon - scaleU(6), barY - scaleU(4), icon, icon);
      roundRect(barX, barY, barW, barH, scaleU(4));
      ctx.fillStyle = 'rgba(255,255,255,0.25)';
      ctx.fill();
      roundRect(barX, barY, barW * frac, barH, scaleU(4));
      ctx.fillStyle = '#4DA3FF';
      ctx.fill();
    }

    for (let i = 0; i < state.popups.length; i++) {
      const p = state.popups[i];
      const alpha = Math.max(0, Math.min(1, p.life / 12));
      ctx.globalAlpha = alpha;
      ctx.fillStyle = p.color;
      ctx.font = '700 ' + Math.max(14, Math.floor(scaleU(22))) + 'px "Fira Sans Condensed", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(p.text, p.x, p.y);
      ctx.globalAlpha = 1;
    }

    ctx.restore();
  }

  function drawPickupSprite(src, kind, x, y, w, h) {
    const img = images[src];
    if (img) {
      ctx.drawImage(img, x, y, w, h);
      return;
    }
    if (kind === 'coin') {
      ctx.beginPath();
      ctx.arc(x + w / 2, y + h / 2, Math.min(w, h) / 2, 0, Math.PI * 2);
      ctx.fillStyle = '#FFD200';
      ctx.fill();
      ctx.strokeStyle = '#B8860B';
      ctx.lineWidth = Math.max(1, scaleU(2));
      ctx.stroke();
      return;
    }
    // shield fallback: hexagon
    const cx = x + w / 2;
    const cy = y + h / 2;
    const r = Math.min(w, h) / 2;
    ctx.beginPath();
    for (let i = 0; i < 6; i++) {
      const ang = (Math.PI / 3) * i - Math.PI / 6;
      const px = cx + Math.cos(ang) * r;
      const py = cy + Math.sin(ang) * r;
      if (i === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fillStyle = '#4DA3FF';
    ctx.fill();
    ctx.strokeStyle = '#1E5AA8';
    ctx.lineWidth = Math.max(1, scaleU(2));
    ctx.stroke();
  }

  function drawPlayerShieldAura() {
    if (!player) return;
    if (state.shieldFramesLeft <= 0 && state.invulnFrames <= 0 && state.shieldFlash <= 0) return;
    const cx = player.x + player.w / 2;
    const cy = player.y + player.h / 2;
    const pulse = 0.85 + 0.15 * Math.sin(state.frameNo / 6);
    const radius = (Math.max(player.w, player.h) / 2 + scaleU(10)) * pulse;
    ctx.save();
    if (state.shieldFramesLeft > 0) {
      ctx.strokeStyle = 'rgba(77,163,255,' + (0.55 + 0.25 * pulse) + ')';
      ctx.lineWidth = Math.max(2, scaleU(3));
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.stroke();
      ctx.strokeStyle = 'rgba(255,255,255,0.35)';
      ctx.lineWidth = Math.max(1, scaleU(1.5));
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 0.78, 0, Math.PI * 2);
      ctx.stroke();
    }
    if (state.shieldFlash > 0 || state.invulnFrames > 0) {
      const a = state.shieldFlash > 0
        ? Math.min(1, state.shieldFlash / 10)
        : 0.25 + 0.25 * Math.sin(state.frameNo);
      ctx.fillStyle = 'rgba(255,255,255,' + a * 0.45 + ')';
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 0.95, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  function draw() {
    if (!ctx) return;
    drawRoadBackground();

    if (player) {
      const sprite = playerSprite || images[PLAYER_SRC];
      if (sprite) ctx.drawImage(sprite, player.x, player.y, player.w, player.h);
      drawPlayerShieldAura();
    }

    for (let i = 0; i < obstacles.length; i++) {
      const o = obstacles[i];
      const img = images[o.src];
      if (img) ctx.drawImage(img, o.x, o.y, o.w, o.h);
    }

    for (let i = 0; i < pickups.length; i++) {
      const p = pickups[i];
      drawPickupSprite(p.src, p.kind, p.x, p.y, p.w, p.h);
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
    if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
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
    if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
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
    if (!parentEl || !canvas || !ctx) return;
    const size = measureParent(parentEl);
    const prevW = viewW || size.w;
    const prevH = viewH || size.h;
    const prevU = prevW / STAGE_DESIGN.w;
    viewW = size.w;
    viewH = size.h;
    const nextU = viewW / STAGE_DESIGN.w;
    dpr = Math.min(window.devicePixelRatio || 1, 3);
    canvas.width = Math.max(1, Math.floor(viewW * dpr));
    canvas.height = Math.max(1, Math.floor(viewH * dpr));
    applyCanvasQuality();
    if (prevW > 0 && prevH > 0) {
      const sx = viewW / prevW;
      const sy = viewH / prevH;
      const sizeRatio = prevU > 0 ? nextU / prevU : 1;
      if (player) {
        player.x *= sx;
        player.y *= sy;
        player.w = scaleU(40);
        player.h = scaleU(70);
        clampPlayer();
      }
      for (let i = 0; i < obstacles.length; i++) {
        const o = obstacles[i];
        o.x *= sx;
        o.y *= sy;
        o.w *= sizeRatio;
        o.h *= sizeRatio;
      }
      for (let i = 0; i < pickups.length; i++) {
        const p = pickups[i];
        p.x *= sx;
        p.y *= sy;
        p.w *= sizeRatio;
        p.h *= sizeRatio;
      }
    }
    bakePlayerSprite();
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
    canvas.setAttribute('aria-label', 'AMSM Runner');
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
        bakePlayerSprite();
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
