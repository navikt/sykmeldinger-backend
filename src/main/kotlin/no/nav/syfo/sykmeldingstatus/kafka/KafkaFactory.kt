package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.utils.Environment
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingStatusKafkaProducer(
            environment: Environment
        ): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig = KafkaUtils.getAivenKafkaConfig("status-producer")
            val kafkaProducer =
                KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusTopic)
        }
    }
}
