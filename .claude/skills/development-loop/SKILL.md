---
name: development-loop
description: Drive a single, already-identified audit finding (a stable ID like F1, F2, F3... from a software-review-style report) through a controlled ANALYZE → PLAN → IMPLEMENT → TEST → REVIEW → DECIDE cycle, iterating (up to 5 times, with up to 2 TEST resumptions per iteration) until the finding is genuinely resolved with evidence, partially resolved pending obtainable evidence, blocked on something only a human can supply, or the iteration cap is reached. Writes and commits code for exactly that one finding — never a full audit, never more than one finding per run, never a PR. Persists all state in docs/loop/F#.md so any future session can resume without this conversation's context. Use when asked to "run the development loop on F#", "resolve finding F#", "work F# to resolution", or to continue/resume an existing docs/loop/F#.md.
---

# Development Loop

A controlled, evidence-gated cycle for taking **one** already-identified finding from an audit report to
a **demonstrated** resolution — not an assumed one. This Skill writes code. It is the implementation
counterpart to `software-review`, which only reads.

## Boundaries — read this first

**This Skill resolves exactly one finding per invocation.** It never runs a full audit, never discovers
and fixes a second finding "while it's at it", and never opens more than one line of work at a time.

**Never, under this Skill:**
- Run a full codebase audit, or anything resembling the 15-area methodology of `software-review`. If no
  finding with a stable ID is given or locatable, stop and say so — do not go looking for a problem to
  solve.
- Start resolving a second finding, even one discovered mid-loop and clearly related. Record it as
  `RELATED_FINDING: F#` (one line, in the state file) and leave it untouched.
- Declare `RESOLVED` on reasoning, code review, or "the tests compile" alone. `RESOLVED` requires the
  specific kind of evidence the finding's own risk calls for, actually produced in this run — see
  **Evidence rules**.
- Open a pull request. V1 stops at a pushed branch (if pushing is asked for) and a `docs/loop/F#.md` a
  human can read to decide whether to open one. This may change in a later version.
- Touch files outside the scope declared in `## Scope` of the state file without explicitly saying so and
  justifying why the deviation was unavoidable — see **Scope: `IN_SCOPE` / `OUT_OF_SCOPE`**.
- Silently keep iterating forever. Iteration and resumption counters are hard caps (see **Iteration &
  resumption bookkeeping**), not suggestions.
- Hide a blocked or ambiguous outcome behind a cheerful-sounding status. If it isn't demonstrated, it is
  `PARTIALLY_RESOLVED` or `BLOCKED`, reported with the exact fields those states require — never `RESOLVED`
  dressed up.

If asked to also fix a *different* finding, or to audit the project, or to open the PR — those are
separate, explicit requests. This Skill does not fold them in on its own initiative.

## The loop in one sentence

**Analyze precisely, plan comparatively, implement narrowly, test for real, review skeptically, and only
call it done when the evidence the risk actually demands exists — repeating a phase only when repeating it
would change the outcome, never as a reflex.**

---

## Locating the finding and the loop's current state

Do this before touching any phase logic.

1. **Parse the finding ID** from the request (e.g. `F4`). If none is given, ask which finding to work —
   do not guess or pick "the most important one" yourself.
2. **Find the finding's full write-up.** Search `docs/*.md` for a `### F<id> —` (or equivalent ID-bearing)
   heading — today that means a report written by the `software-review` Skill's current template
   (`docs/software-review-*.md`). Older ad-hoc audit reports in this repo (e.g. anything that labels
   findings only as `[Alto]`/`[Medio]`/`[Bajo]` with no stable ID) are **not valid input** for this Skill —
   if the requested ID only exists in that style of document, or doesn't exist anywhere, stop and tell the
   human: either point to a report that assigns this finding a stable ID, or re-run `software-review` so
   it gets one. Do not invent an ID or assume which paragraph of an unlabeled report the human means.
