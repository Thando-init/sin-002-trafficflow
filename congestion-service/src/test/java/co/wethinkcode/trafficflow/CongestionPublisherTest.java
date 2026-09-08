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

class CongestionPublisherTest {
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
    void publishSendsLevelEventToTopic() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createTopic(MqConfig.TOPIC));
            connection.start();

            new CongestionPublisher().publish(7);
            Message message = consumer.receive(3000);

            assertNotNull(message);
            assertEquals(7, message.getIntProperty("level"));
            assertTrue(message instanceof TextMessage);
            assertEquals("{\"level\":7}", ((TextMessage) message).getText());
            consumer.close();
            session.close();
        }
    }
}
