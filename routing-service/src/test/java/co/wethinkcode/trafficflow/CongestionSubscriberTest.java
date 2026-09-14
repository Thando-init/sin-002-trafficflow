package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CongestionSubscriberTest {
    @Test
    void appliesValidTextEventToLatestLevel() throws Exception {
        LatestCongestionLevel latest = new LatestCongestionLevel(0);
        CongestionSubscriber subscriber = new CongestionSubscriber(latest, new ObjectMapper());

        subscriber.handleMessage(message("{\"level\":6}"));

        assertEquals(6, latest.currentLevel());
    }

    @Test
    void ignoresMalformedAndMissingLevelEvents() throws Exception {
        LatestCongestionLevel latest = new LatestCongestionLevel(2);
        CongestionSubscriber subscriber = new CongestionSubscriber(latest, new ObjectMapper());

        subscriber.handleMessage(message("not-json"));
        subscriber.handleMessage(message("{\"status\":\"ok\"}"));

        assertEquals(2, latest.currentLevel());
    }

    @Test
    void ignoresOutOfRangeLevelEvents() throws Exception {
        LatestCongestionLevel latest = new LatestCongestionLevel(2);
        CongestionSubscriber subscriber = new CongestionSubscriber(latest, new ObjectMapper());

        subscriber.handleMessage(message("{\"level\":9}"));

        assertEquals(2, latest.currentLevel());
    }

    @Test
    void closeIsSafeBeforeAndAfterStart() {
        CongestionSubscriber subscriber = new CongestionSubscriber(new LatestCongestionLevel(0), new ObjectMapper());

        subscriber.close();
        subscriber.close();

        assertEquals(0, new LatestCongestionLevel(0).currentLevel());
    }

    private static ActiveMQTextMessage message(String body) throws Exception {
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setText(body);
        return message;
    }
}
