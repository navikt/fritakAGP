package no.nav.helse.fritakagp.auth

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.jackson.jackson
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.helse.fritakagp.customObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthClientTest {

    fun createAuthClient(): AuthClient {
        val tokenEndpoint = "http://localhost:8080/token"
        val tokenExchangeEndpoint = "http://localhost:8080/token-exchange"
        val tokenIntrospectEndpoint = "http://localhost:8080/token-introspect"
        val authClient = AuthClient(tokenEndpoint, tokenExchangeEndpoint, tokenIntrospectEndpoint)
        return authClient
    }

    @Test
    fun testTokenReturnSuccess() {
        runBlocking {
            val mockEngine = MockEngine { _ ->
                respond(
                    content = """{"access_token": "token", "expires_in": 3600}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        "Content-Type" to listOf(ContentType.Application.Json.toString())
                    )
                )
            }
            mockkStatic(::createHttpClient) {
                every { createHttpClient() } returns httpclientMock(mockEngine)
                val response = createAuthClient().token(IdentityProvider.MASKINPORTEN, "test")
                assertTrue(response is TokenResponse.Success)
                assertEquals("token", (response as TokenResponse.Success).accessToken)
                assertEquals(3600, response.expiresInSeconds)
            }
        }
    }

    @Test
    fun testTokenReturnError() {
        runBlocking {
            val mockEngine = MockEngine { _ ->
                respond(
                    content = """{"error": "invalid_request","error_description": "Invalid request"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(
                        "Content-Type" to listOf(ContentType.Application.Json.toString())
                    )
                )
            }
            mockkStatic(::createHttpClient) {
                every { createHttpClient() } returns httpclientMock(mockEngine)

                val response = createAuthClient().token(IdentityProvider.MASKINPORTEN, "test")
                assertTrue(response is TokenResponse.Error)
                assertEquals("invalid_request", (response as TokenResponse.Error).error.error)
                assertEquals("Invalid request", response.error.errorDescription)
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }
    }
}

private fun httpclientMock(mockEngine: MockEngine) = HttpClient(mockEngine) {
    expectSuccess = true
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(customObjectMapper()))
        jackson {
            registerModule(JavaTimeModule())
        }
    }
}
