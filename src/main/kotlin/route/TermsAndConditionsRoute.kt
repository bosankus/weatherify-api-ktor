package bose.ankush.route

import bose.ankush.route.common.WebResources
import bose.ankush.route.common.setCacheHeaders
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import java.time.Year

/**
 * Route for Terms and Conditions page
 */
fun Route.termsAndConditionsRoute() {
    val pageName = "Weatherify - Terms & Conditions"

    route("/wfy/terms-and-conditions") {
        get {
            // Cache static content for 1 hour (3600 seconds)
            call.setCacheHeaders(maxAgeSeconds = 3600, isPublic = true, mustRevalidate = true)
            call.respondHtml(HttpStatusCode.OK) {
                attributes["lang"] = "en"
                head {
                    WebResources.includeGoogleTag(this)
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
                        href =
                            "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
                    }
                    link {
                        rel = "stylesheet"
                        href = "https://fonts.googleapis.com/icon?family=Material+Icons"
                    }
                    // Include shared CSS
                    WebResources.includeSharedCss(this)
                    WebResources.includeAdminHeaderCss(this)

                    // Include page-specific CSS
                    style {
                        unsafe {
                            raw(
                                """
                                /* Mobile-first responsive styles for terms and conditions */
                                .app-header__subtitle {
                                    text-transform: uppercase;
                                }

                                .terms-content {
                                    max-width: 900px;
                                    margin: 2rem auto;
                                    padding: 2rem;
                                }

                                @media (max-width: 768px) {
                                    .terms-content {
                                        margin: 1rem auto;
                                        padding: 1.5rem;
                                    }

                                    .terms-content h1 {
                                        font-size: 1.75rem;
                                    }

                                    .terms-content h2 {
                                        font-size: 1.35rem;
                                    }
                                }

                                @media (max-width: 480px) {
                                    .terms-content {
                                        padding: 1rem;
                                    }

                                    .terms-content h1 {
                                        font-size: 1.5rem;
                                    }

                                    .terms-content h2 {
                                        font-size: 1.2rem;
                                    }

                                    .terms-content p {
                                        font-size: 0.95rem;
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
                                document.addEventListener('DOMContentLoaded', function() {
                                    // Initialize header with custom subtitle
                                    if (typeof updateHeaderText === 'function') {
                                        updateHeaderText('Androidplay', 'Terms & Conditions');
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

                        // Use the same header structure as admin dashboard
                        header {
                            classes = setOf("app-header")
                            attributes["role"] = "banner"

                            div {
                                classes = setOf("app-header__container")

                                // Brand section
                                div {
                                    classes = setOf("app-header__brand")
                                    a {
                                        href = "/weather"
                                        id = "header-logo-link"
                                        classes = setOf("app-header__logo-link")
                                        attributes["aria-label"] = "Androidplay Home"
                                        h1 {
                                            classes = setOf("app-header__logo")
                                            +"Androidplay"
                                        }
                                    }
                                    span {
                                        id = "header-subtitle"
                                        classes = setOf("app-header__subtitle")
                                        +"TERMS & CONDITIONS"
                                    }
                                }

                                // Spacer
                                div {
                                    classes = setOf("app-header__spacer")
                                }

                                // Actions container with theme toggle
                                div {
                                    id = "header-actions"
                                    classes = setOf("app-header__actions")
                                    attributes["role"] = "navigation"
                                    attributes["aria-label"] = "Header actions"

                                    // Theme toggle
                                    label {
                                        classes = setOf("admin-header__theme-toggle")
                                        attributes["aria-label"] = "Toggle theme"

                                        input {
                                            type = InputType.checkBox
                                            id = "theme-toggle"
                                            attributes["aria-label"] = "Toggle dark/light theme"
                                        }

                                        div {
                                            classes = setOf("admin-header__theme-icon")
                                        }
                                    }
                                }
                            }
                        }


                        // Terms and Conditions content
                        div {
                            classes = setOf("terms-content")

                            h1 { +"Weatherify Android App Terms & Conditions" }
                            p {
                                classes = setOf("last-updated")
                                +"Last Updated: July 27, 2025"
                            }

                            p {
                                +"Welcome to Weatherify. By downloading, installing, or using our application, you agree to be bound by these Terms and Conditions. Please read them carefully before using the app."
                            }

                            h2 { +"1. Data Accuracy Disclaimer" }
                            p {
                                +"The weather data provided through the Weatherify app is sourced from third-party weather services and is provided on an \"as-is\" and \"as-available\" basis. While we strive to ensure the accuracy and reliability of the information presented, we cannot guarantee that the weather data will always be precise, complete, or up-to-date."
                            }
                            p {
                                +"Weather forecasts, by their nature, involve predictions based on complex meteorological models and may not always reflect actual weather conditions. Users should be aware that sudden changes in weather patterns may not be immediately reflected in our app."
                            }
                            p {
                                +"We recommend that users verify critical weather information through multiple sources, especially when making important decisions based on weather conditions."
                            }

                            h2 { +"2. Data Usage and Privacy" }
                            p {
                                +"To provide location-specific weather information, Weatherify may collect and process your device's location data. This information is used solely for the purpose of delivering relevant weather forecasts and alerts for your area."
                            }
                            p {
                                +"We implement appropriate technical and organizational measures to protect your personal data. Your location data is anonymized and is not shared with third parties except as necessary to provide our services (such as with our weather data providers)."
                            }
                            p {
                                +"You can control location permissions through your device settings. Please note that disabling location services may affect the app's ability to provide accurate local weather information."
                            }

                            h2 { +"3. Service Availability" }
                            p {
                                +"While we strive to maintain continuous availability of the Weatherify app, we do not guarantee uninterrupted access to our services. The app may be subject to occasional downtime for maintenance, updates, or due to technical issues beyond our control."
                            }
                            p {
                                +"Weather data delivery depends on various factors including your device's internet connectivity, the availability of our third-party weather data providers, and other technical considerations. We are not liable for any interruptions in service or delays in data updates."
                            }
                            p {
                                +"We reserve the right to modify, suspend, or discontinue any part of our service temporarily or permanently, with or without notice."
                            }

                            h2 { +"4. Third-Party API Usage" }
                            p {
                                +"Weatherify relies on third-party weather data providers to deliver forecasts and other meteorological information. Your use of our app is also subject to the terms and conditions of these third-party services."
                            }
                            p {
                                +"We are not responsible for the accuracy, reliability, or availability of data provided by these third parties. Any limitations, delays, or errors in their services may affect the information displayed in our app."
                            }
                            p {
                                +"Links to third-party websites or services may be provided in the app for your convenience. These links are not under our control, and we are not responsible for the content or privacy practices of these external sites."
                            }

                            h2 { +"5. User Conduct" }
                            p {
                                +"When using the Weatherify app, you agree not to:"
                            }
                            p {
                                +"• Use the app for any unlawful purpose or in violation of any applicable laws"
                            }
                            p {
                                +"• Attempt to gain unauthorized access to any part of the service or its related systems"
                            }
                            p {
                                +"• Interfere with or disrupt the integrity or performance of the app or its data"
                            }
                            p {
                                +"• Reproduce, duplicate, copy, sell, resell, or exploit any portion of the app without express written permission"
                            }
                            p {
                                +"• Use automated means or interfaces not provided by us to access the app or extract data"
                            }

                            h2 { +"6. Intellectual Property" }
                            p {
                                +"The Weatherify app, including its design, graphics, text, and other content, is protected by copyright, trademark, and other intellectual property laws. All rights not expressly granted to you are reserved by us or our licensors."
                            }
                            p {
                                +"You may not modify, adapt, translate, reverse engineer, decompile, or disassemble any portion of the app without our prior written consent."
                            }
                            p {
                                +"The Weatherify name and logo are trademarks owned by us. No right or license to use any of our trademarks is granted without our prior written permission."
                            }

                            h2 { +"7. Limitation of Liability" }
                            p {
                                +"To the maximum extent permitted by applicable law, in no event shall Weatherify, its affiliates, or their respective officers, directors, employees, or agents be liable for any indirect, incidental, special, consequential, or punitive damages, including but not limited to loss of profits, data, use, or goodwill, arising out of or in connection with your use of the app."
                            }
                            p {
                                +"We specifically disclaim liability for any actions you take or decisions you make based on weather information provided through our app. This includes, but is not limited to, travel plans, outdoor activities, or safety-related decisions."
                            }
                            p {
                                +"In jurisdictions where the exclusion or limitation of liability for consequential or incidental damages is not allowed, our liability shall be limited to the maximum extent permitted by law."
                            }

                            h2 { +"8. Changes to Terms" }
                            p {
                                +"We reserve the right to modify these Terms and Conditions at any time. If we make material changes, we will provide notice through the app or by other means. Your continued use of the app after such modifications constitutes your acceptance of the updated terms."
                            }
                            p {
                                +"It is your responsibility to review these Terms periodically for changes. If you do not agree with the modified terms, you should discontinue using the app."
                            }

                            h2 { +"9. Governing Law" }
                            p {
                                +"These Terms and Conditions shall be governed by and construed in accordance with the laws of India, without regard to its conflict of law provisions."
                            }
                            p {
                                +"Any disputes arising under or in connection with these Terms shall be subject to the exclusive jurisdiction of the courts located in India."
                            }

                            h2 { +"10. Contact Information" }
                            p {
                                +"If you have any questions or concerns about these Terms and Conditions, please contact us at ankush@androidplay.in."
                            }
                        }

                        // Footer
                        footer {
                            classes = setOf("footer")
                            div {
                                classes = setOf("footer-content")
                                div {
                                    classes = setOf("footer-copyright")
                                    +"© ${Year.now().value} Androidplay. All rights reserved."
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