3. **Check for `docs/loop/F<id>.md`.**
   - **Missing** → this is a fresh loop. Iteration = 1, resumption count = 0, phase = ANALYZE. Create the
     file (see **State file format**) with the Metadata block filled from the finding's source report.
   - **Present** → this is a resume. Read the whole file, not just the Metadata header — the most recent
     iteration's log is what tells you what was already tried and learned. Take `Estado actual` at face
     value:
     - `RESOLVED`, `BLOCKED`, or `MAX_ITERATIONS_REACHED` → **stop immediately** and report that status
       back plainly. Do not silently reopen a closed loop because you were asked to "work on F#" again —
       reopening a terminal state is the human's call, not an inference you make from being asked once
       more. If the human explicitly asks to reopen, treat it as a new instruction to append a fresh
       iteration on top of the existing file (do not delete history).
     - `PARTIALLY_RESOLVED` with a `resumable_at` field → check whether the condition it names now holds
       (e.g. "Docker available" — actually check, don't assume). If it holds, resume at the named phase. If
       it still doesn't hold, say so and stop again; don't burn a resumption attempt restating the same gap.
     - Any active phase state (`ANALYZING`, `PLANNING`, `IMPLEMENTING`, `TESTING`, `REVIEWING`, `DECIDING`)
       → resume at that phase, using the file's own record of what that phase had already produced (if a
       partial output exists) or starting that phase fresh if it doesn't.

---

## Scope: `IN_SCOPE` / `OUT_OF_SCOPE`

Before `IMPLEMENT` runs for the first time in a loop, the state file must contain a `## Scope` section with
two lists, produced at the end of `PLAN`:

- **`IN_SCOPE`** — the concrete files/classes this fix will touch, taken directly from the plan's chosen
  alternative. Nothing vague ("the schedule use cases") — name the actual paths.
- **`OUT_OF_SCOPE`** — things a reasonable person could mistake for part of this fix, named explicitly so a
  later reviewer (human or Claude) doesn't have to guess whether an omission was deliberate: sibling
  findings that look similar, dead code adjacent to the change, a related but separate concurrency gap,
  etc. Pull these from `ANALYZE`'s findings and from `RELATED_FINDING` entries.

`REVIEW` diffs the real changes against this list on every pass (see **REVIEW**, check 4). If a later
iteration's `PLAN` needs to revise the scope (e.g. `ANALYZE` iteration 2 found the mechanism was bigger
than first thought), it edits this section and says explicitly what changed and why — it does not silently
replace it.

`PLAN` owns redefining `IN_SCOPE`/`OUT_OF_SCOPE` going into a new iteration. `REVIEW` may additionally
append a one-line *accepted deviation* note to `OUT_OF_SCOPE` for a necessary side effect it finds during
its own out-of-scope check (see **REVIEW**) — that's disclosure of something already implemented, not a
scope redefinition, and it does not require going back to `PLAN` first.

---

## Phases

Six phases, always in this order within an iteration, with defined ways to advance or fall back. Every
phase, on completion, appends its result block to `docs/loop/F<id>.md` under the current `## Iteration N`
heading and commits that file (see **Branch and traceability**) — this is what makes a resume from a fresh
session possible. Do not batch several phases' state-file updates into one commit at the end; a session
that gets interrupted mid-loop should leave a trail up to whatever phase actually finished.

**No fallback bypasses the counters.** Each phase below lists its own "falls back to X" condition for when
*that phase itself* recognizes early that it can't proceed — this is a legitimate shortcut so a plainly
unworkable plan doesn't have to be walked all the way to `TEST`/`REVIEW` just to be told what's already
obvious. But recognizing this early does **not** mean skipping the bookkeeping: any such fallback still
increments the same counter it would if `DECIDE` had routed there (iteration count if the target is
`ANALYZE`/`PLAN`/`IMPLEMENT`, resumption count if the target is `TEST`) and is subject to the same caps,
*before* it happens — check the cap first, exactly as `DECIDE` would. There is no path through this Skill
that moves backward without moving one of the two counters in **Iteration & resumption bookkeeping**.

### ANALYZE

- **Purpose:** trace the finding's mechanism in the *current* state of the code — not in the audit report's
  description of it, which may be stale by the time the loop runs, and not by trusting a previous
  iteration's ANALYZE without re-checking what changed since.
- **Inputs:** the finding's write-up from its source report; on iteration 2+, the previous iteration's full
  log (what was tried, what REVIEW found wrong).
- **Allowed actions:** read-only. Grep, read files, trace call chains, check reachability the same way
  `software-review`'s methodology does (a hypothesis is not a finding until traced to a real caller). No
  edits.
- **Produces:** an `ANALYSIS RESULT` block — root cause, affected flow (concrete classes/methods, not
  categories), existing protection, missing protection, confidence, and (iteration 2+) what specifically
  changed in this pass's understanding versus the last one.
