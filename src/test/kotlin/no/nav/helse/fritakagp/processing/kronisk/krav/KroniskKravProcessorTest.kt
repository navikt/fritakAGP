package no.nav.helse.fritakagp.processing.kronisk.krav

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.KroniskTestData
import no.nav.helse.arbeidsgiver.bakgrunnsjobb2.Bakgrunnsjobb
import no.nav.helse.arbeidsgiver.bakgrunnsjobb2.BakgrunnsjobbRepository
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OPPGAVETYPE_FORDELINGSOPPGAVE
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveRequest
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.gcp.BucketDocument
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.jsonEquals
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.emptyJob
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.testJob
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessor
import no.nav.helse.fritakagp.readToObjectNode
import no.nav.helse.fritakagp.service.BehandlendeEnhetService
import no.nav.helse.fritakagp.service.GeografiskTilknytning
import no.nav.helse.fritakagp.service.PdlService
import no.nav.helse.arbeidsgiver.utils.loadFromResources
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.fail

class KroniskKravProcessorTest {

    val joarkMock = mockk<DokArkivClient>(relaxed = true)
    val oppgaveMock = mockk<OppgaveKlient>(relaxed = true)
    val repositoryMock = mockk<KroniskKravRepository>(relaxed = true)
    val pdlServiceMock = mockk<PdlService>(relaxed = true)
    val objectMapper = customObjectMapper()
    val pdfGeneratorMock = mockk<KroniskKravPDFGenerator>(relaxed = true)
    val bucketStorageMock = mockk<BucketStorage>(relaxed = true)
    val bakgrunnsjobbRepomock = mockk<BakgrunnsjobbRepository>(relaxed = true)
    val berregServiceMock = mockk<BrregClient>(relaxed = true)
    val behandlendeEnhetService = mockk<BehandlendeEnhetService>(relaxed = true)
    val prosessor = KroniskKravProcessor(repositoryMock, joarkMock, oppgaveMock, pdlServiceMock, bakgrunnsjobbRepomock, pdfGeneratorMock, objectMapper, bucketStorageMock, berregServiceMock, behandlendeEnhetService)
    lateinit var krav: KroniskKrav

    private val oppgaveId = 9999
    private val arkivReferanse = "12345"
    private var jobb = emptyJob()

