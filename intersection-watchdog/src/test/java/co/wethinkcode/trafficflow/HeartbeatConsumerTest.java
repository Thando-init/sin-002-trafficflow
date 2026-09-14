package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatConsumerTest {
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
    void heartbeatMessageUpdatesWatchdogState() throws Exception {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), Instant.now());
        try (HeartbeatConsumer consumer = new HeartbeatConsumer(state);
             Connection connection = new ActiveMQConnectionFactory(MqConfig.BROKER_URL).createConnection()) {
            consumer.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(MqConfig.HEARTBEAT_QUEUE));
            connection.start();
            producer.send(session.createTextMessage("{\"service\":\"intersection-service\"}"));

            Instant deadline = Instant.now().plusSeconds(3);
            while (state.status().lastHeartbeatAt() == null && Instant.now().isBefore(deadline)) {
                Thread.sleep(25);
            }

            assertNotNull(state.status().lastHeartbeatAt());
            assertTrue(state.status().healthy());
            producer.close();
            session.close();
        }
    }
}

