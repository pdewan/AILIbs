# Project working instructions

## Preserve agreed design; no shortcuts

- Treat agreed design constraints as acceptance criteria. Do not bypass, weaken, or replace them to make implementation easier, obtain passing tests, or make the UI appear complete.
- Before implementing a fix, identify the underlying requirement and verify that the proposed change satisfies it. If a change requires departing from an agreed design, explain the conflict and obtain explicit agreement first; do not treat silence as approval.
- Add a prerequisite only when the dependent check genuinely needs the evidence or correctness established by that prerequisite. State that dependency concretely. Do not add dependencies merely to trigger execution or increase coverage.
- Expected-input checks run only their genuine prerequisite chains, consistently with A1. Independent regular and extra-credit checks remain independently selectable through their own suites or the full assignment suite. An unrun independent check is not a defect.
- Do not add run-everything helpers, zero-point execution entries, or equivalent side effects to bypass prerequisite design. Verify both required dependencies and independence from unrelated failures; do not substitute full-suite success for verification of the requested entry point.

- No fallbacks in any code written. Require explicit configuration and report missing or invalid inputs; do not silently substitute behavior or select an assignment.
- No magic numbers or strings. Use authoritative named definitions for meaningful file names, protocol values, settings, and behavioral constants; keep independent test expectations independent.
- Put shared code in shared packages. Do not duplicate tests or retain obsolete wrappers to accommodate assignment-specific configuration.
- Inspect related definitions and consumers before edits, reuse existing definitions, review touched code for duplication, and run relevant checks. Keep changes within the requested scope.

## Grading JAR builds

For building and publishing grading JARs, follow D:\dewan_backup\Java\EduCopilot\docs\Grading-Jar-Build.md. It records the dependency order, artifact-only verification, and agreed desktop/shared Downloads destinations.
