package owoke.staffmatch.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MaxInitDataValidatorTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private final MaxInitDataValidator validator = new MaxInitDataValidator(
            MaxInitDataFixtures.BOT_TOKEN,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new ObjectMapper()
    );

    @Test
    void acceptsValidSignedInitData() {
        String initData = MaxInitDataFixtures.signed(123456789L, NOW.minusSeconds(30));

        assertEquals(123456789L, validator.validate(initData).maxUserId());
    }

    @Test
    void rejectsModifiedUserAndDuplicateHash() {
        String initData = MaxInitDataFixtures.signed(123L, NOW);

        assertThrows(InvalidMaxInitDataException.class,
                () -> validator.validate(initData.replace("%3A123", "%3A124")));
        assertThrows(InvalidMaxInitDataException.class,
                () -> validator.validate(initData + "&hash=another"));
    }

    @Test
    void rejectsExpiredAndFutureInitData() {
        assertThrows(InvalidMaxInitDataException.class,
                () -> validator.validate(MaxInitDataFixtures.signed(123L, NOW.minusSeconds(3601))));
        assertThrows(InvalidMaxInitDataException.class,
                () -> validator.validate(MaxInitDataFixtures.signed(123L, NOW.plusSeconds(61))));
    }
}
