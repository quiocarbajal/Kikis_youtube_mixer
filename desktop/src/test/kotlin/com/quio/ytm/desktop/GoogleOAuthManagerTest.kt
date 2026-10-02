package com.quio.ytm.desktop

import com.quio.ytm.core.api.InnertubeClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoogleOAuthManagerTest {

    @Test
    fun testGenerateAuthUrlContainsRequiredParams() {
        val innertube = InnertubeClient()
        val tempAuth = File.createTempFile("auth_test", ".json")
        val manager = GoogleOAuthManager(innertube, tempAuth, 8888)

        val url = manager.generateAuthUrl()
        assertNotNull(url)
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(url.contains("client_id="))
        assertTrue(url.contains("redirect_uri=http://127.0.0.1:8888/callback"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("prompt=select_account"))
        assertTrue(url.contains("access_type=offline"))
        assertTrue(url.contains("state="))
        
        tempAuth.delete()
    }
}
