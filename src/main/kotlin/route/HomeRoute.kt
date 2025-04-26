package bose.ankush.route

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Route.homeRoute() {
    val name = "Androidplay API Portal"
    route("/") {
        get {
            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title { +name }
                    style {
                        unsafe {
                            raw(
                                """
                                body {
                                    background-color: #1e1e1e;
                                    color: #f0f0f0;
                                    display: flex; /* Enable flexbox */
                                    flex-direction: column; /* Arrange children in a column */
                                    justify-content: center; /* Center vertically */
                                    align-items: center; /* Center horizontally */
                                    min-height: 100vh;
                                    margin: 0;
                                    font-family: 'Product Sans', sans-serif;
                                    text-align: center;
                                }
                                h4 {
                                    font-size: 1.2em;
                                    margin-bottom: 0.5em; /* Add some spacing between h4 elements */
                                }
                            """
                            )
                        }
                    }
                }
                body {
                    h4 {
                        +"Greetings"
                    }
                    p {
                        +"$name is currently dry or hidden 😉. Please hydrate elsewhere."
                    }
                }
            }
        }
    }

    route("/favicon.ico") {
        get {
            call.respondText(text = "Greetings! $name is currently dry or hidden 😉. Please hydrate elsewhere.")
        }
    }
}