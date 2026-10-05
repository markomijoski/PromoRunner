# PromoRunner

Branded endless-runner web game for lead generation and brand awareness. Players register with email + password, optionally consent to marketing (which unlocks unlimited plays), play the game, submit scores, and compete on a live leaderboard.

Single-tenant: one brand per deployment. Defaults are configured for AMSM Runner in `application.yml`.

## Stack

- Java 17 + Spring Boot
- Thymeleaf, HTMX, Alpine.js, custom canvas game (`static/js/game.js`)
- PostgreSQL + Flyway
- Mailpit for local password-reset email

## Prerequisites

- Java 17+
- Docker (for Mailpit)
- PostgreSQL with a `promorunner` database

Maven Wrapper is included (`mvnw` / `mvnw.cmd`) — no global Maven install required.

## Setup & run

1. Start Mailpit:

```bash
docker compose up -d
```

2. Make sure PostgreSQL is running with a `promorunner` database. Set credentials via environment variables (do not commit passwords):

| Variable | Required | Default |
|----------|----------|---------|
| `DB_PASSWORD` | **Yes** | — |
| `DB_USER` | No | `postgres` |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/promorunner` |

PowerShell example:

```powershell
$env:DB_PASSWORD = "your-local-password"
```

3. Start the app:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

4. Open:

- App: http://localhost:8080
- Mailpit (password-reset emails): http://localhost:8025
