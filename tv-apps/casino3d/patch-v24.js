(() => {
  'use strict';
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];

  const brand = $('.brand small');
  if (brand) brand.textContent = 'v2.4 • Android TV 12 • virtual chips only';
  const vipTitle = $('#vip h2');
  if (vipTitle) vipTitle.textContent = 'Premium 3D Light v2.4';

  const pool = $('#billiards .poolShell');
  const canvas = $('#poolCanvas');
  if (!pool || !canvas) return;

  const style = document.createElement('style');
  style.textContent = `
    #billiards .stage{padding:10px 12px;overflow:auto}
    #billiards .poolShell{width:100%;max-width:none}
    #billiards .poolViewport{padding:0 10px 8px;perspective:1700px;overflow:visible}
    #billiards #poolCanvas{display:block;width:100%!important;height:auto!important;max-height:60vh!important;object-fit:contain;margin:0 auto;border:0!important;border-radius:18px;background:transparent!important;transform-origin:50% 50%;transition:transform .75s ease,filter .75s ease;filter:drop-shadow(0 26px 22px rgba(0,0,0,.58))}
    .v24CameraStrip{display:flex;align-items:center;justify-content:center;gap:7px;flex-wrap:wrap;margin:8px 0 10px}
    .v24Cam{padding:7px 10px;border-radius:11px;border:1px solid #ffffff20;background:#0b1019;color:#b9c2ce;font-weight:850;font-size:12px}
    .v24Cam.active{border-color:#ffd86b;color:#ffd86b;box-shadow:0 0 0 2px #ffd86b20}
    .v24CamInfo{min-width:180px;text-align:center;color:#ffd86b;font-weight:900}
    .v24Power{display:grid;grid-template-columns:150px minmax(320px,1fr) 150px;gap:14px;align-items:center;margin:10px auto 4px;max-width:980px}
    .v24AimPad{display:grid;grid-template-columns:64px 64px 64px;gap:7px;justify-content:center;align-items:center}
    .v24AimPad button{height:54px;padding:0;font-size:25px}
    .v24AimPad .left{grid-column:1}.v24AimPad .shoot{grid-column:2}.v24AimPad .right{grid-column:3}
    .v24Gauge{padding:12px 16px 13px;border-radius:18px;background:linear-gradient(180deg,#151c27,#080b11);border:1px solid #ffffff20;box-shadow:inset 0 0 24px #0008}
    .v24GaugeTitle{text-align:center;font-weight:950;color:#ffd86b;margin-bottom:8px}
    .v24Track{height:22px;border-radius:13px;position:relative;overflow:hidden;background:#07090d;border:1px solid #ffffff25}
    .v24Fill{position:absolute;inset:0 auto 0 0;width:50%;background:linear-gradient(90deg,#59df8e 0 33%,#f1d95e 33% 67%,#ff9955 67% 85%,#ff5968 85%);transition:width .14s ease}
    .v24Segments{position:absolute;inset:0;display:grid;grid-template-columns:repeat(4,1fr);pointer-events:none}
    .v24Segments span{border-right:2px solid rgba(0,0,0,.5)}.v24Segments span:last-child{border-right:0}
    .v24Ticks{display:grid;grid-template-columns:repeat(4,1fr);margin-top:5px;text-align:center;font-weight:1000;color:#e7ebf1}.v24Ticks span:last-child{color:#ff6976}
    .v24PowerRead{text-align:center;margin-top:4px;color:#b8c1ce;font-size:13px}.v24PowerRead b{color:#ffd86b;font-size:17px}
    .v24PowerButtons{display:grid;grid-template-columns:1fr;gap:8px}.v24PowerButtons button{min-height:50px;font-size:18px}
    .v24Hint{text-align:center;color:#aab3c0;font-size:13px;margin:7px 0 0}.v24Hint b{color:#ffd86b}
    @media(max-width:900px){.v24Power{grid-template-columns:130px 1fr 130px;gap:8px}.v24AimPad{grid-template-columns:48px 48px 48px}.v24AimPad button{height:46px}.v24PowerButtons button{min-height:44px}.v24Gauge{padding-left:9px;padding-right:9px}}
  `;
  document.head.appendChild(style);

  $('#poolV21Controls')?.remove();
  $('#poolV22Controls')?.remove();
  const oldCam = $('.cameraHud');
  if (oldCam) oldCam.remove();
  const oldMeter = pool.querySelector('.meter');
  if (oldMeter) oldMeter.style.display = 'none';

  let camera = 4;
  let autoCamera = true;
  let cameraTimer = null;
  const cameraViews = [
    {name:'ΓΩΝΙΑ 1 • ΜΠΡΟΣΤΑ ΑΡΙΣΤΕΡΑ', transform:'perspective(1500px) rotateX(12deg) rotateY(-12deg) rotateZ(-1deg) scale(.88)'},
    {name:'ΓΩΝΙΑ 2 • ΜΠΡΟΣΤΑ ΔΕΞΙΑ', transform:'perspective(1500px) rotateX(12deg) rotateY(12deg) rotateZ(1deg) scale(.88)'},
    {name:'ΓΩΝΙΑ 3 • ΠΙΣΩ ΔΕΞΙΑ', transform:'perspective(1500px) rotateX(10deg) rotateY(-11deg) rotateZ(179deg) scale(.86)'},
    {name:'ΓΩΝΙΑ 4 • ΠΙΣΩ ΑΡΙΣΤΕΡΑ', transform:'perspective(1500px) rotateX(10deg) rotateY(11deg) rotateZ(181deg) scale(.86)'},
    {name:'ΚΑΜΕΡΑ 5 • ΨΗΛΑ', transform:'perspective(1800px) rotateX(1deg) rotateY(0deg) rotateZ(0deg) scale(.97)'}
  ];

  const cameraStrip = document.createElement('div');
  cameraStrip.className = 'v24CameraStrip';
  cameraStrip.innerHTML = `
    <span class="v24CamInfo" id="v24CamInfo">ΚΑΜΕΡΑ 5 • ΨΗΛΑ</span>
    <span class="v24Cam" data-cam="0">Γωνία 1</span><span class="v24Cam" data-cam="1">Γωνία 2</span>
    <span class="v24Cam" data-cam="2">Γωνία 3</span><span class="v24Cam" data-cam="3">Γωνία 4</span>
    <span class="v24Cam active" data-cam="4">Ψηλά</span><span class="v24Cam active" id="v24Auto">AUTO</span>`;
  const viewport = canvas.parentElement;
  viewport.insertAdjacentElement('afterend', cameraStrip);

  function applyCamera(){
    const v = cameraViews[camera];
    canvas.style.transform = v.transform;
    $('#v24CamInfo').textContent = v.name;
    $$('.v24Cam[data-cam]').forEach((x,i)=>x.classList.toggle('active', i===camera));
    $('#v24Auto').classList.toggle('active', autoCamera);
  }
  function nextCamera(){ camera = (camera + 1) % cameraViews.length; applyCamera(); }
  function startAuto(){
    clearInterval(cameraTimer);
    if (autoCamera) cameraTimer = setInterval(nextCamera, 2600);
  }
  $$('.v24Cam[data-cam]').forEach(x => x.addEventListener('click', ()=>{autoCamera=false;camera=+x.dataset.cam;applyCamera();startAuto();}));
  $('#v24Auto').addEventListener('click', ()=>{autoCamera=!autoCamera;applyCamera();startAuto();});
  applyCamera();startAuto();

  const levels = [0.30, 0.48, 0.70, 1.00];
  const labels = ['1','2','3','MAX'];
  let level = 1;
  const controls = document.createElement('div');
  controls.id = 'poolV24Controls';
  controls.innerHTML = `
    <div class="v24Power">
      <div class="v24AimPad">
        <button class="btn left" data-v24="aimLeft" data-v22="aimLeft">◀</button>
        <button class="btn gold shoot" data-v24="shoot" data-v22="shoot">OK</button>
        <button class="btn right" data-v24="aimRight" data-v22="aimRight">▶</button>
      </div>
      <div class="v24Gauge">
        <div class="v24GaugeTitle">ΕΝΤΑΣΗ ΧΤΥΠΗΜΑΤΟΣ</div>
        <div class="v24Track"><div class="v24Fill" id="v24PowerFill"></div><div class="v24Segments"><span></span><span></span><span></span><span></span></div></div>
        <div class="v24Ticks"><span>1</span><span>2</span><span>3</span><span>MAX</span></div>
        <div class="v24PowerRead">Επιλεγμένη ένταση: <b id="v24PowerLabel">2</b></div>
      </div>
      <div class="v24PowerButtons">
        <button class="btn" data-v24="powerUp" data-v22="camPrev">▲ + ΕΝΤΑΣΗ</button>
        <button class="btn" data-v24="powerDown" data-v22="camNext">▼ − ΕΝΤΑΣΗ</button>
      </div>
    </div>
    <div class="v24Hint"><b>◀ ▶</b> στόχευση • <b>▲ ▼</b> ένταση 1–2–3–MAX • <b>OK</b> χτύπημα • 5 αυτόματες κάμερες</div>`;
  const result = $('#poolResult');
  pool.insertBefore(controls, result || null);

  function syncPower(){
    $('#v24PowerFill').style.width = ((level+1)*25) + '%';
    $('#v24PowerLabel').textContent = labels[level];
  }
  function powerUp(){ level = Math.min(3, level+1); syncPower(); try{tone(520,.035)}catch(_){} }
  function powerDown(){ level = Math.max(0, level-1); syncPower(); try{tone(360,.035)}catch(_){} }
  syncPower();

  canvas.width = 1200;
  canvas.height = 780;
  drawPool = function(){
    const c = ctx, W = canvas.width, H = canvas.height;
    c.clearRect(0,0,W,H);

    const sh = c.createRadialGradient(600,690,35,600,690,470);
    sh.addColorStop(0,'rgba(0,0,0,.48)'); sh.addColorStop(1,'rgba(0,0,0,0)');
    c.fillStyle=sh;c.beginPath();c.ellipse(600,692,475,72,0,0,Math.PI*2);c.fill();

    const legGrad = c.createLinearGradient(0,590,0,760);legGrad.addColorStop(0,'#222934');legGrad.addColorStop(.55,'#111721');legGrad.addColorStop(1,'#070b11');
    c.fillStyle=legGrad;
    const legs=[[155,580,270,735],[390,600,475,765],[735,600,820,765],[930,580,1045,735]];
    for(const [x1,y1,x2,y2] of legs){c.beginPath();c.moveTo(x1,y1);c.lineTo(x2,y1+8);c.lineTo(x2-20,y2);c.lineTo(x1+18,y2-4);c.closePath();c.fill();}
    c.fillStyle='#05080d';for(const x of [210,430,775,990]){c.beginPath();c.ellipse(x,748,42,10,0,0,Math.PI*2);c.fill();c.fillStyle='#99a1aa';c.beginPath();c.ellipse(x,745,25,5,0,0,Math.PI*2);c.fill();c.fillStyle='#05080d';}

    const body = c.createLinearGradient(0,530,0,665);body.addColorStop(0,'#2d3542');body.addColorStop(.45,'#171e29');body.addColorStop(1,'#080d14');
    c.fillStyle=body;c.beginPath();c.moveTo(55,525);c.lineTo(1145,525);c.lineTo(1085,655);c.lineTo(115,655);c.closePath();c.fill();
    c.strokeStyle='#4a5666';c.lineWidth=3;c.stroke();
    c.fillStyle='#05070b';c.fillRect(120,560,170,38);c.fillRect(910,560,170,38);

    const wood=c.createLinearGradient(0,0,W,0);wood.addColorStop(0,'#111722');wood.addColorStop(.5,'#37404f');wood.addColorStop(1,'#101620');
    c.fillStyle=wood;c.fillRect(28,28,W-56,570);
    c.strokeStyle='#697789';c.lineWidth=5;c.strokeRect(28,28,W-56,570);
    c.fillStyle='#163e58';c.fillRect(55,55,W-110,525);
    const cloth=c.createRadialGradient(575,285,30,600,320,650);cloth.addColorStop(0,'#20a4d0');cloth.addColorStop(.65,'#108ab7');cloth.addColorStop(1,'#06688f');
    c.fillStyle=cloth;c.fillRect(78,78,W-156,494);

    const pockets=[[78,78],[W/2,72],[W-78,78],[78,572],[W/2,578],[W-78,572]];
    c.fillStyle='#020305';pockets.forEach(([x,y])=>{c.beginPath();c.arc(x,y,25,0,Math.PI*2);c.fill();c.strokeStyle='#7c8997';c.lineWidth=2;c.stroke();});
    c.fillStyle='#c7d0d8';for(let i=1;i<7;i++){c.beginPath();c.arc(78+i*(W-156)/7,48,4,0,Math.PI*2);c.fill();c.beginPath();c.arc(78+i*(W-156)/7,602,4,0,Math.PI*2);c.fill();}
    for(let i=1;i<3;i++){c.beginPath();c.arc(49,78+i*494/3,4,0,Math.PI*2);c.fill();c.beginPath();c.arc(W-49,78+i*494/3,4,0,Math.PI*2);c.fill();}

    c.strokeStyle='rgba(255,255,255,.15)';c.lineWidth=2;c.beginPath();c.moveTo(W*.25,78);c.lineTo(W*.25,572);c.stroke();
    c.fillStyle='rgba(255,255,255,.48)';c.beginPath();c.arc(W*.75,325,4,0,Math.PI*2);c.fill();

    for(const b of balls){
      c.save();c.shadowColor='rgba(0,0,0,.46)';c.shadowBlur=8;c.shadowOffsetX=3;c.shadowOffsetY=5;
      c.beginPath();c.arc(b.x,b.y,13,0,Math.PI*2);c.fillStyle=b.c;c.fill();c.shadowColor='transparent';
      if(b.n>0){
        if(b.stripe){c.save();c.beginPath();c.arc(b.x,b.y,12,0,Math.PI*2);c.clip();c.fillStyle='#f4f1e8';c.fillRect(b.x-13,b.y-13,26,8);c.fillRect(b.x-13,b.y+5,26,8);c.restore();}
        c.beginPath();c.arc(b.x,b.y,7,0,Math.PI*2);c.fillStyle='#f7f4ec';c.fill();c.fillStyle='#111';c.font='bold 9px sans-serif';c.textAlign='center';c.textBaseline='middle';c.fillText(String(b.n),b.x,b.y+.5);
      } else {
        const hi=c.createRadialGradient(b.x-5,b.y-6,1,b.x,b.y,13);hi.addColorStop(0,'#fff');hi.addColorStop(1,'#cfd3d6');c.fillStyle=hi;c.beginPath();c.arc(b.x,b.y,12.5,0,Math.PI*2);c.fill();
      }
      c.restore();
    }

    const cue=balls.find(b=>b.n===0);
    if(cue&&!anim){
      c.strokeStyle='#e8c287';c.lineWidth=7;c.beginPath();c.moveTo(cue.x-Math.cos(aim)*220,cue.y-Math.sin(aim)*220);c.lineTo(cue.x-Math.cos(aim)*28,cue.y-Math.sin(aim)*28);c.stroke();
      c.strokeStyle='rgba(255,255,255,.76)';c.lineWidth=2;c.setLineDash([10,9]);c.beginPath();c.moveTo(cue.x,cue.y);c.lineTo(cue.x+Math.cos(aim)*390,cue.y+Math.sin(aim)*390);c.stroke();c.setLineDash([]);
    }
  };

  let v24FirstHit=null, v24Target=null, v24Potted=[];
  const isNine=()=>{const b=$$('.poolMode').find(x=>x.classList.contains('activeMode'));return !!(b&&/9/.test(b.textContent||''));};
  const lowestNineV24=()=>{const ns=balls.filter(b=>b.n>0&&b.n<=9).map(b=>b.n);return ns.length?Math.min(...ns):null;};
  const pocketCentersV24=()=>[[78,78],[600,72],[1122,78],[78,572],[600,578],[1122,572]];
  const inPocketV24=b=>pocketCentersV24().some(([x,y])=>Math.hypot(b.x-x,b.y-y)<28);
  function endShotV24(){
    anim=false;
    if(!balls.some(b=>b.n===0)) balls.unshift({n:0,x:300,y:325,vx:0,vy:0,c:'#fff',stripe:false});
    let msg='Έτοιμο για επόμενο χτύπημα.';
    if(isNine()){
      const legal=v24Target==null||v24FirstHit===v24Target;
      const nine=v24Potted.includes(9);
      if(!legal){msg=`Φάουλ: έπρεπε να χτυπήσεις πρώτα τη ${v24Target}.`;const cue=balls.find(b=>b.n===0);if(cue){cue.x=300;cue.y=325;cue.vx=cue.vy=0;}}
      else if(nine) msg='🏆 Νόμιμη 9 — ΚΕΡΔΙΣΕΣ ΤΟ 9-BALL!';
      else {const lo=lowestNineV24();msg=lo?`Σειρά: χτύπησε πρώτα τη μπάλα ${lo}.`:'Τέλος παρτίδας.';}
    } else if(v24Potted.length) msg=`Μπήκαν: ${v24Potted.join(', ')}.`;
    v24FirstHit=null;v24Target=null;v24Potted=[];
    const r=$('#poolResult');if(r)r.textContent=msg;drawPool();
  }
  stepPool = function(){
    let moving=false;
    for(const b of balls){
      b.x+=b.vx;b.y+=b.vy;b.vx*=.986;b.vy*=.986;
      if(Math.abs(b.vx)+Math.abs(b.vy)>.07)moving=true;
      if(b.x<91){b.x=91;b.vx=Math.abs(b.vx)*.91}if(b.x>1109){b.x=1109;b.vx=-Math.abs(b.vx)*.91}
      if(b.y<91){b.y=91;b.vy=Math.abs(b.vy)*.91}if(b.y>559){b.y=559;b.vy=-Math.abs(b.vy)*.91}
    }
    for(let i=0;i<balls.length;i++)for(let j=i+1;j<balls.length;j++){
      const a=balls[i],b=balls[j],dx=b.x-a.x,dy=b.y-a.y,dist=Math.hypot(dx,dy);
      if(dist>0&&dist<26){
        if(v24FirstHit==null){if(a.n===0&&b.n>0)v24FirstHit=b.n;else if(b.n===0&&a.n>0)v24FirstHit=a.n;}
        const nx=dx/dist,ny=dy/dist,rel=(a.vx-b.vx)*nx+(a.vy-b.vy)*ny;
        if(rel>0){const impulse=rel*.98;a.vx-=impulse*nx;a.vy-=impulse*ny;b.vx+=impulse*nx;b.vy+=impulse*ny;}
        const ov=26-dist;a.x-=nx*ov/2;a.y-=ny*ov/2;b.x+=nx*ov/2;b.y+=ny*ov/2;
      }
    }
    const kept=[];for(const b of balls){if(inPocketV24(b)){if(b.n>0)v24Potted.push(b.n);continue}kept.push(b)}balls=kept;
    drawPool();if(moving)requestAnimationFrame(stepPool);else endShotV24();
  };
  shootPool = function(){
    if(anim) return;
    const cue = balls.find(b=>b.n===0); if(!cue) return;
    anim=true;v24FirstHit=null;v24Potted=[];v24Target=isNine()?lowestNineV24():null;
    const spd = 7 + levels[level] * 20;
    cue.vx=Math.cos(aim)*spd;cue.vy=Math.sin(aim)*spd;
    try{tone(230,.05)}catch(_){}
    const r=$('#poolResult');if(r)r.textContent=`Χτύπημα έντασης ${labels[level]}...`;
    requestAnimationFrame(stepPool);
  };

  function aimLeft(){if(anim)return;aim-=.055;drawPool()}
  function aimRight(){if(anim)return;aim+=.055;drawPool()}
  $$('[data-v24]').forEach(b=>b.addEventListener('click',()=>{
    const a=b.dataset.v24;
    if(a==='aimLeft')aimLeft(); else if(a==='aimRight')aimRight(); else if(a==='powerUp')powerUp(); else if(a==='powerDown')powerDown(); else if(a==='shoot')shootPool();
  }));
  const topShoot=$('#shootBtn');if(topShoot)topShoot.onclick=shootPool;

  setTimeout(()=>{try{drawPool()}catch(_){}},80);
})();
