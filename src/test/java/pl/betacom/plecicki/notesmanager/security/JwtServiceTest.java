package pl.betacom.plecicki.notesmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should generate token with subject, issuedAt and expiration")
    void shouldGenerateTokenWithClaims() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, 3600);

        // [WHEN]
        String token = jwtService.generateToken("jan.kowalski");

        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // [THEN]
        assertThat(claims.getSubject()).isEqualTo("jan.kowalski");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("should extract login from a valid token")
    void shouldExtractLogin() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, 3600);

        String token = jwtService.generateToken("anna.nowak");

        // [WHEN]
        assertThat(jwtService.extractLogin(token)).isEqualTo("anna.nowak");
    }

    @Test
    @DisplayName("should return configured expiration seconds")
    void shouldReturnExpirationSeconds() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, 3600);

        // [WHEN]
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("should throw on null token")
    void shouldThrowOnNullToken() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, 3600);

        // [WHEN]
        assertThatThrownBy(() -> jwtService.extractLogin(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("should throw on malformed token")
    void shouldThrowOnMalformedToken() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, 3600);

        // [WHEN]
        assertThatThrownBy(() -> jwtService.extractLogin("not-a-valid-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("should throw when token is signed with a different key")
    void shouldThrowOnDifferentKey() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, 3600);
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-12345678901234567890".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("jan.kowalski").signWith(otherKey).compact();

        // [WHEN]
        assertThatThrownBy(() -> jwtService.extractLogin(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("should throw on expired token")
    void shouldThrowOnExpiredToken() {
        // [GIVEN]
        JwtService jwtService = new JwtService(SECRET, -1);

        String token = jwtService.generateToken("jan.kowalski");

        // [WHEN]
        assertThatThrownBy(() -> jwtService.extractLogin(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
