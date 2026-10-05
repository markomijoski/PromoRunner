# cost.md — PromoRunner cloud cost analysis

Full monthly hosting cost analysis for **PromoRunner** (Spring Boot web app with email + password auth, browser game, and public leaderboard).

| | |
|--|--|
| **Prepared** | October 2026 |
| **Scope** | Deploy + run for **1 month** |
| **Currency** | USD primary; Hetzner list prices in EUR excl. VAT (≈ €1 = $1.10) |
| **Disclaimer** | Not a quote. Prices change; verify in vendor consoles. Unverified figures are marked **estimate**. |

---

## Executive verdict

**Cheapest reliable setup**

> **Hetzner CX23** (2 vCPU / 4 GB) + **PostgreSQL on the same VM** + **Cloudflare Free** (DNS/TLS/CDN) + **Resend free tier** (password-reset email) + domain  

| Load | Est. monthly total |
|------|--------------------|
| Small (100 MAU) | **~$7** |
| Medium (1,000 MAU) | **~$8–10** |
| Large (10,000 MAU) | **~$12–20** (bump to CX33) |

**One-line reason:** One cheap VPS covers app + DB; Redis is unused; game assets are ~2.5 MB; egress stays well inside VPS traffic quotas.

---

## 1. Project inspection — what you must pay for

### Stack relevant to cost

| Component | In project? | Production need |
|-----------|-------------|-----------------|
| Java 17 + Spring Boot (web, security, Thymeleaf) | Yes | Always-on compute |
| WebSocket leaderboard | Yes | Same process; sticky/single instance is fine at these sizes |
| PostgreSQL + Flyway | Yes | Required |
| Redis | Dependency present, **auto-config excluded** | **Do not buy** |
| password-reset email (SMTP) | Yes (Mailpit locally) | Real SMTP (Resend / SES / Mailgun) |
| Dockerfile | **No** | Optional; JAR + systemd works |
| CI/CD | **No** | Optional GitHub Actions |
| Object storage / CDN product | **No** | Cloudflare free is enough |
| Analytics / APM | **No** | Optional later |

### Measured / inferred sizes

| Asset | Size / note |
|-------|-------------|
| `static/` total | ~**2.5 MB** |
| `static/game/` (maps, cars, placeholders) | ~**2.5 MB** |
| DB schema | Users, scores, game_sessions, password-reset_tokens — KB-scale per user |
| Suggested JVM heap | 512 MB–1 GB |
| Suggested host RAM | **1–2 GB** minimum; **4 GB** comfortable with Postgres colocated |

### Network / traffic shape

- **Origin:** HTML pages, `/api/**`, `/ws/**`, `/game/**` static files.
- **Third-party (not billed to you):** Tailwind CDN, Alpine, HTMX, SockJS/STOMP, Google Fonts — still a compliance/privacy consideration.
- **Sessions:** 24h timeout → more concurrent HttpSessions than short-lived apps (usually fine on 4 GB).

---

## 2. Usage model (assumptions)

Assumptions are for a **promo campaign**, not measured production telemetry.

### Per monthly active user (MAU)

| Behavior | Assumption |
|----------|------------|
| Visits | 2 |
| Logins | 1 → **1 password-reset email** |
| Game plays | 3 sessions |
| Leaderboard | Page view + short WebSocket or 10s poll fallback |
| Origin bytes | **≈ 4 MB / MAU** (HTML + assets with browser cache) |
| DB growth | **≈ 5–20 KB / user** |

### Three scenarios

| Scenario | MAU | Emails / mo | Origin egress (est.) | Concurrent peak (est.) |
|----------|-----|-------------|----------------------|-------------------------|
| **Small** | 100 | ~100 | ~0.4 GB | &lt; 5 |
| **Medium** | 1,000 | ~1,000 | ~4 GB | ~10–20 |
| **Large** | 10,000 | ~10,000 | ~40 GB | ~50–100 |

---

## 3. Hosting options (prices looked up)

### A) Hetzner Cloud VPS — recommended baseline

| Role | Plan | Specs | List (excl. VAT) | USD (est.) |
|------|------|-------|------------------|------------|
| Small / Medium | **CX23** | 2 vCPU, 4 GB, 40 GB NVMe | ~€3.99–4.49 / mo | **~$4.40–4.95** |
| Large | **CX33** | 4 vCPU, 8 GB, 80 GB | ~€6.49–8.49 / mo | **~$7.15–9.35** |

