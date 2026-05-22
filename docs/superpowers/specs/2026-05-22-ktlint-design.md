# ktlint Integration Design

**Date:** 2026-05-22
**Project:** file-exchange-hub

## Goal

Introduce ktlint to enforce consistent Kotlin code style, with the build failing on violations and auto-fix available on demand.

## Approach

Use `com.github.gantsign.maven:ktlint-maven-plugin` — the standard Maven plugin for ktlint.

## Build Integration

- `check` goal bound to the `verify` phase: `./mvnw verify` enforces style as part of the existing CI gate (already used for integration tests).
- `format` goal available on demand: `./mvnw ktlint:format` auto-fixes all violations in place.

## Configuration

Add `.editorconfig` at the project root with ktlint defaults:
- 4-space indent
- No wildcard imports
- No trailing whitespace

The `.editorconfig` is also respected by IntelliJ IDEA and VS Code, keeping editor formatting consistent with the lint rules.

## One-Time Cleanup

Before the check gate is active, run `./mvnw ktlint:format` once to fix any pre-existing violations so the first `./mvnw verify` does not fail on legacy style issues.

## Commands After Setup

| Command | Purpose |
|---|---|
| `./mvnw verify` | Runs lint check as part of normal verify (fails on violations) |
| `./mvnw ktlint:format` | Auto-fixes all violations in place |
| `./mvnw ktlint:check` | Lint check only, without running tests |
