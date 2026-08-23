package com.example.htmltoapk.data

import com.example.htmltoapk.data.model.ProjectFile

/** Ready-made single-file web templates shown on the empty-state Home screen. */
object Templates {

    val names = listOf("Simple Calculator", "Todo App", "Flappy Game", "Canvas Drawing")

    fun build(name: String): List<ProjectFile> = when (name) {
        "Simple Calculator" -> listOf(ProjectFile("index.html", textContent = CALCULATOR_HTML))
        "Todo App" -> listOf(ProjectFile("index.html", textContent = TODO_HTML))
        "Flappy Game" -> listOf(ProjectFile("index.html", textContent = FLAPPY_HTML))
        "Canvas Drawing" -> listOf(ProjectFile("index.html", textContent = CANVAS_HTML))
        else -> listOf(ProjectFile("index.html", textContent = "<html><body><h1>Empty project</h1></body></html>"))
    }

    private const val CALCULATOR_HTML = """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{font-family:sans-serif;background:#0B1023;color:#EAF0FF;margin:0;display:flex;justify-content:center}
.calc{width:320px;padding:16px}
#disp{width:100%;height:64px;font-size:32px;text-align:right;background:#141B34;color:#fff;border:none;border-radius:8px;padding:8px;box-sizing:border-box}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:8px}
button{height:56px;font-size:20px;border-radius:8px;border:none;background:#1D2547;color:#EAF0FF}
button.op{background:#3B4CFF}
button.eq{background:#10E0A0;color:#000}
</style></head>
<body><div class="calc">
<input id="disp" readonly>
<div class="grid" id="keys"></div>
</div>
<script>
const disp = document.getElementById('disp');
const keys = ['7','8','9','/','4','5','6','*','1','2','3','-','0','.','=','+','C'];
const grid = document.getElementById('keys');
keys.forEach(k => {
  const b = document.createElement('button');
  b.textContent = k;
  if ('/*-+'.includes(k)) b.className='op';
  if (k === '=') b.className='eq';
  b.onclick = () => {
    if (k === 'C') disp.value = '';
    else if (k === '=') { try { disp.value = eval(disp.value).toString(); } catch(e){ disp.value = 'Error'; } }
    else disp.value += k;
  };
  grid.appendChild(b);
});
</script></body></html>
"""

    private const val TODO_HTML = """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{font-family:sans-serif;background:#F6F7FB;margin:0;padding:16px}
h1{color:#3B4CFF}
#input{width:70%;padding:10px;border-radius:8px;border:1px solid #ccc}
#add{padding:10px 16px;border-radius:8px;border:none;background:#10E0A0;color:#000}
li{display:flex;justify-content:space-between;align-items:center;background:#fff;margin:6px 0;padding:10px;border-radius:8px}
li.done span{text-decoration:line-through;color:#888}
ul{list-style:none;padding:0}
button.del{background:#FF5C6C;color:#fff;border:none;border-radius:6px;padding:6px 10px}
</style></head>
<body>
<h1>Todo</h1>
<input id="input" placeholder="New task"><button id="add">Add</button>
<ul id="list"></ul>
<script>
const list = document.getElementById('list');
document.getElementById('add').onclick = () => {
  const val = document.getElementById('input').value.trim();
  if (!val) return;
  const li = document.createElement('li');
  const span = document.createElement('span');
  span.textContent = val;
  span.onclick = () => li.classList.toggle('done');
  const del = document.createElement('button');
  del.className = 'del'; del.textContent = 'X';
  del.onclick = () => li.remove();
  li.appendChild(span); li.appendChild(del);
  list.appendChild(li);
  document.getElementById('input').value = '';
};
</script></body></html>
"""

    private const val FLAPPY_HTML = """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{margin:0;background:#22D3EE;overflow:hidden}canvas{display:block;margin:0 auto;background:#0B1023}</style>
</head><body>
<canvas id="c" width="360" height="640"></canvas>
<script>
const ctx = document.getElementById('c').getContext('2d');
let birdY = 300, velocity = 0, pipes = [], score = 0, running = true;
function reset(){ birdY=300; velocity=0; pipes=[{x:400,gap:220}]; score=0; running=true; }
reset();
document.addEventListener('touchstart', flap);
document.addEventListener('mousedown', flap);
function flap(){ if(!running){ reset(); return; } velocity = -7; }
function loop(){
  if (running) {
    velocity += 0.45; birdY += velocity;
    pipes.forEach(p => p.x -= 3);
    if (pipes[pipes.length-1].x < 200) pipes.push({x:400, gap: 150 + Math.random()*250});
    pipes = pipes.filter(p => p.x > -60);
    pipes.forEach(p => { if (p.x > 40 && p.x < 80 && !p.scored) { score++; p.scored = true; } });
    if (birdY > 640 || birdY < 0) running = false;
    pipes.forEach(p => {
      if (p.x < 60 && p.x > 20 && (birdY < p.gap - 90 || birdY > p.gap + 90)) running = false;
    });
  }
  ctx.fillStyle = '#0B1023'; ctx.fillRect(0,0,360,640);
  ctx.fillStyle = '#10E0A0';
  pipes.forEach(p => {
    ctx.fillRect(p.x, 0, 40, p.gap - 90);
    ctx.fillRect(p.x, p.gap + 90, 40, 640 - (p.gap+90));
  });
  ctx.fillStyle = '#F6B93B'; ctx.beginPath(); ctx.arc(50, birdY, 14, 0, 7); ctx.fill();
  ctx.fillStyle = '#fff'; ctx.font = '24px sans-serif'; ctx.fillText('Score: '+score, 12, 30);
  if (!running) { ctx.fillText('Tap to restart', 100, 320); }
  requestAnimationFrame(loop);
}
loop();
</script></body></html>
"""

    private const val CANVAS_HTML = """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{margin:0;background:#F6F7FB}canvas{touch-action:none;display:block}
#bar{position:fixed;bottom:0;left:0;right:0;display:flex;gap:8px;padding:8px;background:#141B34}
button{flex:1;padding:10px;border-radius:8px;border:none}
</style></head>
<body>
<canvas id="c"></canvas>
<div id="bar">
  <button style="background:#FF5C6C" onclick="color='#FF5C6C'">Red</button>
  <button style="background:#10E0A0" onclick="color='#10E0A0'">Green</button>
  <button style="background:#22D3EE" onclick="color='#22D3EE'">Cyan</button>
  <button onclick="ctx.clearRect(0,0,canvas.width,canvas.height)">Clear</button>
</div>
<script>
const canvas = document.getElementById('c');
canvas.width = window.innerWidth; canvas.height = window.innerHeight - 60;
const ctx = canvas.getContext('2d');
let drawing = false, color = '#FF5C6C';
function pos(e){ const t = e.touches ? e.touches[0] : e; const r = canvas.getBoundingClientRect(); return {x:t.clientX-r.left, y:t.clientY-r.top}; }
function start(e){ drawing = true; const p = pos(e); ctx.beginPath(); ctx.moveTo(p.x,p.y); }
function move(e){ if(!drawing) return; const p = pos(e); ctx.lineWidth=6; ctx.lineCap='round'; ctx.strokeStyle=color; ctx.lineTo(p.x,p.y); ctx.stroke(); e.preventDefault(); }
function end(){ drawing = false; }
canvas.addEventListener('mousedown', start); canvas.addEventListener('mousemove', move); canvas.addEventListener('mouseup', end);
canvas.addEventListener('touchstart', start); canvas.addEventListener('touchmove', move); canvas.addEventListener('touchend', end);
</script></body></html>
"""
}
