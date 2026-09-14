package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.time.Instant;

/** Consumes heartbeat and broker dead-letter messages for the watchdog. */
public final class HeartbeatConsumer implements AutoCloseable {
    private static final String DEAD_LETTER_QUEUE = "ActiveMQ.DLQ";

    private final WatchdogState watchdogState;
    private Connection connection;
    private Session session;
    private MessageConsumer heartbeatConsumer;
    private MessageConsumer deadLetterConsumer;

    /** Documents the HeartbeatConsumer operation and its effect on service state or external communication.
     */
    public HeartbeatConsumer(WatchdogState watchdogState) {
        this.watchdogState = watchdogState;
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
            heartbeatConsumer = session.createConsumer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));
            heartbeatConsumer.setMessageListener(this::handleHeartbeat);
            deadLetterConsumer = session.createConsumer(session.createQueue(DEAD_LETTER_QUEUE),
                    "eventType = 'intersection-heartbeat'");
            deadLetterConsumer.setMessageListener(this::handleDeadLetter);
            connection.start();
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("Unable to subscribe to intersection heartbeat queues", exception);
        }
    }

    /** Records a valid intersection heartbeat receipt.
     */
    private void handleHeartbeat(Message message) {
        if (!(message instanceof TextMessage)) {
            System.err.println("Ignoring non-text heartbeat message.");
            return;
        }
        watchdogState.recordHeartbeat(Instant.now());
    }

    /** Records and logs a heartbeat dead-letter alert.
     */
    private void handleDeadLetter(Message message) {
        watchdogState.recordDeadLetter("An intersection heartbeat message reached the broker dead-letter queue.", Instant.now());
        System.err.println("ALERT: an intersection heartbeat message was dead-lettered.");
    }

    @Override
    /** Closes broker resources and may safely be called more than once.
     */
    public synchronized void close() {
        closeQuietly(heartbeatConsumer);
        heartbeatConsumer = null;
        closeQuietly(deadLetterConsumer);
        deadLetterConsumer = null;
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
            System.err.printf("Unable to close watchdog messaging resource: %s%n", exception.getMessage());
        }
    }
}
