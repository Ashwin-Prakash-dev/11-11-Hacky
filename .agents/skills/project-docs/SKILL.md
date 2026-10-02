---
name: project-docs
description: Keep DeepSight's docs accurate and short during the hackathon. Use after a meaningful change (new pack, gate passed, spike answered, measured number) to update STATUS.md, a pack README, claims, or the repo README, and before the demo/pitch to check that every claim is sourced.
---

# Project Documentation (hackathon)

We are in a 30-hour hackathon building an MVP. Docs exist to keep 4 people and their agents in sync and to back every claim in the pitch. They are not a client handover. **When in doubt, write less.**

## Which doc holds what

Use these; don't create a new doc when one of them fits.

| Doc | Holds | Update when |
|---|---|---|
| `docs/STATUS.md` | Current state: gates, spikes, per-track done/next, verified facts, risks, decisions | Any change that matters. Edit only your track's section and rows you own, in the same commit as the work. |
| `AGENTS.md` | Rules, layout, commands (rarely changes) | A rule or command changes. Shared file: pull first, keep it minimal. |
| `docs/architecture.md` | Target pipeline, design rules, pack format, honest per-module claims | The design changes (team agreement). |
| `contracts/README.md` | Contract semantics and triage order | Only with a team-agreed contract change. |
| `ml/packs/<id>/README.md` | Dataset, licence, held-out metrics, known limits for that pack | The pack's model, data or metrics change. |
| `docs/claims.md` | Every number used in the deck or demo, each with its source or measurement | A number is measured or will be said on stage. |
| `README.md` (repo root) | What DeepSight is, how to install and run, links to the other docs | Run steps change; final pass before judging. One screen. |

Create a doc from this table only when you have content for it; no empty scaffolds.

## Principles

1. Docs reflect what is built and measured, never the plan. Planned work goes in STATUS.md "Next". The one exception is `docs/architecture.md`, which is the agreed target design.
2. Source of truth: implementation → tests/measurements → docs. Never invent features, thresholds, latencies or behaviour.
3. Anything unverified says UNVERIFIED and how to check it (AGENTS.md "Facts").
4. One fact, one place. Link instead of copying.
5. Screening support, not diagnosis: never write accuracy or clinical claims beyond what was measured on held-out data.

## Workflow after a change

1. Read the diff; decide what actually changed.
2. Find the one doc in the table that owns it.
3. Edit only that section. Don't regenerate whole documents.
4. Move a STATUS.md item from "Next" to "Done" with how it was verified (e.g. "ran on demo phone via `connectedDebugAndroidTest`").
5. New measured number → STATUS.md "Verified facts" (and `docs/claims.md` if it goes on a slide).

## Style

- Bullets and tables over prose; paragraphs under 4 sentences.
- Numbered steps for procedures.
- Put the most important line first.
- Plain words. Short headings.

## Before the demo

Read the docs as a judge would:

- Can someone install and run the demo from `README.md` alone?
- Does every number in the deck appear in `docs/claims.md` with a source?
- Do the per-pack claims match the plan's honest wording ("phone-only for the lightweight modules, optional local hub for heavier ones")?
- Is anything stale or contradicting STATUS.md? Fix or delete it.
- Could any section be removed without losing useful information? Remove it.
