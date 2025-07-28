package bose.ankush.route

import bose.ankush.route.common.WebResources
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import util.Constants

fun Route.homeRoute() {
    val pageName = "Androidplay API Portal"
    route(Constants.Api.HOME_ENDPOINT) {
        get {
            call.respondHtml(HttpStatusCode.OK) {
                attributes["lang"] = "en"
                head {
                    title { +pageName }
                    meta {
                        charset = "UTF-8"
                    }
                    meta {
                        name = "viewport"
                        content = "width=device-width, initial-scale=1.0"
                    }
                    link {
                        rel = "preconnect"
                        href = "https://fonts.googleapis.com"
                    }
                    link {
                        rel = "preconnect"
                        href = "https://fonts.gstatic.com"
                        attributes["crossorigin"] = ""
                    }
                    link {
                        rel = "stylesheet"
                        href = "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
                    }
                    link {
                        rel = "stylesheet"
                        href = "https://fonts.googleapis.com/icon?family=Material+Icons"
                    }

                    // Include shared CSS
                    WebResources.includeSharedCss(this)

                    // Include page-specific CSS
                    style {
                        unsafe {
                            raw(
                                """
                                :root {
                                    /* Light theme variables */
                                    --bg-gradient-light-1: #f8f9fa;
                                    --bg-gradient-light-2: #e9ecef;
                                    --bg-gradient-light-3: #dee2e6;
                                    --text-color-light: #212529;
                                    --text-secondary-light: #4a5568;
                                    --card-bg-light: rgba(255, 255, 255, 0.7);
                                    --card-border-light: rgba(0, 0, 0, 0.1);
                                    --card-hover-bg-light: rgba(255, 255, 255, 0.9);
                                    --card-hover-border-light: rgba(0, 0, 0, 0.2);
                                    --card-shadow-light: rgba(0, 0, 0, 0.1);
                                    --header-border-light: rgba(0, 0, 0, 0.1);
                                    --endpoint-bg-light: rgba(245, 245, 250, 0.7);
                                    --endpoint-border-light: rgba(0, 0, 0, 0.1);
                                    --modal-bg-light: rgba(255, 255, 255, 0.95);
                                    --modal-border-light: rgba(0, 0, 0, 0.1);
                                    --modal-title-light: #2d3748;
                                    --modal-text-light: #4a5568;
                                    --response-bg-light: rgba(245, 245, 250, 0.7);
                                    --response-border-light: rgba(0, 0, 0, 0.1);
                                    --icon-color-light: #4a5568;
                                    --card-title-light: #2d3748;
                                    --card-description-light: #4a5568;
                                    --endpoint-path-light: #2d3748;

                                    /* Dark theme variables */
                                    --bg-gradient-dark-1: #121218;
                                    --bg-gradient-dark-2: #1a1a2c;
                                    --bg-gradient-dark-3: #151520;
                                    --text-color-dark: #f0f0f0;
                                    --text-secondary-dark: #a0a0b0;
                                    --card-bg-dark: rgba(30, 30, 45, 0.5);
                                    --card-border-dark: rgba(60, 60, 80, 0.2);
                                    --card-hover-bg-dark: rgba(40, 40, 60, 0.7);
                                    --card-hover-border-dark: rgba(80, 80, 120, 0.4);
                                    --card-shadow-dark: rgba(0, 0, 0, 0.15);
                                    --header-border-dark: rgba(60, 60, 80, 0.2);
                                    --endpoint-bg-dark: rgba(25, 25, 40, 0.3);
                                    --endpoint-border-dark: rgba(60, 60, 80, 0.2);
                                    --modal-bg-dark: rgba(20, 20, 30, 0.95);
                                    --modal-border-dark: rgba(60, 60, 80, 0.2);
                                    --modal-title-dark: #f0f0f0;
                                    --modal-text-dark: #d0d0d0;
                                    --response-bg-dark: rgba(25, 25, 35, 0.6);
                                    --response-border-dark: rgba(60, 60, 80, 0.2);
                                    --icon-color-dark: #8ab4f8;
                                    --card-title-dark: #ffffff;
                                    --card-description-dark: #a0a0b0;
                                    --endpoint-path-dark: #d0d0d0;

                                    /* Default to dark theme */
                                    --bg-gradient-1: var(--bg-gradient-dark-1);
                                    --bg-gradient-2: var(--bg-gradient-dark-2);
                                    --bg-gradient-3: var(--bg-gradient-dark-3);
                                    --text-color: var(--text-color-dark);
                                    --text-secondary: var(--text-secondary-dark);
                                    --card-bg: var(--card-bg-dark);
                                    --card-border: var(--card-border-dark);
                                    --card-hover-bg: var(--card-hover-bg-dark);
                                    --card-hover-border: var(--card-hover-border-dark);
                                    --card-shadow: var(--card-shadow-dark);
                                    --header-border: var(--header-border-dark);
                                    --endpoint-bg: var(--endpoint-bg-dark);
                                    --endpoint-border: var(--endpoint-border-dark);
                                    --modal-bg: var(--modal-bg-dark);
                                    --modal-border: var(--modal-border-dark);
                                    --modal-title: var(--modal-title-dark);
                                    --modal-text: var(--modal-text-dark);
                                    --response-bg: var(--response-bg-dark);
                                    --response-border: var(--response-border-dark);
                                    --icon-color: var(--icon-color-dark);
                                    --card-title: var(--card-title-dark);
                                    --card-description: var(--card-description-dark);
                                    --endpoint-path: var(--endpoint-path-dark);

                                    /* Animation variables */
                                    --x: 50%;
                                    --y: 50%;
                                }

                                /* Light theme class */
                                html.light-theme {
                                    --bg-gradient-1: var(--bg-gradient-light-1);
                                    --bg-gradient-2: var(--bg-gradient-light-2);
                                    --bg-gradient-3: var(--bg-gradient-light-3);
                                    --text-color: var(--text-color-light);
                                    --text-secondary: var(--text-secondary-light);
                                    --card-bg: var(--card-bg-light);
                                    --card-border: var(--card-border-light);
                                    --card-hover-bg: var(--card-hover-bg-light);
                                    --card-hover-border: var(--card-hover-border-light);
                                    --card-shadow: var(--card-shadow-light);
                                    --header-border: var(--header-border-light);
                                    --endpoint-bg: var(--endpoint-bg-light);
                                    --endpoint-border: var(--endpoint-border-light);
                                    --modal-bg: var(--modal-bg-light);
                                    --modal-border: var(--modal-border-light);
                                    --modal-title: var(--modal-title-light);
                                    --modal-text: var(--modal-text-light);
                                    --response-bg: var(--response-bg-light);
                                    --response-border: var(--response-border-light);
                                    --icon-color: var(--icon-color-light);
                                    --card-title: var(--card-title-light);
                                    --card-description: var(--card-description-light);
                                    --endpoint-path: var(--endpoint-path-light);
                                }

                                * {
                                    margin: 0;
                                    padding: 0;
                                    box-sizing: border-box;
                                }

                                body {
                                    background: linear-gradient(135deg, var(--bg-gradient-1) 0%, var(--bg-gradient-2) 50%, var(--bg-gradient-3) 100%);
                                    color: var(--text-color);
                                    font-family: 'Space Grotesk', -apple-system, BlinkMacSystemFont, sans-serif;
                                    line-height: 1.6;
                                    min-height: 100vh;
                                    display: flex;
                                    flex-direction: column;
                                    overflow-x: hidden;
                                    transition: background 0.3s ease, color 0.3s ease;
                                }

                                .container {
                                    max-width: 1400px;
                                    margin: 0 auto;
                                    padding: 0 2rem;
                                    flex: 1;
                                    display: flex;
                                    flex-direction: column;
                                    justify-content: center;
                                }

                                .header {
                                    text-align: left;
                                    margin-bottom: 3rem;
                                    position: relative;
                                    padding: 1.5rem 0;
                                    overflow: hidden;
                                    display: flex;
                                    align-items: center;
                                    background: transparent;
                                    border-bottom: 1px solid var(--header-border);
                                    margin-top: 1rem;
                                }

                                @keyframes fadeIn {
                                    from { opacity: 0; }
                                    to { opacity: 1; }
                                }

                                .brand-text {
                                    display: flex;
                                    align-items: center;
                                    position: relative;
                                }

                                .logo {
                                    font-size: 1.75rem;
                                    font-weight: 700;
                                    background: linear-gradient(135deg, #6b7dbb, #3b4f7d);
                                    -webkit-background-clip: text;
                                    background-clip: text;
                                    -webkit-text-fill-color: transparent;
                                    color: transparent;
                                    letter-spacing: -0.01em;
                                    position: relative;
                                    transition: all 0.3s ease;
                                    margin-right: 1rem;
                                    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
                                }

                                .logo:hover {
                                    filter: brightness(1.2);
                                    transform: translateY(-1px);
                                }

                                .subtitle {
                                    font-size: 0.875rem;
                                    color: var(--text-secondary);
                                    font-weight: 600;
                                    letter-spacing: 0.5px;
                                    text-transform: uppercase;
                                    padding: 0.2rem 0.5rem;
                                    border-radius: 4px;
                                    background: rgba(59, 79, 125, 0.1);
                                }

                                .tagline {
                                    font-size: 1.25rem;
                                    color: #b0b0b0;
                                    font-weight: 400;
                                    margin-bottom: 1.5rem;
                                    max-width: 600px;
                                    margin-left: auto;
                                    margin-right: auto;
                                    line-height: 1.6;
                                    opacity: 0;
                                    animation: fadeInUp 1s ease-out 1.5s both;
                                    text-align: center;
                                }

                                .content {
                                    display: grid;
                                    grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
                                    gap: 2.5rem;
                                    margin-bottom: 4rem;
                                }

                                /* @supports feature query for backdrop-filter */
                                @supports not (backdrop-filter: blur(10px)) and not (-webkit-backdrop-filter: blur(10px)) {
                                    .card {
                                        background: var(--bg-gradient-1); /* Solid background fallback */
                                        border: 1px solid var(--card-border);
                                    }
                                }

                                .card {
                                    background: var(--card-bg);
                                    -webkit-backdrop-filter: blur(10px); /* Safari support */
                                    backdrop-filter: blur(10px);
                                    border: 1px solid var(--card-border);
                                    border-radius: 12px;
                                    padding: 2rem;
                                    transition: transform 0.3s ease, box-shadow 0.3s ease, background 0.3s ease, border-color 0.3s ease;
                                    cursor: pointer;
                                    position: relative;
                                    overflow: hidden;
                                    min-height: 260px;
                                    display: flex;
                                    flex-direction: column;
                                    justify-content: space-between;
                                    box-shadow: 0 4px 12px var(--card-shadow);
                                    -webkit-user-select: none;
                                    user-select: none;
                                }

                                .card:active {
                                    transform: scale(0.98);
                                    box-shadow: 0 2px 8px var(--card-shadow);
                                    transition: transform 0.1s ease, box-shadow 0.1s ease;
                                }

                                .card::after {
                                    content: '';
                                    position: absolute;
                                    top: 0;
                                    left: 0;
                                    right: 0;
                                    height: 1px;
                                    background: linear-gradient(90deg, transparent, rgba(30, 30, 50, 0.4), transparent);
                                    opacity: 0;
                                    transition: opacity 0.3s ease;
                                }

                                .card:hover::after {
                                    opacity: 1;
                                }

                                .card:hover {
                                    transform: translateY(-8px);
                                    border-color: var(--card-hover-border);
                                    background: var(--card-hover-bg);
                                    box-shadow: 
                                        0 12px 24px var(--card-shadow),
                                        0 0 0 1px var(--card-hover-border);
                                }

                                .card-header {
                                    display: flex;
                                    align-items: center;
                                    gap: 1rem;
                                    margin-bottom: 1.5rem;
                                }

                                .card-icon {
                                    width: 56px;
                                    height: 56px;
                                    background: linear-gradient(135deg, #4a5568, #2d3748);
                                    border-radius: 50%;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    color: white;
                                    font-size: 28px;
                                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                                    position: relative;
                                    transition: transform 0.2s ease, box-shadow 0.2s ease;
                                }

                                .card-icon::before {
                                    content: '';
                                    position: absolute;
                                    inset: -2px;
                                    background: linear-gradient(135deg, #2d3748, #1a202c);
                                    border-radius: 50%;
                                    opacity: 0;
                                    transition: opacity 0.3s ease;
                                    z-index: -1;
                                }

                                .card:hover .card-icon::before {
                                    opacity: 0.3;
                                }

                                .card-title {
                                    font-size: 1.75rem;
                                    font-weight: 600;
                                    color: var(--card-title);
                                    margin: 0;
                                    letter-spacing: -0.02em;
                                }

                                .card-description {
                                    color: var(--card-description);
                                    margin-bottom: 2rem;
                                    line-height: 1.7;
                                    font-size: 1rem;
                                    flex-grow: 1;
                                }

                                .endpoints-container {
                                    display: flex;
                                    flex-direction: column;
                                    gap: 0.75rem;
                                }

                                .endpoint {
                                    background: var(--endpoint-bg);
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 8px;
                                    padding: 1rem;
                                    font-family: 'JetBrains Mono', 'Fira Code', 'Monaco', monospace;
                                    font-size: 0.875rem;
                                    color: var(--text-color);
                                    transition: all 0.3s ease, background 0.3s ease, border-color 0.3s ease;
                                    display: flex;
                                    align-items: center;
                                    gap: 0.75rem;
                                    cursor: pointer;
                                    user-select: none;
                                }

                                .endpoint:hover {
                                    background: var(--card-hover-bg);
                                    border-color: var(--card-hover-border);
                                    transform: translateX(4px);
                                }

                                .endpoint:active {
                                    transform: translateX(2px) scale(0.98);
                                    transition: transform 0.1s ease;
                                    opacity: 0.9;
                                }

                                .method {
                                    display: inline-flex;
                                    align-items: center;
                                    justify-content: center;
                                    padding: 0.375rem 0.875rem;
                                    border-radius: 10px;
                                    font-size: 0.75rem;
                                    font-weight: 700;
                                    text-transform: uppercase;
                                    letter-spacing: 0.08em;
                                    min-width: 60px;
                                    text-align: center;
                                }

                                .method.get {
                                    background: rgba(34, 197, 94, 0.15);
                                    color: #4ade80;
                                    border: 1px solid rgba(34, 197, 94, 0.25);
                                }

                                .method.post {
                                    background: rgba(239, 68, 68, 0.15);
                                    color: #f87171;
                                    border: 1px solid rgba(239, 68, 68, 0.25);
                                }

                                .method.delete {
                                    background: rgba(245, 101, 101, 0.15);
                                    color: #fca5a5;
                                    border: 1px solid rgba(245, 101, 101, 0.25);
                                }

                                .endpoint-path {
                                    font-weight: 500;
                                    color: var(--endpoint-path);
                                }



                                /* @supports feature query for modal backdrop-filter */
                                @supports not (backdrop-filter: blur(10px)) and not (-webkit-backdrop-filter: blur(10px)) {
                                    .modal {
                                        background-color: rgba(0, 0, 0, 0.9); /* Darker background as fallback */
                                    }
                                }

                                .modal {
                                    display: none;
                                    position: fixed;
                                    z-index: 1000;
                                    left: 0;
                                    top: 0;
                                    width: 100%;
                                    height: 100%;
                                    background-color: rgba(0, 0, 0, 0.8);
                                    -webkit-backdrop-filter: blur(10px); /* Safari support */
                                    backdrop-filter: blur(10px);
                                    opacity: 0;
                                    transition: opacity 0.3s ease;
                                }

                                /* @supports feature query for modal-content backdrop-filter */
                                @supports not (backdrop-filter: blur(20px)) and not (-webkit-backdrop-filter: blur(20px)) {
                                    .modal-content {
                                        background: var(--bg-gradient-3); /* Solid background fallback */
                                    }
                                }

                                .modal-content {
                                    background: var(--modal-bg);
                                    -webkit-backdrop-filter: blur(20px); /* Safari support */
                                    backdrop-filter: blur(20px);
                                    border: 1px solid var(--modal-border);
                                    border-radius: 12px;
                                    margin: 5% auto;
                                    padding: 2rem;
                                    width: 90%;
                                    max-width: 800px;
                                    max-height: 80vh;
                                    overflow-y: auto;
                                    position: relative;
                                    transition: background 0.3s ease, border-color 0.3s ease, transform 0.3s ease;
                                    transform: translateY(20px);
                                }

                                .close {
                                    color: #aaa;
                                    float: right;
                                    font-size: 28px;
                                    font-weight: bold;
                                    cursor: pointer;
                                    position: absolute;
                                    right: 1.5rem;
                                    top: 1rem;
                                    width: 32px;
                                    height: 32px;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    border-radius: 50%;
                                    transition: all 0.2s ease;
                                    user-select: none;
                                }

                                .close:hover,
                                .close:focus {
                                    color: #fff;
                                    background-color: rgba(255, 255, 255, 0.1);
                                }

                                .close:active {
                                    transform: scale(0.9);
                                    background-color: rgba(255, 255, 255, 0.15);
                                    transition: transform 0.1s ease, background-color 0.1s ease;
                                }

                                .modal h2 {
                                    color: var(--modal-title);
                                    margin-bottom: 1.5rem;
                                    font-size: 1.75rem;
                                    font-weight: 600;
                                }

                                .modal h3, .modal h4 {
                                    color: var(--modal-title);
                                    margin: 1rem 0;
                                }

                                .modal strong {
                                    color: var(--modal-text);
                                }

                                .response-example {
                                    background: var(--response-bg);
                                    border: 1px solid var(--response-border);
                                    border-radius: 8px;
                                    padding: 1.25rem;
                                    margin: 1rem 0;
                                    font-family: 'JetBrains Mono', monospace;
                                    font-size: 0.875rem;
                                    color: var(--text-color);
                                    overflow-x: auto;
                                    transition: background 0.3s ease, border-color 0.3s ease, color 0.3s ease;
                                }

                                .response-example pre {
                                    margin: 0;
                                    white-space: pre-wrap;
                                }

                                /* Styled model containers */
                                .model-container {
                                    margin-bottom: 1.5rem;
                                    border: 1px solid var(--response-border);
                                    border-radius: 8px;
                                    overflow: hidden;
                                    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                                    transition: transform 0.2s ease, box-shadow 0.2s ease;
                                }

                                .model-container:hover {
                                    transform: translateY(-2px);
                                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                                }

                                .model-header {
                                    background: linear-gradient(135deg, #3b4f7d, #2d3748);
                                    color: white;
                                    padding: 0.75rem 1.25rem;
                                    font-weight: 600;
                                    font-size: 1rem;
                                    border-bottom: 1px solid var(--response-border);
                                }

                                .model-content {
                                    padding: 1.25rem;
                                    margin: 0;
                                    background: var(--response-bg);
                                }

                                /* Parameter and description labels */
                                .param-label, .desc-label {
                                    display: inline-block;
                                    font-weight: 600;
                                    margin-right: 0.5rem;
                                }

                                .param-label {
                                    color: #6b7dbb;
                                }

                                .desc-label {
                                    color: #8ab4f8;
                                    margin-top: 0.5rem;
                                }

                                /* Code styling */
                                code {
                                    background: rgba(0, 0, 0, 0.2);
                                    padding: 0.2rem 0.4rem;
                                    border-radius: 4px;
                                    font-size: 0.85em;
                                }

                                .status {
                                    display: inline-flex;
                                    align-items: center;
                                    gap: 0.5rem;
                                    padding: 0.5rem 1rem;
                                    background: rgba(34, 197, 94, 0.1);
                                    border: 1px solid rgba(34, 197, 94, 0.3);
                                    border-radius: 8px;
                                    color: #4ade80;
                                    font-size: 0.875rem;
                                    font-weight: 500;
                                    margin-top: 1rem;
                                }

                                .status-icon {
                                    width: 8px;
                                    height: 8px;
                                    background: #4ade80;
                                    border-radius: 50%;
                                    animation: pulse 2s infinite;
                                }

                                @keyframes pulse {
                                    0%, 100% { opacity: 1; }
                                    50% { opacity: 0.5; }
                                }

                                /* Ripple effect for click feedback */
                                .ripple {
                                    position: absolute;
                                    border-radius: 50%;
                                    background-color: rgba(255, 255, 255, 0.3);
                                    transform: scale(0);
                                    animation: ripple 0.6s linear;
                                    pointer-events: none;
                                    z-index: 10;
                                }

                                html.light-theme .ripple {
                                    background-color: rgba(0, 0, 0, 0.1);
                                }

                                @-webkit-keyframes ripple {
                                    to {
                                        -webkit-transform: scale(2);
                                        opacity: 0;
                                    }
                                }

                                @keyframes ripple {
                                    to {
                                        transform: scale(2);
                                        opacity: 0;
                                    }
                                }

                                .footer {
                                    text-align: center;
                                    padding: 2rem 0;
                                    margin-top: auto;
                                    color: #a0a0b0;
                                    font-size: 0.9rem;
                                    border-top: 1px solid rgba(60, 60, 80, 0.2);
                                    padding-top: 2rem;
                                }

                                .footer a {
                                    color: #6b7280;
                                    text-decoration: none;
                                    transition: color 0.2s ease;
                                }

                                .footer a:hover {
                                    color: #8b93a8;
                                    text-decoration: underline;
                                }

                                .footer-content {
                                    display: flex;
                                    flex-direction: column;
                                    align-items: center;
                                    gap: 1rem;
                                }

                                .source-code {
                                    display: inline-flex;
                                    align-items: center;
                                    background: rgba(30, 30, 45, 0.3);
                                    border: 1px solid rgba(60, 60, 80, 0.2);
                                    border-radius: 8px;
                                    padding: 0.5rem 1rem;
                                    font-family: 'JetBrains Mono', monospace;
                                    font-size: 0.85rem;
                                    color: #a0a0b0;
                                    transition: all 0.3s ease;
                                }

                                .source-code:hover {
                                    background: rgba(40, 40, 60, 0.4);
                                    border-color: rgba(80, 80, 120, 0.3);
                                    transform: translateY(-2px);
                                }

                                .footer-icon {
                                    color: #6b7280;
                                    font-size: 1.2rem;
                                    margin-right: 0.5rem;
                                }

                                /* Navigation icons */
                                .nav-icon {
                                    color: var(--icon-color);
                                    font-size: 26px;
                                    cursor: pointer;
                                    transition: all 0.3s ease;
                                    width: 38px;
                                    height: 38px;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    border-radius: 50%;
                                    user-select: none;
                                    position: relative;
                                    overflow: hidden;
                                }

                                .nav-icon::after {
                                    content: '';
                                    position: absolute;
                                    top: 50%;
                                    left: 50%;
                                    width: 100%;
                                    height: 100%;
                                    background-color: currentColor;
                                    border-radius: 50%;
                                    opacity: 0;
                                    transform: translate(-50%, -50%) scale(0);
                                    transition: transform 0.3s ease, opacity 0.3s ease;
                                }

                                .nav-icon:hover {
                                    transform: translateY(-2px);
                                }

                                .nav-icon:hover::after {
                                    opacity: 0.1;
                                    transform: translate(-50%, -50%) scale(1);
                                }

                                .nav-icon:active {
                                    transform: scale(0.9);
                                    transition: transform 0.1s ease;
                                }

                                .nav-icon:active::after {
                                    opacity: 0.2;
                                    transform: translate(-50%, -50%) scale(1);
                                    transition: opacity 0.1s ease;
                                }

                                /* Toggle styles (shared for theme and music) */
                                .toggle {
                                    position: relative;
                                    display: inline-block;
                                    z-index: 10;
                                    width: 24px;
                                    height: 24px;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                }
                                
                                /* Music toggle styles */
                                .music-toggle {
                                    position: relative;
                                    display: inline-block;
                                    z-index: 10;
                                    width: 24px;
                                    height: 24px;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    cursor: pointer;
                                }
                                
                                .music-toggle input {
                                    display: none;
                                }
                                
                                .music-toggle .icon {
                                    width: 24px;
                                    height: 24px;
                                    position: relative;
                                    color: var(--icon-color);
                                }
                                
                                .music-toggle .icon::before,
                                .music-toggle .icon::after {
                                    content: '';
                                    position: absolute;
                                    background: currentColor;
                                    transition: all 0.3s ease;
                                }
                                
                                /* Music playing icon (bars) */
                                .music-toggle .icon span {
                                    position: absolute;
                                    bottom: 5px;
                                    width: 4px;
                                    background: currentColor;
                                    transition: all 0.3s ease;
                                }
                                
                                .music-toggle .icon span:nth-child(1) {
                                    height: 8px;
                                    left: 4px;
                                    animation: musicBar1 1s infinite alternate;
                                }
                                
                                .music-toggle .icon span:nth-child(2) {
                                    height: 14px;
                                    left: 10px;
                                    animation: musicBar2 1.3s infinite alternate;
                                }
                                
                                .music-toggle .icon span:nth-child(3) {
                                    height: 10px;
                                    left: 16px;
                                    animation: musicBar3 0.8s infinite alternate;
                                }
                                
                                /* Music paused icon (pause symbol) */
                                .music-toggle input:checked + .icon span {
                                    animation: none;
                                    height: 14px;
                                    bottom: 5px;
                                }
                                
                                .music-toggle input:checked + .icon span:nth-child(1) {
                                    left: 6px;
                                }
                                
                                .music-toggle input:checked + .icon span:nth-child(2) {
                                    left: 14px;
                                }
                                
                                .music-toggle input:checked + .icon span:nth-child(3) {
                                    opacity: 0;
                                }
                                
                                @keyframes musicBar1 {
                                    0% { height: 8px; }
                                    100% { height: 14px; }
                                }
                                
                                @keyframes musicBar2 {
                                    0% { height: 14px; }
                                    100% { height: 6px; }
                                }
                                
                                @keyframes musicBar3 {
                                    0% { height: 10px; }
                                    100% { height: 16px; }
                                }

                                .toggle input {
                                    display: none;
                                }

                                .toggle input + div {
                                    border-radius: 50%;
                                    width: 24px;
                                    height: 24px;
                                    position: relative;
                                    border: 2px solid var(--icon-color);
                                    box-shadow: inset 8px -8px 0 0 var(--icon-color);
                                    transform: scale(1) rotate(-2deg);
                                    transition: box-shadow .5s ease 0s, transform .4s ease .1s;
                                }

                                .toggle input + div:before {
                                    content: '';
                                    width: inherit;
                                    height: inherit;
                                    border-radius: inherit;
                                    position: absolute;
                                    left: 0;
                                    top: 0;
                                    transition: background .3s ease;
                                }

                                .toggle input + div:after {
                                    content: '';
                                    width: 5px;
                                    height: 5px;
                                    border-radius: 50%;
                                    margin: -2.5px 0 0 -2.5px;
                                    position: absolute;
                                    top: 50%;
                                    left: 50%;
                                    box-shadow: 0 -14px 0 var(--icon-color),
                                        0 14px 0 var(--icon-color),
                                        14px 0 0 var(--icon-color),
                                        -14px 0 0 var(--icon-color),
                                        10px 10px 0 var(--icon-color),
                                        -10px 10px 0 var(--icon-color),
                                        10px -10px 0 var(--icon-color),
                                        -10px -10px 0 var(--icon-color);
                                    transform: scale(0);
                                    transition: all .3s ease;
                                }

                                .toggle input:checked + div {
                                    box-shadow: inset 16px -16px 0 0 var(--bg-gradient-1);
                                    transform: scale(.6) rotate(0deg);
                                    transition: transform .3s ease .1s, box-shadow .2s ease 0s;
                                }

                                .toggle input:checked + div:before {
                                    background: var(--icon-color);
                                    transition: background .3s ease .1s;
                                }

                                .toggle input:checked + div:after {
                                    transform: scale(1.5);
                                    transition: transform .5s ease .15s;
                                }

                                /* View Transitions API animations */
                                @-webkit-keyframes reveal-in {
                                    from {
                                        -webkit-clip-path: circle(0% at var(--x) var(--y));
                                    }
                                    to {
                                        -webkit-clip-path: circle(150% at var(--x) var(--y));
                                    }
                                }

                                @keyframes reveal-in {
                                    from {
                                        clip-path: circle(0% at var(--x) var(--y));
                                    }
                                    to {
                                        clip-path: circle(150% at var(--x) var(--y));
                                    }
                                }

                                @-webkit-keyframes reveal-out {
                                    from {
                                        -webkit-clip-path: circle(150% at var(--x) var(--y));
                                    }
                                    to {
                                        -webkit-clip-path: circle(0% at var(--x) var(--y));
                                    }
                                }

                                @keyframes reveal-out {
                                    from {
                                        clip-path: circle(150% at var(--x) var(--y));
                                    }
                                    to {
                                        clip-path: circle(0% at var(--x) var(--y));
                                    }
                                }

                                /* Safari fallback animations */
                                @-webkit-keyframes fade-in {
                                    from { opacity: 0; }
                                    to { opacity: 1; }
                                }

                                @keyframes fade-in {
                                    from { opacity: 0; }
                                    to { opacity: 1; }
                                }

                                @-webkit-keyframes fade-out {
                                    from { opacity: 1; }
                                    to { opacity: 0; }
                                }

                                @keyframes fade-out {
                                    from { opacity: 1; }
                                    to { opacity: 0; }
                                }

                                ::view-transition-old(root) {
                                    -webkit-animation: reveal-out 0.5s ease-in-out forwards;
                                    animation: reveal-out 0.5s ease-in-out forwards;
                                }

                                ::view-transition-new(root) {
                                    -webkit-animation: reveal-in 0.5s ease-in-out forwards;
                                    animation: reveal-in 0.5s ease-in-out forwards;
                                }

                                ::view-transition-image-pair(root) {
                                    isolation: isolate;
                                }

                                /* Responsive styles */
                                @media (max-width: 768px) {
                                    /* Container and layout */
                                    .container {
                                        padding: 0 1rem;
                                    }

                                    .content {
                                        grid-template-columns: 1fr;
                                        gap: 1.5rem;
                                    }

                                    /* Header styles */
                                    .header {
                                        padding: 1rem 0;
                                        margin-bottom: 2rem;
                                        flex-wrap: wrap;
                                    }

                                    .brand-text {
                                        align-items: center;
                                    }

                                    .logo {
                                        font-size: 1.5rem;
                                        margin-right: 0.5rem;
                                    }

                                    .subtitle {
                                        font-size: 0.75rem;
                                    }

                                    .tagline {
                                        font-size: 1.1rem;
                                        max-width: 100%;
                                    }

                                    /* Card styles */
                                    .card {
                                        padding: 1.5rem;
                                        min-height: 240px;
                                    }

                                    .card-header {
                                        gap: 0.75rem;
                                    }

                                    .card-icon {
                                        width: 48px;
                                        height: 48px;
                                        font-size: 20px;
                                    }

                                    .card-title {
                                        font-size: 1.5rem;
                                    }

                                    /* Endpoint styles */
                                    .endpoints-container {
                                        gap: 0.5rem;
                                    }

                                    .endpoint {
                                        padding: 1rem;
                                    }

                                    /* Modal styles */
                                    .modal-content {
                                        width: 95%;
                                        margin: 10% auto;
                                        padding: 1.5rem;
                                    }

                                    /* Icons and toggles */
                                    .toggle, .nav-icon {
                                        transform: scale(0.9);
                                    }
                                }

                                /* Small mobile devices */
                                @media (max-width: 480px) {
                                    .header {
                                        justify-content: space-between;
                                    }

                                    .brand-text {
                                        flex-direction: column;
                                        align-items: flex-start;
                                    }

                                    .logo {
                                        margin-right: 0;
                                        margin-bottom: 0.25rem;
                                    }

                                    .subtitle {
                                        font-size: 0.7rem;
                                    }

                                    /* Adjust toggle and nav-icon size for very small screens */
                                    .toggle, .nav-icon {
                                        transform: scale(0.8);
                                    }
                                }
                                """
                            )
                        }
                    }
                    // Include shared JavaScript
                    WebResources.includeSharedJs(this)

                    // Include page-specific JavaScript
                    script {
                        unsafe {
                            raw(
                                """
                                /**
                                 * Modal handling functions
                                 */
                                function showModal(title, content) {
                                    try {
                                        const modal = document.getElementById('apiModal');
                                        const modalTitle = document.getElementById('modalTitle');
                                        const modalContent = document.getElementById('modalContent');
                                        const modalContentDiv = document.querySelector('.modal-content');

                                        if (!modal || !modalTitle || !modalContent || !modalContentDiv) {
                                            console.error('Modal elements not found');
                                            return;
                                        }

                                        // Prepare modal content before showing
                                        modalTitle.textContent = title;
                                        modalContent.innerHTML = content;

                                        // Set initial styles
                                        modal.style.opacity = '0';
                                        modalContentDiv.style.transform = 'translateY(20px)';
                                        modal.style.display = 'block';

                                        // Force browser to process the display change before animation
                                        requestAnimationFrame(() => {
                                            // Use another requestAnimationFrame to ensure the first change was processed
                                            requestAnimationFrame(() => {
                                                modal.style.opacity = '1';
                                                modalContentDiv.style.transform = 'translateY(0)';
                                            });
                                        });

                                        document.body.style.overflow = 'hidden';

                                        // Add keyboard event listener for Escape key
                                        document.addEventListener('keydown', handleEscKey);
                                    } catch (error) {
                                        console.error('Error showing modal:', error);
                                    }
                                }

                                function closeModal() {
                                    try {
                                        const modal = document.getElementById('apiModal');
                                        const modalContentDiv = document.querySelector('.modal-content');

                                        if (!modal || !modalContentDiv) {
                                            console.error('Modal elements not found');
                                            return;
                                        }

                                        // Get the computed transition duration to ensure we wait the right amount of time
                                        const transitionDuration = getTransitionDuration(modal) || 300;

                                        // Trigger animation
                                        requestAnimationFrame(() => {
                                            modal.style.opacity = '0';
                                            modalContentDiv.style.transform = 'translateY(20px)';

                                            // Wait for animation to complete before hiding
                                            setTimeout(() => {
                                                modal.style.display = 'none';
                                                document.body.style.overflow = 'auto';
                                            }, transitionDuration);
                                        });

                                        // Remove keyboard event listener
                                        document.removeEventListener('keydown', handleEscKey);
                                    } catch (error) {
                                        console.error('Error closing modal:', error);
                                        // Fallback in case of error
                                        try {
                                            const modal = document.getElementById('apiModal');
                                            if (modal) {
                                                modal.style.display = 'none';
                                                document.body.style.overflow = 'auto';
                                            }
                                        } catch (e) {
                                            console.error('Critical error closing modal:', e);
                                        }
                                    }
                                }

                                function handleEscKey(event) {
                                    if (event.key === 'Escape') {
                                        closeModal();
                                    }
                                }

                                // Close modal when clicking outside
                                window.onclick = function(event) {
                                    const modal = document.getElementById('apiModal');
                                    if (event.target === modal) {
                                        closeModal();
                                    }
                                }

                                /**
                                 * Initialize event listeners for interactive elements
                                 */
                                function initializeEventListeners() {
                                    // GitHub link
                                    const githubLink = document.getElementById('github-link');
                                    if (githubLink) {
                                        githubLink.addEventListener('click', function() {
                                            const url = this.getAttribute('data-url');
                                            if (url) {
                                                window.open(url, '_blank');
                                            }
                                        });
                                    }

                                    // Modal close button
                                    const modalClose = document.getElementById('modal-close');
                                    if (modalClose) {
                                        modalClose.addEventListener('click', closeModal);
                                    }

                                    // API cards
                                    const apiCards = document.querySelectorAll('.card[data-title][data-content]');
                                    apiCards.forEach(card => {
                                        card.addEventListener('click', function() {
                                            const title = this.getAttribute('data-title');
                                            const content = this.getAttribute('data-content');
                                            if (title && content) {
                                                showModal(title, content);
                                            }
                                        });
                                    });
                                }

                                // Initialize theme toggle functionality is now handled by shared resources

                                // Initialize click feedback for interactive elements is now handled by shared resources

                                // Background music functionality is now handled by shared resources
                                """
                            )
                        }
                    }
                }
                body {
                    div {
                        classes = setOf("container")
                        div {
                            classes = setOf("header")
                            div {
                                classes = setOf("brand-text")
                                h1 {
                                    classes = setOf("logo")
                                    +"Androidplay"
                                }
                                span {
                                    classes = setOf("subtitle")
                                    +"API Portal"
                                }
                            }
                            div {
                                style = "flex-grow: 1;"
                            }
                            div {
                                style = "display: flex; align-items: center; gap: 1rem;"

                                // Theme toggle
                                label {
                                    classes = setOf("toggle")
                                    style = "position: relative; cursor: pointer; margin-right: 0.5rem;"

                                    input {
                                        type = InputType.checkBox
                                        id = "theme-toggle"
                                    }

                                    div {
                                        // This div becomes the toggle button
                                    }
                                }


                                // GitHub icon
                                span {
                                    classes = setOf("material-icons", "nav-icon", "github-link")
                                    id = "github-link"
                                    attributes["data-url"] = "https://github.com/bosankus/weatherify-api-ktor"
                                    +"code"
                                }
                            }
                        }

                        
                        div {
                            classes = setOf("content")
                            div {
                                classes = setOf("card")
                                id = "weather-api-card"
                                attributes["data-title"] = "Weather API"
                                attributes["data-content"] = "<h3>Weather API Endpoints</h3>" +
                                    "<div class='response-example'>" +
                                    "<strong>GET /weather</strong><br>" +
                                    "<span class='param-label'>Parameters:</span> <code>lat</code> (latitude), <code>lon</code> (longitude)<br>" +
                                    "<span class='desc-label'>Description:</span> Returns comprehensive weather data including current conditions, hourly and daily forecasts, and weather alerts" +
                                    "</div>" +
                                    "<div class='response-example'>" +
                                    "<strong>GET /air-pollution</strong><br>" +
                                    "<span class='param-label'>Parameters:</span> <code>lat</code> (latitude), <code>lon</code> (longitude)<br>" +
                                    "<span class='desc-label'>Description:</span> Returns detailed air quality information including AQI and pollutant concentrations" +
                                    "</div>" +
                                    "<h4>Weather Response Model:</h4>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Weather</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"id\": Long?,\n" +
                                    "  \"alerts\": List&lt;Alert?&gt;?,\n" +
                                    "  \"current\": Current?,\n" +
                                    "  \"daily\": List&lt;Daily?&gt;?,\n" +
                                    "  \"hourly\": List&lt;Hourly?&gt;?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Alert</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"description\": String?,\n" +
                                    "  \"end\": Int?,\n" +
                                    "  \"event\": String?,\n" +
                                    "  \"senderName\": String?,\n" +
                                    "  \"start\": Int?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Current</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"clouds\": Int?,\n" +
                                    "  \"dt\": Long?,\n" +
                                    "  \"feelsLike\": Double?,\n" +
                                    "  \"humidity\": Int?,\n" +
                                    "  \"pressure\": Int?,\n" +
                                    "  \"sunrise\": Int?,\n" +
                                    "  \"sunset\": Int?,\n" +
                                    "  \"temp\": Double?,\n" +
                                    "  \"uvi\": Double?,\n" +
                                    "  \"weather\": List&lt;WeatherData?&gt;?,\n" +
                                    "  \"wind_gust\": Double?,\n" +
                                    "  \"wind_speed\": Double?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Daily</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"clouds\": Int?,\n" +
                                    "  \"dewPoint\": Double?,\n" +
                                    "  \"dt\": Long?,\n" +
                                    "  \"humidity\": Int?,\n" +
                                    "  \"pressure\": Int?,\n" +
                                    "  \"rain\": Double?,\n" +
                                    "  \"summary\": String?,\n" +
                                    "  \"sunrise\": Int?,\n" +
                                    "  \"sunset\": Int?,\n" +
                                    "  \"temp\": Temp?,\n" +
                                    "  \"uvi\": Double?,\n" +
                                    "  \"weather\": List&lt;WeatherData?&gt;?,\n" +
                                    "  \"windGust\": Double?,\n" +
                                    "  \"windSpeed\": Double?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Temp</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"day\": Double?,\n" +
                                    "  \"eve\": Double?,\n" +
                                    "  \"max\": Double?,\n" +
                                    "  \"min\": Double?,\n" +
                                    "  \"morn\": Double?,\n" +
                                    "  \"night\": Double?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Hourly</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"clouds\": Int?,\n" +
                                    "  \"dt\": Long?,\n" +
                                    "  \"feelsLike\": Double?,\n" +
                                    "  \"humidity\": Int?,\n" +
                                    "  \"temp\": Double?,\n" +
                                    "  \"weather\": List&lt;WeatherData?&gt;?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>WeatherData</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"description\": String,\n" +
                                    "  \"icon\": String,\n" +
                                    "  \"id\": Int?,\n" +
                                    "  \"main\": String\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<h4>Air Quality Response Model:</h4>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>AirQuality</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"list\": List&lt;QualityDetails?&gt;?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>QualityDetails</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"components\": Components?,\n" +
                                    "  \"dt\": Int?,\n" +
                                    "  \"main\": Main?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Components</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"co\": Double?,\n" +
                                    "  \"nh3\": Double?,\n" +
                                    "  \"no\": Double?,\n" +
                                    "  \"no2\": Double?,\n" +
                                    "  \"o3\": Double?,\n" +
                                    "  \"pm10\": Double?,\n" +
                                    "  \"pm25\": Double?,\n" +
                                    "  \"so2\": Double?\n" +
                                    "}</pre>" +
                                    "</div>" +
                                    "<div class='response-example model-container'>" +
                                    "<div class='model-header'>Main</div>" +
                                    "<pre class='model-content'>" +
                                    "{\n" +
                                    "  \"aqi\": Int?\n" +
                                    "}</pre>" +
                                    "</div>"
                                div {
                                    classes = setOf("card-header")
                                    div {
                                        classes = setOf("card-icon")
                                        span {
                                            classes = setOf("material-icons")
                                            +"cloud"
                                        }
                                    }
                                    h3 {
                                        classes = setOf("card-title")
                                        +"Weather API"
                                    }
                                }
                                p {
                                    classes = setOf("card-description")
                                    +"Access real-time weather information with comprehensive data including temperature, humidity, wind speed, and atmospheric conditions."
                                }
                                div {
                                    classes = setOf("endpoints-container")
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "get")
                                            +"GET"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/weather"
                                        }
                                    }
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "get")
                                            +"GET"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/air-pollution"
                                        }
                                    }
                                }
                            }

                            div {
                                classes = setOf("card")
                                id = "feedback-api-card"
                                attributes["data-title"] = "Feedback API"
                                attributes["data-content"] = "<h3>Feedback API Endpoints</h3>" +
                                        "<div class='response-example'>" +
                                        "<strong>GET /feedback</strong><br>" +
                                        "<span class='param-label'>Parameters:</span> <code>id</code> (feedback ID)<br>" +
                                        "<span class='desc-label'>Description:</span> Returns feedback details for the specified ID" +
                                        "</div>" +
                                        "<div class='response-example'>" +
                                        "<strong>POST /feedback</strong><br>" +
                                        "<span class='param-label'>Parameters:</span> <code>deviceId</code>, <code>deviceOs</code>, <code>feedbackTitle</code>, <code>feedbackDescription</code><br>" +
                                        "<span class='desc-label'>Description:</span> Creates a new feedback entry and returns the ID" +
                                        "</div>" +
                                        "<div class='response-example'>" +
                                        "<strong>DELETE /feedback</strong><br>" +
                                        "<span class='param-label'>Parameters:</span> <code>id</code> (feedback ID)<br>" +
                                        "<span class='desc-label'>Description:</span> Deletes the feedback with the specified ID" +
                                        "</div>" +
                                        "<h4>Feedback Model:</h4>" +
                                        "<div class='response-example model-container'>" +
                                        "<div class='model-header'>Feedback</div>" +
                                        "<pre class='model-content'>" +
                                        "{\n" +
                                        "  \"_id\": String,\n" +
                                        "  \"deviceId\": String,\n" +
                                        "  \"deviceOs\": String,\n" +
                                        "  \"feedbackTitle\": String,\n" +
                                        "  \"feedbackDescription\": String,\n" +
                                        "  \"timestamp\": String\n" +
                                        "}</pre>" +
                                        "</div>" +
                                        "<h4>Response Format:</h4>" +
                                        "<div class='response-example model-container'>" +
                                        "<div class='model-header'>ApiResponse</div>" +
                                        "<pre class='model-content'>" +
                                        "{\n" +
                                        "  \"status\": Boolean,\n" +
                                        "  \"message\": String,\n" +
                                        "  \"data\": T\n" +
                                        "}</pre>" +
                                        "</div>"
                                div {
                                    classes = setOf("card-header")
                                    div {
                                        classes = setOf("card-icon")
                                        span {
                                            classes = setOf("material-icons")
                                            +"feedback"
                                        }
                                    }
                                    h3 {
                                        classes = setOf("card-title")
                                        +"Feedback API"
                                    }
                                }
                                p {
                                    classes = setOf("card-description")
                                    +"Submit and manage user feedback with comprehensive response handling and data validation."
                                }
                                div {
                                    classes = setOf("endpoints-container")
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "get")
                                            +"GET"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/feedback"
                                        }
                                    }
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "post")
                                            +"POST"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/feedback"
                                        }
                                    }
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "delete")
                                            +"DELETE"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/feedback"
                                        }
                                    }
                                }

                            }

                            div {
                                classes = setOf("card")
                                id = "auth-api-card"
                                attributes["data-title"] = "Authentication API"
                                attributes["data-content"] =
                                    "<h3>Authentication API Endpoints</h3>" +
                                            "<div class='response-example'>" +
                                            "<strong>POST /register</strong><br>" +
                                            "<span class='param-label'>Parameters:</span> <code>email</code>, <code>password</code><br>" +
                                            "<span class='desc-label'>Description:</span> Registers a new user with email and password. Password must be at least 8 characters long and contain uppercase, lowercase, digit, and special character." +
                                            "</div>" +
                                            "<div class='response-example'>" +
                                            "<strong>POST /login</strong><br>" +
                                            "<span class='param-label'>Parameters:</span> <code>email</code>, <code>password</code><br>" +
                                            "<span class='desc-label'>Description:</span> Authenticates a user and returns a JWT token for accessing protected resources." +
                                            "</div>" +
                                            "<h4>Request Models:</h4>" +
                                            "<div class='response-example model-container'>" +
                                            "<div class='model-header'>UserRegistrationRequest</div>" +
                                            "<pre class='model-content'>" +
                                            "{\n" +
                                            "  \"email\": String,\n" +
                                            "  \"password\": String\n" +
                                            "}</pre>" +
                                            "</div>" +
                                            "<div class='response-example model-container'>" +
                                            "<div class='model-header'>UserLoginRequest</div>" +
                                            "<pre class='model-content'>" +
                                            "{\n" +
                                            "  \"email\": String,\n" +
                                            "  \"password\": String\n" +
                                            "}</pre>" +
                                            "</div>" +
                                            "<h4>Response Models:</h4>" +
                                            "<div class='response-example model-container'>" +
                                            "<div class='model-header'>LoginResponse</div>" +
                                            "<pre class='model-content'>" +
                                            "{\n" +
                                            "  \"token\": String,\n" +
                                            "  \"email\": String\n" +
                                            "}</pre>" +
                                            "</div>" +
                                            "<div class='response-example model-container'>" +
                                            "<div class='model-header'>ApiResponse</div>" +
                                            "<pre class='model-content'>" +
                                            "{\n" +
                                            "  \"status\": Boolean,\n" +
                                            "  \"message\": String,\n" +
                                            "  \"data\": T\n" +
                                            "}</pre>" +
                                            "</div>"
                                div {
                                    classes = setOf("card-header")
                                    div {
                                        classes = setOf("card-icon")
                                        span {
                                            classes = setOf("material-icons")
                                            +"security"
                                        }
                                    }
                                    h3 {
                                        classes = setOf("card-title")
                                        +"Authentication API"
                                    }
                                }
                                p {
                                    classes = setOf("card-description")
                                    +"Secure user authentication with JWT tokens for protected resource access and comprehensive validation."
                                }
                                div {
                                    classes = setOf("endpoints-container")
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "post")
                                            +"POST"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/register"
                                        }
                                    }
                                    div {
                                        classes = setOf("endpoint")
                                        span {
                                            classes = setOf("method", "post")
                                            +"POST"
                                        }
                                        span {
                                            classes = setOf("endpoint-path")
                                            +"/login"
                                        }
                                    }
                                }
                            }

                        }

                        // Modal
                        div {
                            id = "apiModal"
                            classes = setOf("modal")
                            div {
                                classes = setOf("modal-content")
                                span {
                                    classes = setOf("close")
                                    id = "modal-close"
                                    +"×"
                                }
                                h2 {
                                    id = "modalTitle"
                                    +"API Details"
                                }
                                div {
                                    id = "modalContent"
                                    +""
                                }
                            }
                        }

                        // Footer
                        footer {
                            classes = setOf("footer")
                            div {
                                classes = setOf("footer-content")
                                p {
                                    +"© 2025 Androidplay. All rights reserved."
                                }
                            }
                        }
                    }
                }
            }
        }

        route("/favicon.ico") {
            get {
                call.respondText(text = "Greetings! $pageName is currently dry or hidden 😉. Please hydrate elsewhere.")
            }
        }
    }
}
