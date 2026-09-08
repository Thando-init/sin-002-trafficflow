package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/** Publishes congestion snapshots to the shared ActiveMQ topic. */
public final class CongestionPublisher {
    /** Sends a simple self-contained JSON event to all current topic subscribers. */
    public void publish(int level) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createTopic(MqConfig.TOPIC))) {
            connection.start();
            TextMessage event = session.createTextMessage("{\"level\":" + level + "}");
            event.setIntProperty("level", level);
            producer.send(event);
        } catch (Exception exception) {
            throw new IllegalStateException("ActiveMQ broker is unavailable", exception);
        }
    }
}
