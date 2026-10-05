# PromoRunner — Campaign Sales Plan

**Product:** A branded browser endless-runner with login, live leaderboard, and optional marketing consent.  
**How you sell it:** One brand per deployment. You re-skin / adapt the template for each customer.  
**Honesty rule:** Numbers below are **estimates** based on typical promo funnels and this product’s real features. They are **not** measured results from live campaigns.

---

## 1. The pitch

Turn your next giveaway into a competition people actually want to play. We put your brand inside a short mobile-friendly racing game with a live leaderboard: players create an account, compete for prizes, and — if they choose — join your marketing list for unlimited plays. You get engaged fans, consented emails, and weeks of brand exposure — not just another “leave your email and hope” form.

---

## 2. The problem it solves

| Typical brand promo | What goes wrong |
|---------------------|-----------------|
| Social “comment to win” | Low trust, bots, weak data, zero brand experience |
| Static landing + form | High bounce; people won’t give email for nothing |
| Paid ads alone | You pay per click; most visitors never engage |
| Offline raffle | Hard to measure, hard to scale, weak digital follow-up |

**What brands want:** attention + proved engagement + usable contacts + a fair reason to come back.  
**What this product does today:** wraps that into a playable contest loop (play → score → climb the board → play again).

---

## 3. How it works

### For the brand
1. **Brief** — logo, colors, prize, campaign length, target audience.  
2. **Build** — we re-skin the game template and deploy your campaign URL.  
3. **Launch** — you drive traffic (ads, QR, email, influencers).  
4. **Collect** — watch the leaderboard; export consented leads; announce winners from the top of the board.

### For the player
1. Land on the branded page → tap **Play**.  
2. Register (email + username + password) → start playing in about a minute.  
3. Get **3 free plays**; opt into marketing for **unlimited** plays.  
4. Compete on the **live leaderboard**; come back to beat their best score.

---

## 4. What the brand gains (estimated)

### What exists in the product today
- Branded landing + game shell (name, colors, campaign line via config)
- Email + username capture at registration
- Optional marketing consent → exportable lead list (CSV in admin)
- Live leaderboard (WebSocket + fallback poll)
- Basic admin stats: players, leads, average best score, total plays
- Privacy / terms drafts, account export & delete

### Available after customization (not built as self-serve today)
- Custom car / maps / obstacles / logo in-game
- Prize copy, “top X wins” rules on the site
- Campaign start/end dates and auto-close
- Share / invite links, richer analytics, stronger anti-cheat

### Campaign size estimates

**Assumptions (state these openly in every pitch):**
- Traffic is driven by the brand (ads, organic, QR, email). The game does **not** acquire players by itself.
- **~40–60%** of visitors who open the site register *(wide range; depends on prize clarity and ad quality)*.
- **~2–5 plays** per registered player on average *(3 free plays; more if they consent)*.
- **Average run length ~30–90 seconds** *(observed short competitive runs; skilled players go longer)*.
- **Consent rate ~15–40%** of players *(higher when unlimited plays + prize are clearly tied to opt-in)*.
- **Brand impressions ≈ visits × pages seen + plays** *(every play is branded screen time)*.

| Campaign size | Est. unique players | Est. total plays | Est. engaged minutes | Est. marketing leads (consented) | Est. brand impressions* |
|---------------|--------------------:|-----------------:|---------------------:|---------------------------------:|------------------------:|
| **Small** (local / one city) | 150–400 | 400–1,500 | 200–1,500 | 40–120 | 1,000–4,000 |
| **Medium** (regional + paid social) | 800–2,500 | 2,500–10,000 | 1,500–10,000 | 200–800 | 6,000–25,000 |
| **Large** (national push + partners) | 5,000–15,000 | 15,000–60,000 | 10,000–60,000 | 1,000–5,000 | 40,000–150,000 |

\*Impression = a meaningful branded view (landing, game session, or leaderboard). Not an ad-network “impression.”

### Cost efficiency vs typical alternatives

