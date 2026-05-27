<!--
  Title format: <type>(<scope>): <concise, imperative summary>
  Examples:
    feat(billing): add per-member usage breakdown to analytics dashboard
    fix(auth): prevent session token leak on refresh failure
    refactor(core): extract MongoDB repository base class

  Keep the title under 70 characters. Use the description for everything else.
-->

## Summary

<!-- What changed and why, in 1–3 sentences. Lead with the user-visible or system-level impact, not the implementation detail. -->

## Motivation / Context

<!-- Why is this change needed now? Link the issue, ticket, incident, or design doc that prompted it. -->

- Closes #
- Related: 

## Changes

<!-- Bulleted list of the notable changes. Group by area if the PR touches several modules. -->

- 
- 

## Implementation Notes

<!-- Anything a reviewer needs to know to read the diff efficiently: trade-offs considered, alternatives rejected, non-obvious decisions, follow-ups intentionally deferred. Delete the section if there's nothing to add. -->

## Testing

<!-- How did you verify this works? Be specific — commands run, scenarios exercised, edge cases checked. -->

- [ ] Unit tests added / updated
- [ ] Integration tests added / updated
- [ ] Manually verified against local/staging environment
- [ ] N/A — explain:

## Screenshots / Recordings

<!-- For UI or API-response changes. Before/after preferred. Delete if not applicable. -->

## Risk & Rollout

<!-- Blast radius of this change. Anything reviewers or on-call should watch after merge. -->

- **Risk level:** low / medium / high
- **Rollback plan:** 
- **Feature flag / gate:** 
- **Migrations or data backfills:** 

## Checklist

- [ ] Title follows `<type>(<scope>): <summary>` and is under 70 chars
- [ ] Scope is minimal — unrelated changes split into separate PRs
- [ ] Public APIs, configs, and env vars are documented
- [ ] No secrets, credentials, or PII added to the repo
- [ ] Logs and error messages are actionable and free of sensitive data
- [ ] Breaking changes called out above with a migration note