- **Advance to PLAN when:** the mechanism is traced to specific, current code with file:line evidence, and
  reachability is confirmed, not assumed.
- **Falls back to itself (next iteration) when:** a later phase's REVIEW determines the original mechanism
  understanding was wrong or incomplete.
- **Stops the loop (`BLOCKED`) when:** the finding turns on a fact no amount of code-reading can supply —
  an external system's behavior, a product decision, information only a human has. Do not spend an
  iteration building a plan on top of a guess about that fact.

### PLAN

- **Inputs:** this iteration's `ANALYSIS RESULT`.
- **Allowed actions:** read-only. Compare at least two real alternatives when more than one exists — "this
  is the only way" needs to survive having been asked "why not X instead". Do not treat "similar code
  exists elsewhere in this project" as proof an alternative is correct; that's a starting hypothesis to
  verify, per `software-review`'s own explicit warning about that trap.
- **Produces:** a `PLAN RESULT` block — recommended strategy, reasoning, trade-offs, the `## Scope`
  (`IN_SCOPE`/`OUT_OF_SCOPE`) section described above, the tests required, and — this is the field that
  makes evidence-gating possible — **the specific kind of evidence that will be required before `DECIDE` may
  say `RESOLVED`** (e.g. "a Testcontainers run against real MySQL showing the race can't produce an
  inconsistent state", not just "tests pass").
- **Advance to IMPLEMENT when:** one alternative is chosen with stated trade-offs, `IN_SCOPE`/`OUT_OF_SCOPE`
  are concrete, and the required-evidence statement is specific enough that `TEST`/`REVIEW` can later check
  against it without re-litigating what "enough" means.
- **Falls back to ANALYZE when:** no viable alternative exists without first re-checking or expanding the
  mechanism understanding.
- **Stops the loop (`BLOCKED`) when:** every viable alternative requires a decision only a human can make
  (a product trade-off, an authorization, a resource that can't be provisioned by continuing to iterate).

### IMPLEMENT

- **Inputs:** this iteration's `PLAN RESULT`, including `IN_SCOPE`.
- **Allowed actions:** write/edit code and tests, exactly as planned. Write the tests the plan called for.
  Run whatever compiles/builds cheaply to confirm the change is syntactically sound. Do **not** treat "I
  wrote it" as evidence it's correct — that's `TEST`'s job.
- **Produces:** an `IMPLEMENTATION RESULT` block — files actually changed (diff stat), tests added/changed,
  and an explicit, itemized comparison against `IN_SCOPE`: anything touched that wasn't listed must be
  named here with a one-line justification (a necessary side effect, like a changed exception type) —
  never left for `REVIEW` to discover on its own.
- **Advance to TEST when:** the change compiles, matches the plan (deviations declared), and the tests the
  plan required exist.
- **Falls back to PLAN when:** the chosen approach turns out unworkable partway through implementation (not
  "I found a slightly nicer way" — a real infeasibility).
- **Stops the loop:** `IMPLEMENT` itself should not reach `BLOCKED` directly; a blocker discovered here
  routes back to `PLAN` or `ANALYZE` first, which then decides whether it's genuinely unresolvable in-loop.

### TEST

- **Purpose:** produce *objective, executed* evidence — never assume "should pass" or "compiles" is
  equivalent to "ran and passed". This phase exists as its own gate specifically so that "wrote tests" is
  never silently read as "tests ran and demonstrated the fix."
- **Inputs:** the code and tests `IMPLEMENT` left, and `PLAN`'s stated evidence requirement.
- **Allowed actions:** run tests. Before running anything that needs infrastructure (Docker, a real
  database, an external service), **check that the infrastructure is actually there** — don't discover it's
  missing by watching a test hang or error out uninformatively. Run the specific new/changed tests **and**
  the pre-existing relevant suite (regression check) — never only the new tests. Do not modify code or
  tests to make something pass; if a test is wrong, that's a `REVIEW`/`IMPLEMENT` finding to fix
  deliberately, not something to patch quietly mid-`TEST`.
- **Produces:** an evidence block: what ran, how many times (if repeated/parameterized), pass/fail counts,
  what could **not** run and the specific missing precondition, and whether anything pre-existing regressed.
  Report failures completely (full failure output), not summarized into "some tests failed."
- **Advance to REVIEW when:** every test that *can* run in the current environment has run, and the result
  (pass, fail, or "blocked on X") is recorded honestly.
- **Resume TEST (not a new iteration) when:** the only reason full evidence wasn't obtained is missing
  infrastructure or external evidence that could plausibly become available (Docker not running, a service
  not reachable) — see **TEST resumption**, capped at 2 per iteration.
- **Falls back to IMPLEMENT when:** a test failure points to an actual defect in the code (not a test bug,
  not a missing precondition).
- **Stops the loop (`BLOCKED`) when:** the 2-resumption cap for this iteration is exhausted and the
  required evidence still can't be produced.

### REVIEW

- **Purpose:** an independent, skeptical re-check — not a rubber stamp on what `IMPLEMENT`/`TEST` already
  said. Re-read the *current* code and the *actual* diff yourself; do not just trust the phase summaries
  above. In V1, `REVIEW` runs in the same agent/session as the rest of the loop — precisely because of that,
  treat it as a deliberate context switch: re-derive conclusions from the artifacts, don't recycle
  `IMPLEMENT`'s framing of its own work.
- **Inputs:** `IMPLEMENTATION RESULT`, `TEST`'s evidence block, `PLAN`'s stated evidence requirement, and
  `## Scope`.
- **Must check, explicitly, every time:**
  1. Was the original finding's mechanism actually closed — re-traced from the current code, not assumed
     from the plan's intent?
  2. Does the evidence `TEST` produced actually match what the finding's risk requires (per `PLAN`'s
     evidence statement) — or is it a cheaper substitute (e.g. unit tests standing in for a needed
     integration/concurrency proof)?
  3. Are there regressions — did the pre-existing relevant suite stay green?
  4. Is the real diff (`git diff --stat` against `development`, the branch this fix branched from — see
     **Branch and traceability**) fully inside `IN_SCOPE`?
  5. Were any files modified that aren't part of this finding at all?
  6. Are there tests or verifications the plan called for that are still outstanding?
- **On out-of-scope changes (checks 4/5):** REVIEW cannot look past this or wave it through as "probably
  fine". For each out-of-scope change found:
  - If it's a **necessary, unavoidable side effect** of the approved plan (e.g. a changed exception type
    that only exists because the new lock check had to decide what to throw) — keep it, but add it to
    `OUT_OF_SCOPE` as an *accepted deviation* with a one-line reason, in this REVIEW's own block. This is
    disclosure, not silent tolerance.
  - If it's **not necessary** (incidental cleanup, an unrelated fix, refactoring swept in) — revert exactly
    that change (`git checkout`/`git restore` on the specific file or hunk, nothing else) and re-run the
    check. If it can't be cleanly separated from the real fix, stop the loop with `BLOCKED` and explain why
    — do not ship a bundle no one asked for.
