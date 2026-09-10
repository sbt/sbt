---
name: release-notes
description: Roll the sbt release notes forward to a new checkpoint on the branch that carries the in-progress release. Use when the user asks to "update release notes", "roll release notes forward", or "sync release notes to <sha>".
---

# Release Notes

Use this skill to advance the sbt release notes checkpoint in `notes/<version>/` up to a given commit on `<src-branch>` (the branch the in-progress release is currently developed on), and to keep `backported-to-<prev-branch>.md` and `<version>-draft.md` in sync with what has and hasn't landed on the previous minor's maintenance branch.

## Resolving `<version>`, `<src-branch>`, and `<prev-branch>`

Don't hardcode any of these — derive them fresh every run, since they all roll forward release over release:

- **`<version>`** — the target release directory is whichever `notes/<X>.<Y>.<Z>` directory sorts highest by semver:
  ```bash
  ls notes/ | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -t. -k1,1n -k2,2n -k3,3n | tail -1
  ```
  Unless the user names a specific version, this highest directory is `<version>`. (This is the in-progress release being drafted, e.g. `2.1.0`.)
- **`<src-branch>`** — assume the branch currently checked out locally is the one `<version>` is being developed on:
  ```bash
  git branch --show-current
  ```
  Then sanity-check it before proceeding, rather than assuming it's correct. Fetch `upstream` and compare the current branch against the two branches that would plausibly carry `<version>`'s work: `develop` (pre-cut) and `<major>.<minor>.x` derived from `<version>` (post-cut, e.g. `2.1.0` → `2.1.x`). The current branch looks right if it *is* one of those, or if it's a short-lived local branch stacked directly on top of one of them (its merge-base with that branch is at or near its own tip, i.e. it's just that branch plus a few local, not-yet-pushed commits). If it's neither — a detached HEAD, `<prev-branch>` itself, an unrelated feature branch, or one that has diverged significantly from both candidates — **stop and ask the user to confirm which branch to treat as `<src-branch>`** rather than guessing.
- **`<prev-branch>`** — the previously-released maintenance branch that `<version>`'s fixes get backported to. If `<version>` is `<major>.<minor>.0`, the maintenance branch is `<major>.<minor-1>.x`. E.g. drafting `2.1.0` means the maintenance branch is `2.0.x`; when the cycle rolls forward and drafting becomes `2.2.0`, the maintenance branch becomes `2.1.x`. Compute `<minor-1>` from `<version>`'s own directory name each run — never assume `2.0.x`.

## File Layout

Each in-progress release has a directory `notes/<version>/` containing:

