import { chromium } from 'playwright';
import fs from 'fs';

(async () => {
  const report = { steps: [], ok: true, consoleErrors: [] };
  function log(step, detail) {
    report.steps.push({ step, detail });
    console.log(JSON.stringify({ step, detail }));
  }

  let browser;
  try {
    browser = await chromium.launch({ channel: 'msedge', headless: true });
  } catch (e) {
    log('browser', 'msedge failed: ' + e.message);
    try {
      browser = await chromium.launch({ headless: true });
    } catch (e2) {
      log('browser', 'FAIL ' + e2.message);
      process.exit(1);
    }
  }

  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();
  page.on('console', (msg) => {
    if (msg.type() === 'error') report.consoleErrors.push(msg.text());
  });
  page.on('pageerror', (err) => report.consoleErrors.push(String(err)));

  try {
    await page.goto('http://localhost:8080/', { waitUntil: 'domcontentloaded', timeout: 20000 });
    const homeNav = await page.locator('.amsm-nav').count();
    log('home_header', homeNav > 0 ? 'PASS amsm-nav present' : 'FAIL no amsm-nav');
    if (!homeNav) report.ok = false;

    const email = `playwright.smoke.${Date.now()}@example.com`;
    await page.click('text=Најава');
    await page.waitForTimeout(500);
    await page.fill('input[name=email]', email);
    await page.click('button:has-text("Испрати линк")');
    await page.waitForTimeout(2500);
    log('login_submit', 'submitted ' + email);

    const messages = await (await fetch('http://127.0.0.1:8025/api/v1/messages')).json();
    const mine = (messages.messages || []).find((m) =>
      (m.To || []).some((t) => (t.Address || '').includes(email))
    );
    if (!mine) {
      log('mailpit', 'FAIL no message for ' + email);
      report.ok = false;
    } else {
      const full = await (await fetch('http://127.0.0.1:8025/api/v1/message/' + mine.ID)).json();
      const html = full.HTML || full.Text || '';
      const match = html.match(/http:\/\/localhost:8080\/auth\/verify\?token=[a-f0-9-]+/i);
      if (!match) {
        log('verify_url', 'FAIL no verify URL in email');
        report.ok = false;
      } else {
        log('verify_url', 'PASS found');
        await page.goto(match[0], { waitUntil: 'networkidle', timeout: 20000 });
        await page.waitForTimeout(1500);

        const openModal = await page.evaluate(() => document.body.dataset.openModal || '');
        log('open_modal', openModal || '(none)');

        // Alpine x-show can hide the button; submit via DOM regardless of visibility
        const skipped = await page.evaluate(() => {
          const btn = document.querySelector('button[name="action"][value="skip"]');
          if (!btn) return false;
          const form = btn.closest('form');
          if (form) {
            // Ensure CSRF + submit skip without needing Alpine visibility
            const hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'action';
            hidden.value = 'skip';
            form.appendChild(hidden);
            form.submit();
            return true;
          }
          btn.click();
          return true;
        });
        if (skipped) {
          await page.waitForTimeout(1500);
          log('consent', 'skip submitted via form');
        } else {
          log('consent', 'no skip button (ok if already consented)');
        }

        await page.goto('http://localhost:8080/game', { waitUntil: 'networkidle', timeout: 25000 });
        await page.waitForTimeout(4000);

        const gameNav = await page.locator('.amsm-nav').count();
        log('game_header', gameNav > 0 ? 'PASS' : 'FAIL');
        if (!gameNav) report.ok = false;

        const canvas = await page.locator('#game-canvas canvas').count();
        log('game_canvas', canvas > 0 ? 'PASS' : 'FAIL');
        if (!canvas) report.ok = false;

        const errVisible = await page.evaluate(() => {
          const el = document.getElementById('game-error');
          return el && !el.classList.contains('hidden');
        });
        const errMsg = await page.evaluate(() => {
          const t = document.getElementById('game-error-msg');
          return t ? t.textContent : '';
        });
        log('game_error', errVisible ? 'FAIL visible: ' + errMsg : 'PASS hidden');
        if (errVisible) report.ok = false;

        const gameState = await page.evaluate(() => {
          const canvas = document.querySelector('#game-canvas canvas');
          const game = window.__amsmGame || null;
          return {
            hasCanvas: !!canvas,
            phase: game && game.getPhase ? game.getPhase() : null,
            canvasW: canvas ? canvas.width : 0,
            canvasH: canvas ? canvas.height : 0,
            gameConfig: !!(window.GAME_CONFIG),
            playsRemaining: window.GAME_CONFIG && window.GAME_CONFIG.playsRemaining,
            consoleHint: document.getElementById('game-error-msg')?.textContent || ''
          };
        });
        log('game_state', JSON.stringify(gameState));

        if (report.consoleErrors.length) {
          log('js_errors', report.consoleErrors.slice(0, 8).join(' | '));
          // Duration / anim errors are hard fails for playability
          if (report.consoleErrors.some((e) => /duration|anim|TypeError|ReferenceError/i.test(e))) {
            report.ok = false;
          }
        } else {
          log('js_errors', 'none');
        }

        await page.screenshot({ path: 'smoke-game.png', fullPage: true });

        const box = await page.locator('#game-canvas').boundingBox();
        if (box) {
          // Tap CTA / start area then jump
          await page.mouse.click(box.x + box.width * 0.5, box.y + box.height * 0.55);
          await page.waitForTimeout(2500);
          await page.keyboard.press('Space');
          await page.waitForTimeout(800);
          await page.keyboard.press('ArrowUp');
          await page.waitForTimeout(600);
          await page.keyboard.press('ArrowDown');
          await page.waitForTimeout(800);
          await page.keyboard.press('Space');
          await page.waitForTimeout(1200);
          log('controls', 'click + Space + Up + Down + Space');
        } else {
          log('controls', 'FAIL no game-canvas box');
          report.ok = false;
        }

        await page.screenshot({ path: 'smoke-game-play.png', fullPage: true });

        // Mobile viewport check
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto('http://localhost:8080/game', { waitUntil: 'networkidle', timeout: 25000 });
        await page.waitForTimeout(3500);
        const mobileBox = await page.locator('#game-canvas').boundingBox();
        const mobileOk =
          mobileBox &&
          mobileBox.height >= 360 &&
          mobileBox.width >= 280;
        log(
          'mobile_playfield',
          mobileOk
            ? `PASS ${Math.round(mobileBox.width)}x${Math.round(mobileBox.height)}`
            : `FAIL ${mobileBox ? Math.round(mobileBox.width) + 'x' + Math.round(mobileBox.height) : 'none'}`
        );
        if (!mobileOk) report.ok = false;
        await page.screenshot({ path: 'smoke-game-mobile.png', fullPage: true });

        log('screenshots', 'smoke-game.png, smoke-game-play.png, smoke-game-mobile.png');
      }
    }
  } catch (e) {
    log('exception', String(e && e.stack ? e.stack : e));
    report.ok = false;
  } finally {
    await browser.close();
  }

  fs.writeFileSync('smoke-report.json', JSON.stringify(report, null, 2));
  console.log('RESULT:' + (report.ok ? 'PASS' : 'FAIL'));
  process.exit(report.ok ? 0 : 1);
})();