- **Produces:** a `REVIEW RESULT` with one of three verdicts: **evidence sufficient** (nothing outstanding),
  **evidence insufficient but code looks correct**, or **defect found** (in the code, the plan, or the
  original analysis) — plus, for the latter two, which phase it should route back to and why:
  - Evidence gap only, caused by missing/unavailable infrastructure → route to `TEST` (resumption).
  - Implementation doesn't match an otherwise-sound plan → route to `IMPLEMENT`.
  - The chosen approach doesn't actually solve the mechanism `ANALYZE` described → route to `PLAN`.
  - The mechanism itself was mis-diagnosed → route to `ANALYZE`.
- **Always advances to DECIDE** — `REVIEW` never itself declares the loop's final status; it hands its
  verdict and routing recommendation to `DECIDE`, which owns the state-machine transition.

### DECIDE

- **Purpose:** turn `REVIEW`'s verdict into a state-machine transition, update the counters, and either
  close the loop or start the next step honestly.
- **Inputs:** `REVIEW RESULT`.
- **Logic** (apply in this order):
  1. If `REVIEW` verdict = evidence sufficient, no open scope/regression issues → **`RESOLVED`**. Stop.
  2. Else if the gap is evidence-only and infrastructure-caused:
     - If this iteration's TEST-resumption count < 2 → resume at `TEST` (increment resumption count, do
       **not** increment iteration count) → **`PARTIALLY_RESOLVED`** as this run's exit status, with
       `resumable_at: TESTING` and the precise condition needed.
     - Else (resumption cap already used) → **`BLOCKED`** (see **Blocked handling**).
  3. Else if `REVIEW` found a defect in the code/plan/analysis, **or found evidence still outstanding for
     a reason other than infrastructure** (e.g. a test the plan required was never written — this is the
     `REVIEW` verdict "evidence insufficient but code looks correct" for any cause other than the
     infrastructure-caused one already handled in case 2):
     - If iteration count < 5 → start a new iteration at the phase `REVIEW` named, increment iteration
       count, reset this iteration's resumption count to 0. Continue the loop (this is the "not resolved →
       new iteration" case) automatically, within the same invocation, unless the new iteration's `ANALYZE`
       or `PLAN` itself concludes `BLOCKED`.
     - Else (iteration count already at 5) → **`MAX_ITERATIONS_REACHED`**. Stop.
  4. Else if resolving requires a decision only a human can make (product trade-off, authorization,
     unobtainable external fact) → **`BLOCKED`**. Stop.
