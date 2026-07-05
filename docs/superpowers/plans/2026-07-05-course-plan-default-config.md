# Course Plan Default Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the course-plan default rows out of frontend fallback code and into `system_config` initialization.

**Architecture:** `SystemInitRunner` remains the single startup seed path for global template configuration. The admin teaching-plan page reads only persisted config values; if a key is empty, it shows the empty state instead of silently creating frontend defaults.

**Tech Stack:** Kotlin, Spring Boot `CommandLineRunner`, MockK/JUnit tests, Thymeleaf/Alpine frontend.

---

### Task 1: Backend Seed Defaults

**Files:**
- Modify: `src/main/kotlin/com/example/reportsystem/config/SystemInitRunner.kt`
- Test: `src/test/kotlin/com/example/reportsystem/config/SystemInitRunnerTest.kt`

- [ ] Write a failing test that verifies `SystemInitRunner` saves `GLOBAL_COURSE_PLAN_DEFAULT` and `GLOBAL_COURSE_PLAN_NOTE_DEFAULT` when those keys are missing.
- [ ] Add seed constants and save calls to `SystemInitRunner`.
- [ ] Verify the new test passes.

### Task 2: Remove Frontend Fallback Rows

**Files:**
- Modify: `src/main/resources/templates/admin-teaching-plan.html`

- [ ] Remove hardcoded phase rows from the `coursePlansData ? JSON.parse(coursePlansData) : [...]` branch.
- [ ] Leave `defaultCoursePlans` empty when persisted config is missing or invalid.
- [ ] Keep the visible empty-state row and `+ 添加一行` behavior unchanged.

### Task 3: Verify

**Commands:**
- `./gradlew test --tests com.example.reportsystem.config.SystemInitRunnerTest`
- `./gradlew test`
- `git diff --check`

Expected result: tests pass, and frontend no longer has hidden default rows.
