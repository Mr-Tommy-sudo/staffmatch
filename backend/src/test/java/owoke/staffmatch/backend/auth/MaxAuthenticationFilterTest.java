package owoke.staffmatch.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class MaxAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MaxAuthenticationFilter filter = new MaxAuthenticationFilter(
            new MaxInitDataValidator(MaxInitDataFixtures.BOT_TOKEN,
                    Clock.fixed(NOW, ZoneOffset.UTC), objectMapper),
            objectMapper
    );

    @Test
    void missingInitDataReturnsUnauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("The request must not reach the controller");
        });

        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
    }

    @Test
    void validInitDataSetsPrincipalForRequestOnly() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader(MaxAuthenticationFilter.INIT_DATA_HEADER,
                MaxInitDataFixtures.signed(4321L, NOW));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<MaxPrincipal> seenPrincipal = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                seenPrincipal.set((MaxPrincipal) SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal()));

        assertEquals(4321L, seenPrincipal.get().maxUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.setServletPath("/api/v1/me");
        return request;
    }
}
