(function(){
  var CURRENT_MODE = 'json'; // 'json' | 'xml' | 'protobuf'
  function getMode(){
    var sel = document.getElementById('format-select');
    var val = sel && sel.value ? sel.value.toLowerCase() : CURRENT_MODE;
    if (val !== 'json' && val !== 'xml' && val !== 'protobuf') val = 'json';
    CURRENT_MODE = val;
    return val;
  }
  function setMode(mode){
    CURRENT_MODE = mode;
    var sel = document.getElementById('format-select');
    if (sel) sel.value = mode;
  }
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

  // ===== XML helpers =====
  function xmlParserResult(xmlText){
    try {
      var parser = new DOMParser();
      var doc = parser.parseFromString(xmlText, 'application/xml');
      var errors = doc.getElementsByTagName('parsererror');
      if (errors && errors.length){
        var msg = errors[0].textContent || 'XML parse error';
        // Try to extract line/column from browser-specific parsererror text
        var line = null, col = null;
        var m;
        // Common patterns from different engines
        m = msg.match(/error on line\s+(\d+)\s+at column\s+(\d+)/i);
        if (m){ line = parseInt(m[1],10); col = parseInt(m[2],10); }
        if (line == null){ m = msg.match(/line\s+(\d+)/i); if (m) line = parseInt(m[1],10); }
        if (col == null){ m = msg.match(/column\s+(\d+)/i); if (m) col = parseInt(m[1],10); }
        if (line == null){ m = msg.match(/Line Number\s*:?\s*(\d+)/i); if (m) line = parseInt(m[1],10); }
        if (col == null){ m = msg.match(/Column Number\s*:?\s*(\d+)/i); if (m) col = parseInt(m[1],10); }
        return { ok:false, error: msg, line: line || undefined, column: col || undefined };
      }
      return { ok:true, doc: doc };
    } catch(e){
      return { ok:false, error: e.message || 'XML parse error' };
    }
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
    return s.replace(/[\{,]\s*([A-Za-z_][A-Za-z0-9_\-]*)\s*:/g, function(m, key){ return m.replace(key, '"'+key+'"'); });
  }
  function replacePythonLiterals(s){
    return s
      .replace(/\bTrue\b/g, 'true')
      .replace(/\bFalse\b/g, 'false')
      .replace(/\bNone\b/g, 'null');
  }
  function removeControlChars(s){
    return s.replace(/[\u0000-\u001F\u007F]/g, function(ch){ return (ch==='\n'||ch==='\r'||ch==='\t') ? ch : ''; });
  }
  function insertMissingCommas(s){
    // Insert commas between common adjacent tokens like "}""{" or "]""{" or '"' '"'
    var out = s;
    // Between closing token and next opening quote/brace/bracket
    out = out.replace(/([}\]"\d])\s*(?=[{\["])/g, '$1,');
    // Between literals and opening
    out = out.replace(/\b(true|false|null)\b\s*(?=[{\["])/g, '$1,');
    // Between closing quote and next identifier of a key (rare in JSON5-like inputs)
    return out;
  }
  function balanceBrackets(s){
    var curL = (s.match(/\{/g)||[]).length, curR = (s.match(/\}/g)||[]).length;
    var braL = (s.match(/\[/g)||[]).length, braR = (s.match(/\]/g)||[]).length;
    if (curL > curR && curL - curR <= 2){ s += '}'.repeat(curL - curR); }
    if (braL > braR && braL - braR <= 2){ s += ']'.repeat(braL - braR); }
    return s;
  }
  function tryParsePretty(s){
    try{ var obj = JSON.parse(s); return { ok:true, pretty: JSON.stringify(obj, null, 2) }; }catch(e){ return { ok:false, error: e && e.message, fixed: s }; }
  }
  function attemptAutoFix(text){
    var fixed = (text||'').replace(/^\uFEFF/, '');
    fixed = normalizeQuotes(fixed);
    fixed = stripComments(fixed);
    fixed = fixSingleQuotedStrings(fixed);
    fixed = replacePythonLiterals(fixed);
    fixed = removeTrailingCommas(fixed);
    fixed = quoteUnquotedKeys(fixed);
    fixed = insertMissingCommas(fixed);
    fixed = removeControlChars(fixed);
    fixed = balanceBrackets(fixed);
    var parsed = tryParsePretty(fixed);
    if (parsed.ok) return parsed;
    // Second pass: try after stripping trailing commas again
    try {
      var again = removeTrailingCommas(fixed);
      var p2 = tryParsePretty(again);
      if (p2.ok) return p2;
    } catch(_){}
    return { ok:false, error: parsed.error || 'Invalid JSON', fixed: fixed };
  }
  function autoFixNow(){
    if (getMode() !== 'json'){ var err = byId('error-box'); if (err){ err.textContent = 'Auto Fix is available only in JSON mode.'; err.style.display='block'; } return; }
    var inp = byId('json-input'); if (!inp) return;
    var text = inp.value || '';
    if (!text.trim()){ formatNow(); return; }
    var res = attemptAutoFix(text);
    if (res.ok){
      inp.value = res.pretty;
      formatNow();
    } else {
      inp.value = res.fixed || text;
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
    var mode = getMode();
    var text = (inp.value||'').trim(); if (!text) { out.innerHTML = ''; return; }
    if (mode === 'xml'){
      var res = xmlMinify(text);
      if (res.ok){
        out.textContent = res.min;
        byId('line-gutter').innerHTML = buildGutter(computeLines(res.min), -1);
        byId('error-box').style.display='none';
      } else {
        // show error via formatter
        formatNow();
      }
      return;
    }
    if (mode === 'protobuf'){
      var t = (out.innerText||text).trim();
      var compact = t.replace(/\s+/g, ' ').trim();
      out.textContent = compact;
      byId('line-gutter').innerHTML = buildGutter(computeLines(compact), -1);
      byId('error-box').style.display='none';
      return;
    }
    // JSON
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
    if (getMode() !== 'json'){ formatNow(); return; }
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

  function updateUiForMode(){
    var mode = getMode();
    var idsJsonOnly = ['btn-autofix','btn-sort','btn-create-mock','btn-unescape'];
    idsJsonOnly.forEach(function(id){ var el = byId(id); if (el){ el.disabled = (mode !== 'json'); }});
    if (mode !== 'json'){ hideMockBox(); }
  }
  function updatePlaceholderForMode(){
    var inp = byId('json-input'); if (!inp) return;
    var mode = getMode();
    if (mode === 'xml'){
      inp.placeholder = '<person>\n  <name>Ada</name>\n  <skills>\n    <skill lang="Kotlin" exp="5"/>\n    <skill lang="JS" exp="7"/>\n  </skills>\n  <active>true</active>\n  <score>99.5</score>\n</person>';
    } else if (mode === 'protobuf'){
      inp.placeholder = 'Paste Base64-encoded Protobuf bytes here (e.g., CgNBZGE=). We will show a hex dump.';
    } else {
      inp.placeholder = '{\n  "hello":"world",\n  "n":123,\n  "ok":true\n}';
    }
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
    var sel = byId('format-select');

    if (inp){ inp.addEventListener('input', function(){ formatNow(); }); }
    if (btnFmt){ btnFmt.addEventListener('click', function(){ formatNow(true); }); }
    if (btnFix){ btnFix.addEventListener('click', autoFixNow); }
    if (btnUnesc){ btnUnesc.addEventListener('click', unescapeNow); }
    if (btnClr){ btnClr.addEventListener('click', function(){ byId('json-input').value=''; updatePlaceholderForMode(); formatNow(); }); }
    if (btnCpy){ btnCpy.addEventListener('click', copyOutput); }
    if (btnMin){ btnMin.addEventListener('click', minifyNow); }
    if (btnSort){ btnSort.addEventListener('click', sortKeysNow); }
    if (btnDwn){ btnDwn.addEventListener('click', downloadOutput); }
    if (btnSmp){ btnSmp.addEventListener('click', insertSample); }
    if (btnCreateMock){ btnCreateMock.addEventListener('click', createMock); }
    if (btnMockCopy){ btnMockCopy.addEventListener('click', copyMockUrl); }
    if (btnMockReset){ btnMockReset.addEventListener('click', resetMock); }
    if (sel){
      sel.addEventListener('change', function(){
        setMode(sel.value.toLowerCase());
        updateUiForMode();
        updatePlaceholderForMode();
        formatNow();
      });
      setMode(sel.value.toLowerCase());
    }
    updateUiForMode();
    updatePlaceholderForMode();
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
