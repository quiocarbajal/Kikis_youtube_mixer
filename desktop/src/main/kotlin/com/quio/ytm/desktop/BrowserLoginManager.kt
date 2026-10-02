package com.quio.ytm.desktop

import com.google.gson.JsonParser
import com.quio.ytm.core.api.InnertubeClient
import kotlinx.coroutines.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient as JvmHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class BrowserLoginManager(
    private val innertubeClient: InnertubeClient,
    private val localPlaylistManager: LocalPlaylistManager,
    private val authFile: File,
    private val debugPort: Int = 9223
) {
    private var loginProcess: Process? = null
    private var pollerJob: Job? = null
    private val httpClient = JvmHttpClient.newHttpClient()

    @Volatile
    var isLoggingIn: Boolean = false
        private set

    fun start1ClickLogin(onSuccess: suspend () -> Unit, onError: (String) -> Unit) {
        if (isLoggingIn) return
        isLoggingIn = true

        val os = System.getProperty("os.name").lowercase()
        val profileDir = File(System.getProperty("java.io.tmpdir"), "kiki_ytm_login_profile")
        profileDir.mkdirs()

        val browserPaths = if (os.contains("mac")) {
            listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Chromium.app/Contents/MacOS/Chromium"
            )
        } else if (os.contains("win")) {
            listOf(
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
            )
        } else {
            listOf("google-chrome", "chromium-browser", "chromium", "brave-browser")
        }

        val browserExec = browserPaths.firstOrNull { File(it).exists() } ?: "google-chrome"
        val loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com"

        try {
            val pb = ProcessBuilder(
                browserExec,
                "--user-data-dir=${profileDir.absolutePath}",
                "--remote-debugging-port=$debugPort",
                "--no-first-run",
                "--no-default-browser-check",
                "--app=$loginUrl"
            )
            loginProcess = pb.start()
            println("🔑 1-Click Login browser window launched: $browserExec (CDP Port: $debugPort)")
        } catch (e: Exception) {
            isLoggingIn = false
            System.err.println("Failed to launch 1-Click Login browser: ${e.message}")
            onError("Could not launch browser automatically: ${e.message}")
            return
        }

        // Start background CDP cookie polling
        pollerJob = CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            val timeoutMs = 5 * 60 * 1000L // 5 minutes timeout

            while (isActive && isLoggingIn && (System.currentTimeMillis() - startTime < timeoutMs)) {
                delay(1200L)
                try {
                    val captured = pollCookiesFromCdp()
                    if (captured != null && captured.cookie.isNotBlank() && captured.sapisid.isNotBlank()) {
                        println("🎉 Authenticated session cookies successfully captured from browser!")
                        innertubeClient.setCookies(captured.cookie, captured.sapisid)
                        try {
                            val json = kotlinx.serialization.json.Json { prettyPrint = true }
                            authFile.writeText(json.encodeToString(kotlinx.serialization.serializer(), captured))
                        } catch (_: Exception) {}

                        // Clean up browser window
                        stopLogin()

                        // Trigger library sync and success callback
                        onSuccess()
                        break
                    }
                } catch (_: Exception) {}

                // Check if browser was closed manually by user
                if (loginProcess != null && !loginProcess!!.isAlive) {
                    println("Login browser window closed by user.")
                    isLoggingIn = false
                    break
                }
            }
            isLoggingIn = false
        }
    }

    fun stopLogin() {
        isLoggingIn = false
        pollerJob?.cancel()
        pollerJob = null
        try {
            loginProcess?.destroyForcibly()
        } catch (_: Exception) {}
        loginProcess = null
    }

    private suspend fun pollCookiesFromCdp(): AuthData? {
        return withContext(Dispatchers.IO) {
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI("http://127.0.0.1:$debugPort/json"))
                    .GET()
                    .build()
                val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() != 200) return@withContext null

                val root = JsonParser.parseString(resp.body()).asJsonArray
                var wsUrl: String? = null
                for (item in root) {
                    val obj = item.asJsonObject
                    val url = obj.get("url")?.asString ?: ""
                    if (url.contains("youtube.com") || url.contains("google.com")) {
                        wsUrl = obj.get("webSocketDebuggerUrl")?.asString
                        if (wsUrl != null) break
                    }
                }
                if (wsUrl == null && root.size() > 0) {
                    wsUrl = root.get(0).asJsonObject.get("webSocketDebuggerUrl")?.asString
                }
                if (wsUrl == null) return@withContext null

                val cookiesFuture = CompletableFuture<List<Pair<String, String>>>()
                val wsListener = object : WebSocket.Listener {
                    private val buffer = StringBuilder()

                    override fun onOpen(webSocket: WebSocket) {
                        val cmd = """{"id": 100, "method": "Network.getCookies", "params": {"urls": ["https://music.youtube.com", "https://youtube.com", "https://google.com"]}}"""
                        webSocket.sendText(cmd, true)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                        buffer.append(data)
                        if (last) {
                            try {
                                val json = JsonParser.parseString(buffer.toString()).asJsonObject
                                if (json.has("result") && json.getAsJsonObject("result").has("cookies")) {
                                    val cookiesArr = json.getAsJsonObject("result").getAsJsonArray("cookies")
                                    val list = mutableListOf<Pair<String, String>>()
                                    for (c in cookiesArr) {
                                        val cObj = c.asJsonObject
                                        val name = cObj.get("name")?.asString ?: ""
                                        val value = cObj.get("value")?.asString ?: ""
                                        if (name.isNotEmpty()) {
                                            list.add(name to value)
                                        }
                                    }
                                    cookiesFuture.complete(list)
                                } else {
                                    cookiesFuture.complete(emptyList())
                                }
                            } catch (e: Exception) {
                                cookiesFuture.completeExceptionally(e)
                            }
                        }
                        return null
                    }

                    override fun onError(webSocket: WebSocket, error: Throwable) {
                        cookiesFuture.completeExceptionally(error)
                    }
                }

                val ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI(wsUrl), wsListener)
                    .join()

                val cookieList = withTimeoutOrNull(2500L) {
                    cookiesFuture.get()
                } ?: emptyList()

                try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "done") } catch (_: Exception) {}

                val cookieMap = cookieList.toMap()
                val sapisid = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"] ?: ""
                if (sapisid.isNotEmpty()) {
                    val cookieStr = cookieList.joinToString("; ") { "${it.first}=${it.second}" }
                    return@withContext AuthData(cookie = cookieStr, sapisid = sapisid)
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
