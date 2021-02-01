package no.nav.helse.fritakagp.integration.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.KroniskKrav
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import java.util.concurrent.TimeUnit


interface KravmeldingSender {
    fun sendMessage(melding: KroniskKrav): RecordMetadata?
    fun sendMessage(melding: GravidKrav): RecordMetadata?

}

class KravmeldingKafkaProducer(props: MutableMap<String, Any>, private val topicName: String, private val om : ObjectMapper) :
        KravmeldingSender {
    private val producer = KafkaProducer(props, StringSerializer(), StringSerializer())

    override fun sendMessage(melding: KroniskKrav): RecordMetadata?{
        return sendKafkaMessage(om.writeValueAsString(melding), "KroniskKrav")
    }

    override fun sendMessage(melding: GravidKrav): RecordMetadata? {
        return sendKafkaMessage(om.writeValueAsString(melding), "GravidKrav")
    }

    private fun sendKafkaMessage(melding: String, type : String): RecordMetadata? {
        val record: ProducerRecord<String, String> = ProducerRecord(topicName, melding)
        record.headers().add(RecordHeader("type", type.toByteArray()))
        return producer.send(record).get(10, TimeUnit.SECONDS)
    }
}
