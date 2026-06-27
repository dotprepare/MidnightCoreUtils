# Contributing

Thank you for your interest in MidnightCoreUtils. This document outlines the development conventions and process.

## Development Setup

- **JDK:** Corretto 21 (`/home/chanakan/.jdks/corretto-21.0.11` or any JDK 21)
- **IDE:** IntelliJ IDEA with Kotlin plugin 2.0.0+
- **Gradle:** 8.8 (wrapper included)

```bash
git clone <repo-url>
cd MidnightCoreUtils
./gradlew build
```

## Project Structure

```
MidnightCoreUtils/
├── utils-api/                          # Pure Kotlin library (no Minecraft)
│   ├── src/main/kotlin/.../api/        # 10 systems
│   │   ├── config/                     # SharedConfig, ConfigValue, ConfigSerializer
│   │   ├── debug/                      # DebugInspector
│   │   ├── error/                      # ErrorBoundary, ServiceUnavailableEvent
│   │   ├── event/                      # ModEventBus, ModEvent, EventPriority, etc.
│   │   ├── lifecycle/                  # ModLifecycleHooks, LifecyclePhase
│   │   ├── network/                    # PacketSync, NbtPayload, RateLimiter, etc.
│   │   ├── registry/                   # SharedRegistry, RegistryEntry, RegistryKey, etc.
│   │   ├── scheduler/                  # TickScheduler, TickPhase
│   │   ├── service/                    # ServiceLocator, CircularDependencyException
│   │   └── util/                       # ThreadSafe helpers (withReadLock, concurrentHashMap, etc.)
│   └── src/test/kotlin/.../api/        # JUnit 5 + MockK tests
├── midnightcoreutils/                  # NeoForge mod bridge
│   └── src/main/kotlin/.../
│       ├── MidnightCoreUtilsMod.kt     # @Mod entry point
│       ├── bridge/                     # CoreSystems, NBTUtils, Forge bridges
│       └── bridge/                     # CoreSystems, NBTUtils, Forge bridges
├── build.gradle.kts                    # Root build (Kotlin plugin declaration)
├── settings.gradle.kts                 # Multi-module includes
└── gradle.properties                   # JDK 21, NeoForge 21.1.219, Kotlin 2.0.0
```

## Coding Conventions

- **Kotlin:** Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Naming:** Classes/types → PascalCase. Functions/properties → camelCase. Constants → SCREAMING_SNAKE_CASE.
- **Indentation:** 4 spaces. No tabs.
- **Null safety:** Prefer nullable types (`Type?`) over `null` assertions (`!!`). Use `require()` and `check()` for invariants.
- **Thread safety:** Every new public API must document its thread-safety guarantees. Use `ReentrantReadWriteLock` for read-heavy concurrent access, `ConcurrentHashMap`/`CopyOnWriteArrayList` for lock-free patterns.
- **Imports:** No wildcard imports. Configure IntelliJ: `Settings → Editor → Code Style → Kotlin → Imports → "Use single name import"`.
- **Tests:** Write tests for all new public methods. Use `@Test`, `@Timeout`, and meaningful test names (e.g., `"task fires after delay"`).

## Adding a New System

1. Create a new package under `utils-api/src/main/kotlin/.../api/`.
2. Implement all logic there — zero Minecraft imports.
3. Write tests in `utils-api/src/test/kotlin/.../api/`.
4. Wire it into `CoreSystems.kt` in the bridge module.
5. Add a `CoreSystems.onModUnload()` cleanup call if the system holds per-mod state.
6. Update the README.

## Testing

```bash
# Run all API tests
./gradlew :utils-api:test

# Run a single test class
./gradlew :utils-api:test --tests "*ModEventBusTest*"

# Run tests with detailed output
./gradlew :utils-api:test -i
```

All tests must pass before merging. The test suite uses:
- **JUnit 5** (`org.junit.jupiter`)
- **MockK** for mocking
- **kotlin.test** for assertions (`assertThrows`, `assertEquals`, etc.)
- No NeoForge or Minecraft dependencies in API tests

## Pull Request Process

1. Create a feature branch from `main`.
2. Make your changes, including tests.
3. Run `./gradlew :utils-api:test` — all tests must pass.
4. Run `./gradlew :utils-api:compileKotlin :midnightcoreutils:compileKotlin` — zero warnings.
5. Submit a PR with a clear description of the change and its motivation.

## Code of Conduct

Be respectful, constructive, and inclusive. This is a small open-source project — we're all here to learn and build cool things together.
