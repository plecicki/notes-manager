# Notes Manager
## Jak to działa
### Docker Compose
1. **init-env** — tworzy plik `.env` (kopiuje z `.env.example`).
2. **db** — MySQL 8.4. Tworzy bazę `Notes manager` oraz użytkownika `admin` (hasło `admin`). Wystawiony na `localhost:3306`.
3. **app** — Spring Boot + Hibernate/JPA. Startuje po bazie danych, łączy się z nią i nasłuchuje na `localhost:8080`.

## Uruchomienie
```bash
docker compose up --build
```