- **Extra safeguard:** before starting a new iteration under case 3, compare this iteration's `ANALYSIS
  RESULT`/`PLAN RESULT` to the previous one. If they're materially the same with no new evidence driving a
  different approach, don't spend another iteration repeating it — treat it as `BLOCKED` instead ("stuck,
  not iterating") and say so plainly, even if the iteration cap hasn't been reached yet. This is judgment,
  not a hard string match — use it when a fresh iteration would obviously just replay the last one.
- **Always:** update the state file's Metadata header (status, iteration, phase, resumption count, last
  updated) and commit it, regardless of which branch of the logic fired.

---

## TEST resumption

Distinct from a new iteration. A resumption means: nothing about the diagnosis, plan, or code was wrong —
the only gap is that some piece of required evidence couldn't be produced *in this environment, right now*
(Docker not running, a real database unreachable, an external dependency unavailable), and that gap is
plausibly temporary.

- Cap: **2 resumptions per iteration.** Reset to 0 at the start of every new iteration.
- A resumption re-enters at `TEST` directly — `ANALYZE`/`PLAN`/`IMPLEMENT` are not re-run, and the
  iteration counter does not move.
- Before spending a resumption, actually re-check the missing precondition (e.g. run `docker info`) rather
  than assuming it's now available because time has passed.
- Exceeding the cap without obtaining the evidence is `BLOCKED`, not a third silent attempt.

---

## Iteration & resumption bookkeeping

| Counter | Starts at | Incremented when | Reset when | Hard cap |
|---|---|---|---|---|
| Iteration | 1 | `DECIDE` routes back to `ANALYZE`, `PLAN`, or `IMPLEMENT` | Never (monotonic per loop) | 5 |
| TEST resumption | 0 | `DECIDE` routes back to `TEST` for an infra/evidence-only gap | Start of each new iteration | 2 per iteration |

Both live in the state file's Metadata header at all times, so a resumed session can read them without
recomputing anything from the log.

---

## Evidence rules

- The kind of evidence required is **whatever `PLAN` declared it to be for this specific finding** — not a
  fixed checklist. A pure logic bug in a pure function may only need a unit test; a concurrency claim needs
  a real concurrent run against real infrastructure (Testcontainers, a real DB) the same way F4 did; a
  schema claim needs the actual migration/schema inspected, not inferred; an HTTP contract claim needs an
  actual request/response check, not just a use-case-level test.
- Unit tests passing are never sufficient by themselves to close a finding whose risk is about integration,
  concurrency, or infrastructure behavior — no matter how confident the reasoning sounds. This is the
  literal lesson of F4: correct-looking code plus passing unit tests plus untested Testcontainers files was
  `PARTIALLY_RESOLVED`, not `RESOLVED`, until the real run happened.
- If the required evidence genuinely cannot be produced in this environment even after resumptions, say so
  plainly (`BLOCKED` or `PARTIALLY_RESOLVED`, per the DECIDE logic) — never substitute a weaker kind of
  evidence and call it equivalent without saying that's what happened.

---

## Honesty rules

Never omit, downplay, or bury, in any phase's output or in the state file:
- Tests that were written but not executed, and exactly why.
- Missing infrastructure, even if it turned out not to matter in the end.
- Genuine uncertainty about whether something is fully correct.
- Any result that's ambiguous rather than clearly pass/fail.
- Any regression found, however minor.
- Any change made outside the declared scope, even a one-line necessary one.

If a terminal state can't honestly be `RESOLVED`, it is `PARTIALLY_RESOLVED` or `BLOCKED` — reported with
the required fields (see **Final states**), never smoothed over.

---

## Blocked handling

Whenever any phase's logic above says `BLOCKED`, write exactly this shape into the state file and stop:

```
STATUS: BLOCKED

