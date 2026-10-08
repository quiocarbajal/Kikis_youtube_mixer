package com.quio.ytm.desktop

import com.google.gson.JsonParser
import com.quio.ytm.core.api.InnertubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class GoogleOAuthManager(
    private val innertubeClient: InnertubeClient,
    private val authFile: File,
    private val port: Int = 8888
) {
    private val httpClient = HttpClient.newHttpClient()
    private val secureRandom = SecureRandom()
    
    companion object {
        private fun loadCredential(key: String, envKey: String): String {
            val env = System.getenv(envKey)
            if (!env.isNullOrBlank()) return env
            val propFiles = listOf(
                File("local.properties"),
                File("../local.properties"),
                File(System.getProperty("user.home"), ".kiki_ytm/local.properties"),
                File(System.getProperty("user.home"), ".kiki_ytm/oauth_config.properties")
            )
            for (f in propFiles) {
                if (f.exists()) {
                    try {
                        val props = java.util.Properties().apply { f.inputStream().use { load(it) } }
                        val value = props.getProperty(key)
                        if (!value.isNullOrBlank()) return value
                    } catch (_: Exception) {}
                }
            }
            // Check bundled classpath resource embedded during packaging
            try {
                val stream = GoogleOAuthManager::class.java.getResourceAsStream("/oauth_config.properties")
                if (stream != null) {
                    val props = java.util.Properties().apply { stream.use { load(it) } }
                    val value = props.getProperty(key)
                    if (!value.isNullOrBlank()) return value
                }
            } catch (_: Exception) {}
            return ""
        }
    }

    // Google OAuth 2.0 Credentials loaded from local.properties or environment
    private val clientId = loadCredential("google.client.id", "GOOGLE_CLIENT_ID")
    private val clientSecret = loadCredential("google.client.secret", "GOOGLE_CLIENT_SECRET")
    
    private var currentCodeVerifier: String = ""
    private var currentState: String = ""

    fun generateAuthUrl(): String {
        currentCodeVerifier = generateCodeVerifier()
        currentState = generateRandomString(16)
        val codeChallenge = generateCodeChallenge(currentCodeVerifier)
        val redirectUri = "http://127.0.0.1:$port/callback"
        val scopes = listOf(
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"
        ).joinToString("%20")

        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=$clientId" +
                "&redirect_uri=$redirectUri" +
                "&response_type=code" +
                "&scope=$scopes" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256" +
                "&prompt=select_account" +
                "&access_type=offline" +
                "&state=$currentState"
    }

    fun openBrowserForLogin(): String {
        val url = generateAuthUrl()
        val os = System.getProperty("os.name").lowercase()
        try {
            if (os.contains("mac")) {
                ProcessBuilder("open", url).start()
            } else if (os.contains("win")) {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            } else {
                ProcessBuilder("xdg-open", url).start()
            }
        } catch (_: Exception) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                }
            } catch (_: Exception) {}
        }
        return url
    }

    suspend fun exchangeCodeForToken(code: String): AuthData? {
        return withContext(Dispatchers.IO) {
            try {
                val redirectUri = "http://127.0.0.1:$port/callback"
                val formBody = listOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "code" to code,
                    "code_verifier" to currentCodeVerifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to redirectUri
                ).joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
                }

                val tokenReq = HttpRequest.newBuilder()
                    .uri(URI("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build()

                val tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString())
                if (tokenResp.statusCode() !in 200..299) {
                    System.err.println("Token exchange error (${tokenResp.statusCode()}): ${tokenResp.body()}")
                    return@withContext null
                }

                val root = JsonParser.parseString(tokenResp.body()).asJsonObject
                val accessToken = root.get("access_token")?.asString ?: return@withContext null
                val refreshToken = root.get("refresh_token")?.asString ?: ""
                val expiresIn = root.get("expires_in")?.asLong ?: 3600L
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                var userName = "Google User"
                var userEmail = ""
                try {
                    val userReq = HttpRequest.newBuilder()
                        .uri(URI("https://www.googleapis.com/oauth2/v2/userinfo"))
                        .header("Authorization", "Bearer $accessToken")
                        .GET()
                        .build()
                    val userResp = httpClient.send(userReq, HttpResponse.BodyHandlers.ofString())
                    if (userResp.statusCode() == 200) {
                        val userObj = JsonParser.parseString(userResp.body()).asJsonObject
                        userName = userObj.get("name")?.asString ?: "Google User"
                        userEmail = userObj.get("email")?.asString ?: ""
                    }
                } catch (_: Exception) {}

                val authData = AuthData(
                    cookie = "",
                    sapisid = "",
                    access_token = accessToken,
                    refresh_token = refreshToken,
                    expires_at = expiresAt,
                    user_name = userName,
                    user_email = userEmail
                )

                innertubeClient.setOAuthToken(accessToken)

                try {
                    val json = Json { prettyPrint = true }
                    authFile.writeText(json.encodeToString(authData))
                } catch (_: Exception) {}

                authData
            } catch (e: Exception) {
                System.err.println("Failed to exchange OAuth code: ${e.message}")
                null
            }
        }
    }

    /**
     * Ensures the InnertubeClient holds a valid (non-expired) OAuth access token.
     * Access tokens last ~1 hour; when the stored one is expired (or about to) it is
     * renewed with the stored refresh token and persisted back to the auth file.
     * @return true if a usable access token is available afterwards.
     */
    suspend fun ensureFreshToken(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!authFile.exists()) return@withContext innertubeClient.getOAuthToken().isNotBlank()
                val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
                val auth = json.decodeFromString<AuthData>(authFile.readText())
                if (auth.access_token.isBlank() && auth.refresh_token.isBlank()) return@withContext false

                val stillValid = auth.access_token.isNotBlank() &&
                    auth.expires_at > System.currentTimeMillis() + 60_000L
                if (stillValid) {
                    if (innertubeClient.getOAuthToken() != auth.access_token) {
                        innertubeClient.setOAuthToken(auth.access_token)
                    }
                    return@withContext true
                }

                if (auth.refresh_token.isBlank()) {
                    System.err.println("OAuth access token expired and no refresh token stored. Please reconnect your Google account.")
                    return@withContext false
                }
                if (clientId.isBlank() || clientSecret.isBlank()) {
                    System.err.println("OAuth token expired but google.client.id / google.client.secret are not configured (see local.properties.example).")
                    return@withContext false
                }

                val formBody = listOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "refresh_token" to auth.refresh_token,
                    "grant_type" to "refresh_token"
                ).joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
                }
                val req = HttpRequest.newBuilder()
                    .uri(URI("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build()
                val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() !in 200..299) {
                    System.err.println("OAuth token refresh failed (${resp.statusCode()}): ${resp.body()}. Please reconnect your Google account.")
                    return@withContext false
                }

                val root = JsonParser.parseString(resp.body()).asJsonObject
                val newAccess = root.get("access_token")?.asString ?: return@withContext false
                val expiresIn = root.get("expires_in")?.asLong ?: 3600L
                // Google normally does not return a new refresh_token on refresh; keep the old one.
                val newRefresh = root.get("refresh_token")?.asString ?: auth.refresh_token
                val updated = auth.copy(
                    access_token = newAccess,
                    refresh_token = newRefresh,
                    expires_at = System.currentTimeMillis() + expiresIn * 1000L
                )
                innertubeClient.setOAuthToken(newAccess)
                authFile.writeText(json.encodeToString(updated))
                true
            } catch (e: Exception) {
                System.err.println("OAuth token refresh error: ${e.message}")
                false
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(StandardCharsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
