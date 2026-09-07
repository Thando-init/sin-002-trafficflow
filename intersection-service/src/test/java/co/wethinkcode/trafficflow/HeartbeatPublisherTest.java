package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatPublisherTest {
    private static BrokerService broker;

    @BeforeAll
    static void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector(MqConfig.BROKER_URL);
        broker.start();
        broker.waitUntilStarted();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    @Test
    void publishSendsHeartbeatTextWithEventType() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        try (Connection connection = factory.createConnection();
             HeartbeatPublisher publisher = new HeartbeatPublisher()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));
            connection.start();

            publisher.publish();
            Message message = consumer.receive(3000);

            assertNotNull(message);
            assertEquals("intersection-heartbeat", message.getStringProperty("eventType"));
            assertEquals(true, message instanceof TextMessage);
            assertTrue(((TextMessage) message).getText().contains("intersection-service"));
            consumer.close();
            session.close();
        }
    }
}
