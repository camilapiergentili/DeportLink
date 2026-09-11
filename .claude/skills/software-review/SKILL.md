---
name: software-review
description: Perform a comprehensive, evidence-based, read-only software review of a codebase in two phases — an audit (architecture, SOLID, Clean Code, DRY/code smells, coupling & cohesion, testing, security, concurrency, database & indexes, data integrity, performance, production readiness) followed by an independent validation pass that re-derives, re-traces, and re-judges every finding before it is reported. Analysis only: finds and explains issues with evidence and severity, never edits code, never creates migrations, never touches a database. Works on any language, framework, ORM, or database — not tied to any specific stack.
---

# Software Review

A general-purpose, stack-agnostic methodology for auditing a codebase across architecture, code quality,
testing, security, concurrency, database design, performance, and production readiness — and then
independently re-checking every conclusion before it is handed over. This Skill is an **analysis tool,
not an implementation tool**, and its output is only trustworthy to the extent that its own findings have
survived a genuinely skeptical second look.

## Boundaries — read this first

This Skill produces a report. It does not change the project.

**Never, under this Skill:**
- Edit, refactor, or "fix" application code.
- Create, edit, or apply database migrations.
- Run any command that writes to a database, or run `EXPLAIN`/`EXPLAIN ANALYZE` (or equivalent) against a
  live database — only *recommend* that the user run it.
- Install dependencies, run codemods, or auto-format code as part of the review.
- Silently skip a review area because it looks fine — say explicitly what was checked and that it passed.
- Silently skip the validation phase because the audit "looks solid" — the validation phase is not
  optional and is not a separate follow-up request; it is part of every run of this Skill.

If the person invoking this asks you to also fix what you find, treat that as a separate, explicit
follow-up request after the review is delivered — do not fold it into the same pass.

## The method, in one sentence

**Investigate, then hypothesize, then validate, then present — and only after that, doubt yourself again.**
A pattern noticed while reading code is a *hypothesis*, not a finding, until it has been traced to concrete
evidence and a real, reachable execution path. Once the full set of hypotheses has been promoted to
findings this way, an independent second pass re-opens every one of them assuming nothing from the first
pass is correct, actively hunts for evidence that contradicts them, and looks for what the first pass
missed. Both phases are mandatory parts of a single review; neither is a checklist to tick mechanically.

## Before you start — orient yourself

Do not apply a generic checklist blind. Spend the first pass understanding what you're actually looking at:

1. **Identify the stack**: language(s), framework(s), ORM/query layer (or raw SQL), migration tool
   (Flyway, Liquibase, Prisma Migrate, Alembic, Django migrations, Rails migrations, EF Core, hand-rolled
   SQL, or none — schema-less/NoSQL), database engine(s), test frameworks, build/deploy tooling.
2. **Identify project stage and context**: greenfield prototype, early-stage product, mature production
   system, internal tool, library. This calibrates everything — a missing index on a table with a few
   hundred rows is not the same finding as one on a table taking production traffic at scale. State your
   read of the stage explicitly and use it to temper severity, not to suppress the finding.
