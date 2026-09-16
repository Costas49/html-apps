(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];

  const brand = $('.brand small');
  if (brand) brand.textContent = 'v2.3 • Android TV 12 • virtual chips only';
  const vipTitle = $('#vip h2');
  if (vipTitle) vipTitle.textContent = 'Premium 3D Light v2.3';

  function activeScreen() {
    return $('.screen.active');
  }

  function visible(el) {
    if (!el) return false;
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
    const r = el.getBoundingClientRect();
    return r.width > 1 && r.height > 1;
  }

  function focusables() {
    const s = activeScreen();
    if (!s) return [];
    return $$('#' + s.id + ' button:not([disabled]), #' + s.id + ' [tabindex="0"]').filter(visible);
  }

  function focusFirst() {
    const items = focusables();
    if (items.length) {
      items[0].focus();
      try { items[0].scrollIntoView({block:'nearest', inline:'nearest'}); } catch (_) {}
    }
  }

  function moveFocus(dir) {
    const items = focusables();
    if (!items.length) return;

    const cur = document.activeElement;
    if (!items.includes(cur)) {
      focusFirst();
      return;
    }

    const r = cur.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;
    let best = null;
    let bestScore = Infinity;

    for (const el of items) {
      if (el === cur) continue;
      const q = el.getBoundingClientRect();
      const x = q.left + q.width / 2;
      const y = q.top + q.height / 2;
      const dx = x - cx;
      const dy = y - cy;

      let primary, secondary, valid = false;
      if (dir === 'left'  && dx < -4) { valid = true; primary = -dx; secondary = Math.abs(dy); }
      if (dir === 'right' && dx >  4) { valid = true; primary =  dx; secondary = Math.abs(dy); }
      if (dir === 'up'    && dy < -4) { valid = true; primary = -dy; secondary = Math.abs(dx); }
      if (dir === 'down'  && dy >  4) { valid = true; primary =  dy; secondary = Math.abs(dx); }
      if (!valid) continue;

      const score = primary + secondary * 1.65;
      if (score < bestScore) {
        bestScore = score;
        best = el;
      }
    }

    if (best) {
      best.focus();
      try { best.scrollIntoView({block:'nearest', inline:'nearest'}); } catch (_) {}
    }
  }

  function clickFocused() {
    const items = focusables();
    const cur = document.activeElement;
    if (!items.includes(cur)) {
      focusFirst();
      return;
    }
    if (typeof cur.click === 'function') cur.click();
  }

  function billiardsActive() {
    return !!$('#billiards.screen.active');
  }

  function clickPool(selector) {
    const el = $(selector);
    if (el && typeof el.click === 'function') {
      el.click();
      return true;
    }
    return false;
  }

  function handleBilliards(action) {
    if (!billiardsActive()) return false;
    if (action === 'left')  return clickPool('[data-v22="aimLeft"]');
    if (action === 'right') return clickPool('[data-v22="aimRight"]');
    if (action === 'up')    return clickPool('[data-v22="camPrev"]');
    if (action === 'down')  return clickPool('[data-v22="camNext"]');
    if (action === 'ok')    return clickPool('[data-v22="shoot"]');
    return false;
  }

  function handleGlobal(action) {
    if (handleBilliards(action)) return;

    if (action === 'left' || action === 'right' || action === 'up' || action === 'down') {
      moveFocus(action);
      return;
    }

    if (action === 'ok') clickFocused();
  }

  window.addEventListener('android-dpad-v23', (e) => {
    handleGlobal(e.detail);
  });

  const billiardsTile = $('[data-open="billiards"]');
  if (billiardsTile) {
    billiardsTile.addEventListener('click', () => {
      setTimeout(() => {
        const nine = $$('.poolMode').find(b => /9/.test(b.textContent || ''));
        if (nine) nine.click();
      }, 80);
    });
  }

  setTimeout(() => {
    if (!visible(document.activeElement) || document.activeElement === document.body) focusFirst();
  }, 250);
})();