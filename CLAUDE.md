# CLAUDE.md — DevPulse Plugin

## Project Overview

DevPulse is a **source-available IntelliJ IDEA plugin** that tracks developer productivity metrics inside the IDE:
- **Focus time** per file/class — accumulated in 1-second ticks while a file is active and the developer is not idle
- **Edit activity** — broken down into typed (≤2 chars), inserted (3+ chars), and pasted characters
- **Pomodoro sessions** — configurable work/break durations with auto-start support

Target platform: IntelliJ IDEA 2025.2+. Built with IntelliJ Platform Gradle Plugin v2.16.0, Kotlin 2.4.0.

The production UI is **Swing** (not Compose). The `src/composePreview/` source set is a standalone design prototype — it is never included in the shipped plugin JAR.

---

## Architecture

```
com.github.filiplabs.devpulse
├── model/          — Pure data types (DayStats, EditEvent, EditType, FocusStatus, PomodoroSnapshot, PomodoroState)
├── settings/       — App-level persistent settings (DevPulseSettingsService, DevPulseSettingsState, DevPulseMemoryMode)
├── storage/        — Project-level stats accumulation + XML persistence (DevPulseStatsService)
├── services/       — Runtime services: Pomodoro timer (DevPulsePomodoroService), parent disposable (DevPulseProjectLifecycleService)
├── startup/        — Plugin initialization (DevPulseStartupActivity implements ProjectActivity)
├── tracking/       — IDE event listeners: ActiveFileTracker, DevPulseFocusTracker, DevPulseDocumentChangeTracker, PasteActionTracker, EditClassifier
├── toolWindow/     — Swing UI: DevPulseDashboardPanel (~1080 lines), DevPulseSettingsDialog, DevPulseToolWindowFactory
└── ui/preview/     — Compose prototype (composePreview source set — not shipped)
```

### Data flow

```
IDE events
    ├── FileEditorManager ──────────────────────► ActiveFileTracker (tracks open file path, records activity)
    ├── AnActionListener ───────────────────────► PasteActionTracker (sets paste/non-writing flags)
    └── DocumentListener.documentChanged ──────► DevPulseDocumentChangeTracker
                                                      └── EditClassifier
                                                            └── DevPulseStatsService.recordEditEvent()

AppScheduledExecutorService (1s tick)
    ├── DevPulseFocusTracker.tick()
    │       reads: ActiveFileTracker.activeFilePath + DevPulseStatsService.isIdle()
    │       writes: DevPulseStatsService.addFocusSeconds()
    └── DevPulsePomodoroService.tick()
            state machine: IDLE → WORK → BREAK → (WORK | IDLE)
            writes: DevPulseStatsService.incrementCompletedPomodoroSessions()

Swing Timer (1s, EDT)
    └── DevPulseDashboardPanel.refreshUi()
            reads: DevPulseStatsService, DevPulsePomodoroService, ActiveFileTracker, DevPulseFocusTracker
```

### Service registration

All services use the `@Service` annotation (light services). **None are declared in `plugin.xml`** — the IntelliJ Platform 2025.2 auto-discovers them from the annotation. Only `toolWindow` and `postStartupActivity` appear in `plugin.xml`.

### Lifecycle / Disposable chain

`DevPulseProjectLifecycleService` is the project-level parent `Disposable`. All message bus connections (in `ActiveFileTracker`, `PasteActionTracker`, `DevPulseDocumentChangeTracker`) are scoped to it. `DevPulseFocusTracker` and `DevPulsePomodoroService` implement `Disposable` directly and cancel their `ScheduledFuture` on disposal.

---

## Build & Run

```bash
# Run in sandboxed IDEA (recommended for development)
./gradlew runIde

# Build plugin ZIP for distribution
./gradlew buildPlugin

# Run all tests
./gradlew test

# Full check (tests + composePreview compilation)
./gradlew check

# Verify plugin descriptor compliance
./gradlew verifyPlugin
```

All version pins are in `gradle.properties`. No `libs.versions.toml` — versions are split between `gradle.properties` (runtime/runtime-test) and `settings.gradle.kts` (plugin management).

---

## Code Conventions

**Threading model**: no coroutines. Background work uses `AppExecutorUtil.getAppScheduledExecutorService()` for 1-second tick loops. Shared mutable state in services is guarded by `synchronized(lock)`. Single-field reads that don't need to be atomic (e.g. `lastEditorActivityAtMillis`) use `@Volatile`.

