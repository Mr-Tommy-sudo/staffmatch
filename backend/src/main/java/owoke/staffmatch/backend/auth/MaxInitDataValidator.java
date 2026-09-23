package owoke.staffmatch.backend.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MaxInitDataValidator {

    private static final long MAX_AGE_SECONDS = 3600;
    private static final long FUTURE_TOLERANCE_SECONDS = 60;

    private final byte[] botToken;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public MaxInitDataValidator(@Value("${max.bot-token}") String botToken, ObjectMapper objectMapper) {
        this(botToken, Clock.systemUTC(), objectMapper);
    }

    MaxInitDataValidator(String botToken, Clock clock, ObjectMapper objectMapper) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("MAX_BOT_TOKEN must be configured");
        }
        this.botToken = botToken.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public MaxPrincipal validate(String rawInitData) {
        if (rawInitData == null || rawInitData.isBlank()) {
            throw new InvalidMaxInitDataException();
        }

        try {
            Map<String, String> fields = parseFields(rawInitData);
            String suppliedHash = fields.remove("hash");
            if (suppliedHash == null) {
                throw new InvalidMaxInitDataException();
            }

            String checkString = fields.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));
            byte[] secretKey = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken);
            byte[] expectedHash = hmac(secretKey, checkString.getBytes(StandardCharsets.UTF_8));
            byte[] actualHash = HexFormat.of().parseHex(suppliedHash);
            if (!MessageDigest.isEqual(expectedHash, actualHash)) {
                throw new InvalidMaxInitDataException();
            }

            long authDate = Long.parseLong(fields.get("auth_date"));
            long now = Instant.now(clock).getEpochSecond();
            if (authDate > now + FUTURE_TOLERANCE_SECONDS || authDate < now - MAX_AGE_SECONDS) {
                throw new InvalidMaxInitDataException();
            }

            JsonNode user = objectMapper.readTree(fields.get("user"));
            JsonNode userId = user.path("id");
            if (!userId.isIntegralNumber() || !userId.canConvertToLong() || userId.longValue() <= 0) {
                throw new InvalidMaxInitDataException();
            }
            return new MaxPrincipal(userId.longValue());
        } catch (InvalidMaxInitDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidMaxInitDataException();
        }
    }

    private static Map<String, String> parseFields(String rawInitData) {
        Map<String, String> fields = new HashMap<>();
        Arrays.stream(rawInitData.split("&", -1)).forEach(pair -> {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                throw new InvalidMaxInitDataException();
            }
            String key = pair.substring(0, separator);
            String encodedValue = pair.substring(separator + 1);
            String value = URLDecoder.decode(encodedValue.replace("+", "%2B"), StandardCharsets.UTF_8);
            if (fields.putIfAbsent(key, value) != null) {
                throw new InvalidMaxInitDataException();
            }
        });
        return fields;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
