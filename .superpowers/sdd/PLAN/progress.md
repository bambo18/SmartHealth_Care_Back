# SDD ledger — plan: PLAN.md

Scope (user, 2026-10-06): 진단서(health certificate) only — Phase 0 (Task 1-9) + Phase 1 (Task 10-18).
Phase 2 (Task 19-21, 치주염/oral) EXCLUDED. Task 22 (docs) limited to certificate-relevant config.

Setup: Ruling: no git repository (`git rev-parse` → fatal) — plan assumes worktree/branch/commits and
every task ends with `git add` + `git commit`. No worktree created, no commits made; the plan's commit
steps are recorded as skipped and this ledger is the only progress record. User also owns branching
decisions. Cost if wrong: no per-task rollback points; all work lands as one uncommitted working tree.

Setup: Ruling: migration numbering — plan's Global Constraints claim `V53`(diagnosis_attempts, written
by Task 7) and `V54`(병원 검색 권한 제거) already exist, so new = `V55`. On disk the highest is `V52`
and neither V53 nor V54 exists. This session created `V53__add_search_permissions_to_social_account_user.sql`
(the SOCIAL_ACCOUNT_USER 403 fix). Therefore: Task 7's diagnosis_attempts migration becomes **`V54`**;
Task 10's pet_health_certificates stays **`V55`** and Task 19's oral ALTER stays **`V56`** exactly as the
plan numbers them. Cost if wrong: one file rename before the migration is applied anywhere.

Setup: Ruling: SPEC.md is present and read as the binding authority (951 lines).
