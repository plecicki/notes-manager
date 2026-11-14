package pl.betacom.plecicki.notesmanager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("notesmanager")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("MYSQL_NOTES_MANAGER_PASS", () -> "test");
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("full register -> login round trip against real MySQL")
    void shouldRegisterAndLogin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> register = restTemplate.postForEntity(
                "/register",
                new HttpEntity<>(Map.of("login", "integracja.user", "password", "password123"), headers),
                Map.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody()).containsEntry("login", "integracja.user");
        assertThat(register.getBody().get("createdAt")).isNotNull();

        ResponseEntity<Map> duplicate = restTemplate.postForEntity(
                "/register",
                new HttpEntity<>(Map.of("login", "integracja.user", "password", "password123"), headers),
                Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/login",
                new HttpEntity<>(Map.of("login", "integracja.user", "password", "password123"), headers),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("token")).isNotNull();
        assertThat(login.getBody().get("expiresIn")).isEqualTo(3600);
    }

    @Test
    @DisplayName("login with wrong password returns 401")
    void shouldReturnUnauthorizedForWrongPassword() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(
                "/register",
                new HttpEntity<>(Map.of("login", "zly.user", "password", "password123"), headers),
                Map.class);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/login",
                new HttpEntity<>(Map.of("login", "zly.user", "password", "zlehaslo123"), headers),
                Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
