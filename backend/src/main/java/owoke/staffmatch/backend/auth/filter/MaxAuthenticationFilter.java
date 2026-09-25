package owoke.staffmatch.backend.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import owoke.staffmatch.backend.auth.exception.InvalidMaxInitDataException;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.auth.service.MaxInitDataValidator;
import tools.jackson.databind.ObjectMapper;

public class MaxAuthenticationFilter extends OncePerRequestFilter {

    public static final String INIT_DATA_HEADER = "X-Max-Init-Data";

    private final MaxInitDataValidator validator;
    private final ObjectMapper objectMapper;

    public MaxAuthenticationFilter(MaxInitDataValidator validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            MaxPrincipal principal = validator.validate(request.getHeader(INIT_DATA_HEADER));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidMaxInitDataException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "type", "about:blank",
                    "title", "Unauthorized",
                    "status", HttpServletResponse.SC_UNAUTHORIZED,
                    "detail", exception.getMessage()
            ));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
