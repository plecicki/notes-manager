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
class ItemShareIntegrationTest {

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
    @DisplayName("owner can share, update role and revoke access")
    void shouldShareUpdateAndRevoke() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "share.owner", "password", "password123"), json), Map.class);
        ResponseEntity<Map> targetRegister = restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "share.target", "password", "password123"), json), Map.class);
        String targetUserId = (String) targetRegister.getBody().get("id");

        ResponseEntity<Map> ownerLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "share.owner", "password", "password123"), json), Map.class);
        String ownerToken = (String) ownerLogin.getBody().get("token");

        ResponseEntity<Map> targetLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "share.target", "password", "password123"), json), Map.class);
        String targetToken = (String) targetLogin.getBody().get("token");

        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ownerHeaders.setBearerAuth(ownerToken);

        HttpHeaders targetHeaders = new HttpHeaders();
        targetHeaders.setContentType(MediaType.APPLICATION_JSON);
        targetHeaders.setBearerAuth(targetToken);

        ResponseEntity<Map> created = restTemplate.postForEntity("/items",
                new HttpEntity<>(Map.of("title", "Shared note", "content", "Content"), ownerHeaders), Map.class);
        String itemId = (String) created.getBody().get("id");

        ResponseEntity<Map> share = restTemplate.postForEntity("/items/" + itemId + "/share",
                new HttpEntity<>(Map.of("userId", targetUserId, "role", "VIEWER"), ownerHeaders), Map.class);
        assertThat(share.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(share.getBody().get("role")).isEqualTo("VIEWER");
        assertThat(share.getBody().get("grantedAt")).isNotNull();

        ResponseEntity<Map> shareUpdate = restTemplate.postForEntity("/items/" + itemId + "/share",
                new HttpEntity<>(Map.of("userId", targetUserId, "role", "EDITOR"), ownerHeaders), Map.class);
        assertThat(shareUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shareUpdate.getBody().get("role")).isEqualTo("EDITOR");

        ResponseEntity<List> targetList = restTemplate.exchange("/items", HttpMethod.GET, new HttpEntity<>(targetHeaders), List.class);
        assertThat(targetList.getBody()).hasSize(1);
        Map listItem = (Map) targetList.getBody().get(0);
        assertThat(listItem.get("myRole")).isEqualTo("EDITOR");

        ResponseEntity<Void> revoke = restTemplate.exchange("/items/" + itemId + "/share/" + targetUserId, HttpMethod.DELETE, new HttpEntity<>(ownerHeaders), Void.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> revokeAgain = restTemplate.exchange("/items/" + itemId + "/share/" + targetUserId, HttpMethod.DELETE, new HttpEntity<>(ownerHeaders), Map.class);
        assertThat(revokeAgain.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<List> targetEmpty = restTemplate.exchange("/items", HttpMethod.GET, new HttpEntity<>(targetHeaders), List.class);
        assertThat(targetEmpty.getBody()).isEmpty();
    }

    @Test
    @DisplayName("editor can edit shared item, viewer cannot")
    void shouldAllowEditorToEditAndDenyViewer() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "edit.owner", "password", "password123"), json), Map.class);
        ResponseEntity<Map> targetRegister = restTemplate.postForEntity("/register", new HttpEntity<>(Map.of("login", "edit.target", "password", "password123"), json), Map.class);
        String targetUserId = (String) targetRegister.getBody().get("id");

        ResponseEntity<Map> ownerLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "edit.owner", "password", "password123"), json), Map.class);
        String ownerToken = (String) ownerLogin.getBody().get("token");

        ResponseEntity<Map> targetLogin = restTemplate.postForEntity("/login", new HttpEntity<>(Map.of("login", "edit.target", "password", "password123"), json), Map.class);
        String targetToken = (String) targetLogin.getBody().get("token");

        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ownerHeaders.setBearerAuth(ownerToken);

        HttpHeaders targetHeaders = new HttpHeaders();
        targetHeaders.setContentType(MediaType.APPLICATION_JSON);
        targetHeaders.setBearerAuth(targetToken);

        ResponseEntity<Map> editorItem = restTemplate.postForEntity("/items",
                new HttpEntity<>(Map.of("title", "Editor item", "content", "c"), ownerHeaders), Map.class);
        String editorItemId = (String) editorItem.getBody().get("id");
        restTemplate.postForEntity("/items/" + editorItemId + "/share",
                new HttpEntity<>(Map.of("userId", targetUserId, "role", "EDITOR"), ownerHeaders), Map.class);

        ResponseEntity<Map> viewerItem = restTemplate.postForEntity("/items",
                new HttpEntity<>(Map.of("title", "Viewer item", "content", "c"), ownerHeaders), Map.class);
        String viewerItemId = (String) viewerItem.getBody().get("id");
        restTemplate.postForEntity("/items/" + viewerItemId + "/share",
                new HttpEntity<>(Map.of("userId", targetUserId, "role", "VIEWER"), ownerHeaders), Map.class);

        Map<String, Object> editorPatch = new HashMap<>();
        editorPatch.put("title", "Edited");
        editorPatch.put("version", 0);
        ResponseEntity<Map> editorUpdate = restTemplate.exchange("/items/" + editorItemId, HttpMethod.PATCH,
                new HttpEntity<>(editorPatch, targetHeaders), Map.class);
        assertThat(editorUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(editorUpdate.getBody().get("version")).isEqualTo(1);

        Map<String, Object> viewerPatch = new HashMap<>();
        viewerPatch.put("title", "Forbidden");
        viewerPatch.put("version", 0);
        ResponseEntity<Map> viewerUpdate = restTemplate.exchange("/items/" + viewerItemId, HttpMethod.PATCH,
                new HttpEntity<>(viewerPatch, targetHeaders), Map.class);
        assertThat(viewerUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}