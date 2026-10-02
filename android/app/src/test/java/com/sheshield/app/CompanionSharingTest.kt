package com.sheshield.app

import com.google.gson.JsonParser
import com.sheshield.app.data.network.SheShieldApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CompanionSharingTest {
    @Test fun companionLinkRequestIncludesJsonEvenWithoutInputFields() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests++
            assertEquals("POST", request.method)
            assertEquals("/v1/trips/current-trip/share", request.url.encodedPath)
            val body = requireNotNull(request.body)
            assertEquals("application/json", body.contentType()?.let { "${it.type}/${it.subtype}" })
            val buffer = Buffer()
            body.writeTo(buffer)
            assertEquals(0, JsonParser.parseString(buffer.readUtf8()).asJsonObject.size())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("""{"url":"https://api.example/share/private-token"}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.example/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(SheShieldApi::class.java)

        assertEquals("https://api.example/share/private-token", api.share("current-trip").url)
        assertEquals(1, requests)
    }
}