| Add-on | Cost |
|--------|------|
| Postgres on same VM | **$0** |
| Included traffic | Typically **~20 TB** → $0 at our egress |
| Cloudflare Free | **$0** |
| Resend free tier | **$0** until limit |
| Snapshots / backups | **~$0–3** (estimate) |
| Domain (amortized) | **~$1 / mo** |

*Sources: Hetzner 2026 price-adjustment documentation and CX23/CX33 listings (third-party trackers). Confirm in Hetzner Console. VAT and optional IPv4 may apply.*

### B) Railway (PaaS)

| Resource | Public rate (2025–2026) |
|----------|-------------------------|
| Hobby plan | **$5 / mo** (includes **$5** usage credit) |
| RAM | **~$10 / GB-month** |
| CPU | **~$20 / vCPU-month** |
| Volume storage | **~$0.15 / GB-month** |
| Egress | **$0.05 / GB** |

**Assumed always-on footprint:** ~1 GB app + ~0.5 GB Postgres + fractional CPU  
→ raw usage often **~$20–25 / mo** before credit math settles → **~$17–28 / mo** all-in at small/medium after plan fee/credit, higher at large egress.

*Source: [railway.com/pricing](https://railway.com/pricing). Exact bill = allocated limits × time.*

### C) AWS Lightsail (+ SES)

| Item | Estimate |
|------|----------|
| 1 GB instance | **~$3.50–5 / mo** |
| 2 GB instance | **~$7–10 / mo** |
| Managed DB (optional — avoid) | **~$15 / mo** |
| SES | Negligible at ≤10k emails (~$0.10 / 1k after free allowance) |
| Egress | Region-dependent; **~$0.09 / GB** after free allowance (**estimate**) |

*Verify in AWS Console for your region.*

### Render (PaaS alternative, not tabulated in detail)

Hobby compute starts around **~$7 / mo** for a small web service; Postgres often **~$6+ / mo**. Typically **similar or slightly higher** than Railway for this stack. Use Railway numbers as the PaaS column unless you prefer Render’s UX.

---

## 4. Monthly cost tables (USD, rounded)

Line items: compute, database, storage, bandwidth, email, domain, backups, SSL/CDN.

### Small — 100 MAU

| Line item | Hetzner CX23 | Railway Hobby | AWS Lightsail 1 GB |
|-----------|--------------|---------------|--------------------|
| Compute | 4.70 | ~15–20 | 5.00 |
| Database | 0 (on VM) | (in usage) | 0 (on instance) |
| Storage | 0 | ~0.50 | 0 |
| Bandwidth | 0 | ~0.02 | ~0 |
| Email | 0 | 0 | 0 |
| Domain | 1.00 | 1.00 | 1.00 |
| Backups | 1.00 | 0 | 1.00 |
| SSL / CDN | 0 | 0 | 0 |
| **Total (est.)** | **~$7** | **~$17–22** | **~$7** |

### Medium — 1,000 MAU

| Line item | Hetzner CX23 | Railway Hobby | AWS Lightsail 1–2 GB |
|-----------|--------------|---------------|----------------------|
| Compute | 4.70 | ~18–25 | 7.00 |
| Database | 0 | (in usage) | 0 |
| Storage | 0 | ~0.50 | 0 |
| Bandwidth | 0 | ~0.20 | ~0.30 |
| Email | 0–1 | 0–1 | 0–1 |
| Domain | 1.00 | 1.00 | 1.00 |
| Backups | 1–2 | 0–1 | 1–2 |
| **Total (est.)** | **~$8–10** | **~$20–28** | **~$9–12** |

### Large — 10,000 MAU

| Line item | Hetzner CX33 | Railway | AWS Lightsail 2 GB |
|-----------|--------------|---------|--------------------|
| Compute | 8.50 | ~25–40 | 10.00 |
| Database | 0 (or +15 managed later) | (larger Postgres usage) | 0 (or +15 managed) |
| Storage | 0–2 | 1–2 | 0–2 |
| Bandwidth | 0 (≪ 20 TB) | **~$2** | **~$3–4** |
| Email | 0–3 | 0–3 | 0–2 |
| Domain | 1.00 | 1.00 | 1.00 |
| Backups | 2–4 | 1–2 | 2–4 |
| **Total (est.)** | **~$12–20** | **~$30–50** | **~$16–35** |

### Summary matrix

| Scenario | Hetzner | Railway (PaaS) | AWS Lightsail |
|----------|---------|----------------|---------------|
| Small (100 MAU) | **~$7** | **~$20** | **~$7** |
| Medium (1,000 MAU) | **~$9** | **~$24** | **~$10** |
| Large (10,000 MAU) | **~$15** | **~$40** | **~$25** |

---

## 5. Hidden and easy-to-forget costs

| Cost | Why it bites |
|------|----------------|
| **VAT** | Hetzner EUR often excl. VAT (e.g. +19% DE) |
| **IPv4 surcharge** | Some providers charge for public IPv4 |
| **Domain year one** | $10–15 once (+ WHOIS privacy) |
| **Email deliverability** | Cheap SMTP without SPF/DKIM = failed logins |
| **Backup snapshots** | Forgotten until restore day |
| **Your time** | VPS = you patch OS, Postgres, JVM |
| **PaaS oversizing** | Leaving 2 GB RAM allocated 24/7 on Railway adds up |
| **Third-party CDNs** | Not on your invoice; still compliance risk |
| **FX** | EUR/USD moves the Hetzner line |
| **CI minutes** | Rarely material until heavy pipelines |

---

## 6. Savings (ordered by dollars saved)

| # | Action | Typical before → after | Est. saved / mo |
|---|--------|------------------------|-----------------|
| 1 | **Colocate Postgres on the VPS** (skip managed DB) | +$15 DB → $0 | **~$15** |
| 2 | **Choose Hetzner over Railway** at this traffic | ~$24 → ~$9 | **~$15** |
| 3 | **Do not deploy Redis** | Accidental $5–15 → $0 | **~$5–15** |
| 4 | **Cloudflare Free** for TLS + cache `/game/**` | Paid CDN → $0 | **~$5–20** avoided |
| 5 | **Resend/SES free tier** for password-resets | Paid ESP min → $0 | **~$0–15** |
| 6 | **Build Tailwind for prod**; compress map images | Slightly less egress + privacy | **~$0–2** at large |
| 7 | **Skip paid APM** until needed | Datadog-class → free ping | **~$0–15+** |
| 8 | **Stay on GitHub Actions free** | Paid CI → $0 | **~$0–10** |

**After savings 1–5 (medium traffic on Hetzner):** about **~$8–12 / month** (+ VAT if applicable).

---

## 7. App-side waste (cost relevance)

| Finding | Cost impact today |
|---------|-------------------|
| ~2.5 MB game assets | Low — compress maps if you grow |
| Leaderboard WebSocket + 10s poll fallback | Low until high concurrency |
| Tailwind / fonts / Alpine from CDNs | $0 egress to you; privacy cost |
| 24h HTTP sessions | Memory pressure only at scale |
| No Dockerfile | Ops time, not hosting $ |

---

## 8. Recommended architecture (cheap + reliable)

```text
Users → Cloudflare (TLS, cache /game/**)
          → Hetzner CX23
               ├─ Spring Boot JAR (HTTPS reverse proxy optional via Caddy/nginx)
               └─ PostgreSQL (local)
          → Resend/SES (outbound password-resets)
Nightly → pg_dump / Hetzner snapshot
```

**Scale-up trigger:** sustained CPU &gt;70%, RAM pressure with Postgres, or concurrent players ≫100 → move to **CX33** or split managed Postgres later.

---

## 9. Confidence / sources

| Input | Confidence |
|-------|------------|
| Hetzner CX23 / CX33 2026 prices | Medium–high (confirm + VAT) |
| Railway Hobby + usage rates | High (public pricing) |
| Lightsail / SES | Medium (region-dependent estimates) |
| MAU → egress model | **Estimate** (not measured) |
| Email free-tier limits | Vendor-dependent — recheck Resend/SES quotas |

---

## 10. Bottom line

| Question | Answer |
|----------|--------|
| Monthly cost tables | See §4 |
| Hidden costs | See §5 |
| How to cut spend | See §6 |
| Cheapest reliable total | **~$7–12 / mo** (Hetzner CX23 combo) for typical campaign sizes |
| Why | Single VPS + colocated Postgres + free Cloudflare/email; PaaS costs 2–3× more for little gain at this scale |

This file is the full analysis.
