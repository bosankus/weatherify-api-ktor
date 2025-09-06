package bose.ankush.route

import bose.ankush.route.common.WebResources
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.koin.ktor.ext.inject

fun Route.pollingEngineRoute() {
    val pageName = "PollingEngine (KMM SDK) | Androidplay"
    val analytics: util.Analytics by application.inject()

    route("/kmm-sdk/pollingengine") {
        get {
            analytics.event(
                name = "page_view",
                params = mapOf(
                    "page_location" to call.request.path(),
                    "page_title" to pageName
                ),
                userAgent = call.request.headers["User-Agent"]
            )
            call.respondHtml(HttpStatusCode.OK) {
                attributes["lang"] = "en"
                head {
                    WebResources.includeGoogleTag(this)
                    title { +pageName }
                    meta { charset = "UTF-8" }
                    meta {
                        name = "viewport"
                        content = "width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover"
                    }
                    meta { name = "theme-color"; content = "#0f1117" }
                    meta { name = "apple-mobile-web-app-capable"; content = "yes" }
                    meta {
                        name = "apple-mobile-web-app-status-bar-style"; content = "black-translucent"
                    }
                    meta { name = "mobile-web-app-capable"; content = "yes" }

                    // fonts and icons similar to other pages
                    link { rel = "preconnect"; href = "https://fonts.googleapis.com" }
                    link {
                        rel = "preconnect"
                        href = "https://fonts.gstatic.com"
                        attributes["crossorigin"] = ""
                    }
                    link {
                        rel = "stylesheet"
                        href =
                            "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
                    }
                    link {
                        rel = "stylesheet"; href =
                        "https://fonts.googleapis.com/icon?family=Material+Icons"
                    }

                    WebResources.includeSharedCss(this)

                    // Page specific styles for documentation readability
                    style {
                        unsafe {
                            raw(
                                """
                                :root { --safe-top: env(safe-area-inset-top); --safe-bottom: env(safe-area-inset-bottom); --safe-left: env(safe-area-inset-left); --safe-right: env(safe-area-inset-right); }
                                body { background: var(--main-bg, #0f1117); }
                                html.light-theme body { background: var(--main-bg-light, #f8fafc); }
                                html.light-theme .docs-container { background: var(--card-bg-light, #ffffff); box-shadow: 0 8px 24px rgba(15,23,42,0.06); }
                                html.light-theme .subtitle { color: var(--text-color-light, #334155); }
                                html.light-theme .badge-row img { background: var(--badge-bg-light, #f1f5f9); box-shadow: 0 1px 4px rgba(15,23,42,0.06); }
                                html.light-theme .docs-container table thead th { background: rgba(0,0,0,0.04); }
                                .container { padding: calc(2.5rem + var(--safe-top)) calc(2.5rem + var(--safe-right)) calc(2.5rem + var(--safe-bottom)) calc(2.5rem + var(--safe-left)); }
                                .header { margin-top: 0; display:flex; flex-wrap: wrap; gap: 1.5rem; align-items: center; justify-content: space-between; }
                                .brand-text { display: flex; flex-direction: column; gap: 0.25rem; }
                                .logo { font-size: 2.2rem; font-weight: 700; letter-spacing: -1px; }
                                .subtitle { font-size: 1.1rem; color: var(--text-color, #b0b4c1); font-weight: 400; margin-top: 0.2rem; }
                                .badge-row { display: flex; gap: 0.7rem; margin: 1.5rem 0 2.5rem 0; flex-wrap: wrap; }
                                .badge-row img { height: 28px; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); background: var(--badge-bg, #181a20); padding: 2px 8px; max-width: 100%; display: block; }
                                .docs-container { max-width: 1200px; margin: 0 auto; padding: 48px 48px 32px 48px; background: var(--card-bg, #181a20); border-radius: 22px; box-shadow: 0 8px 32px rgba(0,0,0,0.12); }
                                .docs-container h1, .docs-container h2, .docs-container h3 { color: var(--card-title); margin-top: 2.5rem; }
                                .docs-container h1 { margin-top: 0; font-size: 2.1rem; }
                                .docs-container { font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, Helvetica Neue, Arial, "Apple Color Emoji", "Segoe UI Emoji"; font-weight: 400; }
                                .docs-container p, .docs-container li { color: var(--text-color); line-height: 2.0; font-size: 1.06rem; font-weight: 400; }
                                .docs-container code { font-family: 'JetBrains Mono', monospace; font-size: 1.0em; font-weight: 400; line-height: 1.45; }
                                .docs-container pre { background: var(--endpoint-bg); border:1px solid var(--endpoint-border); border-width: 0.5px; padding: 22px; border-radius: 14px; overflow: auto; margin: 2rem 0; }
                                /* Lists (bullets and numbers) */
                                .docs-container ul, .docs-container ol { margin: 1.2rem 0 1.5rem; padding-left: 1.7rem; }
                                .docs-container li { margin: 0.35rem 0; }
                                .docs-container ul li { list-style: disc; }
                                .docs-container ol li { list-style: decimal; }
                                .docs-container li > strong { font-weight: 600; }
                                .docs-container li > span { font-weight: 300; }
                                /* Tables */
                                .docs-container table { width: 100%; border-collapse: separate; border-spacing: 0; margin: 2rem 0; border: 1px solid var(--card-border); border-width: 0.5px; border-radius: 12px; overflow: hidden; display: block; max-width: 100%; }
                                .docs-container img { max-width: 100%; height: auto; }
                                .docs-container table thead th { background: rgba(255,255,255,0.03); font-weight: 600; color: var(--card-title); }
                                .docs-container table th, .docs-container table td { padding: 14px 16px; border-bottom: 1px solid var(--card-border); border-bottom-width: 0.5px; border-right: 1px solid var(--card-border); border-right-width: 0.5px; }
                                .docs-container table tr:last-child td { border-bottom: none; }
                                .docs-container table th:last-child, .docs-container table td:last-child { border-right: none; }
                                .docs-container table tbody tr:nth-child(odd) { background: rgba(255,255,255,0.02); }
                                .docs-container .table-wrap { overflow-x: auto; }
                                /* Remove horizontal dividers in rendered markdown */
                                .docs-container hr { display: none; }
                                /* Terminal UI styling for code examples - refreshed look */
                                .terminal { border:1px solid var(--card-border); border-radius: 16px; background: radial-gradient(120% 140% at 10% -10%, rgba(255,255,255,0.06), rgba(0,0,0,0)) , linear-gradient(0deg, rgba(0,0,0,0.08), rgba(255,255,255,0.02)); box-shadow: 0 10px 36px var(--card-shadow); overflow: hidden; margin: 28px 0; backdrop-filter: saturate(120%) blur(6px); }
                                .terminal-header { display:flex; align-items:center; justify-content: space-between; gap:12px; padding:12px 14px; border-bottom:1px solid var(--card-border); background: linear-gradient(180deg, rgba(26,27,35,0.92), rgba(26,27,35,0.6)); }
                                html.light-theme .terminal-header { background: linear-gradient(180deg, rgba(248,250,252,0.96), rgba(241,245,249,0.78)); }
                                .terminal-left { display:flex; align-items:center; gap:10px; min-width: 0; }
                                .terminal-right { display:flex; align-items:center; gap:6px; }
                                .terminal-btn { width:11px; height:11px; border-radius:50%; display:inline-block; box-shadow: inset 0 0 0 1px rgba(0,0,0,0.15); opacity: 0.9; }
                                .terminal-btn.red { background:#ff5f56; }
                                .terminal-btn.yellow { background:#ffbd2e; }
                                .terminal-btn.green { background:#27c93f; }
                                .terminal-title { color: var(--card-title); font-size: 0.86rem; font-weight: 500; opacity: 0.9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                                .terminal-action { display:inline-flex; align-items:center; gap:6px; padding:6px 10px; border-radius: 10px; border:1px solid var(--card-border); background: rgba(255,255,255,0.02); color: var(--text-color); font-size: 0.78rem; cursor: pointer; user-select: none; }
                                .terminal-action:hover { background: rgba(255,255,255,0.06); }
                                .terminal-action:active { transform: translateY(0.5px); }
                                .terminal-body { padding:0; background: rgba(0,0,0,0.15); }
                                html.light-theme .terminal-body { background: rgba(0,0,0,0.03); }
                                .terminal-body-inner { padding: 14px 16px; overflow: auto; }
                                .terminal-body pre { margin:0; border:none; border-radius:0; background:transparent; line-height: 1.5; }
                                .terminal-body code { font-weight: 400; }
                                .terminal .hljs { background: transparent; }
                                .terminal ::-webkit-scrollbar { height: 10px; width: 10px; }
                                .terminal ::-webkit-scrollbar-thumb { background: rgba(148,163,184,0.35); border-radius: 8px; }
                                .terminal ::-webkit-scrollbar-thumb:hover { background: rgba(148,163,184,0.5); }
                                .docs-container a { color: var(--link-color); }
                                .card { background: transparent; border:none; box-shadow: none; }
                                .card-body { padding: 0; }
                                /* Code example colors (override to ensure vivid syntax in terminal UI) */
                                /* Terminal-specific color tuning for bash/shell */
                                .terminal-body pre code.hljs.language-bash,
                                .terminal-body pre code.hljs.language-shell,
                                .terminal-body pre code.hljs.language-sh {
                                    color: #d1d5db; /* base text slightly lighter */
                                }
                                /* Prompt and comments dim */
                                .terminal-body pre code.hljs .hljs-meta,
                                .terminal-body pre code.hljs .hljs-comment { color: #94a3b8; }
                                html.light-theme .terminal-body pre code.hljs .hljs-meta,
                                html.light-theme .terminal-body pre code.hljs .hljs-comment { color: #64748b; }
                                /* Commands and options */
                                .terminal-body pre code.hljs .hljs-built_in,
                                .terminal-body pre code.hljs .hljs-keyword { color: #7ee787; }
                                html.light-theme .terminal-body pre code.hljs .hljs-built_in,
                                html.light-theme .terminal-body pre code.hljs .hljs-keyword { color: #16a34a; }
                                .terminal-body pre code.hljs .hljs-attr,
                                .terminal-body pre code.hljs .hljs-params,
                                .terminal-body pre code.hljs .hljs-literal { color: #79c0ff; }
                                html.light-theme .terminal-body pre code.hljs .hljs-attr,
                                html.light-theme .terminal-body pre code.hljs .hljs-params,
                                html.light-theme .terminal-body pre code.hljs .hljs-literal { color: #2563eb; }
                                /* Strings remain greenish for consistency */
                                .terminal-body pre code.hljs .hljs-string { color: #7ee787; }
                                html.light-theme .terminal-body pre code.hljs .hljs-string { color: #16a34a; }
                                /* Base code color and background within terminal/docs */
                                .docs-container pre code.hljs, .terminal-body pre code.hljs { color: #c9d1d9; background: transparent; }
                                html.light-theme .docs-container pre code.hljs, html.light-theme .terminal-body pre code.hljs { color: #0f172a; }

                                /* Increase specificity for hljs token colors to beat theme defaults */
                                .docs-container pre code.hljs .hljs-keyword,
                                .terminal-body pre code.hljs .hljs-keyword,
                                .docs-container pre code.hljs .hljs-selector-tag,
                                .terminal-body pre code.hljs .hljs-selector-tag,
                                .docs-container pre code.hljs .hljs-literal,
                                .terminal-body pre code.hljs .hljs-literal,
                                .docs-container pre code.hljs .hljs-built_in,
                                .terminal-body pre code.hljs .hljs-built_in,
                                .docs-container pre code.hljs .hljs-type,
                                .terminal-body pre code.hljs .hljs-type { color: #c084f5; font-weight: 600; }
                                html.light-theme .docs-container pre code.hljs .hljs-keyword,
                                html.light-theme .terminal-body pre code.hljs .hljs-keyword,
                                html.light-theme .docs-container pre code.hljs .hljs-selector-tag,
                                html.light-theme .terminal-body pre code.hljs .hljs-selector-tag,
                                html.light-theme .docs-container pre code.hljs .hljs-literal,
                                html.light-theme .terminal-body pre code.hljs .hljs-literal,
                                html.light-theme .docs-container pre code.hljs .hljs-built_in,
                                html.light-theme .terminal-body pre code.hljs .hljs-built_in,
                                html.light-theme .docs-container pre code.hljs .hljs-type,
                                html.light-theme .terminal-body pre code.hljs .hljs-type { color: #7c3aed; }

                                /* Strings */
                                .docs-container pre code.hljs .hljs-string,
                                .terminal-body pre code.hljs .hljs-string,
                                .docs-container pre code.hljs .hljs-template-variable,
                                .terminal-body pre code.hljs .hljs-template-variable { color: #7ee787; }
                                html.light-theme .docs-container pre code.hljs .hljs-string,
                                html.light-theme .terminal-body pre code.hljs .hljs-string,
                                html.light-theme .docs-container pre code.hljs .hljs-template-variable,
                                html.light-theme .terminal-body pre code.hljs .hljs-template-variable { color: #16a34a; }

                                /* Numbers, attributes */
                                .docs-container pre code.hljs .hljs-number,
                                .terminal-body pre code.hljs .hljs-number,
                                .docs-container pre code.hljs .hljs-attr,
                                .terminal-body pre code.hljs .hljs-attr { color: #f8bd96; }
                                html.light-theme .docs-container pre code.hljs .hljs-number,
                                html.light-theme .terminal-body pre code.hljs .hljs-number,
                                html.light-theme .docs-container pre code.hljs .hljs-attr,
                                html.light-theme .terminal-body pre code.hljs .hljs-attr { color: #ea580c; }

                                /* Functions and titles */
                                .docs-container pre code.hljs .hljs-title.function_,
                                .terminal-body pre code.hljs .hljs-title.function_,
                                .docs-container pre code.hljs .hljs-function .hljs-title,
                                .terminal-body pre code.hljs .hljs-function .hljs-title { color: #79c0ff; }
                                html.light-theme .docs-container pre code.hljs .hljs-title.function_,
                                html.light-theme .terminal-body pre code.hljs .hljs-title.function_,
                                html.light-theme .docs-container pre code.hljs .hljs-function .hljs-title,
                                html.light-theme .terminal-body pre code.hljs .hljs-function .hljs-title { color: #2563eb; }

                                /* Comments */
                                .docs-container pre code.hljs .hljs-comment,
                                .terminal-body pre code.hljs .hljs-comment { color: #8b949e; font-style: italic; }
                                html.light-theme .docs-container pre code.hljs .hljs-comment,
                                html.light-theme .terminal-body pre code.hljs .hljs-comment { color: #64748b; }

                                /* Properties and params */
                                .docs-container pre code.hljs .hljs-params,
                                .terminal-body pre code.hljs .hljs-params,
                                .docs-container pre code.hljs .hljs-property,
                                .terminal-body pre code.hljs .hljs-property { color: #e2b86b; }
                                html.light-theme .docs-container pre code.hljs .hljs-params,
                                html.light-theme .terminal-body pre code.hljs .hljs-params,
                                html.light-theme .docs-container pre code.hljs .hljs-property,
                                html.light-theme .terminal-body pre code.hljs .hljs-property { color: #b45309; }
                                @media (max-width: 1024px){ .docs-container{ padding: 28px 24px; max-width: 92vw; } .header .logo{ font-size:1.9rem; } .subtitle{ font-size:1rem; } }
                                @media (max-width: 820px){ .container { padding: 1.5rem; } .header{ gap:1rem; } .badge-row{ gap:0.5rem; } .docs-container{ padding: 22px 16px; border-radius:18px; } .docs-container h1{ font-size:1.8rem; } .docs-container h2{ font-size:1.4rem; } .docs-container p, .docs-container li{ font-size:1.02rem; line-height:1.9; } .terminal-header{ padding:10px 12px; } .terminal-action{ padding:5px 8px; font-size:0.75rem; } }
                                @media (max-width: 600px){ .container{ padding: max(12px, calc(12px + var(--safe-left))) max(12px, calc(12px + var(--safe-right))) max(12px, calc(12px + var(--safe-bottom))) max(12px, calc(12px + var(--safe-left))); } .header{ flex-direction: column; align-items: flex-start; } .brand-text{ width:100%; } .badge-row img{ height:24px; padding:2px 6px; } .docs-container{ padding: 16px 12px; } .terminal-body-inner{ padding: 10px 12px; } }
                                @media (max-width: 480px){ .docs-container{ padding: 12px; } .badge-row{ gap:0.4rem; } .badge-row img{ height:22px; } .terminal-title{ display:none; } .terminal-body pre code{ white-space: pre-wrap; word-break: break-word; } }
                                @media (max-width: 700px){ .docs-container{ padding: 18px 6px 12px 6px; } .container { padding: 1.2rem; } }
                                """
                            )
                        }
                    }

                    // Include shared JS and page-specific libraries to render markdown safely and with code highlighting
                    WebResources.includeSharedJs(this)

                    // marked.js for Markdown rendering
                    script {
                        attributes["src"] = "https://cdn.jsdelivr.net/npm/marked/marked.min.js"
                        attributes["crossorigin"] = "anonymous"
                    }
                    // DOMPurify for sanitization
                    script {
                        attributes["src"] = "https://cdn.jsdelivr.net/npm/dompurify@3.0.6/dist/purify.min.js"
                        attributes["crossorigin"] = "anonymous"
                    }
                    // highlight.js for syntax highlighting
                    link {
                        rel = "stylesheet"
                        href = "https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github-dark.min.css"
                    }
                    script {
                        attributes["src"] = "https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js"
                        attributes["crossorigin"] = "anonymous"
                    }

                    // Inline script to render markdown content
                    // Update theme-color meta to match current theme
                    script {
                        unsafe {
                            raw(
                                """
                                (function(){
                                  function applyThemeColor(){
                                    var isLight = document.documentElement.classList.contains('light-theme');
                                    var meta = document.querySelector('meta[name=\"theme-color\"]');
                                    if (!meta){ meta = document.createElement('meta'); meta.setAttribute('name','theme-color'); document.head.appendChild(meta); }
                                    meta.setAttribute('content', isLight ? '#ffffff' : '#0f1117');
                                  }
                                  document.addEventListener('DOMContentLoaded', applyThemeColor);
                                  // Observe class changes on <html> to update dynamically on toggle
                                  var observer = new MutationObserver(function(mutations){
                                    for (var m of mutations){ if (m.attributeName === 'class'){ applyThemeColor(); }}
                                  });
                                  observer.observe(document.documentElement, { attributes: true });
                                })();
                                """
                            )
                        }
                    }
                    script {
                        unsafe {
                            raw(
                                """
                                document.addEventListener('DOMContentLoaded', function() {
                                  try {
                                    const md = document.getElementById('markdown-source').textContent;
                                    const html = DOMPurify.sanitize(marked.parse(md));
                                    const target = document.getElementById('doc');
                                    target.innerHTML = html;
                                    if (window.hljs) { document.querySelectorAll('pre code').forEach((el) => hljs.highlightElement(el)); }
                                    // Wrap code blocks in terminal UI
                                    document.querySelectorAll('#doc pre').forEach((pre) => {
                                      const wrapper = document.createElement('div');
                                      wrapper.className = 'terminal';
                                      const header = document.createElement('div');
                                      header.className = 'terminal-header';
                                      const left = document.createElement('div');
                                      left.className = 'terminal-left';
                                      left.innerHTML = '<span class="terminal-btn red"></span><span class="terminal-btn yellow"></span><span class="terminal-btn green"></span>' +
                                                       '<span class="terminal-title"></span>';
                                      const right = document.createElement('div');
                                      right.className = 'terminal-right';
                                      // Copy button
                                      const copyBtn = document.createElement('button');
                                      copyBtn.type = 'button';
                                      copyBtn.className = 'terminal-action copy';
                                      copyBtn.setAttribute('aria-label', 'Copy code');
                                      copyBtn.textContent = 'Copy';
                                      right.appendChild(copyBtn);
                                      header.appendChild(left);
                                      header.appendChild(right);
                                      const body = document.createElement('div');
                                      body.className = 'terminal-body';
                                      const bodyInner = document.createElement('div');
                                      bodyInner.className = 'terminal-body-inner';
                                      pre.parentNode.insertBefore(wrapper, pre);
                                      wrapper.appendChild(header);
                                      wrapper.appendChild(body);
                                      body.appendChild(bodyInner);
                                      bodyInner.appendChild(pre);

                                      // Determine title
                                      const titleEl = left.querySelector('.terminal-title');
                                      const codeEl = pre.querySelector('code');
                                      const lang = (codeEl && (codeEl.dataset.language || codeEl.className.match(/language-([a-z0-9]+)/i)?.[1])) || 'bash';
                                      titleEl.textContent = (lang || 'shell').toUpperCase();

                                      // Copy behavior
                                      copyBtn.addEventListener('click', async () => {
                                        try {
                                          const text = pre.innerText;
                                          if (navigator.clipboard) {
                                            await navigator.clipboard.writeText(text);
                                          } else {
                                            const ta = document.createElement('textarea');
                                            ta.value = text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta);
                                          }
                                          copyBtn.textContent = 'Copied';
                                          setTimeout(() => copyBtn.textContent = 'Copy', 1500);
                                        } catch (e) { console.error('Copy failed', e); }
                                      });
                                    });
                                  } catch (e) {
                                    console.error('Error rendering documentation:', e);
                                  }
                                });
                                """
                            )
                        }
                    }
                }
                body {
                    div {
                        classes = setOf("container")
                        // Header
                        div {
                            classes = setOf("header")
                            div {
                                classes = setOf("brand-text")
                                h1 { classes = setOf("logo"); +"Androidplay" }
                                span { classes = setOf("subtitle"); +"PollingEngine (KMM SDK)" }
                            }
                            div { style = "flex-grow: 1;" }
                            div {
                                style = "display: flex; align-items: center; gap: 1rem;"
                                label {
                                    classes = setOf("toggle")
                                    style = "position: relative; cursor: pointer; margin-right: 0.5rem;"
                                    input { type = InputType.checkBox; id = "theme-toggle" }
                                    div { }
                                }
                            }
                        }
                        // Badges row (library version, build, license, CI)
                        div {
                            classes = setOf("badge-row")
                            // Visible badges
                            a {
                                href = "https://central.sonatype.com/artifact/io.github.bosankus/pollingengine"
                                attributes["target"] = "_blank"
                                img {
                                    src =
                                        "https://img.shields.io/maven-central/v/io.github.bosankus/pollingengine.svg?label=Maven%20Central"
                                    alt = "Maven Central"
                                }
                            }
                            a {
                                href = "https://github.com/bosankus/pollingengine/actions"
                                attributes["target"] = "_blank"
                                img {
                                    src =
                                        "https://img.shields.io/github/actions/workflow/status/bosankus/pollingengine/ci.yml?branch=main&label=CI"
                                    alt = "Build Status"
                                }
                            }
                            a {
                                href = "https://github.com/bosankus/pollingengine"
                                attributes["target"] = "_blank"
                                img {
                                    src = "https://img.shields.io/github/license/bosankus/pollingengine?color=blue"
                                    alt = "License"
                                }
                            }
                            a {
                                href = "https://kotlinlang.org/"
                                attributes["target"] = "_blank"
                                img {
                                    src = "https://img.shields.io/badge/Kotlin-1.9%2B-purple?logo=kotlin"
                                    alt = "Kotlin"
                                }
                            }
                            a {
                                href = "https://kotlinlang.org/docs/multiplatform.html"
                                attributes["target"] = "_blank"
                                img {
                                    src = "https://img.shields.io/badge/KMM-Library-green"
                                    alt = "KMM"
                                }
                            }
                        }

                        // Content card
                        div {
                            classes = setOf("docs-container")
                            div {
                                classes = setOf("card")
                                div {
                                    classes = setOf("card-body")
                                    // Hidden element to carry markdown content
                                    script {
                                        attributes["type"] = "text/plain"
                                        attributes["id"] = "markdown-source"
                                        unsafe {
                                            raw(markdownContent)
                                        }
                                    }
                                    // Rendered documentation target
                                    div { id = "doc" }
                                }
                            }
                        }

                        // Footer
                        footer {
                            classes = setOf("footer")
                            div {
                                classes = setOf("footer-content")
                                p { +"© ${java.time.Year.now().value} Androidplay. All rights reserved." }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Markdown content sourced from project root PollingEngine.md
private val markdownContent = """

PollingEngine is a lightweight, multiplatform library designed to simplify polling operations in your Android (Kotlin) and iOS (Swift) applications. It provides a robust, easy-to-use API for scheduling, starting, stopping, and managing polling tasks, making it ideal for scenarios where you need to repeatedly fetch data or perform background checks at regular intervals.

---

## Features

- **Multiplatform Support:** Use the same API in both Android (Kotlin) and iOS (Swift) projects.
- **Flexible Polling Intervals:** Easily configure how often polling occurs.
- **Lifecycle Awareness:** Start, stop, or pause polling based on your app’s lifecycle.
- **Error Handling:** Built-in support for handling errors and retries.
- **Lightweight & Efficient:** Minimal overhead, designed for performance and battery efficiency.

---

## Getting Started

### Android (Kotlin)

#### 1. Add Dependency

Add the library to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.bosankus:pollingengine:1.0.0")
}
```

#### 2. Basic Usage

```kotlin
import io.github.bosankus.pollingengine.PollingEngine

// Create a polling engine instance
val pollingEngine = PollingEngine(
    intervalMillis = 5000L, // Poll every 5 seconds
    action = {
        // Your polling logic here (e.g., fetch data from API)
    },
    onError = { throwable ->
        // Handle errors here
    }
)

// Start polling
pollingEngine.start()

// Stop polling when needed
pollingEngine.stop()
```

#### 3. Advanced Usage

- **Pause and Resume:**

```kotlin
pollingEngine.pause()
// ...later
pollingEngine.resume()
```

- **Change Interval Dynamically:**

```kotlin
pollingEngine.updateInterval(10000L) // Change to 10 seconds
```

- **Lifecycle Integration (e.g., in an Activity):**

```kotlin
override fun onStart() {
    super.onStart()
    pollingEngine.start()
}

override fun onStop() {
    super.onStop()
    pollingEngine.stop()
}
```

---

### iOS (Swift)

#### 1. Add Dependency

You can integrate PollingEngine into your iOS project via Swift Package Manager (recommended) or CocoaPods.

- Swift Package Manager (Xcode):
  1. In Xcode, go to File > Add Packages...
  2. Enter the package URL: `https://github.com/bosankus/pollingengine.git`
  3. Choose the latest version (e.g., 1.0.0) and add the product “PollingEngine” to your app target.

- Swift Package Manager (Package.swift):
```swift
// In your Package.swift dependencies
.package(url: "https://github.com/bosankus/pollingengine.git", from: "1.0.0")

// And add the product to your target dependencies
.product(name: "PollingEngine", package: "pollingengine")
```

- CocoaPods:
```ruby
# Podfile
platform :ios, '13.0'
use_frameworks!

target 'YourApp' do
  pod 'PollingEngine', '~> 1.0'
end
```

#### 2. Basic Usage

```swift
import PollingEngine

// Create a polling engine instance
let pollingEngine = PollingEngine(
    intervalMillis: 5000, // Poll every 5 seconds
    action: {
        // Your polling logic here (e.g., fetch data from API)
    },
    onError: { error in
        // Handle errors here
    }
)

// Start polling
pollingEngine.start()

// Stop polling when needed
pollingEngine.stop()
```

#### 3. Advanced Usage

- **Pause and Resume:**

```swift
pollingEngine.pause()
// ...later
pollingEngine.resume()
```

- **Change Interval Dynamically:**

```swift
pollingEngine.updateInterval(intervalMillis: 10000) // Change to 10 seconds
```

- **Lifecycle Integration (e.g., in a ViewController):**

```swift
override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    pollingEngine.start()
}

override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    pollingEngine.stop()
}
```

---

## API Reference

### PollingEngine Constructor

| Parameter      | Type        | Description                                 |
|----------------|-------------|---------------------------------------------|
| intervalMillis | Long/Int    | Polling interval in milliseconds            |
| action         | () -> Unit  | The polling action to execute               |
| onError        | (Throwable) -> Unit / (Error) -> Void | Error handler (optional) |

### Methods

- `start()`: Starts the polling process.
- `stop()`: Stops the polling process.
- `pause()`: Pauses polling without resetting state.
- `resume()`: Resumes polling after a pause.
- `updateInterval(newIntervalMillis)`: Changes the polling interval at runtime.

---

## Best Practices

- Always stop polling when your screen or component is not visible to save resources.
- Handle errors gracefully in the `onError` callback.
- Use appropriate intervals to balance freshness and battery/network usage.

---

## Example Use Cases

- Periodically fetch new data from a server.
- Monitor background tasks or job statuses.
- Implement real-time UI updates with minimal effort.

---

## Support

For questions, issues, or feature requests, please visit the [GitHub repository](https://github.com/bosankus/pollingengine).

---

With PollingEngine, you can add robust, efficient polling to your Android and iOS apps with minimal code and maximum flexibility. Happy coding!
"""
