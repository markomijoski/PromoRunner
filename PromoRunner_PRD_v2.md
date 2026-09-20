# PromoRunner — Agent Working Document
### Version 2.0 | Single-Tenant | Spring Boot + Thymeleaf + HTMX + Alpine.js

> **How to use this file:** This is the canonical spec for the PromoRunner project.
> Work through it section by section. Every task has a checkbox. Mark `[x]` when done.
> Ask before deviating from any decision marked **LOCKED**.

---

## Table of Contents

1. [Project Summary](#1-project-summary)
2. [Tech Stack](#2-tech-stack)
3. [Application Structure](#3-application-structure)
4. [Database Schema](#4-database-schema)
5. [Authentication Flow](#5-authentication-flow)
6. [Consent & Play Gate](#6-consent--play-gate)
7. [Game Specification](#7-game-specification)
8. [Asset Registry & Spawn Rules](#8-asset-registry--spawn-rules)
9. [Live Leaderboard](#9-live-leaderboard)
10. [API & Controller Endpoints](#10-api--controller-endpoints)
11. [Thymeleaf Views & HTMX Flows](#11-thymeleaf-views--htmx-flows)
12. [Backend Task List](#12-backend-task-list)
13. [Frontend Task List](#13-frontend-task-list)
14. [Non-Functional Requirements](#14-non-functional-requirements)
15. [Execution Order](#15-execution-order)

---

## 1. Project Summary

PromoRunner is a branded endless-runner web game used as a lead generation and brand awareness tool for a single company. Players authenticate with their email, optionally consent to a marketing mailing list (which unlocks unlimited plays), and compete on a live leaderboard.

The codebase serves one brand. When deploying for a different brand, the developer reconfigures assets and branding — no multi-tenant logic needed.

**Core value loop:**
```
Player lands → sees branded game → logs in with email
→ consent gate (opt-in = unlimited plays)
→ plays game → submits score → sees live leaderboard
→ shares score → friend arrives → loop repeats
```

---

## 2. Tech Stack

**LOCKED — do not change without explicit approval.**

| Layer | Technology | Notes |
|---|---|---|
| Language | Java 21 | LTS |
| Framework | Spring Boot 3.x | Web, Security, Data JPA, Mail, WebSocket |
| Template Engine | Thymeleaf | Server-rendered HTML, all pages |
| Interactivity | HTMX | Partial page swaps, form submissions, polling |
| UI Behaviour | Alpine.js | Dropdowns, toggling, local UI state |
| Styling | Tailwind CSS v3 | Via CDN in dev, build step optional |
| Game Engine | Phaser.js 3 | Loaded via CDN, canvas-based game |
| Database | PostgreSQL 15 | Primary data store |
| Migrations | Flyway | Versioned SQL migrations |
| Session / Cache | Redis | HTTP sessions, leaderboard hot cache |
| Email | Spring Mail + SendGrid | Magic link / transactional emails |
| Build | Maven | Standard Spring Boot setup |
| Dev Environment | Docker Compose | PostgreSQL + Redis local |

---

## 3. Application Structure

```
promorunner/
├── src/
│   ├── main/
│   │   ├── java/com/promorunner/
│   │   │   ├── PromoRunnerApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── WebSocketConfig.java
│   │   │   │   └── RedisConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── PageController.java          # Thymeleaf page routes
│   │   │   │   ├── AuthController.java          # Login, magic link
│   │   │   │   ├── ConsentController.java       # Consent submission
│   │   │   │   ├── GameController.java          # Session start/end
│   │   │   │   ├── LeaderboardController.java   # REST + WS
│   │   │   │   └── AdminController.java         # Asset config, analytics
│   │   │   ├── model/
│   │   │   │   ├── User.java
│   │   │   │   ├── MagicLinkToken.java
│   │   │   │   ├── MarketingConsent.java
│   │   │   │   ├── GameSession.java
│   │   │   │   ├── Score.java
│   │   │   │   └── BrandConfig.java          -- loaded from brand.json at startup
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── ConsentService.java
│   │   │   │   ├── GameSessionService.java
│   │   │   │   ├── ScoreService.java
│   │   │   │   ├── LeaderboardService.java
│   │   │   │   └── GameConfigService.java    -- loads & caches game-config.json
│   │   │   ├── dto/
│   │   │   ├── event/
│   │   │   │   └── ScoreSubmittedEvent.java
│   │   │   └── websocket/
│   │   │       └── LeaderboardWebSocketController.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── game-config.json                 # ← single game config file (assets + spawn rules)
│   │       ├── db/migration/                    # Flyway SQL files
│   │       ├── static/
│   │       │   ├── images/
│   │       │   │   ├── character/               # run, jump, fall, slide frames
│   │       │   │   ├── obstacles/               # horizontal and vertical wall images
│   │       │   │   └── ui/                      # logo, favicon
│   │       │   └── js/
│   │       │       └── game.js                  # Phaser game bootstrap
│   │       └── templates/
│   │           ├── layout/
│   │           │   └── base.html                # Shared layout fragment
│   │           ├── index.html                   # Landing page
│   │           ├── login.html                   # Email login form
│   │           ├── verify.html                  # "Check your email" screen
│   │           ├── consent.html                 # Consent gate
│   │           ├── game.html                    # Game page (Phaser canvas)
│   │           ├── leaderboard.html             # Leaderboard full view
│   │           └── admin/
│   │               ├── dashboard.html
│   │               ├── assets.html              # Asset manager
│   │               └── leads.html               # Consented leads export
├── docker-compose.yml
├── pom.xml
└── .env.example
```

---

## 4. Database Schema

Single-tenant — no `tenant_id` anywhere. Clean and minimal.

### 4.1 Flyway Migration Files

```
V1__create_users.sql
V2__create_magic_link_tokens.sql
V3__create_marketing_consents.sql
V4__create_game_sessions.sql
V5__create_scores.sql
```

> No migration needed for game assets — they live in `game-config.json`.

---

### 4.2 Table: `users`

```sql
CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    display_name          VARCHAR(255),
    is_email_verified     BOOLEAN NOT NULL DEFAULT false,
    free_plays_remaining  INTEGER DEFAULT 3,       -- NULL means unlimited
    role                  VARCHAR(20) NOT NULL DEFAULT 'PLAYER', -- PLAYER | ADMIN
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at          TIMESTAMPTZ
);
```

**Notes:**
- `free_plays_remaining = NULL` → user has consented, plays are unlimited
- `free_plays_remaining = 0` → gate is shown, user must consent to continue
- `role = 'ADMIN'` → can access `/admin/**` routes

---

### 4.3 Table: `magic_link_tokens`

```sql
CREATE TABLE magic_link_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 of raw token
    expires_at  TIMESTAMPTZ NOT NULL,           -- now() + 15 minutes
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_magic_link_token_hash ON magic_link_tokens(token_hash);
```

---

### 4.4 Table: `marketing_consents`

```sql
CREATE TABLE marketing_consents (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consented        BOOLEAN NOT NULL,
    consent_version  VARCHAR(20) NOT NULL DEFAULT '1.0',
    ip_address       INET,
    user_agent       TEXT,
    consented_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at     TIMESTAMPTZ              -- set on withdrawal, never delete rows
);

CREATE INDEX idx_consents_user_id ON marketing_consents(user_id);
```

**Rules:**
- Rows are never updated — append only
- A new row is inserted on every consent state change (grant or withdrawal)
- Latest row per user = current consent state

---

### 4.5 Table: `game_sessions`

```sql
CREATE TABLE game_sessions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ,
    final_score  INTEGER,
    duration_ms  INTEGER,
    device_type  VARCHAR(20),                  -- 'mobile' | 'desktop'
    is_completed BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_sessions_user_id ON game_sessions(user_id);
```

---

### 4.6 Table: `scores`

```sql
-- One row per user — their personal best
CREATE TABLE scores (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    best_score     INTEGER NOT NULL DEFAULT 0,
    total_plays    INTEGER NOT NULL DEFAULT 0,
    last_played_at TIMESTAMPTZ
);

CREATE INDEX idx_scores_best ON scores(best_score DESC);
```

---

### 4.7 Game Config File (`game-config.json`)

Asset registry and spawn rules live in `src/main/resources/game-config.json`. No DB table, no migration, no admin UI needed — edit the file and restart.

**Full file structure:**

```json
{
  "baseScrollSpeed": 300,

  "character": {
    "runImageUrl": "/images/character/run.png",
    "runFrameCount": 4,
    "jumpImageUrl": "/images/character/jump.png",
    "fallImageUrl": "/images/character/fall.png",
    "slideImageUrl": "/images/character/slide.png"
  },

  "obstacles": [
    {
      "key": "obs_h_low_wall",
      "type": "OBSTACLE_HORIZONTAL",
      "imageUrl": "/images/obstacles/h_low_wall.png",
      "active": true,
      "minScoreToSpawn": 0,
      "spawnWeight": 10,
      "minUnits": 1,
      "maxUnits": 3,
      "positionType": "LOW"
    },
    {
      "key": "obs_h_high_wall",
      "type": "OBSTACLE_HORIZONTAL",
      "imageUrl": "/images/obstacles/h_high_wall.png",
      "active": true,
      "minScoreToSpawn": 300,
      "spawnWeight": 8,
      "minUnits": 1,
      "maxUnits": 2,
      "positionType": "HIGH"
    },
    {
      "key": "obs_v_wall",
      "type": "OBSTACLE_VERTICAL",
      "imageUrl": "/images/obstacles/v_wall.png",
      "active": true,
      "minScoreToSpawn": 0,
      "spawnWeight": 10,
      "minUnits": 1,
      "maxUnits": 4,
      "positionType": null
    },
    {
      "key": "obs_h_low_wide",
      "type": "OBSTACLE_HORIZONTAL",
      "imageUrl": "/images/obstacles/h_low_wall.png",
      "active": true,
      "minScoreToSpawn": 600,
      "spawnWeight": 5,
      "minUnits": 3,
      "maxUnits": 4,
      "positionType": "LOW"
    }
  ]
}
```

**Field reference:**

| Field | Description |
|---|---|
| `baseScrollSpeed` | Starting scroll speed in px/sec |
| `character.*` | Image paths and frame count for each animation state |
| `obstacles[].key` | Unique identifier (used by Phaser to reference the loaded texture) |
| `obstacles[].type` | `OBSTACLE_HORIZONTAL` or `OBSTACLE_VERTICAL` |
| `obstacles[].active` | `false` = skip this asset entirely at runtime |
| `obstacles[].minScoreToSpawn` | Asset not eligible until player reaches this score |
| `obstacles[].spawnWeight` | Relative probability; higher = spawns more often |
| `obstacles[].minUnits` / `maxUnits` | Random size drawn from this range at spawn time |
| `obstacles[].positionType` | `LOW` = on ground (jump over); `HIGH` = elevated (slide under); `null` = vertical wall |

**Spring loading — `GameConfigService.java`:**

```java
@Service
public class GameConfigService {

    private final GameConfig config;

    public GameConfigService(ResourceLoader resourceLoader, ObjectMapper objectMapper) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:game-config.json");
        this.config = objectMapper.readValue(resource.getInputStream(), GameConfig.class);
    }

    public GameConfig getConfig() {
        return config;
    }

    // Returns only active obstacles eligible at the given score
    public List<ObstacleAsset> getEligibleObstacles(int currentScore) {
        return config.getObstacles().stream()
            .filter(o -> o.isActive() && o.getMinScoreToSpawn() <= currentScore)
            .collect(Collectors.toList());
    }
}
```

Config is loaded once at startup and held in memory — no file I/O per request.

---

## 5. Authentication Flow

**LOCKED — email form only, no OAuth.**

### 5.1 Flow Diagram

```
User visits /login
    → enters email → POST /auth/login
        → if user exists: generate magic link token, send email
        → if user doesn't exist: create user record, generate token, send email
        → redirect to /login/verify (shows "Check your email" screen)

User clicks link in email → GET /auth/verify?token={rawToken}
    → hash token, look up in magic_link_tokens
    → validate: not used, not expired
    → mark token as used
    → mark user.is_email_verified = true
    → create Spring Security session
    → redirect to /consent (if first time) or /game (if returning with consent on file)
```

### 5.2 Magic Link Details

- Raw token: `UUID.randomUUID()` → 36-char URL-safe string
- Stored as: `SHA-256(rawToken)` in `token_hash` column
- Link format: `https://{domain}/auth/verify?token={rawToken}`
- Expiry: 15 minutes
- Single-use: `used_at` set on first use; subsequent attempts return 400
- Old unused tokens for the same user are invalidated on new request

### 5.3 Spring Security Config

```
Public routes:   /  /login  /login/verify  /auth/**  /static/**
Protected:       /game  /consent  /leaderboard  /api/**
Admin only:      /admin/**
```

Session stored in Redis. `HttpSession` with 24-hour timeout.

---

## 6. Consent & Play Gate

### 6.1 Logic

```
After first login:
    → show /consent page

User checks consent box and submits:
    → insert row into marketing_consents (consented = true)
    → set users.free_plays_remaining = NULL (unlimited)
    → redirect to /game

User skips consent:
    → insert row into marketing_consents (consented = false)
    → users.free_plays_remaining stays at 3
    → redirect to /game

On each game start (POST /api/game/session/start):
    → if free_plays_remaining == NULL: allow (unlimited)
    → if free_plays_remaining > 0: allow, decrement
    → if free_plays_remaining == 0: return 403, frontend shows consent prompt overlay
```

### 6.2 Consent Page UI (Thymeleaf)

```
┌──────────────────────────────────────────┐
│  [Brand Logo]                            │
│                                          │
│  You're almost in.                       │
│                                          │
│  ☐ I agree to receive marketing emails  │
│    from [Brand Name]. Unsubscribe        │
│    anytime.                              │
│                                          │
│  ✓ Accept = unlimited plays             │
│  → Skip = 3 free plays                 │
│                                          │
│  [ Let me play unlimited →  ]           │
│  [ Skip for now ]                        │
└──────────────────────────────────────────┘
```

---

## 7. Game Specification

### 7.1 Overview

- **Engine:** Phaser.js 3, loaded via CDN in `game.html`
- **Camera:** Static viewport; world scrolls left
- **Ground:** Single colored rectangle (CSS configured), 1 unit tall at bottom of canvas
- **Background:** CSS/HTML colored layers — no image background
- **Canvas size:** Responsive — fills viewport width, fixed aspect ratio 16:9
- **Scroll speed:** Starts at base speed, increases gradually with score

### 7.2 Grid Unit System

The game world uses a virtual **grid unit** for consistent sizing across screen sizes.

```
UNIT_SIZE = canvas.height / 12

Ground height     = 1 unit  (bottom of canvas)
Character height  = 2 units
Character width   = 1 unit

Obstacle unit sizes:
  OBSTACLE_HORIZONTAL: width = 1–4 units, height = 1 unit always
  OBSTACLE_VERTICAL:   width = 1 unit always, height = 1–4 units
```

All sizes and positions computed from `UNIT_SIZE` at runtime — responsive by design.

### 7.3 Character States & Animations

Character sits on the ground, runs continuously.

| State | Trigger | Frames | Notes |
|---|---|---|---|
| `RUN` | Default | 4–6 frames loop | Loaded from `CHARACTER_RUN` assets |
| `JUMP` | Space / Tap / Up arrow | 1–2 frames | Plays during upward arc |
| `FALL` | Apex of jump | 1–2 frames | Plays during downward arc |
| `SLIDE` | Down arrow / Swipe down | 2 frames loop | Hitbox shrinks to 1×1 unit; held while key held |

**Jump physics:**
- Single jump only (no double jump in v1)
- Fixed jump force upward; gravity pulls back down
- Phaser Arcade Physics handles arc
- Character cannot jump while sliding

**Input mapping:**

| Action | Keyboard | Mobile |
|---|---|---|
| Jump | `Space` or `ArrowUp` | Tap upper half of canvas |
| Slide | `ArrowDown` | Tap lower half of canvas (or swipe down) |

### 7.4 Obstacle Mechanics

**Horizontal wall (`OBSTACLE_HORIZONTAL`):**
- Width: random 1–4 units, drawn from asset's `min_units`/`max_units`
- Height: always 1 unit
- `position_type = LOW`: spawns on the ground → player must **jump over**
- `position_type = HIGH`: spawns elevated (2 units from ground) → player must **slide under**
- Image tiles horizontally to fill the chosen width

**Vertical wall (`OBSTACLE_VERTICAL`):**
- Width: always 1 unit
- Height: random 1–4 units, drawn from asset's `min_units`/`max_units`
- Always spawns on the ground, extends upward
- Player must **jump over** (if short enough) — if too tall, it's a guaranteed hit unless the gap timing allows passing
- Image tiles vertically to fill the chosen height

### 7.5 Obstacle Spawn Rules

```
Spawn logic (server provides config; Phaser uses it):

1. Minimum gap between obstacles: 4 units (always passable)
2. Never spawn HIGH horizontal immediately after a vertical wall (forces impossible combo)
3. Consecutive LOW horizontals: max 2 in a row (prevent unfair stacking)
4. Select which asset to spawn:
   a. Filter active assets where min_score_to_spawn <= current_score
   b. Weighted random selection using spawn_weight
5. Obstacle width/height: random integer between asset's min_units and max_units
6. Spawn frequency increases every 500 points (reduce minimum gap slightly, increase scroll speed)
```

**Scroll speed progression:**

| Score Range | Scroll Speed (px/frame) | Min Gap (units) |
|---|---|---|
| 0 – 499 | Base | 5 |
| 500 – 999 | Base × 1.2 | 4.5 |
| 1000 – 1999 | Base × 1.5 | 4 |
| 2000+ | Base × 2.0 | 3.5 |

### 7.6 Score System

- Score = distance travelled (increments every 100ms by current speed factor)
- Score displayed in top-right corner of canvas
- Personal best shown below current score if available
- On game over: score submitted to backend, rank returned and displayed

### 7.7 Game States

```
LOADING   → assets loaded, waiting for player to press Space/Tap
PLAYING   → active game loop
PAUSED    → (future, v2)
GAME_OVER → score displayed, "Play Again" and "Share Score" buttons shown
GATE      → out of plays — consent overlay shown instead of game
```

---

## 8. Asset Registry & Spawn Rules

### 8.1 How Assets Are Loaded Into Phaser

On `game.html` load:
1. Spring reads `game-config.json` at startup via `GameConfigService` (loaded once, held in memory)
2. `PageController` passes it to the Thymeleaf model as `gameConfig`
3. Thymeleaf injects it as a JSON object in a `<script>` tag
4. Phaser `BootScene` reads this config and preloads all active image URLs
5. `GameScene` uses the spawn rules from the config for obstacle selection

**Thymeleaf injection pattern:**
```html
<script th:inline="javascript">
  window.GAME_CONFIG = /*[[${gameConfig}]]*/ {};
</script>
<script src="/js/game.js"></script>
```

`gameConfig` is a DTO populated by `AssetService` and passed to the model by `PageController`.

### 8.2 GameConfig Java Model

These classes map directly to `game-config.json` via Jackson. They are also what gets serialised into `window.GAME_CONFIG` on the page.

```java
// Maps to the root of game-config.json
public class GameConfig {
    private int baseScrollSpeed;
    private CharacterAsset character;
    private List<ObstacleAsset> obstacles;
    // getters/setters or use @Data (Lombok)
}

public class CharacterAsset {
    private String runImageUrl;
    private int runFrameCount;
    private String jumpImageUrl;
    private String fallImageUrl;
    private String slideImageUrl;
}

public class ObstacleAsset {
    private String key;
    private String type;           // OBSTACLE_HORIZONTAL | OBSTACLE_VERTICAL
    private String imageUrl;
    private boolean active;
    private int minScoreToSpawn;
    private int spawnWeight;
    private int minUnits;
    private int maxUnits;
    private String positionType;   // LOW | HIGH | null
}

// What PageController passes to Thymeleaf model (adds runtime user state)
public class GameConfigDto {
    private GameConfig config;     // full config from JSON
    private int playsRemaining;    // -1 = unlimited; from users.free_plays_remaining
    private long currentUserId;    // for leaderboard row highlighting
}
```

### 8.3 Image Placeholder Requirements

These placeholder images must exist before the game can run. Create them as simple colored rectangles initially:

| File path | Description | Placeholder color |
|---|---|---|
| `/static/images/character/run.png` | Character run spritesheet (4 frames horizontal) | Blue `#3B82F6` |
| `/static/images/character/jump.png` | Character jump frame | Cyan `#06B6D4` |
| `/static/images/character/fall.png` | Character fall frame | Indigo `#6366F1` |
| `/static/images/character/slide.png` | Character slide frame | Purple `#8B5CF6` |
| `/static/images/obstacles/h_low_wall.png` | Horizontal low wall (1 unit tile) | Red `#EF4444` |
| `/static/images/obstacles/h_high_wall.png` | Horizontal high wall (1 unit tile) | Orange `#F97316` |
| `/static/images/obstacles/v_wall.png` | Vertical wall (1 unit tile) | Yellow `#EAB308` |

All obstacle images are **1-unit tiles** — Phaser repeats/tiles them to fill the chosen size. This means you only need one image per obstacle type, not one per size.

**Character run animation:** If using a spritesheet (recommended), the sheet should be 4 frames wide × 1 frame tall, each frame the same width. Phaser's `anims.create` will slice it.

---

## 9. Live Leaderboard

### 9.1 Architecture

- Leaderboard data: PostgreSQL `scores` table, ordered by `best_score DESC`
- Hot cache: Redis `ZSET` keyed `leaderboard` — member = `userId`, score = `best_score`
- Read: `ZREVRANGE leaderboard 0 9 WITHSCORES` (top 10, O(log N))
- Write: on score submission, `ZADD leaderboard {score} {userId}` if new best
- Live updates: WebSocket (STOMP) push to all connected clients when leaderboard changes

### 9.2 WebSocket Setup

- Endpoint: `/ws` (STOMP over WebSocket)
- Topic: `/topic/leaderboard` — server broadcasts top 10 on any score change
- Client subscribes on `game.html` load
- Fallback: if WebSocket fails, HTMX polling `GET /api/leaderboard` every 10 seconds

### 9.3 Leaderboard Panel UI

Shown as a sidebar panel on `game.html`. Updates in real time without page reload.

```
┌──────────────────────┐
│  🏆 Leaderboard      │
├──────────────────────┤
│  #1  Jane D.  12,540 │
│  #2  Mark S.   9,820 │
│  #3  You ★     4,200 │ ← current user highlighted
│  #4  Alex K.   3,100 │
│  ...                 │
└──────────────────────┘
```

---

## 10. API & Controller Endpoints

### 10.1 Page Routes (Thymeleaf — PageController)

| Method | Path | Auth | Returns |
|---|---|---|---|
| `GET` | `/` | Public | `index.html` — landing page |
| `GET` | `/login` | Public | `login.html` — email form |
| `GET` | `/login/verify` | Public | `verify.html` — "check your email" |
| `GET` | `/consent` | Authenticated | `consent.html` — consent gate |
| `GET` | `/game` | Authenticated | `game.html` — game + leaderboard |
| `GET` | `/leaderboard` | Authenticated | `leaderboard.html` — full leaderboard |
| `GET` | `/admin` | Admin | `admin/dashboard.html` |
| `GET` | `/admin/assets` | Admin | `admin/assets.html` — config viewer + image uploader |
| `GET` | `/admin/leads` | Admin | `admin/leads.html` |

### 10.2 Auth Routes (AuthController)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Submit email → send magic link |
| `GET` | `/auth/verify` | Public | Verify token → create session |
| `POST` | `/auth/logout` | Authenticated | Invalidate session |

**POST /auth/login request (form submit, HTMX):**
```
email=user@example.com
```
**Response:** HTMX swap to show "check your email" message (no redirect needed)

**GET /auth/verify?token={rawToken}:**
- Valid → session created → redirect to `/consent` or `/game`
- Invalid/expired → redirect to `/login?error=invalid_token`

### 10.3 Consent Routes (ConsentController)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/consent` | Authenticated | Submit consent decision |
| `POST` | `/consent/withdraw` | Authenticated | Withdraw consent |

**POST /consent request (form):**
```
consented=true   (or absent if skipping)
```
**Response:** redirect to `/game`

### 10.4 Game API Routes (GameController — JSON)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/game/session/start` | Authenticated | Validate eligibility, create session |
| `POST` | `/api/game/session/{id}/end` | Authenticated | Submit score, close session |

**POST /api/game/session/start response:**
```json
{
  "sessionId": 42,
  "canPlay": true,
  "playsRemaining": 2
}
```
If blocked:
```json
{
  "sessionId": null,
  "canPlay": false,
  "playsRemaining": 0,
  "reason": "CONSENT_REQUIRED"
}
```

**POST /api/game/session/{id}/end request:**
```json
{ "score": 4820, "durationMs": 94200 }
```
**Response:**
```json
{
  "score": 4820,
  "personalBest": true,
  "rank": 3,
  "totalPlayers": 124
}
```

### 10.5 Leaderboard Routes (LeaderboardController)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/leaderboard` | Authenticated | Top 10 scores (JSON) |
| `GET` | `/api/leaderboard/me` | Authenticated | Current user rank |

**GET /api/leaderboard response:**
```json
{
  "entries": [
    { "rank": 1, "displayName": "Jane D.", "score": 12540 },
    { "rank": 2, "displayName": "Mark S.", "score": 9820 }
  ],
  "totalPlayers": 124
}
```

### 10.6 WebSocket (LeaderboardWebSocketController)

| Type | Address | Description |
|---|---|---|
| STOMP endpoint | `/ws` | WebSocket handshake |
| Subscribe | `/topic/leaderboard` | Receive leaderboard updates |
| App destination | `/app/leaderboard/ping` | Client can request immediate snapshot |

**Broadcast payload (on any score change):**
```json
{
  "type": "LEADERBOARD_UPDATE",
  "entries": [...],
  "updatedAt": "2025-03-15T14:23:01Z"
}
```

### 10.7 Admin Routes (AdminController)

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/api/assets` | Returns current parsed `game-config.json` as JSON (read-only view) |
| `POST` | `/admin/api/assets/upload` | Upload an image file → saves to `/static/images/`, returns saved path |
| `GET` | `/admin/api/leads` | Export consented leads (JSON or CSV) |
| `GET` | `/admin/api/analytics` | Summary stats |

---

## 11. Thymeleaf Views & HTMX Flows

### 11.1 Layout Fragment (`base.html`)

All pages extend `base.html` which includes:
- `<meta>` tags with brand config values (injected via Thymeleaf)
- Tailwind CSS CDN
- Alpine.js CDN
- HTMX CDN
- Brand colors as CSS custom properties in `<style>`

```html
<style>
  :root {
    --color-primary: /*[(${brand.primaryColor})]*/;
    --color-secondary: /*[(${brand.secondaryColor})]*/;
    --color-bg: /*[(${brand.backgroundColor})]*/;
  }
</style>
```

### 11.2 Login Page HTMX Flow

```html
<!-- login.html -->
<form hx-post="/auth/login"
      hx-target="#login-result"
      hx-swap="innerHTML">
  <input type="email" name="email" required placeholder="your@email.com" />
  <button type="submit">Send me a login link</button>
</form>
<div id="login-result"></div>
```

Server returns a Thymeleaf fragment on success:
```html
<!-- fragment: login :: success -->
<div>
  <p>Check your inbox — we sent a link to <strong th:text="${email}"></strong>.</p>
  <p>The link expires in 15 minutes.</p>
</div>
```

### 11.3 Consent Page

Standard form submit (no HTMX needed — full redirect after consent is cleaner):
```html
<form method="POST" action="/consent">
  <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
  <label>
    <input type="checkbox" name="consented" value="true" />
    <span th:text="${brand.consentLabelText}">I agree to receive marketing emails.</span>
  </label>
  <button type="submit" name="action" value="consent">Play unlimited</button>
  <button type="submit" name="action" value="skip">Skip (3 free plays)</button>
</form>
```

### 11.4 Game Page Layout

```
┌──────────────────────────────────────────────────┐
│  [Logo]                     [User: Jane | Logout]│
├────────────────────────────┬─────────────────────┤
│                            │  🏆 Leaderboard     │
│   [Phaser Canvas]          │  #1 Jane   12,540   │
│                            │  #2 Mark    9,820   │
│                            │  #3 You ★   4,200   │
│                            │                     │
│                            │  [Share My Score]   │
├────────────────────────────┴─────────────────────┤
│  Space/↑ to jump · ↓ to slide                   │
└──────────────────────────────────────────────────┘
```

- Leaderboard panel updates via WebSocket (JS updates DOM directly)
- HTMX fallback polling: `hx-get="/api/leaderboard" hx-trigger="every 10s"` on leaderboard div

### 11.5 Admin Asset Manager

```
Read-only table of current game-config.json contents (rendered server-side by Thymeleaf).
Each row shows:
  - Asset key, type, image preview (<img> from imageUrl)
  - Current spawn weight, minScoreToSpawn, minUnits/maxUnits, positionType
  - Upload new image button → POST /admin/api/assets/upload → returns path
  - Developer copies the returned path into game-config.json manually

Note shown on page:
  "To change spawn rules or toggle assets, edit game-config.json and restart the app."
```

This is intentionally simple for v1. The config is developer-owned — not end-user editable.

---

## 12. Backend Task List

### Phase 1 — Foundation

- [ ] **B-01** Initialize Spring Boot 3 project with Java 21 via Spring Initializr. Dependencies: Web, Thymeleaf, Security, Data JPA, WebSocket, Mail, Session (Redis), Flyway, PostgreSQL Driver, Lombok, Validation
- [ ] **B-02** Configure `application.yml` for dev profile: datasource, Redis, mail (console output in dev), Flyway location
- [ ] **B-03** Create `docker-compose.yml` with `postgres:15` and `redis:7-alpine` services
- [ ] **B-04** Write `V1__create_users.sql` through `V5__create_scores.sql` Flyway migrations (use exact SQL from Section 4)
- [ ] **B-05** Create `src/main/resources/game-config.json` with the full structure from Section 4.7 — use placeholder image paths; file must be valid JSON before app starts
- [ ] **B-06** Configure Spring Security: `SecurityConfig.java` — public/protected/admin route mapping, form login disabled (we do our own), CSRF enabled (use for forms), session management via Redis

### Phase 2 — Authentication

- [ ] **B-07** Create `User`, `MagicLinkToken` JPA entities + repositories
- [ ] **B-08** Implement `AuthService.requestMagicLink(email)`:
  - Create or find user by email
  - Invalidate old unused tokens for this user
  - Generate `UUID.randomUUID()` raw token → SHA-256 hash → save `MagicLinkToken`
  - Send email with link (Spring Mail, template: `email/magic-link.html`)
- [ ] **B-09** Implement `AuthService.verifyToken(rawToken)`:
  - Hash token, look up in DB
  - Validate: exists, `used_at IS NULL`, `expires_at > now()`
  - Mark `used_at = now()`
  - Mark `user.is_email_verified = true`
  - Create Spring Security `UsernamePasswordAuthenticationToken` and set in `SecurityContext`
  - Save session
  - Return redirect destination: `/consent` if no consent record exists, else `/game`
- [ ] **B-10** Implement `AuthController`: `POST /auth/login` (HTMX), `GET /auth/verify`, `POST /auth/logout`
- [ ] **B-11** Write unit tests for `AuthService` (mock mail, mock repo)

### Phase 3 — Consent

- [ ] **B-12** Create `MarketingConsent` JPA entity + repository
- [ ] **B-13** Implement `ConsentService.recordConsent(userId, consented, ip, userAgent)`:
  - Insert row into `marketing_consents`
  - If `consented = true`: set `user.free_plays_remaining = null`
  - If `consented = false`: leave `free_plays_remaining = 3`
- [ ] **B-14** Implement `ConsentService.withdrawConsent(userId)`:
  - Insert row with `consented = false`, set `withdrawn_at = now()`
  - Note: does NOT restore free plays — user keeps unlimited if they had it, this just unsubscribes from marketing
- [ ] **B-15** Implement `ConsentController`: `POST /consent`, `POST /consent/withdraw`

### Phase 4 — Game Session & Scoring

- [ ] **B-16** Create `GameSession`, `Score` JPA entities + repositories
- [ ] **B-17** Implement `GameSessionService.startSession(userId, deviceType)`:
  - Check `user.free_plays_remaining`: if 0 → throw `NoPlaysRemainingException`
  - If not null: decrement `free_plays_remaining`
  - Create and save `GameSession` record
  - Return session ID
- [ ] **B-18** Implement `GameSessionService.endSession(sessionId, userId, score, durationMs)`:
  - Validate session belongs to this user and is not already completed
  - Set `ended_at`, `final_score`, `duration_ms`, `is_completed = true`
  - Call `ScoreService.updateScore(userId, score)`
  - Publish `ScoreSubmittedEvent`
- [ ] **B-19** Implement `ScoreService.updateScore(userId, score)`:
  - Upsert `scores` table: if `score > best_score` → update; always increment `total_plays`
  - Update Redis ZSET: `ZADD leaderboard {score} {userId}` (only if new best)
- [ ] **B-20** Implement `GameController`: `POST /api/game/session/start`, `POST /api/game/session/{id}/end`

### Phase 5 — Leaderboard & WebSocket

- [ ] **B-21** Implement `LeaderboardService.getTopTen()`:
  - Try Redis: `ZREVRANGE leaderboard 0 9 WITHSCORES` → enrich with display names from DB
  - If Redis miss: query PostgreSQL `scores JOIN users ORDER BY best_score DESC LIMIT 10`
- [ ] **B-22** Implement `LeaderboardController`: `GET /api/leaderboard`, `GET /api/leaderboard/me`
- [ ] **B-23** Configure `WebSocketConfig.java`: STOMP endpoint `/ws`, topic broker `/topic`, app destination prefix `/app`
- [ ] **B-24** Implement `LeaderboardWebSocketController`:
  - `@SubscribeMapping("/leaderboard/ping")` → return current snapshot
  - Listen for `ScoreSubmittedEvent` via `@EventListener` → call `SimpMessagingTemplate.convertAndSend("/topic/leaderboard", leaderboardDto)`
- [ ] **B-25** Test WebSocket broadcast manually using Postman or STOMP test client

### Phase 6 — Asset Service & Admin

- [ ] **B-26** Create `GameConfig`, `CharacterAsset`, `ObstacleAsset` Java model classes (Section 8.2) — annotate with Jackson `@JsonProperty` as needed
- [ ] **B-27** Implement `GameConfigService`: read `game-config.json` from classpath via `ResourceLoader` + `ObjectMapper` in constructor; store as field; expose `getConfig()` and `getEligibleObstacles(int score)` (see Section 4.7 for full code)
- [ ] **B-28** Implement `AdminController` — for v1, image upload only: `POST /admin/api/assets/upload` saves file to `/static/images/`, returns saved path; developer manually updates `game-config.json` with the path
- [ ] **B-29** Implement `PageController`: call `GameConfigService.getConfig()`, wrap with user's `free_plays_remaining` and `currentUserId` into `GameConfigDto`, add to model for `game.html`; add brand config (from `application.yml` or a separate `brand.json`) to model for all pages

### Phase 7 — Security & Quality

- [ ] **B-30** Add CSRF token to all forms (Thymeleaf `_csrf` integration)
- [ ] **B-31** Add rate limiting on `POST /auth/login`: max 5 requests per email per 10 minutes (use `Bucket4j` or simple in-memory counter)
- [ ] **B-32** Add `@Validated` and constraint annotations to all request DTOs
- [ ] **B-33** Add `@ControllerAdvice` global exception handler: map exceptions to appropriate HTTP responses or error page fragments
- [ ] **B-34** Write integration tests for session start/end flow (Testcontainers + PostgreSQL)

---

## 13. Frontend Task List

### Phase 1 — Base Layout & Styling

- [ ] **F-01** Create `base.html` Thymeleaf layout: CSS vars from brand config, include Tailwind CDN, Alpine.js CDN, HTMX CDN
- [ ] **F-02** Build `index.html` (landing page): brand logo, game title, animated teaser text, "Play Now" CTA linking to `/login`
- [ ] **F-03** Build `login.html`: centered email form with HTMX submit, result target div, loading state via Alpine.js (`x-data="{loading: false}"`)
- [ ] **F-04** Build `verify.html`: static "check your email" confirmation screen
- [ ] **F-05** Build `consent.html`: checkbox form per Section 6.2, two submit buttons (consent / skip)

### Phase 2 — Game Page Shell

- [ ] **F-06** Build `game.html` layout: header bar (logo + user name + logout), two-column layout (canvas left, leaderboard right), controls hint footer
- [ ] **F-07** Add Thymeleaf `<script>` block injecting `window.GAME_CONFIG` from model
- [ ] **F-08** Add Phaser.js CDN `<script>` tag after config injection
- [ ] **F-09** Create `/static/js/game.js` as the Phaser game entry point (empty bootstrap for now)

### Phase 3 — Phaser Game Implementation (`game.js`)

- [ ] **F-10** Bootstrap `Phaser.Game` config: canvas parent, Arcade Physics, scenes list, responsive width/height
- [ ] **F-11** Implement `BootScene`:
  - Read `window.GAME_CONFIG`
  - Preload all character images (`this.load.image` or `this.load.spritesheet` for run animation)
  - Preload all obstacle images from `config.obstacles`
  - Show simple loading bar
  - On complete: start `GameScene`
- [ ] **F-12** Implement `GameScene`:
  - Draw ground as filled rectangle (no image)
  - Draw colored background rectangle
  - Place character sprite at fixed X position, on ground Y
  - Set up character animations: `run` (spritesheet frames), `jump`/`fall`/`slide` (single frames)
  - Implement `UNIT_SIZE = this.scale.height / 12`
  - Start scroll speed from `config.baseScrollSpeed`
- [ ] **F-13** Implement player input handling:
  - `Space` / `ArrowUp` → jump (if grounded and not sliding)
  - `ArrowDown` → slide (hold: shrink hitbox; release: restore)
  - Mobile: tap upper half → jump; tap lower half → slide
- [ ] **F-14** Implement jump physics:
  - Set `body.velocity.y` on jump trigger
  - Switch animation: `JUMP` while velocity.y < 0, `FALL` while velocity.y > 0, `RUN` on ground
- [ ] **F-15** Implement obstacle spawner:
  - Timer fires every N ms (decreasing over time per speed table in Section 7.5)
  - Select obstacle type: filter by `min_score_to_spawn <= currentScore`, weighted random
  - Determine size: random int in `[asset.minUnits, asset.maxUnits]`
  - For horizontal: determine Y position from `position_type` (LOW = ground level, HIGH = 2 units from ground)
  - Create obstacle as a Phaser `Image` or `TileSprite` (for tiling)
  - Obstacles move left at scroll speed; destroy when off screen left
- [ ] **F-16** Implement collision detection:
  - `this.physics.add.overlap(character, obstacleGroup, onCollision)`
  - `onCollision` → stop scroll, play death visual, transition to `GameOverScene`
- [ ] **F-17** Implement score counter:
  - Increment score every 100ms by speed multiplier
  - Display in top-right corner as Phaser text
  - Update personal best display if new best
- [ ] **F-18** Implement `GameOverScene`:
  - Show final score and rank (from API response)
  - "Play Again" button: check `playsRemaining`; if 0 → show consent prompt overlay; else start new session and restart `GameScene`
  - "Share Score" button: pre-fill Web Share API or fallback tweet URL
- [ ] **F-19** Implement session API calls in game.js:
  - On game start: `POST /api/game/session/start` → store `sessionId`
  - On game over: `POST /api/game/session/{id}/end` with score → get rank back
  - Handle `canPlay: false` response → show gate overlay without starting game
- [ ] **F-20** Mobile responsive canvas:
  - `window.addEventListener('resize', () => game.scale.resize(...))` 
  - Recalculate `UNIT_SIZE` on resize
  - Recalculate all active physics body sizes on resize

### Phase 4 — Leaderboard Real-Time

- [ ] **F-21** Add STOMP client to `game.html` (CDN: `@stomp/stompjs`):
  - Connect to `/ws` on page load
  - Subscribe to `/topic/leaderboard`
  - On message: update leaderboard panel DOM
- [ ] **F-22** Add HTMX fallback on leaderboard panel:
  - `hx-get="/api/leaderboard" hx-trigger="every 10s" hx-swap="innerHTML"`
  - WebSocket handler: cancel HTMX polling when WS connection is live
- [ ] **F-23** Highlight current user's row in leaderboard panel (compare entry userId with `window.GAME_CONFIG.currentUserId`)
- [ ] **F-24** Build `leaderboard.html` full-page view (same data, larger table, no game canvas)

### Phase 5 — Admin UI

- [ ] **F-25** Build `admin/dashboard.html`: summary cards (total players, total leads, avg score) using HTMX `hx-get` on load
- [ ] **F-26** Build `admin/assets.html`: read-only display of current `game-config.json` contents (rendered by Thymeleaf from `GameConfigService`); image upload form per asset (`POST /admin/api/assets/upload`); note shown to developer to update JSON manually after uploading
- [ ] **F-27** Build `admin/leads.html`: table of consented users, CSV export button (`/admin/api/leads?format=csv`)

### Phase 6 — Polish & Mobile

- [ ] **F-28** Add mobile tap zones: transparent overlay divs (upper half / lower half) for jump/slide on touch devices
- [ ] **F-29** Add "install app" PWA prompt (optional v1 stretch goal — `manifest.json` served by Spring Boot, `beforeinstallprompt` event)
- [ ] **F-30** Add social share: "Share my score" button → Web Share API (`navigator.share`) with fallback to Twitter intent URL

---

## 14. Non-Functional Requirements

### 14.1 Security

| Area | Requirement |
|---|---|
| CSRF | Spring Security CSRF enabled; all state-changing forms include `_csrf` token |
| Session | Server-side sessions in Redis; 24h timeout; invalidated on logout |
| Magic link tokens | SHA-256 hashed in DB; raw token never stored; 15min expiry; single-use |
| SQL injection | JPA parameterized queries only; no string concatenation in queries |
| XSS | Thymeleaf auto-escapes all output; no `th:utext` on user input |
| Rate limiting | Max 5 magic link requests per email per 10 minutes |
| Admin protection | `/admin/**` requires `role = ADMIN`; verified in `SecurityConfig` |

### 14.2 GDPR Compliance

| Requirement | Implementation |
|---|---|
| Explicit opt-in | Checkbox unchecked by default |
| Audit trail | `marketing_consents` is append-only; never update or delete rows |
| Right to withdraw | `POST /consent/withdraw` sets `withdrawn_at`; downstream export respects this |
| Data minimization | Only email and display name collected |
| Lead export | Admin can export only users where latest consent row has `consented = true AND withdrawn_at IS NULL` |

### 14.3 Performance Targets

| Metric | Target |
|---|---|
| Page load (Thymeleaf render) | < 300ms server-side |
| Game canvas frame rate | 60fps on mid-range Android Chrome |
| Leaderboard WebSocket push latency | < 500ms from score submit to display |
| Score submission API | < 200ms p95 |

### 14.4 Dev Environment Setup

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Run Spring Boot (Flyway runs migrations on startup)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Access
# App:       http://localhost:8080
# Admin:     http://localhost:8080/admin (requires ADMIN role user)
```

**Creating an admin user (dev only):**
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
```

---

## 15. Execution Order

Work through phases in this order. Each phase should be fully working before starting the next.

```
Phase 1:  DB + Docker Compose + Flyway migrations                    [B-01 → B-06]
Phase 2:  Auth (magic link end-to-end, session working)             [B-07 → B-11] + [F-01 → F-04]
Phase 3:  Consent gate                                               [B-12 → B-15] + [F-05]
Phase 4:  game-config.json + GameConfigService + game page shell    [B-26 → B-29] + [F-06 → F-11]
Phase 5:  Game mechanics (character, obstacles, score, game over)   [F-12 → F-20]
Phase 6:  Score submission + session API wired into game            [B-16 → B-20]
Phase 7:  Leaderboard + WebSocket                                   [B-21 → B-25] + [F-21 → F-24]
Phase 8:  Admin panel (analytics, leads, asset uploader)            [B-28] + [F-25 → F-27]
Phase 9:  Security hardening + rate limiting + exception handling   [B-30 → B-34]
Phase 10: Polish — mobile controls, share button, PWA              [F-28 → F-30]
```

**Checkpoint after each phase:** verify it works end-to-end before proceeding. Do not skip ahead.

---

*Document version: 2.1 — Game config moved from DB table to JSON file*
*Tech stack locked. Open items: brand colors, final copy text, domain name.*
