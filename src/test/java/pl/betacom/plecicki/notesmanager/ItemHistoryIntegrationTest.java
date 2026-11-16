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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ItemHistoryIntegrationTest {

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
    @DisplayName("history should return ADD, MOD and DEL revisions with changedBy")
    void shouldReturnHistoryWithRevisions() {
        String login = "history.user";
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", login, "password", "password123"), json), Map.class);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", login, "password", "password123"), json), Map.class);
        String token = (String) loginResp.getBody().get("token");

        HttpHeaders auth = new HttpHeaders();
        auth.setContentType(MediaType.APPLICATION_JSON);
        auth.setBearerAuth(token);

        ResponseEntity<Map> created = restTemplate.postForEntity("/items",
                new HttpEntity<>(Map.of("title", "Title 1", "content", "Content 1"), auth), Map.class);
        String id = (String) created.getBody().get("id");

        Map<String, Object> body1 = new HashMap<>();
        body1.put("title", "Title 2");
        body1.put("version", 0);
        ResponseEntity<Map> upd1 = restTemplate.exchange("/items/" + id, HttpMethod.PATCH, new HttpEntity<>(body1, auth), Map.class);
        assertThat(upd1.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> deleted = restTemplate.exchange("/items/" + id, HttpMethod.DELETE, new HttpEntity<>(auth), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> history = restTemplate.exchange("/items/" + id + "/history", HttpMethod.GET, new HttpEntity<>(auth), List.class);

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> entries = history.getBody();
        assertThat(entries).hasSize(3);

        Map first = (Map) entries.get(0);
        assertThat(first.get("revisionType")).isEqualTo("ADD");
        assertThat(first.get("changedBy")).isEqualTo(login);
        assertThat(first.get("title")).isEqualTo("Title 1");

        Map second = (Map) entries.get(1);
        assertThat(second.get("revisionType")).isEqualTo("MOD");
        assertThat(second.get("title")).isEqualTo("Title 2");

        Map third = (Map) entries.get(2);
        assertThat(third.get("revisionType")).isEqualTo("DEL");
    }

    @Test
    @DisplayName("history access: viewer can read, foreign user forbidden, missing item 404")
    void shouldControlHistoryAccess() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "hist.owner", "password", "password123"), json), Map.class);
        ResponseEntity<Map> viewerRegister = restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "hist.viewer", "password", "password123"), json), Map.class);
        String viewerUserId = (String) viewerRegister.getBody().get("id");
        restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "hist.foreign", "password", "password123"), json), Map.class);

        ResponseEntity<Map> ownerLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "hist.owner", "password", "password123"), json), Map.class);
        String ownerToken = (String) ownerLogin.getBody().get("token");

        ResponseEntity<Map> viewerLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "hist.viewer", "password", "password123"), json), Map.class);
        String viewerToken = (String) viewerLogin.getBody().get("token");

        ResponseEntity<Map> foreignLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "hist.foreign", "password", "password123"), json), Map.class);
        String foreignToken = (String) foreignLogin.getBody().get("token");

        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ownerHeaders.setBearerAuth(ownerToken);

        HttpHeaders viewerHeaders = new HttpHeaders();
        viewerHeaders.setContentType(MediaType.APPLICATION_JSON);
        viewerHeaders.setBearerAuth(viewerToken);

        HttpHeaders foreignHeaders = new HttpHeaders();
        foreignHeaders.setContentType(MediaType.APPLICATION_JSON);
        foreignHeaders.setBearerAuth(foreignToken);

        ResponseEntity<Map> created = restTemplate.postForEntity("/items",
                new HttpEntity<>(Map.of("title", "Shared note", "content", "c"), ownerHeaders), Map.class);
        String itemId = (String) created.getBody().get("id");

        restTemplate.postForEntity("/items/" + itemId + "/share",
                new HttpEntity<>(Map.of("userId", viewerUserId, "role", "VIEWER"), ownerHeaders), Map.class);

        ResponseEntity<List> viewerHistory = restTemplate.exchange("/items/" + itemId + "/history", HttpMethod.GET,
                new HttpEntity<>(viewerHeaders), List.class);
        assertThat(viewerHistory.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> foreignHistory = restTemplate.exchange("/items/" + itemId + "/history", HttpMethod.GET,
                new HttpEntity<>(foreignHeaders), Map.class);
        assertThat(foreignHistory.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> missingHistory = restTemplate.exchange("/items/" + UUID.randomUUID() + "/history", HttpMethod.GET,
                new HttpEntity<>(ownerHeaders), Map.class);
        assertThat(missingHistory.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}