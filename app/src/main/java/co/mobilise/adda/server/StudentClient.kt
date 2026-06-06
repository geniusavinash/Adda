package co.mobilise.adda.server

/** Self-contained HTML+JS served at GET / — the student's join-and-ask page. */
object StudentClient {

    val PAGE: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Adda</title>
<style>
  :root{
    --bg:#0D0D0F; --surface:#1A1A20; --outline:#232330;
    --amber:#F5A623; --amber2:#FFC15E; --text:#FFFFFF; --muted:#9A9AA8;
    --ok:#39D98A; --err:#FF6B6B;
  }
  *{box-sizing:border-box;font-family:-apple-system,Segoe UI,Roboto,Inter,sans-serif}
  body{margin:0;background:var(--bg);color:var(--text);min-height:100vh}
  .wrap{max-width:560px;margin:0 auto;padding:20px 16px 40px}
  header{display:flex;align-items:center;gap:12px;margin:8px 0 18px}
  .logo{width:42px;height:42px;border-radius:12px;
    background:linear-gradient(135deg,var(--amber2),var(--amber));
    display:flex;align-items:center;justify-content:center;font-size:22px}
  h1{font-size:22px;margin:0;font-weight:800}
  .pill{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:var(--muted);
    background:#1c1c24;border:1px solid var(--outline);border-radius:999px;padding:5px 10px}
  .dot{width:7px;height:7px;border-radius:50%;background:var(--ok)}
  label{display:block;font-size:13px;color:var(--muted);margin:14px 0 6px}
  input,textarea{width:100%;background:var(--surface);border:1px solid var(--outline);
    color:var(--text);border-radius:14px;padding:13px 14px;font-size:16px;outline:none}
  input:focus,textarea:focus{border-color:var(--amber)}
  textarea{min-height:96px;resize:vertical}
  button{width:100%;margin-top:16px;background:var(--amber);color:#1A1200;border:none;
    border-radius:16px;padding:15px;font-size:16px;font-weight:700;
    box-shadow:0 6px 20px rgba(245,166,35,.28);cursor:pointer}
  button:disabled{opacity:.55;box-shadow:none;cursor:default}
  .ghost{background:transparent;color:var(--muted);border:1px solid var(--outline);
    box-shadow:none;margin-top:10px;font-weight:600}
  .answer{margin-top:22px;background:var(--surface);border:1px solid var(--outline);
    border-radius:18px;padding:16px 16px;line-height:1.5;white-space:normal}
  .answer.hidden{display:none}
  .answer .who{font-size:13px;color:var(--amber2);font-weight:700;margin-bottom:8px}
  pre{background:var(--bg);border:1px solid var(--outline);border-radius:12px;
    padding:12px;overflow-x:auto;color:var(--amber2);font-size:13px;margin:8px 0}
  .err{color:var(--err)}
</style>
</head>
<body>
  <div class="wrap">
    <header>
      <div class="logo">&#128172;</div>
      <div>
        <h1>Adda</h1>
        <span class="pill"><span class="dot"></span> Offline &middot; on-device AI</span>
      </div>
    </header>

    <label for="name">Aapka naam</label>
    <input id="name" placeholder="e.g. Aman" autocomplete="off">

    <label for="q">Apna doubt</label>
    <textarea id="q" placeholder="Yahan likho&hellip; (Hindi/English dono chalega)"></textarea>

    <button id="ask">Poochho</button>
    <button id="newchat" class="ghost">&#8635; Naya chat</button>

    <div id="answer" class="answer hidden"></div>
  </div>

<script>
  function el(id){ return document.getElementById(id); }
  function esc(s){ return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function render(text){
    var parts = String(text).split('```');
    var html = '';
    for (var i=0;i<parts.length;i++){
      if (i % 2 === 1){ html += '<pre>' + esc(parts[i].replace(/^[a-zA-Z0-9]+\n/, '')) + '</pre>'; }
      else { html += esc(parts[i]).replace(/\n/g,'<br>'); }
    }
    return html;
  }

  var savedName = localStorage.getItem('adda_name') || '';
  el('name').value = savedName;
  el('name').addEventListener('change', function(){
    localStorage.setItem('adda_name', el('name').value.trim());
  });

  function heartbeat(){
    var n = el('name').value.trim() || 'Student';
    fetch('/ping', {method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({name:n})}).catch(function(){});
  }
  heartbeat();
  setInterval(heartbeat, 5000);

  el('ask').onclick = async function(){
    var q = el('q').value.trim();
    if (!q) return;
    var student = el('name').value.trim() || 'Student';
    localStorage.setItem('adda_name', student);

    var a = el('answer');
    a.classList.remove('hidden');
    a.innerHTML = '<div class="who">Adda</div>Soch raha hoon&hellip;';
    el('ask').disabled = true;

    try {
      var r = await fetch('/ask', {method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({student:student, question:q})});
      var data = await r.json();
      if (data && data.answer){
        a.innerHTML = '<div class="who">Adda</div>' + render(data.answer);
      } else {
        a.innerHTML = '<div class="who err">Adda</div><span class="err">Jawab nahi mila</span>';
      }
    } catch(e){
      a.innerHTML = '<div class="who err">Adda</div><span class="err">Server se connect nahi hua</span>';
    } finally {
      el('ask').disabled = false;
    }
  };

  el('newchat').onclick = function(){
    var student = el('name').value.trim() || 'Student';
    fetch('/reset', {method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({student:student})}).catch(function(){});
    el('q').value = '';
    var a = el('answer'); a.classList.add('hidden'); a.innerHTML = '';
  };
</script>
</body>
</html>
""".trimIndent()
}
