import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class BrokerReplay {
    public static List<String> replay(List<String> records, String bootstrap, String topicPrefix) throws Exception {
        if (records.isEmpty()) return List.of();
        String topic = topicPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Properties admin = new Properties();
        admin.put("bootstrap.servers", bootstrap);
        try (AdminClient client = AdminClient.create(admin)) {
            client.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(20, TimeUnit.SECONDS);
        }
        Properties producer = new Properties();
        producer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer.put(ProducerConfig.ACKS_CONFIG, "all");
        producer.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        try (KafkaProducer<String, String> client = new KafkaProducer<>(producer)) {
            for (String record : records) {
                String[] fields = record.split(",", -1);
                String key = fields.length > 1 ? fields[1] : record;
                client.send(new ProducerRecord<>(topic, key, record)).get(20, TimeUnit.SECONDS);
            }
        }
        Properties consumer = new Properties();
        consumer.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        consumer.put(ConsumerConfig.GROUP_ID_CONFIG, "portfolio-" + UUID.randomUUID());
        consumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<String> received = new ArrayList<>();
        try (KafkaConsumer<String, String> client = new KafkaConsumer<>(consumer)) {
            client.subscribe(List.of(topic));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (received.size() < records.size() && System.nanoTime() < deadline) {
                client.poll(Duration.ofMillis(500)).forEach(message -> received.add(message.value()));
            }
        }
        if (received.size() != records.size()) throw new IllegalStateException("Expected " + records.size() + " broker records; got " + received.size());
        return received;
    }
}
