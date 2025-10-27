(function(){
  var CURRENT_MODE = 'json'; // 'json' | 'xml' | 'protobuf'
  
  // Format configuration system
  var FORMAT_CONFIGS = {
    json: {
      type: 'json',
      placeholder: '{\n  "hello": "world",\n  "n": 123,\n  "ok": true\n}',
      fileExtension: 'json',
      mimeType: 'application/json',
      supportsAutoFix: true,
      supportsSortKeys: true,
      supportsUnescape: true,
      supportsMockAPI: true,
      supportsMinify: true
    },
    xml: {
      type: 'xml',
      placeholder: '<person>\n  <name>Ada</name>\n  <skills>\n    <skill lang="Kotlin" exp="5"/>\n    <skill lang="JS" exp="7"/>\n  </skills>\n  <active>true</active>\n  <score>99.5</score>\n</person>',
      fileExtension: 'xml',
      mimeType: 'application/xml',
      supportsAutoFix: false,
      supportsSortKeys: false,
      supportsUnescape: false,
      supportsMockAPI: false,
      supportsMinify: true
    },
    protobuf: {
      type: 'protobuf',
      placeholder: 'Paste Base64-encoded Protobuf bytes here (e.g., CgNBZGE=). We will show a hex dump.',
      fileExtension: 'bin',
      mimeType: 'application/octet-stream',
      supportsAutoFix: false,
      supportsSortKeys: false,
      supportsUnescape: false,
      supportsMockAPI: false,
      supportsMinify: false
    }
  };
  
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
  function getFormatConfig(){
    var mode = getMode();
    return FORMAT_CONFIGS[mode] || FORMAT_CONFIGS.json;
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
    if (getMode() !== 'json'){ 
      var err = byId('error-box'); 
      if (err){ 
        err.textContent = 'Auto Fix is available only in JSON mode.'; 
        err.style.display='block'; 
      } 
      return; 
    }
    var inp = byId('json-input'); if (!inp) return;
    var text = inp.value || '';
    if (!text.trim()){ formatNow(); return; }
    
    var originalLength = text.length;
    var res = attemptAutoFix(text);
    
    if (res.ok){
      inp.value = res.pretty;
      formatNow();
      var changes = Math.abs(res.pretty.length - originalLength);
      showToast('Auto-fix successful! Applied ' + changes + ' character changes.', 'success');
    } else {
      var fixedLength = (res.fixed || text).length;
      var changes = Math.abs(fixedLength - originalLength);
      inp.value = res.fixed || text;
      formatNow();
      if (changes > 0){
        showToast('Applied ' + changes + ' fixes, but JSON is still invalid. Check errors below.', 'error');
      } else {
        showToast('Could not auto-fix. Check errors below.', 'error');
      }
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
    var config = getFormatConfig();
    var btnAutofix = byId('btn-autofix');
    var btnSort = byId('btn-sort');
    var btnUnescape = byId('btn-unescape');
    var btnCreateMock = byId('btn-create-mock');
    var btnMinify = byId('btn-minify');
    
    if (btnAutofix) btnAutofix.disabled = !config.supportsAutoFix;
    if (btnSort) btnSort.disabled = !config.supportsSortKeys;
    if (btnUnescape) btnUnescape.disabled = !config.supportsUnescape;
    if (btnCreateMock) btnCreateMock.disabled = !config.supportsMockAPI;
    if (btnMinify) btnMinify.disabled = !config.supportsMinify;
    
    if (!config.supportsMockAPI){ hideMockBox(); }
  }
  function updatePlaceholderForMode(){
    var inp = byId('json-input'); if (!inp) return;
    var config = getFormatConfig();
    inp.placeholder = config.placeholder;
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
    var btn = byId('btn-create-mock');
    var payload = getJsonForMock();
    
    // Validation before creating mock
    if (!payload){
      if (err){ err.textContent = 'Please provide valid JSON to create a mock.'; err.style.display = 'block'; }
      showToast('Invalid JSON for mock API', 'error');
      return;
    }
    
    // Add loading state
    if (btn){
      btn.disabled = true;
      btn.textContent = 'Creating...';
    }
    
    try {
      var res = await fetch('/mock/create', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload });
      var txt = await res.text();
      var data = {};
      try { data = JSON.parse(txt); } catch(_e) { data = { status:false, message: 'Unexpected response' }; }
      
      if (!res.ok || !data || data.status === false){
        var msg = (data && data.message) ? data.message : ('Failed to create mock: ' + res.status);
        if (err){ err.textContent = msg; err.style.display = 'block'; }
        showToast('Failed to create mock API', 'error');
        return;
      }
      
      var url = data.data && data.data.url;
      var id = data.data && data.data.id;
      if (url && id){
        showMockBox(url, id);
        if (err) { err.style.display = 'none'; }
        showToast('Mock API created successfully!', 'success');
      }
    } catch (e){
      if (err){ err.textContent = 'Network error while creating mock'; err.style.display = 'block'; }
      showToast('Network error while creating mock', 'error');
    } finally {
      // Remove loading state
      if (btn){
        btn.disabled = false;
        btn.textContent = 'Create Mock API';
      }
    }
  }
  async function resetMock(){
    var box = byId('mock-api-box'); 
    var err = byId('error-box');
    var btn = byId('btn-mock-reset');
    
    if (!box) return;
    var id = box.getAttribute('data-mock-id');
    if (!id){ hideMockBox(); return; }
    
    // Add loading state
    if (btn){
      btn.disabled = true;
      btn.textContent = 'Deleting...';
    }
    
    try {
      var res = await fetch('/mock/' + encodeURIComponent(id), { method: 'DELETE' });
      if (!res.ok){
        // Even if failed, hide the box to avoid blocking the user
        console.warn('Failed to delete mock', await res.text());
        showToast('Failed to delete mock, but hiding it anyway', 'error');
      } else {
        showToast('Mock API deleted successfully', 'success');
      }
    } catch(e){
      console.warn('Error deleting mock', e);
      showToast('Error deleting mock', 'error');
    } finally {
      hideMockBox();
      if (err) { err.style.display = 'none'; }
      if (btn){
        btn.disabled = false;
        btn.textContent = 'Reset';
      }
    }
  }
  function copyMockUrl(){
    var urlEl = byId('mock-url'); if (!urlEl) return;
    var url = urlEl.textContent || '';
    if (!url) return;
    var tmp = document.createElement('textarea');
    tmp.value = url; document.body.appendChild(tmp); tmp.select();
    try { 
      document.execCommand('copy');
      showToast('URL copied to clipboard!', 'success');
    } finally { document.body.removeChild(tmp); }
  }

  // Debounce helper for real-time formatting
  var debounceTimer = null;
  function debounce(func, delay){
    return function(){
      var context = this, args = arguments;
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(function(){ func.apply(context, args); }, delay);
    };
  }

  // Toast notification system
  function showToast(message, type){
    var toast = document.getElementById('toast-notification');
    if (!toast){
      toast = document.createElement('div');
      toast.id = 'toast-notification';
      toast.style.cssText = 'position:fixed;top:80px;right:20px;padding:12px 20px;border-radius:8px;font-weight:500;z-index:10000;opacity:0;box-shadow:0 4px 12px rgba(0,0,0,0.15);pointer-events:none;';
      document.body.appendChild(toast);
    }
    
    toast.textContent = message;
    if (type === 'success'){
      toast.style.background = 'rgba(16, 185, 129, 0.95)';
      toast.style.color = '#fff';
    } else if (type === 'error'){
      toast.style.background = 'rgba(239, 68, 68, 0.95)';
      toast.style.color = '#fff';
    } else {
      toast.style.background = 'rgba(59, 130, 246, 0.95)';
      toast.style.color = '#fff';
    }
    
    // Trigger animation
    toast.style.animation = 'fadeInSlide 0.3s ease forwards';
    toast.style.opacity = '1';
    
    setTimeout(function(){
      toast.style.animation = 'fadeOut 0.3s ease forwards';
      setTimeout(function(){
        toast.style.opacity = '0';
      }, 300);
    }, 3000);
  }

  // XML minify helper
  function xmlMinify(xmlText){
    var res = xmlParserResult(xmlText);
    if (!res.ok) return res;
    try {
      var serializer = new XMLSerializer();
      var minified = serializer.serializeToString(res.doc).replace(/>\s+</g, '><');
      return { ok: true, min: minified };
    } catch(e){
      return { ok: false, error: e.message || 'XML minify error' };
    }
  }

  // Format and display output
  function formatNow(forceFormat){
    var inp = byId('json-input');
    var out = byId('code-output');
    var err = byId('error-box');
    var gutter = byId('line-gutter');
    if (!inp || !out || !err || !gutter) return;
    
    var text = (inp.value || '').trim();
    var mode = getMode();
    
    // Clear previous errors
    err.style.display = 'none';
    err.textContent = '';
    
    if (!text){
      out.textContent = '';
      gutter.innerHTML = '';
      return;
    }
    
    if (mode === 'json'){
      try {
        var obj = JSON.parse(text);
        var pretty = JSON.stringify(obj, null, 2);
        out.innerHTML = syntaxHighlight(pretty);
        var lines = computeLines(pretty);
        gutter.innerHTML = buildGutter(lines, -1);
      } catch(e){
        // Show detailed error with line/column info
        var errorMsg = e.message || 'Invalid JSON';
        var line = null, column = null;
        
        // Try to extract line and column from error message
        var match = errorMsg.match(/position\s+(\d+)/i);
        if (match){
          var pos = parseInt(match[1], 10);
          var lines = text.substring(0, pos).split('\n');
          line = lines.length;
          column = lines[lines.length - 1].length + 1;
        } else {
          match = errorMsg.match(/line\s+(\d+)/i);
          if (match) line = parseInt(match[1], 10);
          match = errorMsg.match(/column\s+(\d+)/i);
          if (match) column = parseInt(match[1], 10);
        }
        
        // Display error with line/column info
        var errorText = 'Syntax Error: ' + errorMsg;
        if (line !== null){
          errorText += ' at line ' + line;
          if (column !== null) errorText += ', column ' + column;
        }
        err.textContent = errorText;
        err.style.display = 'block';
        
        // Highlight error line in gutter
        if (line !== null){
          var textLines = text.split('\n');
          gutter.innerHTML = buildGutter(textLines.length, line);
        }
        
        // Show raw text in output
        out.textContent = text;
      }
    } else if (mode === 'xml'){
      var xmlRes = xmlParserResult(text);
      if (xmlRes.ok){
        try {
          var serializer = new XMLSerializer();
          var formatted = serializer.serializeToString(xmlRes.doc);
          // Basic pretty print
          formatted = formatted.replace(/></g, '>\n<');
          out.textContent = formatted;
          gutter.innerHTML = buildGutter(computeLines(formatted), -1);
        } catch(e){
          out.textContent = text;
          gutter.innerHTML = buildGutter(computeLines(text), -1);
        }
      } else {
        // Show XML error
        var xmlError = 'XML Error: ' + xmlRes.error;
        if (xmlRes.line !== null && xmlRes.line !== undefined){
          xmlError += ' at line ' + xmlRes.line;
          if (xmlRes.column !== null && xmlRes.column !== undefined){
            xmlError += ', column ' + xmlRes.column;
          }
        }
        err.textContent = xmlError;
        err.style.display = 'block';
        
        // Highlight error line
        if (xmlRes.line){
          var xmlLines = text.split('\n');
          gutter.innerHTML = buildGutter(xmlLines.length, xmlRes.line);
        }
        
        out.textContent = text;
      }
    } else if (mode === 'protobuf'){
      // Protobuf: decode base64 and show hex dump
      try {
        var decoded = atob(text);
        var hex = '';
        var ascii = '';
        for(var i = 0; i < decoded.length; i++){
          var byte = decoded.charCodeAt(i);
          hex += ('0' + byte.toString(16)).slice(-2) + ' ';
          ascii += (byte >= 32 && byte <= 126) ? decoded.charAt(i) : '.';
          if ((i + 1) % 16 === 0){
            hex += '  ' + ascii + '\n';
            ascii = '';
          }
        }
        if (ascii){
          hex += '  ' + ascii;
        }
        out.textContent = hex || 'Empty data';
        gutter.innerHTML = buildGutter(computeLines(hex || 'Empty data'), -1);
      } catch(e){
        err.textContent = 'Error: Invalid Base64 encoding';
        err.style.display = 'block';
        out.textContent = text;
        gutter.innerHTML = buildGutter(computeLines(text), -1);
      }
    }
  }

  // Copy output to clipboard
  function copyOutput(){
    var out = byId('code-output');
    if (!out) return;
    var text = out.innerText || out.textContent || '';
    if (!text.trim()){
      showToast('Nothing to copy', 'error');
      return;
    }
    
    var tmp = document.createElement('textarea');
    tmp.value = text;
    document.body.appendChild(tmp);
    tmp.select();
    try {
      document.execCommand('copy');
      showToast('Copied to clipboard!', 'success');
    } catch(e){
      showToast('Failed to copy', 'error');
    } finally {
      document.body.removeChild(tmp);
    }
  }

  // Download output as file
  function downloadOutput(){
    var out = byId('code-output');
    if (!out) return;
    var text = out.innerText || out.textContent || '';
    if (!text.trim()){
      showToast('Nothing to download', 'error');
      return;
    }
    
    var config = getFormatConfig();
    var blob = new Blob([text], { type: config.mimeType });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = 'output.' + config.fileExtension;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast('Downloaded successfully!', 'success');
  }

  // Insert sample data
  function insertSample(){
    var inp = byId('json-input');
    if (!inp) return;
    var config = getFormatConfig();
    inp.value = config.placeholder;
    formatNow();
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

    if (inp){ inp.addEventListener('input', debounce(formatNow, 300)); }
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
