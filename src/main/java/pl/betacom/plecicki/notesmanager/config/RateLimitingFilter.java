package pl.betacom.plecicki.notesmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.betacom.plecicki.notesmanager.exception.ErrorResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final int capacity;
    private final long refillPeriodSeconds;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            ObjectMapper objectMapper,
            @Value("${rate-limit.login.capacity:5}") int capacity,
            @Value("${rate-limit.login.refill-period-seconds:60}") long refillPeriodSeconds) {
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.refillPeriodSeconds = refillPeriodSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, key -> newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);
        log.warn("Rate limit exceeded for IP {} on POST /login", ip);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "Too many login attempts, try again later"));
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofSeconds(refillPeriodSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }
}