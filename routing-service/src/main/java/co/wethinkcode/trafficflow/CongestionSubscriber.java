package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

/** Listens for congestion updates from the shared ActiveMQ topic.
 *  Creates a JMS topic consumer and listens for congestion events, updating routing’s local cache.
 * */
public final class CongestionSubscriber implements AutoCloseable {
    private final LatestCongestionLevel latestLevel;
    private final ObjectMapper objectMapper;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    /** Documents the CongestionSubscriber operation and its effect on service state or external communication.
     */
    public CongestionSubscriber(LatestCongestionLevel latestLevel, ObjectMapper objectMapper) {
        this.latestLevel = latestLevel;
        this.objectMapper = objectMapper;
    }

    /** Creates the ActiveMQ connection and begins consuming configured messages.
     */
    public synchronized void start() {
        if (connection != null) {
            return;
        }
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            consumer = session.createConsumer(session.createTopic(MqConfig.TOPIC));
            consumer.setMessageListener(this::handleMessage);
            connection.start();
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("Unable to subscribe to congestion topic", exception);
        }
    }

    void handleMessage(Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            System.err.println("Ignoring non-text congestion event.");
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(textMessage.getText());
            if (!event.has("level") || !event.get("level").canConvertToInt()) {
                throw new IllegalArgumentException("No integer level field");
            }
            latestLevel.update(event.get("level").intValue());
        } catch (Exception exception) {
            System.err.printf("Ignoring invalid congestion event: %s%n", exception.getMessage());
        }
    }

    @Override
    /** Closes broker resources and may safely be called more than once.
     */
    public synchronized void close() {
        closeQuietly(consumer);
        consumer = null;
        closeQuietly(session);
        session = null;
        closeQuietly(connection);
        connection = null;
    }

    /** Documents the closeQuietly operation and its effect on service state or external communication.
     */
    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception exception) {
            System.err.printf("Unable to close ActiveMQ resource: %s%n", exception.getMessage());
        }
    }
}