- `meta.md` — a running log of checkpoints reached, one line per update: `- Updated to up <src-branch-sha> (<src-branch>) / <prev-branch-sha> (<prev-branch>)`.
- `<version>-<sha8>-all.md` — exactly ONE checkpoint snapshot file at any time, in raw GitHub-auto-generated-release-notes style, covering every PR merged into `<src-branch>` from the start of the release up through commit `<sha8>` (first 8 hex chars) — i.e. it's cumulative from the very start of `<version>`, not just since the last checkpoint. Rolling the checkpoint forward renames this one file to the new `<sha8>` and appends the new commit range's entries to it; it never leaves an old-sha copy behind. If more than one `<version>-*-all.md` file is ever found (e.g. left over from before this rule existed, or from a manual process), merge them into one before doing anything else — see step 3.
- `<version>-draft.md` — the hand-curated, cumulative draft for the whole release. It opens with hand-written prose highlight sections (e.g. "Test summary", "Ivyless publishing") that are NOT auto-updated by this skill, followed by catch-all sections (`## 🚀 Other updates`, `## 🐛 Bug fixes`, `## ⚡ Performance improvements`, `## Behind the scenes`) that ARE auto-updated by this skill — but only with PRs that are fresh to `<src-branch>`/`<version>` and have not already been backported to `<prev-branch>` (backported fixes ship to users via `<prev-branch>` already, so they don't need separate `<version>` release-note billing).
- `backported-to-<prev-branch>.md` — every PR from `<src-branch>` that has already shipped on `<prev-branch>`, so it can be cross-checked/excluded from the fresh-in-`<version>` story. Grouped the same way, each entry suffixed `(released in <prev-branch version>)`.

## Section Categorization

Entries are grouped in this order (skip a section header if it would be empty):

1. `## changes with compatibility implications`
2. `## Security fixes`
3. `## 🚀 updates` (`<version>-draft.md` calls this `## 🚀 Other updates`) — new features and user-facing improvements. No separate "new features" section.
4. `## 🐛 Bug fixes` — any commit/PR titled `fix:` (including test-only fixes for a bug), plus untitled fixes.
5. `## ⚡ Performance improvements` — `perf:` commits.
6. `## Behind the scenes` — everything else: `ci:`, `test:`, `refactor:`, `docs:`, `deps:`/dependency bumps (Zinc, sbtn, Coursier, Scala, ipcsocket, sjson-new, mima settings), `build(deps):` dependabot commits.

Entry format:

- In `-all.md` checkpoint files: `- <commit subject, "[2.x] " prefix stripped, "(#N)" suffix kept as-is> by @<author> in https://github.com/sbt/sbt/pull/<N>`
- In `<version>-draft.md`: same but drop the trailing `(#N)` (the link already identifies the PR) and wrap code identifiers (setting/task/class names) in backticks for readability.
- In `backported-to-<prev-branch>.md`: same style as the draft, plus a trailing `(released in <prev-branch version>)`.

## Steps

1. **Resolve `<version>`, `<src-branch>`, and `<prev-branch>`** as described above.

2. **Resolve the target GITSHA.** If the user specifies one, use it (8 hex chars). Otherwise, use the tip of the current branch (`<src-branch>`, per the sanity-checked resolution above):
   ```bash
   git log -1 --format=%h
   ```
   If that tip commit is itself a prior "Update release notes" bookkeeping commit (touches only `notes/`, no `(#N)` PR reference), it's not a real checkpoint target — use its parent instead, or ask the user which sha they meant.

3. **Find the last checkpoint, and make sure there's only one.**
   ```bash
   ls notes/<version>/<version>-*-all.md
   ```
   This must return exactly one file. If it returns more than one, consolidate first: read every one of them, and for each section concatenate their entries in file order from oldest sha to newest (each file's own entries are already chronological internally, and the files themselves don't overlap in PR coverage — verify that by comparing their `pull/<N>` references before assuming it), drop the trailing `(#N)` self-reference on any entry that has one (keep any other, non-self-referencing parenthetical numbers in a title as-is), set the `Full Changelog` line to span from the oldest file's start sha to the newest file's end sha, write the result to the newest file's name, delete the rest, and log the consolidation as a `meta.md` line before continuing to step 1 above with the now-single file in place. Never proceed to step 4 with more than one checkpoint file present.

   Then read `notes/<version>/meta.md` for the most recent sha and the branch it was taken from. If that branch differs from the just-resolved `<src-branch>` (e.g. the release was cut to its own branch since the last run), note the switch and confirm the old sha is still an ancestor of the current `<src-branch>` tip before treating it as the range start — otherwise use the branch-cut commit itself as the range start, and flag the discrepancy to the user rather than silently proceeding. Confirm the sha in that single checkpoint file's name matches the sha from `meta.md`.

4. **Roll the checkpoint file forward in place** — rename it, don't copy it, so there is still only one when this step finishes:
   ```bash
   git mv notes/<version>/<version>-<oldsha8>-all.md notes/<version>/<version>-<newsha8>-all.md
   ```
   (plain `mv` if the file isn't tracked yet). The new entries get appended to this same renamed file in step 7 — there is never a second `-all.md` file coexisting even momentarily as a deliberate step.

5. **Enumerate the new commit range** in chronological order, resolving merge commits to the single PR they bundle:
   ```bash
   git log <oldsha>..<newsha> --reverse --format="%h %ci %s"
   ```
   For each entry, extract the trailing `(#N)` PR number from the subject. A merge commit like `Merge pull request #N from ...` bundles the commits between it and the previous merge into that one PR — collapse them to a single entry keyed by `#N`, not one entry per commit. Skip any commit with no `(#N)` reference at all (e.g. a local, not-yet-pushed bookkeeping commit) — it isn't a PR to list.

6. **Fetch canonical PR metadata** for each PR number (title can differ slightly from the commit subject — prefer the PR title):
   ```bash
   gh pr view <N> --repo sbt/sbt --json number,title,author,mergedAt,url
   ```

7. **Categorize and append** each new entry into the matching section of the new `-all.md` checkpoint file (append at the end of that section's list, immediately before the next `##` header), then update the trailing line:
   ```
   **Full Changelog**: https://github.com/sbt/sbt/compare/<old-full-sha>...<new-full-sha>
   ```

8. **Check `<prev-branch>` backport status** for each new PR. A backport is NOT reliably identifiable by PR number appearing in the `<prev-branch>` commit subject alone — some backports carry a different subject (e.g. a batch "Backports" PR, or a manually retitled one). Check, in order:
   ```bash
   git fetch upstream <prev-branch> --quiet
   git log upstream/<prev-branch> --grep="#<N>" -i --oneline
   ```
   and, for any merged `<prev-branch>`-base PR whose subject doesn't already mention `#<N>`, check its body for a link:
   ```bash
   gh pr list --repo sbt/sbt --base <prev-branch> --state merged --json number,title,body \
     | jq '.[] | select(.body | test("pull/<N>"))'
   ```
   Bodies typically read "This is a `<prev-branch>` backport of https://github.com/sbt/sbt/pull/<N>" (a batch PR lists several such lines). Titles can also match with an added `bport:`/`[<prev-branch>]` prefix when no body link exists — treat a clear title match as sufficient corroboration.

9. **Update `backported-to-<prev-branch>.md`** by appending an entry, in the matching section, for each new PR found backported — `(released in <version>)`, where `<version>` is the nearest following `sbt <version>` tag commit on `upstream/<prev-branch>` (`git log upstream/<prev-branch> --oneline --grep="^sbt "`).

10. **Update `<version>-draft.md`** by appending an entry, in the matching catch-all section, for each new PR that is NOT backported to `<prev-branch>` — these are the ones freshly available in `<version>` and not already shipped to `<prev-branch>` users. Do not touch the hand-written prose highlight sections at the top of the file.

11. **Update `meta.md`** by appending a new line: `- Updated to up <newsha8> (<src-branch>) / <prev-branch-sha8> (<prev-branch>)`, using `git log upstream/<prev-branch> -1 --format=%h` for the `<prev-branch>` side.

12. Report a short summary: new sha range (and branch, if it differs from the previous checkpoint's), PR count by section, and how many were newly backported vs. fresh-only.

## Notes

- `<src-branch>` is whatever the user currently has checked out — trust it once it passes the sanity check above, and re-derive it fresh each run rather than remembering a previous run's answer. When in doubt, stop and ask; don't silently pick `develop` or the version branch on the user's behalf.
- For `<prev-branch>` state, never fetch or rely on `origin`/personal forks — always use the `upstream` remote (`sbt/sbt`).
- Do not rewrite or reorder existing entries; only append new ones in chronological (merge-time) order within each section.
- If `gh` is not authenticated, tell the user to run `gh auth login` — PR titles/authors/backport-body checks all depend on it.
- `<src-branch>` and `<prev-branch>` can coincide in principle right after a cut (both pointing at nearly the same commit) but are never assumed equal — always resolve them independently.
