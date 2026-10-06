package com.quio.ytm.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import com.quio.ytm.BuildConfig
import com.quio.ytm.core.api.InnertubeClient
import com.quio.ytm.data.repository.YtmMixerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

class AndroidOAuthManager(
    private val innertubeClient: InnertubeClient,
    private val repository: YtmMixerRepository
) {
    companion object {
        private const val TAG = "AndroidOAuthManager"
        private val CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID
        private val CLIENT_SECRET = BuildConfig.GOOGLE_CLIENT_SECRET
        private const val PORT = 8888
        private const val REDIRECT_URI = "http://127.0.0.1:8888/callback"
    }

    private val okHttpClient = OkHttpClient()
    private val secureRandom = SecureRandom()
    private var currentCodeVerifier: String = ""
    private var currentState: String = ""
    private var serverSocket: ServerSocket? = null
    private val isCodeHandled = AtomicBoolean(false)

    fun generateAuthUrl(): String {
        currentCodeVerifier = generateCodeVerifier()
        currentState = generateRandomString(16)
        val codeChallenge = generateCodeChallenge(currentCodeVerifier)
        val scopes = listOf(
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"
        ).joinToString("%20")

        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=$CLIENT_ID" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=code" +
                "&scope=$scopes" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256" +
                "&prompt=select_account" +
                "&access_type=offline" +
                "&state=$currentState"
    }

    fun launchGoogleLogin(
        context: Context,
        onSuccess: (token: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        val authUrl = generateAuthUrl()
        isCodeHandled.set(false)

        // Close any pre-existing server socket
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}

        // Bind the server BEFORE opening browser to avoid race condition
        CoroutineScope(Dispatchers.IO).launch {
            var server: ServerSocket? = null
            var bound = false
            for (attempt in 1..8) {
                try {
                    val s = ServerSocket()
                    s.reuseAddress = true // MUST be set before bind() in Java
                    s.soTimeout = 180_000 // 3 min total timeout
                    s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 50)
                    server = s
                    bound = true
                    Log.i(TAG, "Successfully bound OAuth server to 127.0.0.1:$PORT (attempt $attempt)")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Attempt $attempt to bind port $PORT failed: ${e.message}. Retrying...")
                    try { server?.close() } catch (_: Exception) {}
                    server = null
                    kotlinx.coroutines.delay(250)
                }
            }

            if (!bound || server == null) {
                Log.e(TAG, "Failed to bind port $PORT after multiple attempts")
                withContext(Dispatchers.Main) {
                    onError("El puerto de autenticación local ($PORT) está ocupado. Intenta de nuevo en unos segundos.")
                }
                return@launch
            }

            serverSocket = server

            // Start foreground keep-alive service so OS cgroup freezer never suspends app
            com.quio.ytm.service.OAuthKeepAliveService.start(context)

            // Start listening loop
            startListeningLoop(server, context, onSuccess, onError)

            // Once server is confirmed bound, launch browser on main thread
            withContext(Dispatchers.Main) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Log.i(TAG, "Browser opened with Google OAuth URL")
                } catch (e: Exception) {
                    com.quio.ytm.service.OAuthKeepAliveService.stop(context)
                    onError("No se pudo abrir el navegador: ${e.message}")
                }
            }
        }
    }

    private fun startListeningLoop(
        server: ServerSocket,
        context: Context,
        onSuccess: (token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                while (!server.isClosed && !isCodeHandled.get()) {
                    try {
                        val clientSocket = server.accept()
                        // Use SO_LINGER=0 to immediately reset socket on close and prevent TIME_WAIT
                        try {
                            clientSocket.setSoLinger(true, 0)
                        } catch (_: Exception) {}

                        Thread {
                            handleClientSocket(clientSocket, server, context, onSuccess, onError)
                        }.start()
                    } catch (e: java.net.SocketException) {
                        break // Server closed normally
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.w(TAG, "OAuth server timeout waiting for callback")
                        com.quio.ytm.service.OAuthKeepAliveService.stop(context)
                        CoroutineScope(Dispatchers.Main).launch {
                            onError("Tiempo de espera agotado al conectar con Google.")
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OAuth listening loop error: ${e.message}", e)
            } finally {
                try {
                    server.close()
                } catch (_: Exception) {}
                if (serverSocket === server) {
                    serverSocket = null
                }
            }
        }.start()
    }

    private fun handleClientSocket(
        socket: Socket,
        server: ServerSocket,
        context: Context,
        onSuccess: (token: String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            socket.soTimeout = 4000 // 4s read timeout per connection
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: ""
            Log.d(TAG, "HTTP Request: $requestLine")

            if (requestLine.isBlank()) {
                socket.close()
                return
            }

            if (requestLine.contains("favicon.ico")) {
                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(notFound.toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()
                return
            }

            val code = extractQueryParam(requestLine, "code")
            val error = extractQueryParam(requestLine, "error")

            if (!code.isNullOrBlank()) {
                if (isCodeHandled.compareAndSet(false, true)) {
                    Log.i(TAG, "Auth code successfully received via loopback HTTP!")
                    val htmlResponse = """
                        <!DOCTYPE html>
                        <html lang="es">
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <title>Kiki's YouTube Mixer</title>
                            <style>
                                body { background-color: #030303; color: #ffffff; font-family: -apple-system, Roboto, sans-serif; text-align: center; padding: 40px 20px; }
                                h1 { color: #ff0033; font-size: 26px; margin-bottom: 8px; }
                                p { color: #aaaaaa; font-size: 16px; margin-top: 10px; }
                                .btn { display: inline-block; margin-top: 24px; padding: 14px 28px; background-color: #ff0033; color: #ffffff; text-decoration: none; border-radius: 28px; font-weight: bold; font-size: 16px; }
                            </style>
                        </head>
                        <body>
                            <h1>¡Conexión Exitosa!</h1>
                            <p>Tu cuenta de YouTube Music se ha conectado correctamente.</p>
                            <p>Regresando a la aplicación...</p>
                            <a class="btn" href="intent://#Intent;package=com.quio.ytm;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;end">Abrir App</a>
                            <script>
                                setTimeout(function() {
                                    window.location.href = "intent://#Intent;package=com.quio.ytm;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;end";
                                }, 1200);
                            </script>
                        </body>
                        </html>
                    """.trimIndent()

                    val responseBytes = htmlResponse.toByteArray(StandardCharsets.UTF_8)
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.UTF_8))
                    out.write("Content-Type: text/html; charset=UTF-8\r\n".toByteArray(StandardCharsets.UTF_8))
                    out.write("Content-Length: ${responseBytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
                    out.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                    out.write(responseBytes)
                    out.flush()

                    socket.close()
                    try {
                        server.close()
                    } catch (_: Exception) {}

                    // Exchange code for token
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.i(TAG, "Exchanging auth code for access token...")
                        val token = exchangeCodeForToken(code)
                        com.quio.ytm.service.OAuthKeepAliveService.stop(context)
                        if (!token.isNullOrBlank()) {
                            Log.i(TAG, "OAuth sign-in successful! Token saved.")
                            // Automatically bring app back to foreground
                            try {
                                val bringAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                if (bringAppIntent != null) {
                                    context.startActivity(bringAppIntent)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not bring app to foreground: ${e.message}")
                            }
                            onSuccess(token)
                        } else {
                            Log.e(TAG, "Failed to exchange authorization code for token")
                            onError("Error al canjear el código de autorización de Google.")
                        }
                    }
                } else {
                    socket.close()
                }
            } else if (!error.isNullOrBlank()) {
                Log.w(TAG, "OAuth error received: $error")
                val errHtml = """
                    <!DOCTYPE html>
                    <html lang="es">
                    <head><meta charset="utf-8"><title>Error</title></head>
                    <body style="background:#030303;color:#fff;text-align:center;padding:40px;">
                        <h1 style="color:#ff0033;">Error de Autorización</h1>
                        <p>$error</p>
                    </body>
                    </html>
                """.trimIndent()
                val responseBytes = errHtml.toByteArray(StandardCharsets.UTF_8)
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 400 Bad Request\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                out.write(responseBytes)
                out.flush()
                socket.close()
                try {
                    server.close()
                } catch (_: Exception) {}
                com.quio.ytm.service.OAuthKeepAliveService.stop(context)
                CoroutineScope(Dispatchers.Main).launch {
                    onError("OAuth error: $error")
                }
            } else {
                val empty = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(empty.toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client socket: ${e.message}")
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun extractQueryParam(requestLine: String, paramName: String): String? {
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val uri = parts[1]
        val queryStart = uri.indexOf('?')
        if (queryStart == -1) return null
        val query = uri.substring(queryStart + 1)
        val pairs = query.split("&")
        for (pair in pairs) {
            val kv = pair.split("=")
            if (kv.size == 2 && kv[0] == paramName) {
                return java.net.URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return null
    }

    suspend fun exchangeCodeForToken(code: String): String? = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("code_verifier", currentCodeVerifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            Log.d(TAG, "Token response code: ${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "Token exchange failed: $responseBody")
                return@withContext null
            }

            val root = JsonParser.parseString(responseBody).asJsonObject
            val accessToken = root.get("access_token")?.asString ?: return@withContext null
            val refreshToken = root.get("refresh_token")?.asString ?: ""
            val expiresIn = root.get("expires_in")?.asLong ?: 3600L
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

            // Save tokens to repository
            repository.setSetting("ytm_access_token", accessToken)
            if (refreshToken.isNotBlank()) {
                repository.setSetting("ytm_refresh_token", refreshToken)
            }
            repository.setSetting("ytm_token_expires_at", expiresAt.toString())

            // Fetch user profile
            try {
                val userReq = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v2/userinfo")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                val userResp = okHttpClient.newCall(userReq).execute()
                val userBody = userResp.body?.string()
                if (userResp.isSuccessful && !userBody.isNullOrBlank()) {
                    val userObj = JsonParser.parseString(userBody).asJsonObject
                    val name = userObj.get("name")?.asString ?: "Google User"
                    val email = userObj.get("email")?.asString ?: ""
                    repository.setSetting("ytm_user_name", name)
                    repository.setSetting("ytm_user_email", email)
                    Log.i(TAG, "User profile loaded: $name ($email)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching user profile: ${e.message}")
            }

            innertubeClient.setOAuthToken(accessToken)
            accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging OAuth code", e)
            null
        }
    }

    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val refreshToken = repository.getSetting("ytm_refresh_token")
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "No refresh token available in repository")
                return@withContext null
            }

            Log.i(TAG, "Refreshing Google OAuth access token using refresh_token...")
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) {
                Log.e(TAG, "Token refresh failed (HTTP ${response.code}): $responseBody")
                return@withContext null
            }

            val root = JsonParser.parseString(responseBody).asJsonObject
            val newAccessToken = root.get("access_token")?.asString ?: return@withContext null
            val expiresIn = root.get("expires_in")?.asLong ?: 3600L
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

            repository.setSetting("ytm_access_token", newAccessToken)
            repository.setSetting("ytm_token_expires_at", expiresAt.toString())
            innertubeClient.setOAuthToken(newAccessToken)

            Log.i(TAG, "Google OAuth access token successfully refreshed! Expires in $expiresIn s")
            newAccessToken
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh", e)
            null
        }
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val token = repository.getSetting("ytm_access_token")
        val refreshToken = repository.getSetting("ytm_refresh_token")
        if (token.isNullOrBlank() && refreshToken.isNullOrBlank()) return@withContext null

        val expiresAtStr = repository.getSetting("ytm_token_expires_at")
        val expiresAt = expiresAtStr?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()

        // If current token is valid for at least 5 more minutes, use it
        if (!token.isNullOrBlank() && expiresAt > (now + 300_000L)) {
            return@withContext token
        }

        // Token expired or close to expiry: automatically refresh with Google OAuth
        if (!refreshToken.isNullOrBlank()) {
            val refreshed = refreshAccessToken()
            if (!refreshed.isNullOrBlank()) {
                return@withContext refreshed
            }
        }

        token
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(StandardCharsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
