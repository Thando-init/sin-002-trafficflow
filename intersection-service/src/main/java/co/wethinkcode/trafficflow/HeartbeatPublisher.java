package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.time.Instant;

/** Publishes liveness signals for intersection-service to the watchdog queue. */
public final class HeartbeatPublisher implements AutoCloseable {
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    /** Publishes a JSON congestion or heartbeat event to the configured ActiveMQ destination.
     */
    public synchronized void publish() {
        try {
            ensureConnected();
            TextMessage heartbeat = session.createTextMessage(
                    "{\"service\":\"intersection-service\",\"sentAt\":\"" + Instant.now() + "\"}");
            heartbeat.setStringProperty("eventType", "intersection-heartbeat");
            producer.send(heartbeat);
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("Unable to publish intersection heartbeat", exception);
        }
    }

    private void ensureConnected() throws Exception {
        if (connection != null) {
            return;
        }
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = connectionFactory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        producer = session.createProducer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));
        connection.start();
    }

    @Override
    /** Closes broker resources and may safely be called more than once.
     */
    public synchronized void close() {
        closeQuietly(producer);
        producer = null;
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
            System.err.printf("Unable to close heartbeat resource: %s%n", exception.getMessage());
        }
    }
}
