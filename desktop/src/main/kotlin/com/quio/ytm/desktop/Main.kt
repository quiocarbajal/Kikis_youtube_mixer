package com.quio.ytm.desktop

import com.quio.ytm.core.models.ActiveQueue
import com.quio.ytm.core.models.Track
import com.quio.ytm.core.shuffle.ShuffleEngine
import com.quio.ytm.core.state.ActiveQueueManager
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MoveRequest(val fromIndex: Int, val toIndex: Int)

@Serializable
data class AppendTracksRequest(val tracks: List<Track>)

@Serializable
data class PlayIndexRequest(val index: Int)

fun main() {
    val port = 8888
    println("Starting YouTube Music Player Studio (Desktop) on http://localhost:$port...")

    val desktopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val queueRepository = InMemoryQueueRepository()
    val queueManager = ActiveQueueManager(queueRepository, desktopScope)

    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        install(CORS) {
            anyHost()
            allowHeader("Content-Type")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        routing {
            // Serve frontend resources from Spotify_handler
            staticResources("/", "frontend", index = "index.html")

            get("/api/health") {
                call.respond(mapOf("status" to "ok", "app" to "YouTube Music Player Studio"))
            }

            get("/api/queue") {
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/shuffle") {
                val current = queueManager.queueState.value
                val shuffled = ShuffleEngine.shuffleQueue(
                    currentTracks = current.tracks,
                    currentIndex = current.currentIndex,
                    applyAntiClumping = true
                )
                queueManager.setQueue(shuffled, startIndex = 0)
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/move") {
                val req = call.receive<MoveRequest>()
                queueManager.moveTrack(req.fromIndex, req.toIndex)
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/append") {
                val req = call.receive<AppendTracksRequest>()
                queueManager.appendTracks(req.tracks)
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/play-next") {
                queueManager.playNext()
                call.respond(queueManager.queueState.value)
            }

            post("/api/queue/play-prev") {
                queueManager.playPrevious()
                call.respond(queueManager.queueState.value)
            }
        }
    }.start(wait = true)
}
