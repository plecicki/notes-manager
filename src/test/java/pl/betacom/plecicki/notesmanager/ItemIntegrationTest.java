package pl.betacom.plecicki.notesmanager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ItemIntegrationTest {

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
    @DisplayName("full items lifecycle against real MySQL")
    void shouldCreateUpdateConflictListAndDelete() {
        String login = "items.user";
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", login, "password", "password123"), jsonHeaders), Map.class);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", login, "password", "password123"), jsonHeaders), Map.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) loginResp.getBody().get("token");

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(token);

        ResponseEntity<Map> created = restTemplate.postForEntity("/items",
                new HttpEntity<>(Map.of("title", "Note", "content", "Content"), authHeaders), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(created.getBody().get("version")).isEqualTo(0);
        assertThat(created.getBody().get("ownerId")).isNotNull();

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("title", "New title");
        updateBody.put("version", 0);
        ResponseEntity<Map> updated = restTemplate.exchange("/items/" + id, HttpMethod.PATCH,
                new HttpEntity<>(updateBody, authHeaders), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("version")).isEqualTo(1);
        assertThat(updated.getBody().get("title")).isEqualTo("New title");

        ResponseEntity<Map> conflict = restTemplate.exchange("/items/" + id, HttpMethod.PATCH,
                new HttpEntity<>(updateBody, authHeaders), Map.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<List> list = restTemplate.exchange("/items", HttpMethod.GET, new HttpEntity<>(authHeaders), List.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);

        ResponseEntity<Void> deleted = restTemplate.exchange("/items/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> empty = restTemplate.exchange("/items", HttpMethod.GET, new HttpEntity<>(authHeaders), List.class);
        assertThat(empty.getBody()).isEmpty();

        ResponseEntity<Map> notFound = restTemplate.exchange("/items/" + id, HttpMethod.PATCH,
                new HttpEntity<>(updateBody, authHeaders), Map.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}