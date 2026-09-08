package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongestionLevelTest {
    @Test
    void defaultsToTheMinimumLevel() {
        assertEquals(0, new CongestionLevel().current());
    }

    @Test
    void acceptsBothInclusiveBounds() {
        assertDoesNotThrow(() -> new CongestionLevel(0));
        assertDoesNotThrow(() -> new CongestionLevel(8));
    }

    @Test
    void rejectsValuesOutsideTheSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new CongestionLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> new CongestionLevel(9));
    }

    @Test
    void reportsWhetherAnUpdateActuallyChangedTheLevel() {
        CongestionLevel level = new CongestionLevel(3);

        assertFalse(level.update(3));
        assertTrue(level.update(7));
        assertEquals(7, level.current());
    }

    @Test
    void invalidUpdateLeavesExistingLevelUntouched() {
        CongestionLevel level = new CongestionLevel(4);

        assertThrows(IllegalArgumentException.class, () -> level.update(12));
        assertEquals(4, level.current());
    }
}