3. **Read what the project already says about itself**: README, ARCHITECTURE.md, ADRs, prior audit/review
   documents, CONTRIBUTING.md, inline module-level docs. Two things to get from this:
   - Documented intentional decisions (e.g. "we chose eventual consistency here because...") — don't
     re-litigate a documented, deliberate trade-off as if it were an oversight; you can still flag it if
     the code no longer matches what's documented, or if the trade-off has a gap it doesn't cover.
   - Claims to verify: existing docs often assert things ("all endpoints require auth", "239 tests, 0
     failures", "indexes cover the hot queries") that may or may not still be true. Check them against the
     actual code rather than repeating them.
4. **Check for a prior review of this same repo** (e.g. earlier reports from this Skill, or anything
   matching `docs/*audit*`, `docs/*review*`). If one exists, match its structure/severity conventions and
   avoid re-reporting findings it already confirmed and that still hold — note briefly that they were
   re-verified instead of writing them up again from scratch. If a prior review's own validation phase is
   present, treat its still-open items (Requires More Evidence, Rejected, anything downgraded) as a
   starting point for this run's hypotheses, not as settled fact.
5. **Scope**: unless told otherwise, review the whole project across all fifteen areas below. If asked to
   focus on a subset (a module, a service, "just the database", "just concurrency"), scope accordingly but
   keep the same evidence standard, both phases, and the same report skeleton — sections with nothing to
   say state that explicitly rather than being dropped.

## Core principles — non-negotiable

- **Cite evidence, always.** File path and line number (or a stable identifier if line numbers aren't
  meaningful, e.g. a migration filename) for every claim. If you can't point to it, don't assert it.
- **Do not invent problems.** A finding needs a concrete mechanism — specific input/state that leads to a
  specific wrong outcome — not "this could theoretically be an issue somewhere."
- **A hypothesis is not a finding.** Noticing that something *looks* wrong is the start of the work, not
  the end of it. Before writing anything up, trace it: who actually calls this code, from where, is that
  caller itself reachable, under what real conditions does the bad state occur. Only what survives that
  trace gets written as a finding; everything else is either discarded, or — if the mechanism is real but a
  fact you can't check (data volume, production topology, runtime load) blocks confirming impact — kept as
  a finding explicitly marked *Potential risk* with the missing fact named.
- **Separate confidence levels.** For every finding, say whether it is:
  - *Confirmed* — verified directly by reading the code/schema/config, including tracing it to a real,
    reachable caller.
  - *Potential risk* — the mechanism is real but depends on something you couldn't verify (data volume,
    runtime behavior, infrastructure topology, concurrent load, a future code path that doesn't exist yet).
  - *Optional improvement* — not a defect, a legitimate quality/consistency upgrade.
- **Distinguish real defects from stylistic preference.** "This isn't how I'd write it" is not a finding.
  A finding needs an actual consequence: a bug, a race, a maintenance trap, a real cost.
- **Prioritize by impact, not by purity.** A SOLID violation with zero practical consequence ranks below a
  Low finding with a real (if minor) one. Say why something matters, not just that it deviates from a
  principle.
- **Avoid over-engineering in your own recommendations.** Don't recommend a new abstraction layer, a
  design pattern, or a framework migration unless the current code demonstrably causes pain that the
  abstraction would remove. "More layers" is not automatically an improvement.
- **Explain trade-offs, not just verdicts.** Every recommendation should make clear what you gain and what
  you give up — including cases where the right call might be "leave it as is."
- **State explicitly when you can't verify something.** Data volume, production behavior under load,
  infrastructure/network topology, whether a test suite currently passes, whether a migration has already
  run against production — if reading the repo can't answer it, say so instead of guessing or asserting.
- **Do not claim a performance improvement without evidence.** This applies especially to indexes (see
  below) — "this index would help" needs a query that would actually use it and some basis (even
  qualitative) for believing that query's cost matters. When you lack that basis, say the improvement is
  unverified and suggest how the user could verify it (`EXPLAIN`/`EXPLAIN ANALYZE` or the engine's
  equivalent, query logging, profiling) — do not run it yourself.
- **Do not exaggerate severity, and do not flatten it either.** A finding's severity follows from its
  traced impact, not from how interesting or how embarrassing it sounds.

## Phase 1 — Audit

Work area by area (see the fifteen below). For each one, run the same inner loop rather than free-writing
observations directly into the report:

1. **Observe.** Read the relevant code/config/schema and note anything that looks like it could be a
   problem, a strength, or worth stating as explicitly checked.
2. **Hypothesize.** State to yourself precisely what the suspected defect is and the mechanism by which it
   would cause harm — concrete inputs/state, concrete wrong outcome.
3. **Investigate across sources.** Findings rarely live in one file. Relate what the code does to the
   other artifacts that constrain or reveal its real behavior wherever relevant:
   - application code ↔ entities/domain models ↔ repositories/DAOs/query builders ↔ raw queries
   - migrations ↔ the schema they actually produce ↔ what an ORM's models/entities *claim* the schema is
   - configuration (per environment) ↔ what actually runs in each environment
   - tests ↔ what they actually exercise (a green suite against a test double proves less than the same
     assertion against something that can exhibit the real behavior — a real DB, a real lock manager)
   - controllers/endpoints/CLI entry points ↔ the use case or handler they invoke ↔ everything that use
     case touches
4. **Validate the hypothesis before writing it up.** At minimum:
   - Trace every method/function/query you're about to cite to a **real caller in reachable application
     code**. A query method, use case, or code path with no live caller is dead code — report it as dead
     code, don't build an index/performance/security finding on top of something nothing ever invokes.
   - If the hypothesis depends on "two concurrent callers do X", identify concretely *what code path* would
     produce two such callers, and confirm that path can actually supply the specific colliding state the
     race requires (see Concurrency deep-dive below — this is where hypotheses most often turn out to be
     unreachable in practice even when the missing safeguard is real).
   - If the hypothesis depends on data volume, production topology, or runtime behavior you cannot observe
     from the repo, keep it, but downgrade confidence to *Potential risk* and name exactly what's missing.
5. **Promote or discard.** Only a hypothesis that survives step 4 becomes a finding written with the full
   template (see below). A hypothesis that turns out to be unreachable, already mitigated elsewhere, or
   simply wrong gets dropped silently — it was never a finding, so it does not need to be reported as a
   withdrawn one.

### The fifteen review areas

Apply each area where it's relevant to the project; skip only what genuinely doesn't apply (e.g. no
concurrency section of substance for a single-user offline script), and say so briefly rather than
omitting the heading.

1. **Architecture** — layering and dependency direction, module/service boundaries, where business rules
   actually live vs. where the framework encourages them to leak. Judge the *chosen* architecture against
   the project's actual context — see the fundamental rule on architecture below before writing anything
   here.
2. **SOLID** — concrete violations with a real consequence (hard to test, hard to extend without touching
   unrelated code, a change in one place silently breaks another). Not a principle-by-principle box-tick.
3. **Clean Code** — naming that misleads or requires tribal knowledge, functions/classes doing too much,
   dead code, comments that restate the code instead of explaining why, magic numbers/strings that encode
   a business rule invisibly.
4. **DRY** — genuine duplication that will drift (the same rule expressed twice, so a change to one and
   not the other silently breaks something) vs. coincidental similarity that happens to look alike today
   but represents different concerns that may evolve independently. Only the former is a finding.
5. **Code smells** — long methods, god classes/modules, feature envy, primitive obsession, shotgun
   surgery, inappropriate intimacy between modules, speculative generality.
6. **Spaghetti code** — tangled control flow, deep nesting, implicit/hidden data flow, mixed levels of
   abstraction in one function, unclear ownership of state.
7. **Coupling & cohesion** — leaky abstractions, circular dependencies, modules that must change together
   for unrelated reasons, a "port" that only ever has one real implementation and a thin pass-through.
8. **Testing** — shape of the test pyramid vs. what's actually there, tests that assert against a mock
   instead of behavior, missing coverage for the paths that matter most (critical business rules,
   concurrency-sensitive code, security boundaries), flaky or environment-dependent tests, whether test
   configuration (e.g. an in-memory DB) actually exercises the same guarantees as production. If build
   artifacts already present in the repo (test reports, coverage reports) give real evidence instead of
   "not verifiable," use them — but say plainly that they're from a past run, not one you executed.
9. **Security** — authn/authz correctness (including IDOR-shaped bugs, not just "is there a login"),
   injection surfaces, secrets in code/config/tracked files, input validation and output encoding, rate
   limiting and its assumptions (e.g. what IP source it trusts), CORS configuration, session/token
   handling and revocation, dependency versions if a lockfile is inspectable.
10. **Concurrency** — see the dedicated methodology below.
11. **Database** — schema design (normalization vs. deliberate denormalization), primary/foreign keys,
    cascade behavior on delete/update, nullability choices that encode (or fail to encode) business rules,
    which tool is the actual source of truth for the live schema (see below), migration discipline
    (ordering, reversibility, backfill safety, whether a migration could fail against real data, and
    whether a migration's own comments match what it actually executes — see below).
12. **Indexes** — see the dedicated methodology below. Never analyzed in isolation from the queries that
    would use them.
13. **Data integrity** — for each important invariant ("no duplicates," "no orphaned rows," "unique
    membership," "no double-booking"-shaped rules, but also less obvious ones like "a national ID / tax ID
    / email is unique"), determine whether it's enforced by the database (constraint), by the application
    (validation/check-then-act), or both — and whether both is warranted. Check this for *every* column
    that carries real-world identity semantics, not only the one or two invariants that are already
    suspicious — a table can have a correct constraint on one such column and be missing it on a sibling
    column with the exact same semantics.
14. **Performance** — N+1 query patterns, unbounded/unpaginated queries that will grow with the data, join
    fetch of a to-many collection combined with pagination (an anti-pattern in most ORMs — it forces
    in-memory pagination even when it looks paginated at the API level), expensive computed queries (e.g.
    per-row trigonometry/geo distance without a bounding-box or spatial index), missing caching where
    repeated identical reads are expensive, and the inverse — caching that risks serving stale data for
    something that must be fresh.
15. **Production readiness** — configuration and secrets management (and drift between environments — is
    the equivalent of "dev convenience settings" ever able to leak into a production path?), logging and
    observability, error handling and what a client actually sees when something fails, health checks,
    startup-time validation of required config, graceful handling of dependency failures.

## Database & index deep-dive — mandatory correlation method

Never call an index missing, redundant, or duplicated without tracing it to the queries that would use it.
Follow this sequence:

1. **Find the real source of truth for the schema.** Migration tool (and its history/order), or an ORM in
   auto-sync mode (e.g. `ddl-auto=update`/`synchronize: true`/`AUTO_INCREMENT` frameworks that generate
   schema from models). Check whether this differs by environment (a common and dangerous pattern: schema
   auto-sync enabled in dev, migrations authoritative in prod) — schema drift between environments, or
   between what a model/entity declares and what a migration actually created, is itself a finding.
2. **Enumerate tables, primary keys, foreign keys, unique constraints, and indexes** (simple and compound,
   with exact column order) from the migration/schema source of truth — don't trust ORM model annotations
   as ground truth if migrations govern the live schema; treat a mismatch between the two as a finding in
   itself (documentation drift at best, real schema uncertainty at worst).
3. **Read every migration's own comments against what it actually executes, in order.** A migration that
   documents an intention ("the next migration will add X") is a promise, not a fact — check whether a
   later migration actually kept it. A gap here (a documented plan that was never carried out) is itself
   strong, cheap evidence for a data-integrity or schema-debt finding elsewhere in the review — don't
   treat it as a footnote.
4. **Enumerate every query the application actually issues**: repository/DAO methods, query-builder calls,
   raw SQL, ORM-generated queries from derived method names (a method name alone can imply a WHERE clause
   the query builder actually applies — read it as carefully as hand-written SQL).
5. **Trace each query method to a live caller**, all the way to a controller/endpoint/entry point when
   possible. A query method that exists but is never invoked from reachable application code is dead code
   — report it as such, not as a subject for index analysis. Don't let unreferenced code inflate the
   apparent set of "unsupported query patterns" — and conversely, don't let a shared, generic-sounding
   method name (e.g. a "search by name" style query reused across similar-but-not-identical query shapes)
   hide a live predicate you haven't accounted for. Enumerate what each live query filters on precisely,
   including columns pulled in only through a `LIKE`/text-search predicate alongside equality columns —
   the equality columns still matter for indexing even when the text predicate itself isn't sargable.
6. **For each index, identify which live query pattern(s) it serves**, using the leftmost-prefix rule
   (equality columns before range columns, in the order the index defines them). An index with no
   traceable live consumer is a candidate for removal — but check for consumers outside the application
   layer first (reporting tools, BI queries, other services against the same database) before recommending
   dropping it, and say plainly when you can't rule that out.
7. **For each live query pattern with a WHERE/JOIN/ORDER BY shape, check for index coverage.** Flag a gap
   only when you've confirmed the query is actually reachable — and note column order requirements
   explicitly (an index on `(a, b)` does not serve a query that filters on `b` alone, and does not serve a
   query that filters on `b` and `c` but never `a`, even if `a` is one of the table's columns).
8. **Detect duplicate/overlapping indexes** — one index whose column list is a strict prefix of another,
   or two indexes serving the same access pattern.
9. **Foreign keys and implicit indexes are engine-specific — say so.** Some engines (e.g. MySQL/InnoDB)
   auto-create a supporting index for a foreign key column if one doesn't already cover it; others (e.g.
   PostgreSQL) do not. Don't assume; state which engine is in play and what that implies before calling an
   unindexed FK column a gap or a non-issue.
10. **Check whether important invariants have a matching UNIQUE constraint** (or equivalent), separately
    from whether the application also checks them — see the concurrency section for why this matters, and
    check this for *every* column with real-world identity semantics on a table, not only the one that
    already looks suspicious (see Data integrity above).
11. **Account for volume and selectivity qualitatively** wherever you can infer it (a small reference/lookup
    table vs. a high-write transactional table), and say explicitly when you can't infer it and that
    materially limits confidence in the recommendation.
12. **Never assert a performance win without evidence.** Recommend the user validate with
    `EXPLAIN`/`EXPLAIN ANALYZE` (or the engine's equivalent) before treating an index recommendation as
    settled — but do not run it yourself.

## Concurrency deep-dive

- Look specifically for **check-then-act** sequences: read some state, decide based on it, then write —
  with no lock and no constraint preventing two callers from both passing the check before either writes.
- For every important business invariant, determine whether it is enforced **only in application
  memory/logic**, **only by the database**, or **both**. An invariant enforced only in application memory
  under concurrent access is a real risk *in principle* — but before reporting a specific race scenario as
  a finding, **trace the actual write path(s) that reach the vulnerable check.** Concretely:
  - Find every caller of the check-then-act code, not just the first one you find.
  - For each caller, determine what value actually reaches the check on each concurrent invocation. A
    check-then-act race described as "two concurrent callers pass the same identifier" is only a real
    finding if some real code path can actually produce two concurrent callers with that same identifier.
    If, for example, the only path that reaches the check always generates a fresh, never-reused identifier
    first (e.g. it always creates a new row before referencing its own new ID), the specific duplicate-row
    scenario may not be reachable today even though the missing lock/constraint is real. In that case,
    report the missing safeguard as a **preventive/defense-in-depth finding** ("nothing stops this the
    moment a new caller reuses an existing identifier here") rather than as an actively exploitable race —
    the distinction changes both the severity and the priority.
  - Do not stop at the first caller found and assume it's the only one — the whole point of this trace is
    that the answer is sometimes "only one caller exists, and it happens to make the race unreachable,"
    which you cannot know without enumerating callers.
- If the same codebase already has a correct pattern for this exact class of problem elsewhere (e.g. a
  pessimistic lock plus a backing unique constraint used for one entity but not for a structurally similar
  one), point to it explicitly as the template — that's stronger and more actionable than an abstract
  recommendation.
- Note whether concurrent-access scenarios have any test coverage at all, and whether that coverage runs
  against something that could actually exhibit the race (a real database/lock manager) or only against a
  test double that can't.

## Design principles — evidence over checklist

Evaluate SOLID, Clean Code, DRY, KISS, cohesion, coupling, maintainability, and testability as lenses for
finding real problems, not as a form to fill in principle by principle. A class that "violates" a principle
with zero practical consequence is not a finding. Every claimed violation needs:
- the concrete evidence (file/line),
- the mechanism by which it actually hurts (harder to test, a change silently breaks something unrelated,
  a bug that already happened or plausibly will), and
- an honest account of the trade-off in whatever you recommend instead — including when the right answer
  is "this isn't worth the abstraction it would take to fix."

## The fundamental rule on architecture

Do not assume Hexagonal Architecture, Clean Architecture, MVC, Layered, microservices, Modular Monolith, or
any other named architecture is the correct choice by default, and do not treat deviation from one of these
as inherently wrong. Evaluate the architecture against this project's actual context — domain complexity,
requirements, expected scale, integrations, constraints, expected evolution, and the complexity the
architecture itself introduces. A layered architecture that looks "less pure" than a textbook pattern but
fits the team and the problem is not a finding. An elaborate architecture that the team can't maintain, or
that solves a scaling problem the project doesn't have, is worth flagging just as much as messy code is.
Differentiate real problems from architectural taste.

## Phase 2 — Independent Validation

Once Phase 1 has produced a full set of findings, re-open the review as if someone else had written it.
**Do not assume the first pass's conclusions are correct.** This phase exists precisely to catch what
confirmation bias in Phase 1 would miss — a hypothesis that felt right while writing it up is exactly the
kind of thing a second, skeptical look is for.

For every finding produced in Phase 1:

1. **Re-trace the real execution flow from scratch**, independent of how Phase 1 described it — read the
   caller(s) again, don't just re-read Phase 1's summary of them.
2. **Verify who actually calls the code involved.** Enumerate all callers, not just the one that motivated
   the original hypothesis. If a security- or concurrency-relevant method has exactly one caller, that
   fact can change the entire risk assessment (see the Concurrency deep-dive above).
3. **Confirm the described scenario is actually reachable** with the code and configuration that exist
   today — not with a plausible-sounding future extension of the code. A real missing safeguard whose
   concrete failure scenario isn't reachable today is still worth reporting, but as a different kind of
   finding (see below).
4. **Classify each finding into exactly one of:**
   - **Confirmed problem** — the mechanism, the evidence, and the reachability all hold up.
   - **Potential problem** — the mechanism is real but reachability or impact depends on something
     unverifiable from the repo (data volume, infra topology, a future code path).
   - **Preventive improvement** — the safeguard is genuinely missing, but no current, reachable code path
     can trigger the failure yet; fixing it is still worthwhile defense-in-depth, just not urgent for the
     reason originally given.
   - **Non-demonstrable claim** — something Phase 1 asserted that this pass could not verify one way or the
     other from the repository; say plainly what would be needed to confirm or refute it.
5. **Re-judge the severity** in light of 1–4 — a finding whose scenario turned out to be unreachable today
   almost always needs its severity or urgency revised downward from how Phase 1 framed it; conversely, a
   finding Phase 1 treated as narrow but which turns out to have a second, easier-to-trigger path (see
   step 7) may need to go up.
6. **Actively look for evidence that contradicts the finding**, not just evidence that supports it — read
   the code that would have to be true for the finding to be wrong, on purpose, before concluding it's
   right.
7. **Look for what Phase 1 missed.** Apply the same checks that produced the confirmed findings (the
   UNIQUE-constraint sweep, the caller trace, the migration-comment-vs-execution check, the
   dead-code-via-grep check) to *adjacent* code that Phase 1 didn't happen to look at — a table with the
   same kind of identity column as one already flagged, a sibling query built the same way as one already
   analyzed. The most valuable output of this phase is often a finding Phase 1 never wrote down at all.

If every single finding from Phase 1 comes back "Confirmed, severity unchanged, nothing missed," treat that
as a signal to look harder before concluding the validation was thorough — a genuinely independent second
pass on a nontrivial codebase usually adjusts, downgrades, or adds at least one thing.

## Severity

Use exactly these four levels:

- 🔴 **Critical** — actively broken, exploitable now, or causing/will imminently cause data loss or
  corruption.
- 🟠 **High** — a serious defect or risk with a plausible real-world trigger, not yet catastrophic but
  should be prioritized.
- 🟡 **Medium** — a real problem with limited or conditional impact (needs specific conditions, scale, or
  timing to bite).
- 🔵 **Low** — a genuine but minor issue: cleanup, consistency, documentation drift, or a small robustness
  gap.

Findings that are pure preference with no demonstrated consequence are not reported as findings at all —
mention them only as optional notes if truly worth raising, clearly separated from real findings.

## Finding template

Every finding gets a short stable ID (F1, F2, ...; new ones found in Phase 2 continue the sequence or use
an N-prefix, e.g. N1) so Phase 2 and the New Findings section can reference it without repeating the whole
write-up. Every finding includes all of these fields:

- **ID** — e.g. `F3`.
- **Title** — one line naming the problem.
- **Severity** — one of the four emoji levels above.
- **Confidence** — Confirmed / Potential risk / Optional improvement (Phase 1 label; Phase 2 may revise it
  — see below).
- **Evidence** — file(s) and line(s) or equivalent stable location; quote or precisely describe what's
  there.
- **Location** — the affected component/module/table/endpoint, if not obvious from the evidence.
- **Impact** — the concrete consequence, with a scenario if it's conditional ("if two requests X and Y
  happen concurrently, then...").
- **Explanation** — why this happens; the mechanism, not just the symptom.
- **Recommendation** — what to do about it, including trade-offs, and explicitly marked as optional /
  conditional where relevant (e.g. "only worth doing if X grows past Y").

## Report format

Produce the report with exactly this section skeleton (write the analytical prose in whatever language the
person you're working with is using; keep these headings as given):

```
# Software Review

## Executive Summary

## Architecture

## SOLID

## Clean Code

## DRY & Code Smells

## Testing

## Security

## Concurrency

## Database & Indexes

## Performance

## Production Readiness

## Findings

## Audit Validation

## New Findings

## Final Assessment
```

Notes on filling it in:

- **Executive Summary**: a short severity-count table (Critical/High/Medium/Low × Confirmed/Potential
  risk/Optional improvement, or a simpler version if a project is small) plus a few sentences of overall
  read — what the project gets right, and what the single biggest risk is. Update this after Phase 2, not
  before — it should reflect the validated picture, not the raw Phase 1 output.
- **Per-topic sections** (Architecture through Production Readiness): the narrative, evidence-based
  walkthrough of that area — what was checked, what held up, what didn't. Reference findings by ID (`F3`)
  rather than repeating the full template inline; write the full template once, in Findings. If a topic has
  no findings, say so explicitly and briefly state what was checked — e.g. "Reviewed index coverage for
  all live query patterns in the reservations module; all are covered by an existing composite index,
  column order confirmed correct." Silence is not evidence of quality; a stated, verified pass is.
- **Findings**: the complete, authoritative list of every Phase 1 finding that survived its own validation
  step (see Phase 1, step 4–5), each with the full template, ranked by actual impact (not by which section
  it came from) — this is the "if you only fix N things" list, and it's what Phase 2 operates on.
- **Audit Validation**: one entry per finding ID from Findings, each labeled exactly one of **Confirmed** /
  **Downgraded** / **Upgraded** / **Requires More Evidence** / **Rejected**, with a brief explanation of why
  — grounded in the Phase 2 steps above (who really calls this, is the scenario reachable, what would
  contradict it). A downgrade or upgrade should say what the corrected severity/confidence is, not just
  that it changed.
- **New Findings**: anything Phase 2 turned up that Phase 1 never wrote down — full template, own IDs.
  Explicitly the place where "look for what Phase 1 missed" pays off; an empty section on a nontrivial
  review is worth a second look before accepting it as accurate (see the end of Phase 2 above).
- **Final Assessment**: an honest overall verdict calibrated to the project's stage (a prototype and a
  production system are held to different bars, and you should say which bar you're using), what to
  prioritize first — drawing on the *validated* severities from Audit Validation and New Findings, not the
  raw Phase 1 list — and, as part of this section rather than as extra top-level headings, anything that
  materially limits the review (couldn't run the test suite, couldn't see production data volume, couldn't
  confirm infrastructure topology, etc.) and any open questions worth the team resolving.

## What this Skill does not do

It does not write the fix, the migration, the test, or the refactor. It does not decide priorities for the
team — it ranks by impact and says why, and leaves the call to act on it with the people who own the
trade-offs. If asked to also implement fixes, treat that as a distinct next step, not part of this Skill's
output.
