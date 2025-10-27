package bose.ankush.route

import bose.ankush.route.common.WebResources
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import java.time.Year

/**
 * Route for Privacy Policy page
 */
fun Route.privacyPolicyRoute() {
    val pageName = "Weatherify - Privacy Policy"

    route("/wfy/privacy-policy") {
        get {
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
                                /* Mobile-first responsive styles for privacy policy */
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
                                        updateHeaderText('Androidplay', 'Privacy Policy');
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
                                        +"PRIVACY POLICY"
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


                        // Privacy Policy content
                        div {
                            classes = setOf("terms-content")

                            h1 { +"Weatherify Android App Privacy Policy" }
                            p {
                                classes = setOf("last-updated")
                                +"Last Updated: July 27, 2025"
                            }

                            p {
                                +"At Weatherify, we take your privacy seriously. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our Weatherify mobile application. Please read this privacy policy carefully. If you do not agree with the terms of this privacy policy, please do not access the application."
                            }

                            h2 { +"1. Information We Collect" }
                            p {
                                +"We may collect information about you in various ways when you use our application. The information we may collect includes:"
                            }
                            p {
                                +"• Personal Information: Email address, device information, and IP address when you register for an account."
                            }
                            p {
                                +"• Device Information: Device model, operating system, version, and other technical data when you use our application."
                            }
                            p {
                                +"• Location Data: With your permission, we collect precise location data to provide you with localized weather information."
                            }
                            p {
                                +"• Usage Information: How you interact with our application, including features you use and time spent on the app."
                            }

                            h2 { +"2. How We Use Your Information" }
                            p {
                                +"We use the information we collect for various purposes, including to:"
                            }
                            p {
                                +"• Provide and maintain our service, including to monitor the usage of our application."
                            }
                            p {
                                +"• Manage your account and provide you with customer support."
                            }
                            p {
                                +"• Deliver location-based weather forecasts and alerts relevant to your area."
                            }
                            p {
                                +"• Improve and personalize your experience with our application."
                            }
                            p {
                                +"• Develop new products, services, features, and functionality."
                            }
                            p {
                                +"• Communicate with you about updates, security alerts, and support messages."
                            }

                            h2 { +"3. Sharing Your Information" }
                            p {
                                +"We may share information we have collected about you in certain situations. Your information may be disclosed as follows:"
                            }
                            p {
                                +"• With Service Providers: We may share your information with third-party vendors, service providers, contractors or agents who perform services for us or on our behalf and require access to such information to do that work."
                            }
                            p {
                                +"• For Business Transfers: We may share or transfer your information in connection with, or during negotiations of, any merger, sale of company assets, financing, or acquisition of all or a portion of our business to another company."
                            }
                            p {
                                +"• With Your Consent: We may disclose your personal information for any other purpose with your consent."
                            }
                            p {
                                +"• Other Legal Requirements: We may disclose your information where we are legally required to do so in order to comply with applicable law, governmental requests, a judicial proceeding, court order, or legal process."
                            }

                            h2 { +"4. Data Security" }
                            p {
                                +"We use administrative, technical, and physical security measures to help protect your personal information. While we have taken reasonable steps to secure the personal information you provide to us, please be aware that despite our efforts, no security measures are perfect or impenetrable, and no method of data transmission can be guaranteed against any interception or other type of misuse."
                            }

                            h2 { +"5. Your Privacy Rights" }
                            p {
                                +"Depending on your location, you may have certain rights regarding your personal information, such as:"
                            }
                            p {
                                +"• The right to access personal information we hold about you."
                            }
                            p {
                                +"• The right to request correction of your personal information."
                            }
                            p {
                                +"• The right to request deletion of your personal information."
                            }
                            p {
                                +"• The right to object to processing of your personal information."
                            }
                            p {
                                +"• The right to data portability."
                            }
                            p {
                                +"To exercise these rights, please contact us using the information provided in the 'Contact Us' section below."
                            }

                            h2 { +"6. Children's Privacy" }
                            p {
                                +"Our application is not intended for children under 13 years of age. We do not knowingly collect personal information from children under 13. If you are a parent or guardian and you are aware that your child has provided us with personal information, please contact us so that we can take necessary actions."
                            }

                            h2 { +"7. Changes to This Privacy Policy" }
                            p {
                                +"We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page and updating the 'Last Updated' date at the top of this Privacy Policy."
                            }
                            p {
                                +"You are advised to review this Privacy Policy periodically for any changes. Changes to this Privacy Policy are effective when they are posted on this page."
                            }

                            h2 { +"8. Analytics and Third-Party Tools" }
                            p {
                                +"We may use third-party Service Providers to monitor and analyze the use of our application. These third parties may use cookies, web beacons, and other tracking technologies to collect information about your use of our application."
                            }
                            p {
                                +"These third parties may collect information such as how often you use the application, the events that occur within the application, usage, performance data, and where the application was downloaded from. This information may be used to improve our application and services."
                            }

                            h2 { +"9. Contact Us" }
                            p {
                                +"If you have any questions or concerns about this Privacy Policy, please contact us at ankush@androidplay.in."
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
