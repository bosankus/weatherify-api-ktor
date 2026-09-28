package util

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import util.AuthHelper.requireBotSharedSecretOrRespond
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthHelperTest {
    private companion object {
        const val EXPECTED_SECRET = "configured-bot-secret"
    }

    @Test
    fun `missing bot secret is rejected`() = testApplication {
        application {
            routing {
                get("/gate") {
                    if (call.requireBotSharedSecretOrRespond(EXPECTED_SECRET)) {
                        call.respondText("ok")
                    }
                }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/gate").status)
    }

    @Test
    fun `blank bot secret is rejected`() = testApplication {
        application {
            routing {
                get("/gate") {
                    if (call.requireBotSharedSecretOrRespond(EXPECTED_SECRET)) {
                        call.respondText("ok")
                    }
                }
            }
        }

        val response = client.get("/gate") {
            header("X-Bot-Token", "   ")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `wrong and dummy bot secrets are rejected`() = testApplication {
        application {
            routing {
                get("/gate") {
                    if (call.requireBotSharedSecretOrRespond(EXPECTED_SECRET)) {
                        call.respondText("ok")
                    }
                }
            }
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/gate") { header(HttpHeaders.Authorization, "Bearer wrong-secret") }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/gate") { header("X-Bot-Token", "dummy") }.status,
        )
    }

    @Test
    fun `blank configured bot secret fails closed`() = testApplication {
        application {
            routing {
                get("/gate") {
                    if (call.requireBotSharedSecretOrRespond("   ")) {
                        call.respondText("ok")
                    }
                }
            }
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/gate") { header("X-Bot-Token", "anything") }.status,
        )
    }

    @Test
    fun `public example dummy configured bot secret fails closed`() = testApplication {
        application {
            routing {
                get("/gate") {
                    if (call.requireBotSharedSecretOrRespond("dummy_bot_atlassian_shared_secret")) {
                        call.respondText("ok")
                    }
                }
            }
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/gate") {
                header("X-Bot-Token", "dummy_bot_atlassian_shared_secret")
            }.status,
        )
    }

    @Test
    fun `configured bot secret is accepted`() = testApplication {
        application {
            routing {
                get("/gate") {
                    if (call.requireBotSharedSecretOrRespond(EXPECTED_SECRET)) {
                        call.respondText("ok")
                    }
                }
            }
        }

        val response = client.get("/gate") {
            header("X-Bot-Token", EXPECTED_SECRET)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
