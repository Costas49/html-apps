(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];

  // --- VERSION LABELS ---
  const brandSmall = document.querySelector('.brand small');
  if (brandSmall) brandSmall.textContent = 'v2.1 • Android TV 12 • virtual chips only';
  const vipTitle = document.querySelector('#vip h2');
  if (vipTitle) vipTitle.textContent = 'Premium 3D Light v2.1';

  // --- EMBEDDED FRONT-PAGE BACKGROUND IMAGE (offline SVG) ---
  const bgSvg = `
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 900">
    <defs>
      <linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
        <stop stop-color="#25102e"/>
        <stop offset=".46" stop-color="#090b13"/>
        <stop offset="1" stop-color="#020307"/>
      </linearGradient>
      <radialGradient id="lamp">
        <stop stop-color="#fff3b2" stop-opacity=".95"/>
        <stop offset=".22" stop-color="#ffd65f" stop-opacity=".7"/>
        <stop offset="1" stop-color="#ffb700" stop-opacity="0"/>
      </radialGradient>
      <linearGradient id="gold" x1="0" x2="1">
        <stop stop-color="#6e4209"/><stop offset=".5" stop-color="#f6d26d"/><stop offset="1" stop-color="#6e4209"/>
      </linearGradient>
    </defs>
    <rect width="1600" height="900" fill="url(#g)"/>
    <ellipse cx="800" cy="880" rx="780" ry="360" fill="#3b0718" opacity=".48"/>
    <path d="M0 610 L800 390 L1600 610 L1600 900 L0 900Z" fill="#19060e" opacity=".9"/>
    <g opacity=".8">
      <rect x="90" y="120" width="44" height="610" rx="12" fill="url(#gold)"/>
      <rect x="1466" y="120" width="44" height="610" rx="12" fill="url(#gold)"/>
      <rect x="300" y="170" width="32" height="510" rx="10" fill="url(#gold)" opacity=".72"/>
      <rect x="1268" y="170" width="32" height="510" rx="10" fill="url(#gold)" opacity=".72"/>
    </g>
    <g opacity=".72">
      <circle cx="460" cy="210" r="155" fill="url(#lamp)"/>
      <circle cx="800" cy="145" r="190" fill="url(#lamp)"/>
      <circle cx="1140" cy="210" r="155" fill="url(#lamp)"/>
    </g>
    <g fill="#0c111a" stroke="#d9ad4b" stroke-width="5" opacity=".92">
      <rect x="190" y="440" width="180" height="250" rx="24"/>
      <rect x="1230" y="440" width="180" height="250" rx="24"/>
    </g>
    <g fill="#f4cf69" font-family="sans-serif" font-weight="700" text-anchor="middle" opacity=".85">
      <text x="280" y="535" font-size="64">7 7 7</text>
      <text x="1320" y="535" font-size="64">7 7 7</text>
    </g>
    <ellipse cx="800" cy="610" rx="355" ry="120" fill="#0a5b42" stroke="#6a3c18" stroke-width="26" opacity=".92"/>
    <ellipse cx="800" cy="608" rx="250" ry="72" fill="#083b2d" opacity=".75"/>
    <path d="M590 765 Q800 680 1010 765" fill="none" stroke="#e4b64f" stroke-width="7" opacity=".6"/>
  </svg>`;
  const bgUrl = `url("data:image/svg+xml;charset=UTF-8,${encodeURIComponent(bgSvg)}")`;

  const style = document.createElement('style');
  style.textContent = `
    #lobby{
      background-image:linear-gradient(rgba(2,3,8,.38),rgba(2,3,8,.82)),${bgUrl};
      background-size:cover;
      background-position:center top;
      background-attachment:local;
    }
    #lobby .hero{background:rgba(5,7,12,.50);backdrop-filter:blur(1px)}
    #lobby .tile{background:linear-gradient(145deg,rgba(25,31,43,.91),rgba(13,17,24,.89) 60%,rgba(29,18,8,.92))}

    /* BILLIARDS v2.1 */
    #billiards .stage{min-height:0;padding:10px;overflow:visible}
    #billiards .center{min-height:0;display:block}
    #billiards .poolShell{width:100%;max-width:none}
    #billiards .poolCanvas{width:100%;height:auto;max-height:58vh;object-fit:contain;display:block;margin:0 auto}
    .poolControlRow{display:grid;grid-template-columns:150px 1fr 150px;gap:16px;align-items:center;margin-top:12px}
    .poolPad{display:grid;grid-template-columns:54px 54px 54px;grid-template-rows:48px 48px 48px;gap:6px;justify-content:center}
    .poolPad button{padding:0;min-width:0;font-size:26px}
    .poolPad .up{grid-column:2;grid-row:1}.poolPad .left{grid-column:1;grid-row:2}.poolPad .ok{grid-column:2;grid-row:2}.poolPad .right{grid-column:3;grid-row:2}.poolPad .down{grid-column:2;grid-row:3}
    .powerConsole{position:relative;height:110px;border-radius:18px;background:linear-gradient(180deg,#151a23,#090b10);border:1px solid #ffffff25;padding:14px 24px 12px;overflow:hidden}
    .powerTitle{text-align:center;color:#ffd86b;font-weight:900;margin-bottom:10px}
    .powerGauge{position:relative;height:40px;margin:0 10px}
    .powerTrack{position:absolute;left:0;right:0;top:19px;height:8px;border-radius:8px;background:linear-gradient(90deg,#55dd8a,#c8e966,#ffe16c,#ffae55,#ff5965)}
    .powerNeedle{position:absolute;top:0;width:4px;height:40px;background:white;box-shadow:0 0 10px #fff;transform:translateX(-2px);transition:left .12s ease}
    .powerTicks{display:flex;justify-content:space-between;margin-top:2px;font-weight:900;color:#dce3eb}
    .powerTicks span:last-child{color:#ff6b73}
    .poolHint{text-align:center;color:#a7afbd;font-size:13px;margin-top:6px}

    /* SLOT: stronger true reel spin impression */
    .spinning .reel{animation:reelV21 .11s linear infinite;transform-style:preserve-3d;will-change:transform,filter}
    .spinning .reel:nth-child(2){animation-duration:.10s}.spinning .reel:nth-child(3){animation-duration:.09s}.spinning .reel:nth-child(4){animation-duration:.105s}.spinning .reel:nth-child(5){animation-duration:.095s}
    @keyframes reelV21{0%{transform:rotateX(0deg) translateY(0);filter:blur(0)}50%{transform:rotateX(180deg) translateY(-12px);filter:blur(2px)}100%{transform:rotateX(360deg) translateY(0);filter:blur(0)}}

    /* DERBY: six visible lanes, forward-only live road */
    #horses .stage{padding:10px;min-height:520px}
    #horses .center{min-height:490px}
    #race.v21Race{display:grid;grid-template-columns:repeat(6,1fr);gap:6px;width:100%;height:470px;perspective:900px;overflow:hidden;border-radius:24px;background:linear-gradient(#29425b 0 12%,#6d7b50 12% 18%,#20242a 18% 100%);border:2px solid #ffffff20;position:relative}
    #race.v21Race:before{content:'FINISH';position:absolute;top:70px;left:2%;right:2%;height:7px;background:repeating-linear-gradient(90deg,#fff 0 18px,#111 18px 36px);color:white;font-size:12px;letter-spacing:4px;text-align:center;line-height:28px;z-index:8;opacity:.9}
    .v21Lane{position:relative;overflow:hidden;border-left:1px solid #ffffff18;border-right:1px solid #0008;background:linear-gradient(180deg,#35383d 0,#24262b 100%)}
    .v21Lane:before{content:'';position:absolute;inset:-120% 44% -120% 44%;background:repeating-linear-gradient(180deg,transparent 0 42px,#e9e6d6 42px 74px,transparent 74px 122px);opacity:.65;animation:roadFlow .72s linear infinite;animation-play-state:paused}
    #race.v21Race.racing .v21Lane:before{animation-play-state:running}
    @keyframes roadFlow{to{transform:translateY(122px)}}
    .v21Horse{position:absolute;left:50%;bottom:18px;transform:translateX(-50%);font-size:54px;filter:drop-shadow(0 8px 8px #000);transition:bottom .10s linear,transform .10s linear;z-index:4;text-align:center;white-space:nowrap}
    .v21Horse b{display:block;font-size:14px;background:#05070bdc;color:#ffd86b;border:1px solid #ffd86b55;border-radius:999px;padding:2px 8px;margin:0 auto 2px;width:max-content}
    .v21RoadGlow{position:absolute;inset:0;background:linear-gradient(180deg,rgba(255,255,255,.03),transparent 25%,rgba(0,0,0,.16));pointer-events:none;z-index:6}
    @media(max-width:900px){#race.v21Race{height:410px}.v21Horse{font-size:42px}.poolControlRow{grid-template-columns:130px 1fr 130px}}
  `;
  document.head.appendChild(style);

  // --- BILLIARDS CONTROLS / POWER 1..5(MAX) ---
  const poolShell = document.querySelector('#billiards .poolShell');
  let powerLevel = 3;
  function syncPowerLevel(){
    try { power = [0.22,0.40,0.58,0.78,1.0][powerLevel-1]; } catch(e) {}
    const needle = document.querySelector('#poolPowerNeedle');
    if (needle) needle.style.left = `${(powerLevel-1)*25}%`;
    const label = document.querySelector('#poolPowerLabel');
    if (label) label.textContent = powerLevel === 5 ? '5 / MAX' : String(powerLevel);
    const old = document.querySelector('#powerBar');
    if (old) old.style.width = `${powerLevel*20}%`;
  }
  function poolLeft(){ try { aim -= .10; drawPool(); } catch(e){} }
  function poolRight(){ try { aim += .10; drawPool(); } catch(e){} }
  function poolUp(){ powerLevel = Math.min(5,powerLevel+1); syncPowerLevel(); }
  function poolDown(){ powerLevel = Math.max(1,powerLevel-1); syncPowerLevel(); }
  function poolShoot(){ try { if (!anim) shootPool(); } catch(e){} }

  if (poolShell && !document.querySelector('#poolV21Controls')) {
    const oldMeter = poolShell.querySelector('.meter');
    if (oldMeter) oldMeter.style.display = 'none';
    const controls = document.createElement('div');
    controls.id = 'poolV21Controls';
    controls.className = 'poolControlRow';
    controls.innerHTML = `
      <div class="poolPad">
        <button class="btn up" data-pool="up">▲</button>
        <button class="btn left" data-pool="left">◀</button>
        <button class="btn gold ok" data-pool="shoot">OK</button>
        <button class="btn right" data-pool="right">▶</button>
        <button class="btn down" data-pool="down">▼</button>
      </div>
      <div>
        <div class="powerConsole">
          <div class="powerTitle">ΕΝΤΑΣΗ ΧΤΥΠΗΜΑΤΟΣ: <span id="poolPowerLabel">3</span></div>
          <div class="powerGauge"><div class="powerTrack"></div><div class="powerNeedle" id="poolPowerNeedle"></div></div>
          <div class="powerTicks"><span>1</span><span>2</span><span>3</span><span>4</span><span>5 / MAX</span></div>
        </div>
        <div class="poolHint">◀ ▶ στόχος • ▲ ▼ ένταση 1–5 • OK/MAX χτύπημα</div>
      </div>
      <button class="btn gold" id="poolMaxShoot">MAX<br>ΧΤΥΠΗΜΑ</button>`;
    const result = poolShell.querySelector('#poolResult');
    poolShell.insertBefore(controls, result || null);
    controls.querySelectorAll('[data-pool]').forEach(b => b.addEventListener('click', () => {
      const a = b.dataset.pool;
      if (a==='left') poolLeft();
      if (a==='right') poolRight();
      if (a==='up') poolUp();
      if (a==='down') poolDown();
      if (a==='shoot') poolShoot();
    }));
    document.querySelector('#poolMaxShoot').addEventListener('click', () => { powerLevel=5; syncPowerLevel(); poolShoot(); });
    const topShoot = document.querySelector('#shootBtn');
    if (topShoot) topShoot.onclick = poolShoot;
  }
  syncPowerLevel();

  // Capture Android TV D-pad before the old navigation handler.
  window.addEventListener('keydown', (e) => {
    let isBilliards = false;
    try { isBilliards = current === 'billiards'; } catch(err) { isBilliards = document.querySelector('#billiards.screen.active') !== null; }
    if (!isBilliards) return;
    const code = e.keyCode || e.which || 0;
    let handled = true;
    if (e.key === 'ArrowLeft' || code === 21 || code === 37) poolLeft();
    else if (e.key === 'ArrowRight' || code === 22 || code === 39) poolRight();
    else if (e.key === 'ArrowUp' || code === 19 || code === 38) poolUp();
    else if (e.key === 'ArrowDown' || code === 20 || code === 40) poolDown();
    else if (e.key === 'Enter' || e.key === ' ' || code === 23 || code === 66) poolShoot();
    else handled = false;
    if (handled) { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); }
  }, true);

  // --- SLOTS: visible symbol cycling + 3D reel rotation ---
  const spinBtn = document.querySelector('#spinBtn');
  if (spinBtn) {
    spinBtn.onclick = () => {
      try { if (!pay(50)) return; } catch(e) { return; }
      const reelBox = document.querySelector('#reels5');
      const reels = $$('#reels5 .reel');
      const syms = ['🍒','🍋','🔔','7️⃣','💎','⭐'];
      reelBox.classList.add('spinning');
      const out = document.querySelector('#slotResult');
      if (out) out.textContent = 'Οι κύλινδροι περιστρέφονται...';
      const timers = reels.map((r,i) => setInterval(() => {
        r.textContent = syms[Math.floor(Math.random()*syms.length)];
        r.style.transform = `rotateX(${(Date.now()/2 + i*72)%360}deg)`;
      }, 70 + i*7));
      const final = [];
      reels.forEach((r,i) => {
        setTimeout(() => {
          clearInterval(timers[i]);
          const s = syms[Math.floor(Math.random()*syms.length)];
          final[i] = s;
          r.textContent = s;
          r.style.transform = 'rotateX(0deg)';
          if (i === reels.length-1) {
            reelBox.classList.remove('spinning');
            const counts = {};
            final.forEach(x => counts[x] = (counts[x]||0)+1);
            const wild = counts['⭐']||0, scatter = counts['💎']||0;
            const best = Math.max(...Object.values(counts));
            let prize = 0;
            if (scatter >= 3) prize += scatter*180;
            if (best + wild >= 5) prize += 1800;
            else if (best + wild >= 4) prize += 600;
            else if (best + wild >= 3) prize += 180;
            if (final.every(x => x === '7️⃣')) prize = 5000;
            if (prize) {
              try { reward(prize); } catch(e){}
              if (out) out.innerHTML = `<span class="win">Κέρδος ${prize.toLocaleString('el-GR')} μαρκών!</span>`;
            } else if (out) out.innerHTML = '<span class="lose">Χωρίς πληρωμή σε αυτή την περιστροφή.</span>';
          }
        }, 750 + i*190);
      });
    };
  }

  // --- DERBY: all 6 horses visible, forward-only live road flow ---
  const race = document.querySelector('#race');
  if (race) {
    race.className = 'race v21Race';
    race.innerHTML = '';
    for (let i=1;i<=6;i++) {
      const lane = document.createElement('div');
      lane.className = 'v21Lane';
      lane.innerHTML = `<div class="v21Horse" id="vh${i}"><b>#${i}</b>🏇</div>`;
      race.appendChild(lane);
    }
    const glow = document.createElement('div');
    glow.className = 'v21RoadGlow';
    race.appendChild(glow);
  }

  const raceBtn = document.querySelector('#raceBtn');
  if (raceBtn) {
    raceBtn.onclick = () => {
      try { if (!pay(100)) return; } catch(e) { return; }
      let pick = 1;
      try { pick = horsePick; } catch(e) {}
      const positions = Array(6).fill(0);
      let finished = false;
      const road = document.querySelector('#race');
      road.classList.add('racing');
      for (let i=1;i<=6;i++) {
        const h = document.querySelector(`#vh${i}`);
        if (h) { h.style.bottom = '18px'; h.style.transform = 'translateX(-50%) scale(1)'; }
      }
      const result = document.querySelector('#raceResult');
      if (result) result.textContent = 'LIVE • Και τα 6 άλογα τρέχουν μπροστά...';
      const t = setInterval(() => {
        for (let i=0;i<6;i++) positions[i] = Math.min(100, positions[i] + 1.1 + Math.random()*3.4);
        positions.forEach((p,i) => {
          const h = document.querySelector(`#vh${i+1}`);
          if (h) {
            const bottom = 18 + p*3.25;
            const scale = 1 - p*0.0032;
            h.style.bottom = `${bottom}px`;
            h.style.transform = `translateX(-50%) scale(${Math.max(.68,scale)})`;
          }
        });
        const winnerIndex = positions.findIndex(p => p >= 100);
        if (winnerIndex >= 0 && !finished) {
          finished = true;
          clearInterval(t);
          road.classList.remove('racing');
          const winner = winnerIndex + 1;
          if (winner === pick) {
            try { reward(500); } catch(e){}
            if (result) result.innerHTML = `<span class="win">Νίκησε το Άλογο ${winner} • +500</span>`;
          } else if (result) result.innerHTML = `<span class="lose">Νίκησε το Άλογο ${winner}</span>`;
        }
      }, 100);
    };
  }

  // Ensure 6 choice buttons are present and visible.
  const choices = document.querySelector('#horseChoices');
  if (choices) {
    const buttons = [...choices.querySelectorAll('.horsePick')];
    buttons.forEach(b => { b.style.display='inline-block'; b.style.visibility='visible'; });
  }
})();
