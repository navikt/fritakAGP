package no.nav.helse.slowtests.systemtests.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.setCookie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import no.nav.helse.fritakagp.FritakAgpApplication
import no.nav.helse.fritakagp.web.api.resreq.Problem
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.koin.test.KoinTest
import org.koin.test.inject

@TestInstance(TestInstance.Lifecycle.PER_CLASS)

/**
 * Denne klassen kjører opp applikasjonen med Koin-profilen LOCAL
 * slik at man kan
 * 1) Kjøre tester mot HTTP-endepunktene slik de er i miljøene (Q+P)
 * 2) Kjøre tester mot systemet (bakgrunnsjobber feks) mens de er realistisk  konfigurert
 * 3) Kjøre ende til ende-tester (feks teste at en søknad send inn på HTTP-endepunktet havner i databasen riktig)
 */
open class SystemTestBase : KoinTest {

    val httpClient by inject<HttpClient>()
    val objectMapper = jacksonObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .registerModule(KotlinModule())
        .registerModule(Jdk8Module())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    companion object {
        val testServerPort = 8989
        var app: FritakAgpApplication? = null
    }

    @BeforeAll
    fun before() {
        if (app == null) {
            app = FritakAgpApplication(port = testServerPort)
            app!!.start()
            Thread.sleep(200)
        }
    }

    @AfterAll
    fun after() {
    }

    /**
     * Hjelpefunksjon for å kalle HTTP-endepunktene med riktig port i testene
     */
    fun HttpRequestBuilder.appUrl(relativePath: String) {
        url("http://localhost:$testServerPort$relativePath")
    }

    /**
     * Hjelpefunksjon for å hente ut gyldig JWT-token og legge det til som Auth header på en request
     */
    suspend fun HttpRequestBuilder.loggedInAs(subject: String) {
        val response = httpClient.get<HttpResponse> {
            appUrl("/local/cookie-please?subject=$subject")
            contentType(ContentType.Application.Json)
        }

        header("Authorization", "Bearer ${response.setCookie()[0].value}")
    }

    /**
     * Hjelpefunksjon for at JUnit5 skal kunne kjenne igjen tester som kaller har "suspend"-funksjoner
     */
    fun suspendableTest(block: suspend CoroutineScope.() -> Unit) {
        runBlocking {
            block()
            Unit
        }
    }

    suspend fun extractResponseBody(response: HttpResponse) =
        response.call.response.receive<Problem>()
}
