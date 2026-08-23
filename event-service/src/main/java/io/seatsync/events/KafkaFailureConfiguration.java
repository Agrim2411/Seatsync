package io.seatsync.events;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaFailureConfiguration {
  @Bean
  CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(250L, 3L));
  }
}