**Kotlin 2.4 feature in use**: `PasteActionTracker.kt` uses multi-dollar string literals (`$$"$Paste"`, `$$"$Undo"`, `$$"$Redo"`). With the `$$` prefix, the interpolation delimiter inside the string becomes `$$`, so a single `$` is treated as a literal character. This produces the literal strings `$Paste`, `$Undo`, `$Redo`, which are real IntelliJ action IDs. This is intentional and compiles without any opt-in flag in Kotlin 2.4.

**Settings state serialization**: `DevPulseSettingsState` uses `var` properties (not `val`) because `XmlSerializerUtil.copyBean()` requires mutable setters on the target object. `FocusEntryState` uses an explicit no-arg primary constructor for the same reason (JAXB-style XML deserialization).

**Enum toString**: `DevPulseMemoryMode` overrides `toString()` to return human-readable labels. This is how the enum displays in the `ComboBox` in `DevPulseSettingsDialog`.

**No `!!(non-null assertion)` except one justified case**: `DevPulsePomodoroService.kt:109` uses `tickerFuture!!.isCancelled` inside `synchronized(lock)` immediately after a `!= null` check — safe because the lock prevents concurrent nullification.

---

## Key Files & Entry Points

| Task | Start here |
|------|-----------|
| Understanding init order | `DevPulseStartupActivity.kt` |
| Adding a new tracked metric | `DevPulseStatsService.kt` + `DayStats.kt` |
| Changing edit classification logic | `EditClassifier.kt` |
| Modifying the dashboard UI | `DevPulseDashboardPanel.kt` (large — use Find in File to navigate sections) |
| Adding a new setting | `DevPulseSettingsState.kt` → `DevPulseSettingsService.kt` → `DevPulseSettingsDialog.kt` |
| Understanding the Pomodoro state machine | `DevPulsePomodoroService.tick()` |
| Plugin descriptor / feature flags | `src/main/resources/META-INF/plugin.xml` |

---

## Plugin-Specific Gotchas

1. **Multi-dollar strings** (`PasteActionTracker.kt:110-114`): `$$"$Paste"` is valid Kotlin 2.4 — it produces the literal string `"$Paste"`. Do not "fix" these to `"$Paste"` (which would try to interpolate the variable `Paste`).

2. **Double reset on first tool window open** (known bug): When `memoryMode == RESET_WHEN_TOOL_WINDOW_OPENS`, `DevPulseToolWindowFactory.createToolWindowContent` resets stats on line 26, and then `DevPulseDashboardPanel.installToolWindowOpenResetHandler` also resets stats when the panel first becomes visible. The fix is to remove the reset from the factory (`DevPulseToolWindowFactory.kt:25-27`) since the hierarchy listener handles it.

3. **Two independent 1-second tick loops**: The focus tracker and the Pomodoro timer both run on `AppScheduledExecutorService` with 1-second intervals. They are independent — they may fire in any order. Both write to `DevPulseStatsService` under `synchronized(lock)`, so there is no data race.