    @BeforeEach
    fun setup() {
        krav = KroniskTestData.kroniskKrav.copy()
        jobb = testJob(objectMapper.writeValueAsString(KroniskKravProcessor.JobbData(krav.id)))
        every { repositoryMock.getById(krav.id) } returns krav
        every { bucketStorageMock.getDocAsString(any()) } returns null
        every { pdlServiceMock.hentAktoerId(krav.identitetsnummer) } returns "aktør-id"
        every { pdlServiceMock.hentGeografiskTilknytning(krav.identitetsnummer) } returns GeografiskTilknytning(diskresjonskode = null, geografiskTilknytning = "SWE")
        coEvery { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any()) } returns OpprettOgFerdigstillResponse(arkivReferanse, true, null, emptyList())
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } returns KroniskTestData.kroniskOpprettOppgaveResponse.copy(id = oppgaveId)
        coEvery { berregServiceMock.getVirksomhetsNavn(krav.virksomhetsnummer) } returns "Stark Industries"
    }

    @Test
    fun `skal ikke journalføre når det allerede foreligger en journalpostId, men skal forsøke sletting fra bucket `() {
        krav.journalpostId = "joark"
        prosessor.prosesser(jobb)

        coVerify(exactly = 0) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { bucketStorageMock.deleteDoc(krav.id) }
    }

    @Test
    fun `skal opprette fordelingsoppgave når stoppet`() {
        prosessor.stoppet(jobb)

        val oppgaveRequest = CapturingSlot<OpprettOppgaveRequest>()

        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(capture(oppgaveRequest), any()) }
        assertThat(oppgaveRequest.captured.oppgavetype).isEqualTo(OPPGAVETYPE_FORDELINGSOPPGAVE)
    }

    @Test
    @Disabled("må se på senere")
    fun `Om det finnes ekstra dokumentasjon skal den journalføres og så slettes`() {
        val dokumentData = "test"
        val filtypeArkiv = "pdf"
        val filtypeOrginal = "JSON"

        every { bucketStorageMock.getDocAsString(krav.id) } returns BucketDocument(dokumentData, filtypeArkiv)

//        val joarkRequest = slot<JournalpostRequest>()
//        every { joarkMock.journalførDokument(capture(joarkRequest), any(), any()) } returns JournalpostResponse(arkivReferanse, true, "M", null, emptyList())
//
//        Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(krav))
//        prosessor.prosesser(jobb)
//
//        verify(exactly = 1) { bucketStorageMock.getDocAsString(krav.id) }
//        verify(exactly = 1) { bucketStorageMock.deleteDoc(krav.id) }
//
//        assertThat((joarkRequest.captured.dokumenter)).hasSize(2)
//        val dokumentasjon = joarkRequest.captured.dokumenter.filter { it.brevkode == KroniskKravProcessor.dokumentasjonBrevkode }.first()
//
//        assertThat(dokumentasjon.dokumentVarianter[0].fysiskDokument).isEqualTo(dokumentData)
//        assertThat(dokumentasjon.dokumentVarianter[0].filtype).isEqualTo(filtypeArkiv.uppercase())
//        assertThat(dokumentasjon.dokumentVarianter[0].variantFormat).isEqualTo("ARKIV")
//        assertThat(dokumentasjon.dokumentVarianter[1].filtype).isEqualTo(filtypeOrginal)
//        assertThat(dokumentasjon.dokumentVarianter[1].variantFormat).isEqualTo("ORIGINAL")
    }

    @Test
    fun `skal ikke lage oppgave når det allerede foreligger en oppgaveId `() {
        krav.oppgaveId = "ppggssv"
        prosessor.prosesser(jobb)
        coVerify(exactly = 0) { oppgaveMock.opprettOppgave(any(), any()) }
    }

    @Test
    fun `skal journalføre, opprette oppgave og oppdatere søknaden i databasen`() {
        val forventetJson = "kroniskKravRobotBeskrivelse.json".loadFromResources()

        prosessor.prosesser(jobb)

        assertThat(krav.journalpostId).isEqualTo(arkivReferanse)
        assertThat(krav.oppgaveId).isEqualTo(oppgaveId.toString())

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            oppgaveMock.opprettOppgave(
                withArg {
                    assertEquals("ROB_BEH", it.oppgavetype)
                    if (!forventetJson.jsonEquals(objectMapper, it.beskrivelse!!, "id", "opprettet")) {
                        println("expected json to be equal, was not: \nexpectedJson=$forventetJson \nactualJson=${it.beskrivelse}")
                        fail()
                    }
                    if (!forventetJson.readToObjectNode(objectMapper)["kravType"].asText().equals("KRONISK")) {
                        println("expected json to contain kravType = KRONISK, was not")
                        fail()
                    }
                },
                any()
            )
        }
        verify(exactly = 1) { repositoryMock.update(krav) }
    }

    @Test
    fun `skal opprette jobber`() {
        prosessor.prosesser(jobb)

        val opprettetJobber = mutableListOf<Bakgrunnsjobb>()

        verify(exactly = 2) {
            bakgrunnsjobbRepomock.save(capture(opprettetJobber))
        }

        val kafkajobb = opprettetJobber.find { it.type == KroniskKravKafkaProcessor.JOB_TYPE }
        assertThat(kafkajobb?.data).contains(krav.id.toString())

        val beskjedJobb = opprettetJobber.find { it.type == BrukernotifikasjonProcessor.JOB_TYPE }
        assertThat(beskjedJobb?.data).contains(BrukernotifikasjonProcessor.Jobbdata.SkjemaType.KroniskKrav.name)
        assertThat(beskjedJobb?.data).contains(krav.id.toString())
    }

    @Test
    fun `Ved feil i oppgave skal joarkref lagres, og det skal det kastes exception oppover`() {
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } throws IOException()

        assertThrows<IOException> { prosessor.prosesser(jobb) }

        assertThat(krav.journalpostId).isEqualTo(arkivReferanse)
        assertThat(krav.oppgaveId).isNull()

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(any(), any()) }
        verify(exactly = 1) { repositoryMock.update(krav) }
    }
}