**Industry context (public ranges, not your results):** Meta/Google lead campaigns often land roughly **€5–25 per email lead** in many EU markets, depending on niche and creative. Cost-per-click often **€0.20–1.50**. Verify for your category.

| Metric | PromoRunner campaign (estimate) | Typical paid social form ad (estimate) |
|--------|----------------------------------|----------------------------------------|
| What you buy | Setup + hosting + traffic you already plan | Clicks / form fills |
| Est. cost per **consented** lead* | **€2–12** at medium scale if traffic CPL is controlled | **€5–25** |
| Est. cost per **engaged minute*** | Often **lower** than video ads for similar spend, because playtime is voluntary | Hard to compare; most clicks bounce in &lt;10s |

\*Formula example: `(campaign fee + media spend) ÷ consented leads`. Media spend dominates; the game fee is usually a fraction of ad budget.

**Hosting reality (from project cost analysis):** roughly **~$7–20 / month** for small–large traffic on a simple VPS setup. Negligible vs media and prizes.

---

## 5. What the player gains

- A **real skill game** (dodge, collect coins, climb ranks) — not a fake spinner  
- **Public username** on a live board (email stays private)  
- **Short sessions** — easy to retry “one more run”  
- A clear prize story when you add it: *top scores win*  
- Optional: unlimited plays in exchange for marketing messages they can unsubscribe from  

**Why they return:** beat personal best, climb rank, win the giveaway.

---

## 6. Giveaway structure examples

*The software ranks by best score. Prize rules are a campaign design choice you publish in terms + on the landing page (UI prize module = customization).*

| Structure | Best for | Suggested prize budget | Campaign length |
|-----------|----------|------------------------|-----------------|
| **Top 3** | Premium prizes, prestige | €300–1,500 total (e.g. 1st big, 2nd/3rd smaller) | 2–3 weeks |
| **Top 10** | Broad motivation | €500–3,000 (mix of products + vouchers) | 3–4 weeks |
| **Top 50** | Mass engagement / retail | €1,000–5,000 (many small rewards + 1–3 hero prizes) | 4–6 weeks |

**Prize ideas by budget**
- **Low (€200–500):** vouchers, merch, memberships, service discounts  
- **Mid (€500–2,000):** flagship product, weekend package, training course, gear bundle  
- **High (€2,000+):** trip, flagship device, annual membership + partner gifts  

**Fairness tips for the brand**
- Publish rules before launch (eligibility, one account per person, prize claim process).  
- Manual winner verification (ID / receipt) for valuable prizes.  
- Note: scores are submitted by the browser today — add anti-cheat hardening before high-value national prizes *(see objections)*.

---

## 7. How to promote the campaign

### Where players come from
- Brand Instagram / Facebook / TikTok (organic + paid)  
- Email / SMS to existing customers  
- **In-store / event QR** on posters, receipts, stands  
- Influencers / micro-creators in your niche  
- Newsletter partners, association members, B2B partner cross-posts  
- Website banner / homepage takeover  

### Simple 2–4 week launch timeline

