package co.mobilise.adda.server

/**
 * Self-contained HTML+JS served at GET /admin — the laptop big-screen console
 * (shown via the iQOO Office Kit). Connects to the /feed WebSocket and renders
 * the live question feed, the selected Q + its on-device answer, the connected
 * list, and Pause/End controls.
 */
object AdminConsole {

    val PAGE: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Adda · Console</title>
<style>
  :root{
    --bg:#0D0D0F; --surface:#1A1A20; --surface2:#15151b; --outline:#232330;
    --amber:#F5A623; --amber2:#FFC15E; --text:#FFFFFF; --muted:#9A9AA8;
    --ok:#39D98A; --err:#FF6B6B;
  }
  *{box-sizing:border-box;font-family:-apple-system,Segoe UI,Roboto,Inter,sans-serif}
  html,body{margin:0;height:100%;background:var(--bg);color:var(--text)}
  body{display:flex;flex-direction:column;height:100vh;overflow:hidden}

  header{display:flex;align-items:center;gap:16px;padding:14px 22px;border-bottom:1px solid var(--outline)}
  .logo{width:38px;height:38px;border-radius:11px;
    background:linear-gradient(135deg,var(--amber2),var(--amber));
    display:flex;align-items:center;justify-content:center;font-size:20px}
  .brand{font-size:20px;font-weight:800;letter-spacing:-.3px}
  .pill{display:inline-flex;align-items:center;gap:7px;font-size:12px;color:var(--muted);
    background:#1c1c24;border:1px solid var(--outline);border-radius:999px;padding:6px 12px}
  .dot{width:8px;height:8px;border-radius:50%;background:var(--ok)}
  .dot.off{background:var(--err)}
  .grow{flex:1}
  .kit{font-size:12px;font-weight:700;color:#1A1200;background:linear-gradient(135deg,var(--amber2),var(--amber));
    border-radius:999px;padding:7px 13px}
  button{border:none;border-radius:12px;padding:10px 16px;font-size:14px;font-weight:700;cursor:pointer}
  .btn-ghost{background:transparent;color:var(--text);border:1px solid var(--outline)}
  .btn-warn{background:#3A2E14;color:var(--amber2)}
  .btn-danger{background:#3a1a1a;color:var(--err)}
  .acts{margin-left:auto;display:inline-flex;gap:6px}
  .act{background:#23232e;color:var(--amber2);border:1px solid var(--outline);border-radius:8px;
    padding:5px 12px;font-size:12px;font-weight:700;cursor:pointer;box-shadow:none}
  .act:hover{border-color:var(--amber)}
  .toast{position:fixed;bottom:26px;left:50%;transform:translateX(-50%);background:#23232e;
    border:1px solid var(--outline);color:#fff;padding:10px 18px;border-radius:12px;font-size:14px;
    opacity:0;transition:opacity .2s;pointer-events:none;z-index:9}
  .toast.show{opacity:1}

  .banner{padding:10px 22px;font-weight:600;font-size:14px;display:none}
  .banner.show{display:block}
  .banner.paused{background:#3A2E14;color:var(--amber2)}
  .banner.ended{background:#3a1a1a;color:var(--err)}

  main{flex:1;display:grid;grid-template-columns:320px 1fr 280px;gap:0;min-height:0}
  .col{min-height:0;display:flex;flex-direction:column;overflow:hidden}
  .col.feed{border-right:1px solid var(--outline)}
  .col.right{border-left:1px solid var(--outline)}
  .col h2{font-size:13px;text-transform:uppercase;letter-spacing:1px;color:var(--muted);
    padding:16px 18px 10px;margin:0}
  .scroll{overflow-y:auto;padding:0 14px 18px}

  .qitem{background:var(--surface);border:1px solid var(--outline);border-radius:14px;
    padding:12px 14px;margin-bottom:10px;cursor:pointer;transition:border-color .15s}
  .qitem:hover{border-color:#39394a}
  .qitem.sel{border-color:var(--amber);background:#20201a}
  .qhead{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}
  .who{display:flex;align-items:center;gap:8px;font-weight:700;font-size:13px}
  .av{width:22px;height:22px;border-radius:50%;display:flex;align-items:center;justify-content:center;
    font-size:11px;font-weight:800;color:#fff}
  .time{font-size:11px;color:var(--muted)}
  .qtext{font-size:13px;color:#d6d6de;line-height:1.4;
    display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
  .badge{font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.5px;
    padding:3px 8px;border-radius:999px;margin-top:8px;display:inline-block}
  .b-queued{background:#23232e;color:var(--muted)}
  .b-answering{background:#3A2E14;color:var(--amber2)}
  .b-answered{background:#12301f;color:var(--ok)}
  .b-error{background:#3a1a1a;color:var(--err)}

  .detail{padding:22px 26px;overflow-y:auto}
  .detail .qbig{font-size:24px;font-weight:800;line-height:1.3;margin:8px 0 18px}
  .detail .meta{color:var(--muted);font-size:13px;margin-bottom:6px}
  .ansbox{background:var(--surface);border:1px solid var(--outline);border-radius:18px;padding:20px 22px}
  .anslabel{color:var(--amber2);font-weight:800;font-size:13px;margin-bottom:10px;display:flex;align-items:center;gap:8px}
  .ans{font-size:16px;line-height:1.6}
  pre{background:var(--bg);border:1px solid var(--outline);border-radius:12px;padding:14px;
    overflow-x:auto;color:var(--amber2);font-size:14px;margin:10px 0}
  .pulse{width:9px;height:9px;border-radius:50%;background:var(--amber);animation:p 1s infinite}
  @keyframes p{0%,100%{opacity:.3}50%{opacity:1}}
  .empty{color:var(--muted);text-align:center;margin-top:60px}

  .student{display:flex;align-items:center;gap:10px;padding:9px 10px;background:var(--surface);
    border:1px solid var(--outline);border-radius:12px;margin-bottom:8px}
  .student .av{width:26px;height:26px}
  .student .nm{font-size:14px;font-weight:600}
  .follow{font-size:11px;color:var(--muted);padding:0 18px 12px}
</style>
</head>
<body>
  <header>
    <div class="logo">&#128172;</div>
    <div class="brand">Adda</div>
    <span class="pill"><span id="liveDot" class="dot off"></span> <span id="liveText">Connecting&hellip;</span> &middot; on-device</span>
    <span class="grow"></span>
    <span class="kit">iQOO Office Kit</span>
    <button id="pauseBtn" class="btn-warn" onclick="togglePause()">Pause queue</button>
    <button class="btn-danger" onclick="endSession()">End session</button>
  </header>

  <div id="banner" class="banner"></div>

  <main>
    <section class="col feed">
      <h2 id="feedTitle">Questions</h2>
      <div id="feed" class="scroll"></div>
    </section>

    <section class="col">
      <div id="detail" class="detail"></div>
    </section>

    <section class="col right">
      <h2 id="connTitle">Connected</h2>
      <div id="students" class="scroll"></div>
    </section>
  </main>

  <div id="toast" class="toast"></div>

<script>
  var snap = { items:[], students:[], connected:0, paused:false, ended:false };
  var selectedId = null;
  var pinned = false;

  function el(id){ return document.getElementById(id); }
  function esc(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function fmtTime(ms){ try { return new Date(ms).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}); } catch(e){ return ''; } }
  function colorFor(name){
    var pal = ['#6C8CFF','#39D98A','#FF8FB1','#FFC15E','#8E7CFF','#4EC6E0'];
    var h = 0; for (var i=0;i<name.length;i++){ h = (h*31 + name.charCodeAt(i)) & 0x7fffffff; }
    return pal[h % pal.length];
  }
  function avatar(name, cls){
    var n = (name||'?').trim();
    return '<span class="av '+(cls||'')+'" style="background:'+colorFor(n)+'">'+esc((n[0]||'?').toUpperCase())+'</span>';
  }
  function renderAnswer(text){
    var parts = String(text||'').split('```');
    var html = '';
    for (var i=0;i<parts.length;i++){
      if (i%2===1){ html += '<pre>'+esc(parts[i].replace(/^[a-zA-Z0-9+#]+\n/, ''))+'</pre>'; }
      else { html += esc(parts[i]).replace(/\n/g,'<br>'); }
    }
    return html;
  }

  function setConn(ok){
    el('liveDot').className = 'dot' + (ok ? '' : ' off');
    el('liveText').textContent = ok ? 'Live' : 'Reconnecting';
  }

  function onSnap(){
    var items = snap.items || [];
    if (!pinned){
      var last = items[items.length-1];
      selectedId = last ? last.id : null;
    } else if (!items.some(function(i){ return i.id === selectedId; })){
      pinned = false;
      var l = items[items.length-1];
      selectedId = l ? l.id : null;
    }
    render();
  }

  function selectItem(id){ selectedId = id; pinned = true; render(); }
  function followLatest(){ pinned = false; onSnap(); }

  function render(){
    // banner
    var b = el('banner');
    if (snap.ended){ b.className = 'banner ended show'; b.textContent = 'Session ended'; }
    else if (snap.paused){ b.className = 'banner paused show'; b.textContent = 'Queue paused — naye sawaal hold pe hain'; }
    else { b.className = 'banner'; b.textContent = ''; }
    el('pauseBtn').textContent = snap.paused ? 'Resume queue' : 'Pause queue';

    var items = snap.items || [];
    el('feedTitle').textContent = 'Questions (' + items.length + ')';
    el('connTitle').textContent = 'Connected (' + (snap.connected||0) + ')';

    // feed (latest first)
    var f = '';
    for (var i=items.length-1; i>=0; i--){
      var it = items[i];
      var sel = it.id === selectedId ? ' sel' : '';
      f += '<div class="qitem'+sel+'" onclick="selectItem('+it.id+')">'
        + '<div class="qhead"><span class="who">'+avatar(it.asker)+esc(it.asker)+'</span>'
        + '<span class="time">'+fmtTime(it.createdAt)+'</span></div>'
        + '<div class="qtext">'+esc(it.question)+'</div>'
        + '<span class="badge b-'+String(it.status).toLowerCase()+'">'+esc(it.status)+'</span>'
        + '</div>';
    }
    el('feed').innerHTML = f || '<div class="empty">Abhi koi sawaal nahi&hellip;</div>';

    // detail
    var cur = null;
    for (var j=0;j<items.length;j++){ if (items[j].id===selectedId){ cur = items[j]; break; } }
    if (cur){
      var answering = String(cur.status).toLowerCase() === 'answering';
      var followNote = pinned ? '<span class="follow" style="cursor:pointer;color:var(--amber2)" onclick="followLatest()">&#8635; Follow latest</span>' : '';
      var acts = (cur.answer && !answering)
        ? '<span class="acts"><button class="act" onclick="copyAns()">Copy</button>'
          + '<button class="act" onclick="shareAns()">Share</button></span>'
        : '';
      el('detail').innerHTML =
        '<div class="meta">'+esc(cur.asker)+' &middot; '+fmtTime(cur.createdAt)+followNote+'</div>'
        + '<div class="qbig">'+esc(cur.question)+'</div>'
        + '<div class="ansbox"><div class="anslabel">'+(answering?'<span class="pulse"></span>':'')+'Adda&rsquo;s answer &middot; on-device'+acts+'</div>'
        + '<div class="ans">'+(cur.answer ? renderAnswer(cur.answer) : '<span style="color:var(--muted)">Soch raha hoon&hellip;</span>')+'</div></div>';
    } else {
      el('detail').innerHTML = '<div class="empty">Koi sawaal select karo &mdash; ya naye sawaal ka wait karo.</div>';
    }

    // connected list
    var s = '';
    var st = snap.students || [];
    for (var k=0;k<st.length;k++){ s += '<div class="student">'+avatar(st[k])+'<span class="nm">'+esc(st[k])+'</span></div>'; }
    el('students').innerHTML = s || '<div class="empty">Koi connected nahi</div>';
  }

  function togglePause(){ control(snap.paused ? 'resume' : 'pause'); }
  function endSession(){ if (confirm('Session end karein? Naye sawaal band ho jaayenge.')) control('end'); }
  function control(action){
    fetch('/control', {method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({action:action})}).catch(function(){});
  }

  function currentItem(){
    var items = snap.items || [];
    for (var j=0;j<items.length;j++){ if (items[j].id === selectedId) return items[j]; }
    return null;
  }
  function flash(msg){
    var t = el('toast'); t.textContent = msg; t.className = 'toast show';
    setTimeout(function(){ t.className = 'toast'; }, 1500);
  }
  function copyText(t){
    // navigator.clipboard needs a secure context; LAN http falls back to execCommand.
    if (navigator.clipboard && navigator.clipboard.writeText){
      navigator.clipboard.writeText(t).then(function(){ flash('Copied'); }, function(){ fallbackCopy(t); });
    } else { fallbackCopy(t); }
  }
  function fallbackCopy(t){
    var ta = document.createElement('textarea');
    ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.focus(); ta.select();
    try { document.execCommand('copy'); flash('Copied'); } catch(e){ flash('Copy failed'); }
    document.body.removeChild(ta);
  }
  function copyAns(){ var c = currentItem(); if (c) copyText(c.answer || ''); }
  function shareAns(){
    var c = currentItem(); if (!c) return;
    var t = 'Q: ' + (c.question||'') + '\n\nA: ' + (c.answer||'') + '\n\n— via Adda (offline, on-device AI)';
    if (navigator.share){ navigator.share({text:t}).catch(function(){}); }
    else { copyText(t); }
  }

  function wsConnect(){
    var proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    var ws = new WebSocket(proto + location.host + '/feed');
    ws.onopen = function(){ setConn(true); };
    ws.onclose = function(){ setConn(false); setTimeout(wsConnect, 1500); };
    ws.onerror = function(){ try { ws.close(); } catch(e){} };
    ws.onmessage = function(ev){ try { snap = JSON.parse(ev.data); onSnap(); } catch(e){} };
  }

  render();
  wsConnect();
</script>
</body>
</html>
""".trimIndent()
}
