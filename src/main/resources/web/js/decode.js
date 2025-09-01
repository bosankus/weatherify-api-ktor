(function(){
  function byId(id){ return document.getElementById(id); }
  function escapeHtml(s){
    return s.replace(/[&<>"]/g, function(c){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]); });
  }
  function syntaxHighlight(jsonStr){
    // naive tokenizer for pretty JSON text (already pretty-printed)
    return jsonStr
      .replace(/(&)/g,'&amp;')
      .replace(/</g,'&lt;')
      .replace(/>/g,'&gt;')
      .replace(/(".*?\")(?=\s*:)/g, '<span class="token-key">$1</span>')
      .replace(/(:\s*)"(.*?)"/g, '$1<span class="token-string">"$2"</span>')
      .replace(/(:\s*)(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g, '$1<span class="token-number">$2</span>')
      .replace(/(:\s*)(true|false)/g, '$1<span class="token-boolean">$2</span>')
      .replace(/(:\s*)(null)/g, '$1<span class="token-null">$2</span>');
  }
  function buildGutter(lines, errorLine){
    var out = '';
    for(var i=1;i<=lines;i++){
      if (i === errorLine) out += '<div class="error">'+i+'</div>'; else out += '<div>'+i+'</div>';
    }
    return out;
  }
  function toPretty(input){
    return JSON.stringify(JSON.parse(input), null, 2);
  }
  function computeLines(s){ return s.split(/\n/).length; }

  function formatNow(){
    var inp = byId('json-input');
    var out = byId('code-output');
    var gut = byId('line-gutter');
    var err = byId('error-box');
    if (!inp || !out || !gut) return;
    var text = inp.value.trim();
    if (!text){ out.innerHTML=''; gut.innerHTML=''; err.style.display='none'; return; }

    try {
      // Prefer jsonlint for better errors & tolerance
      if (window.jsonlint){
        var ast = window.jsonlint.parse(text); // throws with line/column
      } else {
        JSON.parse(text);
      }
      var pretty = toPretty(text);
      var highlighted = syntaxHighlight(pretty);
      var lineCount = computeLines(pretty);
      out.innerHTML = highlighted;
      gut.innerHTML = buildGutter(lineCount, -1);
      err.style.display='none';
      // remove any error line class
      out.innerHTML = highlighted;
    } catch(e){
      var message = (e && e.message) ? e.message : 'Invalid JSON';
      var m = message.match(/line\s+(\d+)/i);
      var line = m ? parseInt(m[1],10) : null;
      // Fallback: try to extract position and map to line
      if (!line){
        var m2 = message.match(/position\s*(\d+)/i) || message.match(/at\s*position\s*(\d+)/i);
        if (m2){
          var pos = parseInt(m2[1],10);
          var upTo = text.slice(0, pos);
          line = computeLines(upTo);
        }
      }
      // build gutter with error
      var lineCount2 = computeLines(text);
      gut.innerHTML = buildGutter(lineCount2, line||-1);

      // show raw text with an error line highlight if we can
      var lines = text.split(/\n/);
      var htmlLines = lines.map(function(l, idx){
        var safe = escapeHtml(l);
        if (line && idx+1===line) return '<div class="error-line">'+safe+'</div>';
        return '<div>'+safe+'</div>';
      }).join('');
      out.innerHTML = htmlLines;
      err.textContent = message;
      err.style.display='block';
    }
  }

  function copyOutput(){
    var code = byId('code-output');
    if (!code) return;
    var tmp = document.createElement('textarea');
    tmp.value = code.innerText;
    document.body.appendChild(tmp);
    tmp.select();
    try{ document.execCommand('copy'); } finally { document.body.removeChild(tmp); }
  }
  function downloadOutput(){
    var code = byId('code-output'); if (!code) return;
    var blob = new Blob([code.innerText||''], {type:'application/json'});
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'decoded.json';
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    setTimeout(function(){ URL.revokeObjectURL(a.href); }, 1000);
  }
  function insertSample(){
    var inp = byId('json-input'); if (!inp) return;
    inp.value = '{"name":"Ada","skills":[{"lang":"Kotlin","exp":5},{"lang":"JS","exp":7}],"active":true,"score":99.5,"meta":null}';
    formatNow();
  }

  function stripComments(s){
    // Remove // and /* */ comments (naive, not string-aware)
    return s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*(?=\n|\r|$)/g, '$1');
  }
  function normalizeQuotes(s){
    // Convert smart quotes to straight and backticks to double quotes
    return s
      .replace(/[\u2018\u2019\u201A\u201B\u2032\u2035]/g, "'")
      .replace(/[\u201C\u201D\u201E\u201F\u2033\u2036]/g, '"')
      .replace(/`([^`\\]*(?:\\.[^`\\]*)*)`/g, '"$1"');
  }
  function fixSingleQuotedStrings(s){
    // Replace single-quoted string literals with double-quoted
    return s.replace(/'([^'\\]*(?:\\.[^'\\]*)*)'/g, function(_, inner){
      var v = inner.replace(/\\'/g, "'").replace(/"/g, '\\"');
      return '"' + v + '"';
    });
  }
  function removeTrailingCommas(s){
    return s.replace(/,(?=\s*[}\]])/g, '');
  }
  function quoteUnquotedKeys(s){
    // Quote keys like { key: 1 } => { "key": 1 }
    return s.replace(/([\{,]\s*)([A-Za-z_][A-Za-z0-9_\-]*)(\s*:)/g, '$1"$2"$3');
  }
  function autoFixNow(){
    var inp = byId('json-input'); if (!inp) return;
    var text = inp.value || '';
    if (!text.trim()){ formatNow(); return; }
    try {
      var fixed = text.replace(/^\uFEFF/, ''); // strip BOM
      fixed = normalizeQuotes(fixed);
      fixed = stripComments(fixed);
      fixed = fixSingleQuotedStrings(fixed);
      fixed = removeTrailingCommas(fixed);
      fixed = quoteUnquotedKeys(fixed);
      // Try parse; if ok, pretty print
      var obj = JSON.parse(fixed);
      inp.value = JSON.stringify(obj, null, 2);
      formatNow();
    } catch(e){
      // If still failing, keep best-effort fixed text and trigger formatter for error display
      inp.value = fixed;
      formatNow();
    }
  }
  function unescapeNow(){
    var inp = byId('json-input'); if (!inp) return;
    var t = (inp.value || '').trim();
    if (!t){ formatNow(); return; }
    var candidate = t;
    try {
      // If it looks like a quoted string literal, normalize to double quotes and parse as JSON string
      if ((candidate.startsWith('"') && candidate.endsWith('"')) || (candidate.startsWith("'") && candidate.endsWith("'"))){
        candidate = normalizeQuotes(candidate);
        candidate = candidate.replace(/^'/, '"').replace(/'$/, '"');
        var unesc = JSON.parse(candidate);
        inp.value = unesc;
        formatNow();
        return;
      }
      // Otherwise, try to decode escape sequences by JSON-parsing a wrapped string
      var wrapped = '"' + candidate.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
      var unesc2 = JSON.parse(wrapped);
      inp.value = unesc2;
      formatNow();
    } catch(err){
      // leave content and show error via formatter
      formatNow();
    }
  }
  function minifyNow(){
    var inp = byId('json-input'); var out = byId('code-output'); if (!inp || !out) return;
    var text = inp.value.trim(); if (!text) { out.innerHTML = ''; return; }
    try {
      var obj = JSON.parse(text);
      var min = JSON.stringify(obj);
      out.textContent = min;
      byId('line-gutter').innerHTML = buildGutter(computeLines(min), -1);
      byId('error-box').style.display='none';
    } catch(e){
      // Attempt to minify from pretty output text if output contains valid JSON
      try {
        var obj2 = JSON.parse(out.innerText||'');
        var min2 = JSON.stringify(obj2);
        out.textContent = min2;
        byId('line-gutter').innerHTML = buildGutter(computeLines(min2), -1);
        byId('error-box').style.display='none';
      } catch(_e){
        // fallback to normal formatter to show error
        formatNow();
      }
    }
  }

  function deepSort(value){
    if (Array.isArray(value)) return value.map(deepSort);
    if (value && typeof value === 'object'){
      var sorted = {};
      Object.keys(value).sort().forEach(function(k){
        sorted[k] = deepSort(value[k]);
      });
      return sorted;
    }
    return value;
  }
  function sortKeysNow(){
    var inp = byId('json-input'); var out = byId('code-output'); if (!inp || !out) return;
    var source = inp.value.trim();
    var obj = null;
    try {
      if (source) obj = JSON.parse(source);
    } catch(_e) {}
    if (obj === null){
      try { obj = JSON.parse(out.innerText||''); } catch(_e){}
    }
    if (obj === null){
      formatNow();
      return;
    }
    var sorted = deepSort(obj);
    var pretty = JSON.stringify(sorted, null, 2);
    out.textContent = pretty;
    byId('line-gutter').innerHTML = buildGutter(computeLines(pretty), -1);
    byId('error-box').style.display='none';
  }

  function showMockBox(url, id){
    var box = byId('mock-api-box');
    var urlEl = byId('mock-url');
    if (!box || !urlEl) return;
    urlEl.textContent = url;
    box.setAttribute('data-mock-id', id || '');
    box.style.display = 'block';
  }
  function hideMockBox(){
    var box = byId('mock-api-box');
    var urlEl = byId('mock-url');
    if (!box || !urlEl) return;
    urlEl.textContent = '';
    box.setAttribute('data-mock-id', '');
    box.style.display = 'none';
  }
  function getJsonForMock(){
    var out = byId('code-output');
    var inp = byId('json-input');
    // Prefer output panel content if it's valid JSON
    if (out && out.innerText){
      var t = out.innerText.trim();
      if (t){
        try { JSON.parse(t); return t; } catch(e) {}
      }
    }
    if (inp && inp.value){
      var s = inp.value.trim();
      if (s){
        try { var obj = JSON.parse(s); return JSON.stringify(obj); } catch(e){}
      }
    }
    return null;
  }
  async function createMock(){
    var err = byId('error-box');
    var payload = getJsonForMock();
    if (!payload){
      if (err){ err.textContent = 'Please provide valid JSON to create a mock.'; err.style.display = 'block'; }
      return;
    }
    try {
      var res = await fetch('/mock/create', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload });
      var txt = await res.text();
      var data = {};
      try { data = JSON.parse(txt); } catch(_e) { data = { status:false, message: 'Unexpected response' }; }
      if (!res.ok || !data || data.status === false){
        var msg = (data && data.message) ? data.message : ('Failed to create mock: ' + res.status);
        if (err){ err.textContent = msg; err.style.display = 'block'; }
        return;
      }
      var url = data.data && data.data.url;
      var id = data.data && data.data.id;
      if (url && id){
        showMockBox(url, id);
        if (err) { err.style.display = 'none'; }
      }
    } catch (e){
      if (err){ err.textContent = 'Network error while creating mock'; err.style.display = 'block'; }
    }
  }
  async function resetMock(){
    var box = byId('mock-api-box'); var err = byId('error-box');
    if (!box) return;
    var id = box.getAttribute('data-mock-id');
    if (!id){ hideMockBox(); return; }
    try {
      var res = await fetch('/mock/' + encodeURIComponent(id), { method: 'DELETE' });
      if (!res.ok){
        // Even if failed, hide the box to avoid blocking the user
        console.warn('Failed to delete mock', await res.text());
      }
    } catch(e){
      console.warn('Error deleting mock', e);
    }
    hideMockBox();
    if (err) { err.style.display = 'none'; }
  }
  function copyMockUrl(){
    var urlEl = byId('mock-url'); if (!urlEl) return;
    var url = urlEl.textContent || '';
    if (!url) return;
    var tmp = document.createElement('textarea');
    tmp.value = url; document.body.appendChild(tmp); tmp.select();
    try { document.execCommand('copy'); } finally { document.body.removeChild(tmp); }
  }

  function initDecode(){
    var inp = byId('json-input');
    var btnFmt = byId('btn-format');
    var btnClr = byId('btn-clear');
    var btnCpy = byId('btn-copy');
    var btnDwn = byId('btn-download');
    var btnSmp = byId('btn-sample');
    var btnFix = byId('btn-autofix');
    var btnUnesc = byId('btn-unescape');
    var btnMin = byId('btn-minify');
    var btnSort = byId('btn-sort');
    var btnCreateMock = byId('btn-create-mock');
    var btnMockCopy = byId('btn-mock-copy');
    var btnMockReset = byId('btn-mock-reset');
    if (inp){ inp.addEventListener('input', function(){ formatNow(); }); }
    if (btnFmt){ btnFmt.addEventListener('click', function(){ formatNow(); }); }
    if (btnFix){ btnFix.addEventListener('click', autoFixNow); }
    if (btnUnesc){ btnUnesc.addEventListener('click', unescapeNow); }
    if (btnClr){ btnClr.addEventListener('click', function(){ byId('json-input').value=''; formatNow(); }); }
    if (btnCpy){ btnCpy.addEventListener('click', copyOutput); }
    if (btnMin){ btnMin.addEventListener('click', minifyNow); }
    if (btnSort){ btnSort.addEventListener('click', sortKeysNow); }
    if (btnDwn){ btnDwn.addEventListener('click', downloadOutput); }
    if (btnSmp){ btnSmp.addEventListener('click', insertSample); }
    if (btnCreateMock){ btnCreateMock.addEventListener('click', createMock); }
    if (btnMockCopy){ btnMockCopy.addEventListener('click', copyMockUrl); }
    if (btnMockReset){ btnMockReset.addEventListener('click', resetMock); }
    // initial render
    formatNow();
  }

  // Hook into global initializer if present
  if (typeof window.initializeApp === 'function'){
    var prev = window.initializeApp;
    window.initializeApp = function(){ try{ prev(); }catch(e){}; try{ initDecode(); }catch(e){ console.error(e); } };
  } else {
    document.addEventListener('DOMContentLoaded', initDecode);
  }
})();