Reason: <what specifically can't be resolved by continuing to iterate>
Evidence available: <what was actually established so far, with pointers>
Missing evidence: <precisely what's absent and why it couldn't be obtained>
Human action required: <the concrete thing a person needs to decide or provide>
```

Do not attempt to invent a workaround to avoid reporting `BLOCKED`. A blocked loop that says so clearly is
a correct outcome; a loop that guesses past a real blocker to avoid reporting one is not.

---

## Related findings

If ANALYZE, PLAN, IMPLEMENT, or REVIEW surfaces a different problem — even one that looks obviously related
or trivially fixable — record it in the state file:

```
RELATED_FINDING: F#
<one line: what it is and where it was noticed>
```

Do not investigate it further, do not fix it, do not let it influence `IN_SCOPE`. It's a note for whoever
picks up that finding next, not a second task for this run.

---

## Branch and traceability

Match the project's existing convention (verify it hasn't changed before assuming this): feature/fix work
branches off `development` (the project's documented source of truth, not `main`), named
`fix/f<id>-<short-slug>` — e.g. `fix/f4-schedule-concurrency-lock`. `docs/audit-<date>` is a different,
docs-only convention; don't reuse it for code.

- If no branch exists for this finding yet, create it off `development` before `IMPLEMENT`'s first commit.
- Record the branch name in the state file's Metadata block the moment it's created or confirmed.
- Commit the state file at every phase boundary (see **Phases**), and commit code changes as they're made
  during `IMPLEMENT`, using Conventional Commits (`type(scope): message`) matching the project's existing
  history, in English, referencing the finding ID and its source report (e.g. "See docs/software-review-
  2026-08-30.md, finding F4").
- Opening the PR is explicitly out of scope for this Skill in V1. When a loop reaches `RESOLVED`, say so and
  name the branch — leave opening the PR to the human or to a separate, explicit request.

---

## State file format: `docs/loop/F<id>.md`

Write this file in Spanish, matching the language of the audit reports it references (code and commit
messages stay in English, per this project's existing convention — the loop's narrative record follows the
audit docs' language instead, since it's the same kind of document).

```markdown
# Loop: F<id> — <título corto tomado del finding>

## Metadata
- **Finding:** F<id>
- **Auditoría de origen:** <path del reporte> (sección/línea del finding)
- **Objetivo:** <una línea — qué invariante o riesgo este loop busca cerrar>
- **Branch:** <nombre o "— (no creado todavía)">
- **Estado actual:** <uno de los estados activos o terminales>
- **Iteración actual:** N / 5
- **Reanudaciones de TEST (esta iteración):** N / 2
- **Fase actual:** <ANALYZE|PLAN|IMPLEMENT|TEST|REVIEW|DECIDE|— (cerrado)>
- **Reanudable en:** <fase, o "—">
- **Última actualización:** <fecha>

## Scope
### IN_SCOPE
- <archivo/clase>
- ...

### OUT_OF_SCOPE
- <cosa que podría confundirse con parte de este fix, y por qué no lo es>
- ...

(Si una iteración posterior revisa esto, agregar una nota fechada explicando qué cambió y por qué —
nunca sobreescribir en silencio.)

## Related findings
- `RELATED_FINDING: F#` — <una línea>

---

## Iteración 1

### ANALYZE
<ANALYSIS RESULT completo>

### PLAN
<PLAN RESULT completo, incluye la sección Scope de arriba la primera vez>

### IMPLEMENT
<IMPLEMENTATION RESULT completo>

### TEST
<evidencia objetiva: qué corrió, cuántas veces, resultados, qué no pudo correr y por qué>

### REVIEW
<REVIEW RESULT completo, con los 6 checks explícitos>

### DECIDE
- Veredicto: <...>
- Ruta: <RESOLVED | resume TEST | nueva iteración en <fase> | BLOCKED>
- Razón: <...>