4. **Idle detection reads outside the lock**: `DevPulseStatsService.isIdle()` reads `lastEditorActivityAtMillis` directly (it's `@Volatile`, not under `lock`). This is intentional — an occasional stale read is acceptable for idle detection purposes.

5. **`service<DevPulseSettingsService>()` in property initializers**: `DevPulseStatsService` and `DevPulsePomodoroService` call `service<DevPulseSettingsService>()` (app-level service, no project) as property initializers. This is safe because the platform guarantees app-level services are ready before project-level service constructors run.

6. **`DevPulseDashboardPanel.refreshUi()` guard**: the method redirects to EDT via `SwingUtilities.invokeLater` if not on EDT. The Swing `Timer` already fires on EDT, and all button listeners fire on EDT, so this guard is defensive but never actually triggered in normal operation.

7. **`toggle()` is not atomic** (`DevPulsePomodoroService.kt:77-83`): reads state with `getSnapshot()` (acquires+releases lock), then calls `start()` or `stop()` (acquires lock again). A TOCTOU window exists between the two lock acquisitions. In practice, `toggle()` is only called from the EDT (button listener), so concurrent callers are not possible.

8. **Log flood** (known bug): `DevPulseStatsService.addFocusSeconds()` uses `logger.info()` — this fires every second when tracking is ACTIVE. Change to `logger.debug()`.

---

## Testing

Tests are in `src/test/kotlin/com/github/filiplabs/devpulse/`. Run with `./gradlew test`.

| Test file | What it covers |
|-----------|---------------|
| `DevPulsePluginDescriptorTest` | plugin.xml registrations; class loadability via `Class.forName` |
| `DevPulseSettingsServiceTest` | Default values; clamping of out-of-range loaded values; minutes→seconds conversion |
| `EditClassifierTest` | TYPED / PASTED / INSERTED classification; `shouldIgnore` logic |

**Untested**: `DevPulsePomodoroService` (state machine, tick countdown, long break cycle), `DevPulseStatsService` (daily rollover, concurrent access, persistence), `DevPulseFocusTracker` (status transitions), `DevPulseDocumentChangeTracker` (end-to-end edit flow), all UI code.

The tests do NOT use `BasePlatformTestCase` — they are plain JUnit 4 tests with no IntelliJ test infrastructure. Services that depend on the platform (e.g. `DevPulsePomodoroService`) cannot be unit-tested without a sandboxed IDEA or mocking the platform.

---

## Dependencies of Note

| Dependency | Role |
|-----------|------|
| `org.jetbrains.intellij.platform` 2.16.0 | IntelliJ Platform Gradle Plugin v2 (auto-discovers light services, handles instrumentation) |
| IntelliJ IDEA 2025.2.6.2 | Host IDE — provides `FileEditorManager`, `AnActionListener`, `DocumentListener`, `AppExecutorUtil`, Swing UI toolkit wrappers (`JBUI`, `JBColor`, `JBFont`, etc.) |
| Compose Multiplatform 1.10.0 | Design prototype only (`composePreview` source set, excluded from plugin ZIP) |
| `compose.runtime` (`compileOnly`) | Compile-time only on main source set — prevents classpath conflicts without pulling Compose into the shipped JAR |
| JUnit 4.13.2 | Test framework — aligned with IntelliJ Platform test infrastructure (which uses JUnit 4, not 5) |

No external Maven Central runtime dependencies. `kotlin.stdlib.default.dependency = false` prevents bundling stdlib (the host IDE provides it).

---

## Known Issues / Tech Debt

| Priority | Location | Issue |
|----------|----------|-------|
| 🔴 | `DevPulseStatsService.kt:129` | `logger.info()` inside `addFocusSeconds()` fires every second — floods `idea.log`. Change to `logger.debug()`. |
| 🟡 | `DevPulseToolWindowFactory.kt:25-27` | Double reset on first tool window open (see gotcha #2). Remove the reset from the factory. |
| 🟡 | `DevPulseSettingsDialog.kt:68` | `DevPulseMemoryMode.values()` is deprecated since Kotlin 1.9. Use `DevPulseMemoryMode.entries.toTypedArray()`. |
| 🟡 | `DevPulseDocumentChangeTracker.kt:48-57` | `isNonWritingActionInProgress()` read 3 times in one method. Capture with `val isNonWriting = ...` to avoid TOCTOU and triple AtomicBoolean reads. |
| 🟡 | `DevPulsePomodoroService.kt:77-83` | `toggle()` is not atomic (TOCTOU between state read and action). Low risk (EDT-only). |
| 🟡 | `DevPulseDocumentChangeTracker.kt:53-57` | Operator precedence bug in log message: the `, offset=...` suffix is only appended to the `else` branch, so the `IGNORED_ACTION` case loses offset info in the log. Minor (log-only). |
| 🟡 | `DevPulsePomodoroService.kt:56-63` | `stop()` logs "IDLE" unconditionally even if state was already IDLE. `start()` uses a `shouldLog` guard. Inconsistent. |
| 🟢 | `DevPulseDashboardPanel.kt` | 1080-line god class — UI sections (header, focus card, pomodoro card, etc.) could be extracted into separate inner classes or files. |
| 🟢 | `DevPulseDashboardPanel.kt:220-222` | `createPulseLineComponent()` is a one-liner that just returns `headerPulseLine`. Can be inlined. Same for `createStatusIndicator()` on line 421. |
| 🟢 | `DevPulseStatsService.kt:213-223` | `FocusEntryState` secondary constructor can be replaced with default parameter values: `class FocusEntryState(var file: String = "", var seconds: Long = 0L)`. |
| 🟢 | Build | No `libs.versions.toml` — versions scattered between `gradle.properties` and `settings.gradle.kts`. |
| 🟢 | `plugin.xml` | Missing `<version>` tag (version is in `gradle.properties` but not reflected in the descriptor). |
| 🟢 | Tests | No coverage for Pomodoro timer logic, stats rollover, concurrent access, focus tracking, or any UI behaviour. |
