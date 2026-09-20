(function () {
  'use strict';

  const dto = window.GAME_CONFIG || {};
  const configData = dto.config || dto;
  const playsRemainingInitial = typeof dto.playsRemaining === 'number' ? dto.playsRemaining : -1;

  let sessionId = null;
  let playsRemaining = playsRemainingInitial;

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
    const w = Math.max(280, Math.floor(rect.width) || el.clientWidth || 640);
    const h = Math.max(360, Math.floor(rect.height) || el.clientHeight || 420);
    return { w: w, h: h };
  }

  if (playsRemainingInitial === 0) {
    showGate();
  }

  function bakeTextures(scene) {
    const g = scene.make.graphics({ x: 0, y: 0, add: false });

    g.clear();
    for (let f = 0; f < 4; f++) {
      const ox = f * 64;
      const leg = f % 2 === 0 ? 5 : -5;
      g.fillStyle(0xffd200, 1);
      g.fillCircle(ox + 32, 16, 13);
      g.fillStyle(0x0a0a0a, 1);
      g.fillRect(ox + 25, 28, 14, 18);
      g.fillStyle(0xffd200, 1);
      g.fillRect(ox + 28, 30, 5, 12);
      g.fillStyle(0x0a0a0a, 1);
      g.fillRect(ox + 22, 46, 9, 14 + leg);
      g.fillRect(ox + 35, 46, 9, 14 - leg);
      g.fillRect(ox + 14, 32, 11, 6);
      g.fillRect(ox + 40, 32, 11, 6);
    }
    g.generateTexture('run_sheet', 256, 64);

    g.clear();
    g.fillStyle(0xffd200, 1);
    g.fillCircle(32, 14, 13);
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(25, 24, 14, 16);
    g.fillStyle(0xffd200, 1);
    g.fillRect(28, 26, 5, 10);
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(18, 38, 10, 20);
    g.fillRect(36, 38, 10, 20);
    g.fillRect(10, 26, 14, 6);
    g.fillRect(40, 26, 14, 6);
    g.generateTexture('jump', 64, 64);

    g.clear();
    g.fillStyle(0xffd200, 1);
    g.fillCircle(32, 22, 13);
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(25, 32, 14, 14);
    g.fillStyle(0xffd200, 1);
    g.fillRect(28, 34, 5, 8);
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(16, 44, 12, 14);
    g.fillRect(36, 44, 12, 14);
    g.fillRect(8, 30, 16, 6);
    g.fillRect(40, 30, 16, 6);
    g.generateTexture('fall', 64, 64);

    g.clear();
    g.fillStyle(0xffd200, 1);
    g.fillCircle(14, 36, 11);
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(22, 30, 34, 16);
    g.fillStyle(0xffd200, 1);
    g.fillRect(28, 34, 20, 5);
    g.generateTexture('slide', 64, 64);

    g.clear();
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(0, 0, 64, 32);
    g.fillStyle(0xffd200, 1);
    g.fillRect(4, 4, 56, 8);
    g.generateTexture('obs_low', 64, 32);

    g.clear();
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(0, 0, 64, 32);
    g.fillStyle(0xffd200, 1);
    for (let i = 0; i < 5; i++) g.fillRect(6 + i * 11, 4, 5, 24);
    g.generateTexture('obs_high', 64, 32);

    g.clear();
    g.fillStyle(0x0a0a0a, 1);
    g.fillRect(0, 0, 32, 96);
    g.fillStyle(0xffd200, 1);
    g.fillRect(10, 0, 12, 96);
    g.generateTexture('obs_vert', 32, 96);

    g.destroy();

    const src = scene.textures.get('run_sheet').getSourceImage();
    if (scene.textures.exists('run')) {
      scene.textures.remove('run');
    }
    scene.textures.addSpriteSheet('run', src, { frameWidth: 64, frameHeight: 64 });
  }

  const OBSTACLE_DEFS = [
    { key: 'obs_low', type: 'H', positionType: 'LOW', minUnits: 1, maxUnits: 3, spawnWeight: 10, minScore: 0 },
    { key: 'obs_high', type: 'H', positionType: 'HIGH', minUnits: 1, maxUnits: 2, spawnWeight: 8, minScore: 150 },
    { key: 'obs_vert', type: 'V', positionType: null, minUnits: 1, maxUnits: 3, spawnWeight: 10, minScore: 0 }
  ];

  class BootScene extends Phaser.Scene {
    constructor() {
      super('Boot');
    }

    create() {
      bakeTextures(this);
      // Anim optional — gameplay uses rectangle runner
      this.scene.start('Game');
    }
  }

  class GameScene extends Phaser.Scene {
    constructor() {
      super('Game');
    }

    init() {
      this.score = 0;
      this.alive = false;
      this.sliding = false;
      this.unit = 32;
      this.scrollSpeed = (configData && configData.baseScrollSpeed) || 280;
      this.obstacles = null;
      this.lastTypes = [];
      this.spawnTimer = 0;
      this.scoreTimer = 0;
      this.waiting = true;
      this.runStartedAt = 0;
      this.cursors = null;
      this.space = null;
      this.ready = false;
    }

    create() {
      hideGate();
      const tip = this.add
        .text(this.scale.width / 2, this.scale.height / 2, 'Се поврзува…', {
          fontFamily: 'Source Sans 3, sans-serif',
          fontSize: '20px',
          color: '#5C5C5C'
        })
        .setOrigin(0.5);

      this.startSession()
        .then((ok) => {
          if (!this.scene.isActive('Game')) return;
          tip.destroy();
          if (!ok) {
            if (playsRemaining === 0) {
              showGate();
            } else {
              showError('Сесијата не можеше да се стартува.');
            }
            return;
          }
          try {
            this.buildWorld();
          } catch (e) {
            console.error(e);
            showError((e && e.message) || 'Грешка при цртање на играта.');
          }
        })
        .catch((err) => {
          if (!this.scene.isActive('Game')) return;
          tip.destroy();
          showError(err && err.message ? err.message : 'Грешка при стартување.');
        });
    }

    buildWorld() {
      this.unit = Math.max(28, this.scale.height / 12);
      this.physics.world.gravity.y = this.unit * 40;
      this.physics.world.setBounds(0, 0, this.scale.width, this.scale.height);

      this.add.rectangle(0, 0, this.scale.width, this.scale.height, 0xe8e8e8).setOrigin(0);

      for (let i = 0; i < 16; i++) {
        this.add
          .rectangle(i * (this.scale.width / 8), this.scale.height * 0.5, 32, 5, 0xffd200, 0.45)
          .setOrigin(0, 0.5);
      }

      const groundY = this.scale.height - this.unit;
      this.ground = this.add.rectangle(0, groundY, this.scale.width, this.unit, 0x0a0a0a).setOrigin(0);
      this.physics.add.existing(this.ground, true);

      const charW = this.unit * 1.2;
      const charH = this.unit * 2.2;
      // Solid yellow runner — no sprite-sheet/anim dependency (avoids Phaser duration crash)
      this.player = this.add.rectangle(
        this.unit * 2.6,
        groundY - charH / 2,
        charW,
        charH,
        0xffd200
      );
      this.physics.add.existing(this.player);
      this.player.body.setCollideWorldBounds(true);
      this.player.body.setSize(charW * 0.85, charH * 0.9);
      this.player.setDepth(5);
      // Accent stripe
      this.playerStripe = this.add.rectangle(
        this.player.x,
        this.player.y,
        charW * 0.25,
        charH * 0.7,
        0x0a0a0a
      ).setDepth(6);

      this.physics.add.collider(this.player, this.ground);

      this.obstacles = this.physics.add.group();
      this.physics.add.overlap(this.player, this.obstacles, () => this.gameOver(), null, this);

      this.scoreText = this.add
        .text(this.scale.width - 18, 14, '0', {
          fontFamily: 'Fira Sans Condensed, sans-serif',
          fontSize: Math.max(28, Math.round(this.unit * 1.1)),
          color: '#0A0A0A',
          fontStyle: 'bold'
        })
        .setOrigin(1, 0)
        .setDepth(20);

      this.hint = this.add
        .text(this.scale.width / 2, this.scale.height * 0.32, 'Допрете или Space за старт', {
          fontFamily: 'Source Sans 3, sans-serif',
          fontSize: Math.max(18, Math.round(this.unit * 0.55)),
          color: '#0A0A0A',
          backgroundColor: '#FFD200',
          padding: { x: 16, y: 10 }
        })
        .setOrigin(0.5)
        .setDepth(20);

      if (this.input.keyboard) {
        this.cursors = this.input.keyboard.createCursorKeys();
        this.space = this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.SPACE);
      }

      this.input.on('pointerdown', (p) => {
        if (!this.ready) return;
        if (!this.alive) {
          this.beginRun();
          return;
        }
        if (p.y < this.scale.height / 2) this.tryJump();
        else this.startSlide();
      });
      this.input.on('pointerup', () => this.endSlide());

      this.ready = true;
      this.waiting = true;
    }

    fitBody(sprite, wFrac, hFrac) {
      if (!sprite || !sprite.body || !sprite.frame) return;
      const fw = sprite.frame.width;
      const fh = sprite.frame.height;
      const bw = fw * wFrac;
      const bh = fh * hFrac;
      sprite.body.setSize(bw, bh);
      sprite.body.setOffset((fw - bw) / 2, fh - bh);
    }

    startSession() {
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

    beginRun() {
      if (!this.waiting || !sessionId || !this.ready) return;
      this.waiting = false;
      this.alive = true;
      this.runStartedAt = this.time.now;
      if (this.hint) this.hint.setVisible(false);
      this.spawnTimer = 400;
    }

    onGround() {
      return (
        this.player &&
        this.player.body &&
        (this.player.body.blocked.down || this.player.body.touching.down)
      );
    }

    tryJump() {
      if (!this.alive || this.sliding || !this.onGround()) return;
      this.player.body.setVelocityY(-this.unit * 12.5);
    }

    startSlide() {
      if (!this.alive || this.sliding || !this.onGround()) return;
      this.sliding = true;
      const u = this.unit;
      this.player.setSize(u * 1.8, u);
      this.player.body.setSize(u * 1.6, u * 0.85);
      this.player.y = this.scale.height - u - u / 2;
      if (this.playerStripe) {
        this.playerStripe.setSize(u * 1.8 * 0.25, u * 0.6);
        this.playerStripe.setPosition(this.player.x, this.player.y);
      }
    }

    endSlide() {
      if (!this.sliding) return;
      this.sliding = false;
      const u = this.unit;
      this.player.setSize(u * 1.2, u * 2.2);
      this.player.body.setSize(u * 1.2 * 0.85, u * 2.2 * 0.9);
      if (this.playerStripe) {
        this.playerStripe.setSize(u * 1.2 * 0.25, u * 2.2 * 0.7);
        this.playerStripe.setPosition(this.player.x, this.player.y);
      }
    }

    keyJust(key) {
      return key && Phaser.Input.Keyboard.JustDown(key);
    }

    update(_t, dt) {
      if (!this.ready || !this.player) return;

      if (this.waiting) {
        if (this.keyJust(this.space) || (this.cursors && this.keyJust(this.cursors.up))) {
          this.beginRun();
        }
        return;
      }
      if (!this.alive) return;

      if (this.keyJust(this.space) || (this.cursors && this.keyJust(this.cursors.up))) {
        this.tryJump();
      }
      if (this.cursors && this.cursors.down.isDown) this.startSlide();
      else if (this.sliding && !this.input.activePointer.isDown) this.endSlide();

      if (this.playerStripe) {
        this.playerStripe.setPosition(this.player.x, this.player.y);
      }

      const band = this.score < 400 ? 1 : this.score < 900 ? 1.25 : this.score < 1800 ? 1.55 : 2;
      this.scrollSpeed = ((configData && configData.baseScrollSpeed) || 280) * band;

      this.scoreTimer += dt;
      if (this.scoreTimer > 90) {
        this.score += Math.max(1, Math.round(band));
        this.scoreTimer = 0;
        this.scoreText.setText(String(this.score));
      }

      this.spawnTimer += dt;
      if (this.spawnTimer > Math.max(600, 1400 - this.score * 0.4)) {
        this.spawnTimer = 0;
        this.spawnObstacle(this.score < 500 ? 5 : this.score < 1000 ? 4.2 : 3.5);
      }

      this.obstacles.children.iterate((obs) => {
        if (!obs) return;
        obs.x -= (this.scrollSpeed * dt) / 1000;
        if (obs.x < -140) obs.destroy();
      });
    }

    pick(list) {
      const total = list.reduce((s, o) => s + o.spawnWeight, 0);
      let r = Math.random() * total;
      for (let i = 0; i < list.length; i++) {
        r -= list[i].spawnWeight;
        if (r <= 0) return list[i];
      }
      return list[0];
    }

    spawnObstacle(minGapUnits) {
      let list = OBSTACLE_DEFS.filter((o) => o.minScore <= this.score);
      const last = this.lastTypes[this.lastTypes.length - 1];
      if (last === 'V') list = list.filter((o) => o.positionType !== 'HIGH');
      let lows = 0;
      for (let i = this.lastTypes.length - 1; i >= 0; i--) {
        if (this.lastTypes[i] === 'LOW') lows++;
        else break;
      }
      if (lows >= 2) list = list.filter((o) => o.positionType !== 'LOW');
      if (!list.length) list = OBSTACLE_DEFS.slice();

      const asset = this.pick(list);
      const units = Phaser.Math.Between(asset.minUnits, asset.maxUnits);
      const u = this.unit;
      const groundY = this.scale.height - u;
      let w;
      let h;
      let y;
      let tag;

      if (asset.type === 'V') {
        w = u * 0.95;
        h = u * units;
        y = groundY - h / 2;
        tag = 'V';
      } else if (asset.positionType === 'HIGH') {
        w = u * units;
        h = u * 0.9;
        y = groundY - u * 2.15 - h / 2;
        tag = 'HIGH';
      } else {
        w = u * units;
        h = u * 0.9;
        y = groundY - h / 2;
        tag = 'LOW';
      }
      this.lastTypes.push(tag);
      if (this.lastTypes.length > 4) this.lastTypes.shift();

      const obs = this.obstacles.create(this.scale.width + minGapUnits * u, y, asset.key);
      obs.setDisplaySize(w, h);
      this.fitBody(obs, 0.85, 0.85);
      obs.body.setAllowGravity(false);
      obs.body.setImmovable(true);
    }

    async gameOver() {
      if (!this.alive) return;
      this.alive = false;
      this.player.setFillStyle(0xffaa00);
      this.physics.pause();

      const durationMs = Math.max(0, Math.round(this.time.now - (this.runStartedAt || 0)));
      let result = { score: this.score, personalBest: false, rank: null, totalPlayers: 0 };
      if (sessionId != null) {
        try {
          const res = await window.amsmFetch('/api/game/session/' + sessionId + '/end', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ score: this.score, durationMs: durationMs })
          });
          if (res.ok) result = await res.json();
        } catch (e) {}
      }
      this.scene.start('GameOver', result);
    }
  }

  class GameOverScene extends Phaser.Scene {
    constructor() {
      super('GameOver');
    }

    create(data) {
      this.add.rectangle(0, 0, this.scale.width, this.scale.height, 0xffffff, 0.96).setOrigin(0);
      this.add
        .text(this.scale.width / 2, this.scale.height * 0.22, 'КРАЈ', {
          fontFamily: 'Fira Sans Condensed, sans-serif',
          fontSize: '48px',
          color: '#0A0A0A',
          fontStyle: 'bold'
        })
        .setOrigin(0.5);
      this.add
        .text(this.scale.width / 2, this.scale.height * 0.4, 'Резултат: ' + (data.score || 0), {
          fontFamily: 'Fira Sans Condensed, sans-serif',
          fontSize: '28px',
          color: '#0A0A0A'
        })
        .setOrigin(0.5);
      if (data.rank) {
        this.add
          .text(this.scale.width / 2, this.scale.height * 0.5, 'Ранг #' + data.rank, {
            fontFamily: 'Source Sans 3, sans-serif',
            fontSize: '18px',
            color: '#5C5C5C'
          })
          .setOrigin(0.5);
      }

      const again = this.add
        .text(this.scale.width / 2, this.scale.height * 0.68, 'ИГРАЈ ПОВТОРНО', {
          fontFamily: 'Fira Sans Condensed, sans-serif',
          fontSize: '22px',
          color: '#0A0A0A',
          backgroundColor: '#FFD200',
          padding: { x: 22, y: 12 }
        })
        .setOrigin(0.5)
        .setInteractive({ useHandCursor: true });

      again.on('pointerup', () => {
        if (playsRemaining === 0) {
          showGate();
          const root = document.querySelector('[x-data]');
          if (root && window.Alpine) Alpine.$data(root).openConsent();
          return;
        }
        this.scene.start('Game');
      });
    }
  }

  function bootGame() {
    const parent = document.getElementById('game-canvas');
    if (!parent) {
      showError('Нема game-canvas елемент.');
      return;
    }
    if (typeof Phaser === 'undefined') {
      showError('Phaser не се вчита. Проверете ја мрежата.');
      return;
    }

    const size = measureParent(parent);
    const game = new Phaser.Game({
      type: Phaser.AUTO,
      parent: 'game-canvas',
      width: size.w,
      height: size.h,
      backgroundColor: '#e8e8e8',
      physics: { default: 'arcade', arcade: { debug: false } },
      scale: {
        mode: Phaser.Scale.RESIZE,
        parent: 'game-canvas',
        width: size.w,
        height: size.h,
        autoCenter: Phaser.Scale.CENTER_BOTH
      },
      scene: [BootScene, GameScene, GameOverScene]
    });

    window.__amsmGame = game;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      requestAnimationFrame(bootGame);
    });
  } else {
    requestAnimationFrame(bootGame);
  }
})();