<!-- Si DECIDE resuelve reanudar TEST, seguir agregando bajo esta MISMA Iteración N,
     no crear una nueva sección de iteración: -->

### TEST — reanudación 1
<evidencia real>

### REVIEW (post-reanudación 1)
<...>

### DECIDE
- Veredicto: ...

---

## Iteración 2
(mismo patrón — solo si DECIDE de la iteración 1 inició una nueva iteración)

---

## Resultado final

**STATUS: <RESOLVED | PARTIALLY_RESOLVED | BLOCKED | MAX_ITERATIONS_REACHED>**

<Para RESOLVED: qué evidencia concreta lo sostiene.>
<Para PARTIALLY_RESOLVED o BLOCKED: Reason / Evidence available / Missing evidence / Human action required.>
<Para MAX_ITERATIONS_REACHED: resumen de qué se probó en cada iteración y una recomendación concreta.>

Qué queda fuera de este loop: <findings relacionados no tocados, PR no abierto, etc.>
```

This has to be readable by a session with **no memory of this conversation** — write every block as if the
next reader has only this file and the repository.

---

## Final states

- **`RESOLVED`** — `TEST` produced exactly the kind of evidence `PLAN` declared this finding needs, executed
  (not just written), and `REVIEW` independently confirms all six of its checks with nothing outstanding.
  Not a state of convenience: if any of the six REVIEW checks is open, it isn't `RESOLVED`.
- **`PARTIALLY_RESOLVED`** — the code is implemented and `REVIEW` judges it correct, but the specific
  evidence the finding requires couldn't be produced in this run for a reason that's plausibly temporary
  (typically missing infrastructure), **and** at least one TEST resumption is still available (resumption
  count < 2) to try again once the gap is closed. Always includes `resumable_at` and the exact condition
  needed. The moment the 2-resumption cap is used up without obtaining the evidence, this stops being
  `PARTIALLY_RESOLVED` and becomes `BLOCKED` instead (see below) — `PARTIALLY_RESOLVED` never means "gave up
  after using all the resumptions", it means "genuinely still resumable, just not right now."
- **`BLOCKED`** — the loop cannot safely continue without something only a human can supply: a decision, a
  fact no code-reading can produce, an infrastructure resource that isn't just "not running right now" but
  genuinely unavailable to this session, **or the 2-resumption cap exhausted without obtaining the required
  evidence**. Always uses the four-field format in **Blocked handling**.
- **`MAX_ITERATIONS_REACHED`** — 5 iterations (full ANALYZE/PLAN/IMPLEMENT restarts, not TEST resumptions)
  were spent without reaching `RESOLVED`. Always includes a per-iteration summary of what was tried and a
  concrete recommendation for what a human should look at next — never a bare "gave up".

These four are mutually exclusive and must be distinguishable from the Metadata header alone, without
reading the full log: `Estado actual` names exactly one of them (or an active phase, if still running).

---

## Operational flow (what to actually do, start to finish)

1. Parse the finding ID from the request. If missing or ambiguous, ask.
2. Locate the finding in an ID-bearing report. If not found, stop and say what's needed (a report with a
   stable ID for this finding).
3. Check for `docs/loop/F<id>.md`. Resume per **Locating the finding and the loop's current state** if it
   exists and isn't already terminal; otherwise start fresh at `ANALYZE`, iteration 1.
4. Confirm or create the `fix/f<id>-<slug>` branch off `development`; record it.
5. Run the current phase per its contract above. Append its result block to the state file; commit.
6. At `DECIDE`, apply the transition logic exactly as specified; update the Metadata header; commit.
7. If the outcome is an active phase (a new iteration or a TEST resumption), continue automatically within
   this same invocation — do not wait for a new human message to keep going, that's the point of a
   controlled loop. Keep applying step 5 onward.
8. The moment the outcome is `RESOLVED`, `PARTIALLY_RESOLVED`, `BLOCKED`, or `MAX_ITERATIONS_REACHED`, stop
   and report that status plainly, with what it means and (for anything short of `RESOLVED`) exactly what a
   human needs to do next. Never continue past a terminal state without a new, explicit instruction.

Use judgment inside each phase's contract — this is a set of gates and required evidence, not a script that
anticipates every situation. Where this document doesn't cover a specific situation, apply the same
standard used throughout: trace before concluding, disclose what you can't verify, and never call something
resolved that you haven't actually demonstrated.
