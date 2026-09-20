# PromoRunner

Branded endless-runner web game for lead generation and brand awareness. Players sign in with email, optionally consent to marketing (which unlocks unlimited plays), play the game, submit scores, and compete on a live leaderboard.

Single-tenant: one brand per deployment. Defaults are configured for AMSM Runner in `application.yml`.

## Stack

- Java 17 + Spring Boot
- Thymeleaf, HTMX, Alpine.js, Phaser.js
- PostgreSQL + Flyway
- Mailpit for local magic-link email

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

2. Make sure PostgreSQL is running and credentials match the `dev` profile in [`src/main/resources/application.yml`](src/main/resources/application.yml) (database name: `promorunner`).

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
- Mailpit (magic-link emails): http://localhost:8025