| Week | Brand actions | You (builder) |
|------|---------------|---------------|
| **−2** | Confirm prizes, legal copy, creatives | Re-skin, staging URL, test devices |
| **−1** | Soft tease (“game drops Monday”) | Final QA, analytics/admin access |
| **1** | Launch posts + paid boost + QR live | Monitor uptime, leaderboard, consent rate |
| **2–3** | Reminder creatives (“you’re #47 — climb!”) | Optional mid-campaign balance tweaks |
| **End** | Announce winners publicly | Export leads, handoff report, archive |

---

## 8. Pricing packages

Prices are **suggested selling prices** for a custom single-brand campaign (your time + hosting). Adjust to your market. Currency: **EUR**.

| Package | Price (suggest) | Includes | Setup time | Rough customer ROI framing |
|---------|----------------:|----------|------------|----------------------------|
| **Starter** | **€900–1,500** | Config re-skin (name, colors, consent text), deploy, 1 revision, 2–4 week hosting, admin + CSV leads | **2–4 days** | If they collect **~80–150** consented leads, effective platform cost ≈ **€6–19 / lead** before media |
| **Growth** | **€2,500–4,000** | Starter + custom in-game art (car/maps), prize/rules page copy, launch checklist, basic performance report | **1–2 weeks** | At **~300–700** leads, platform ≈ **€4–13 / lead**; strong if prize + ads are funded |
| **Flagship** | **€5,000–8,000** | Growth + deeper game theming, share/invite add-on, campaign dates, light anti-cheat, on-site QR kit support, weekly check-ins | **2–4 weeks** | Aimed at national pushes; ROI driven by media + prize, not hosting |

**Your cost floor (to protect margin):** hosting ~€10–20/mo + your build hours. Starter is mostly configuration; Growth/Flagship are mostly design + integration labor.

**Optional add-ons:** extra campaign month hosting (€50–150), influencer landing variant, post-campaign CRM import help, legal review coordination (lawyer not included).

---

## 9. Objection handling

| Doubt | Short answer |
|-------|--------------|
| “Will people really play?” | Yes if the prize is clear and the first run is under a minute. The loop is: short game → live rank → try again. Soft-launch to your email list first and show early leaderboard activity. |
| “Can’t someone cheat the score?” | Today scores are client-submitted. For small local prizes, social accountability is often enough. For valuable prizes we add verification / anti-cheat in Flagship — and you verify winners manually before payout. |
| “Is the email list legally usable?” | Marketing emails only for players who **opt in**. Export is limited to consented users. Privacy/terms are drafted from real app behavior — have counsel review before launch. |
| “Why not just run ads to a form?” | A form buys a contact. This buys **playtime + competition + repeat visits**. Same ad budget often yields warmer leads because they already experienced the brand. |
| “How long until we go live?” | Starter: a few days after assets/copy. Custom art: 1–2 weeks. We need logo, colors, prize rules, and a domain/subdomain decision. |

---

## 10. Call to action

**Book a 20-minute campaign fit call.** Bring: target audience, prize idea, and launch window.  

We’ll reply with:
1. Recommended package (Starter / Growth / Flagship)  
2. A one-page estimate (timeline + price)  
3. A staging preview plan using your logo and colors  

**Next step for you:** send logo + brand colors + prize concept → get a playable demo URL for stakeholder approval.

---

## Appendix A — What ships today vs customization

| Capability | Today | After customization |
|------------|:----:|:-------------------:|
| Email / username / password auth | ✓ | |
| 3 free plays → marketing consent for unlimited | ✓ | Configurable limits |
| Live leaderboard | ✓ | Prize callouts, top-X badges |
| Admin dashboard + leads CSV | ✓ | Richer analytics |
| Brand name / colors / consent text via config | ✓ | |
| In-game art / maps / car | AMSM sample | Per brand |
| Share score / invite friends | — | ✓ |
| Campaign start/end automation | — | ✓ |
| Strong server-side anti-cheat | — | ✓ |
| Multi-tenant self-serve SaaS | — | Not the model (per-brand deploy) |

---

## Appendix B — Re-skin effort (internal)

| Work | Reusable | Per-customer rewrite | Effort |
|------|----------|----------------------|--------|
| Auth, sessions, scores, leaderboard, admin, consent | ✓ | — | 0 (reuse) |
| `brand.*` config, privacy/terms names | mostly | copy pass | **2–6 h** |
| UI chrome / language tweaks | mostly | light | **4–12 h** |
| Game sprites, maps, car, audio feel | structure | art + `game.js` paths | **1–5 days** |
| Prize/rules page, share, dates, anti-cheat | — | new work | **2–10 days** depending on package |
| Deploy + DNS + email SMTP | checklist | each env | **2–8 h** |

**Total:** Starter **~1–3 days**; Growth **~1–2 weeks**; Flagship **~2–4 weeks**.
