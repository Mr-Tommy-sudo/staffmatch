package owoke.staffmatch.backend.auth.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class MaxInitDataFixtures {

    public static final String BOT_TOKEN = "test-bot-token";

    private MaxInitDataFixtures() {
    }

    public static String signed(long maxUserId, Instant authDate) {
        String userJson = "{\"id\":" + maxUserId + ",\"first_name\":\"Test User\"}";
        String checkString = "auth_date=" + authDate.getEpochSecond() + "\nuser=" + userJson;
        try {
            byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                    BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(hmac(secret, checkString.getBytes(StandardCharsets.UTF_8)));
            String encodedUser = URLEncoder.encode(userJson, StandardCharsets.UTF_8).replace("+", "%20");
            return "user=" + encodedUser + "&auth_date=" + authDate.getEpochSecond() + "&hash=" + hash;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
