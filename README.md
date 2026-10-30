# LuxFeed

LuxFeed is a small Paper plugin that adds a single command, `/feed`, which fully restores a player's
hunger and saturation. Uses are limited by a cooldown that depends on the player's permissions and
is stored in SQLite, so it survives disconnections and server restarts.

## Features

- `/feed` restores hunger (food level 20) and saturation (20) and resets exhaustion.
- Permission-based cooldowns configured in `config.yml`. When several tiers match, the shortest applies.
- Cooldowns stored in a local SQLite database, keyed by player UUID.
- English and French messages, editable in language files.
- A `CombatCheck` interface that can refuse `/feed` during combat. LuxFeed ships only a stub
  implementation; see [Combat check](#combat-check).

## Compatibility

| Component | Version |
|-----------|---------|
| Minecraft Java Edition | 26.2 |
| Server | Paper 26.2 (developed and tested against build 135) |
| Java | 25 |

The plugin is compiled against `io.papermc.paper:paper-api:26.2.build.135-stable`.

## Requirements

- To run: a Paper 26.2 server running on Java 25.
- To build: JDK 25. The Gradle wrapper is included, so Gradle does not need to be installed.
  The build uses a Java 25 toolchain; if Gradle does not find your JDK 25 automatically, set
  `org.gradle.java.installations.paths` in your user `gradle.properties`.

## Building

```bash
./gradlew build
```

On Windows, use `gradlew.bat build`. The build compiles the plugin, runs the tests and writes the
jar to `build/libs/LuxFeed-<version>.jar`.

## Installation

1. Copy `LuxFeed-<version>.jar` into the server's `plugins/` folder.
2. Start the server. LuxFeed creates `plugins/LuxFeed/` with `config.yml`, `lang/en.yml`,
   `lang/fr.yml` and the `luxfeed.db` database.
3. Adjust the configuration if needed and restart the server.

Existing configuration and language files are never overwritten.

## Command

| Command | Description | Permission |
|---------|-------------|------------|
| `/feed` | Fully restores your hunger and saturation. | `luxfeed.feed` |

`/feed` only works for players; running it from the console prints a message and does nothing else.
The checks run in this order:

1. The player has `luxfeed.feed`.
2. The combat check does not report the player in combat.
3. The player's cooldown is over.
4. The use is successfully written to the database.

The player is fed only after all four succeed. If any fails, the player gets the matching message
and nothing changes.

## Permissions

| Permission | Default | Effect |
|------------|---------|--------|
| `luxfeed.feed` | everyone | Allows using `/feed`. |
| `luxfeed.cooldown.default` | everyone | Applies the `default` cooldown tier. |
| `luxfeed.cooldown.vip` | nobody | Applies the `vip` cooldown tier. |
| `luxfeed.cooldown.premium` | nobody | Applies the `premium` cooldown tier. |
| `luxfeed.cooldown.staff` | nobody | Applies the `staff` cooldown tier. |

The tier permissions above match the bundled `config.yml` and are examples. Permissions of tiers
added to `config.yml` are registered at startup with a default of `false`, so operators do not
receive them implicitly.

## Cooldown configuration

```yaml
cooldowns:
  default:
    permission: "luxfeed.cooldown.default"
    duration-minutes: 60
  vip:
    permission: "luxfeed.cooldown.vip"
    duration-minutes: 30
  premium:
    permission: "luxfeed.cooldown.premium"
    duration-minutes: 15
  staff:
    permission: "luxfeed.cooldown.staff"
    duration-minutes: 5
```

- Each tier has a `permission` and a `duration-minutes`. Tier names are free; `default` is special.
- A player holding several tier permissions gets the **shortest** duration among them.
- A player holding none of them gets the `default` tier's duration.
- `duration-minutes` must be a whole number from `0` to `2147483647`; `0` disables the cooldown.

Invalid values never stop the plugin. They are handled at startup, with a warning in the console:

| Problem | Result |
|---------|--------|
| A tier has a negative, decimal, text or missing duration | The tier is ignored. |
| A tier has no `permission` | The tier is ignored. |
| The `default` tier is missing or invalid | A built-in default of 60 minutes is used. |
| The `cooldowns` section is missing | A built-in default of 60 minutes is used. |

The remaining time is shown rounded up to the next second, for example `42m 10s`.

## Language configuration

```yaml
language: "en"
```

Supported values are `en` (English, the default) and `fr` (French). The messages live in
`plugins/LuxFeed/lang/en.yml` and `plugins/LuxFeed/lang/fr.yml` and use the
[MiniMessage](https://docs.papermc.io/adventure/minimessage/) format. Placeholders:
`<remaining>` in the cooldown message and `<amount>` in the time units.

Fallback behaviour, each case logged as a warning:

- An unsupported language code falls back to English.
- A missing or invalid language file falls back to the messages bundled in the jar.
- A message missing from an edited file falls back to the bundled message of the same language,
  then to the bundled English message.

## SQLite storage

Uses are stored in `plugins/LuxFeed/luxfeed.db`, in a single table:

```sql
CREATE TABLE IF NOT EXISTS feed_usage (
    player_uuid  TEXT    NOT NULL PRIMARY KEY,
    last_used_at INTEGER NOT NULL  -- epoch milliseconds of the last successful /feed
)
```

- The file and table are created on startup if they do not exist; existing data is kept.
- Every query is a prepared statement and every JDBC resource is closed with try-with-resources.
- Database work runs on a dedicated single thread, never on the server main thread. Because this
  thread runs one task at a time, checking the cooldown and recording the use form one step:
  several `/feed` sent at once cannot all succeed.
- The use is written **before** the player is fed. If the player disconnects or enters combat
  before the result comes back to the main thread, the use is cancelled and the previous
  timestamp is restored.
- If the database cannot be opened or written, `/feed` is refused with an error message and the
  error is logged. The connection is retried on the next use, so a temporary problem does not
  require a restart.
- When the plugin is disabled, pending database tasks are given up to 10 seconds to finish, then
  the connection is closed. Since cooldowns are on disk, they survive restarts and reloads.

The SQLite JDBC driver (`org.xerial:sqlite-jdbc` 3.49.1.0) is not bundled: Paper 26.2 already
provides it to plugins, as listed in the server jar's `META-INF/libraries.list`.

## Combat check

LuxFeed does **not** detect combat. The `CombatCheck` interface defines the contract:

```java
@FunctionalInterface
public interface CombatCheck {
    boolean isInCombat(Player player);
}
```

The bundled `StubCombatCheck` performs no detection and **always reports that the player is not in
combat**. With it, `/feed` is never refused for combat reasons. It exists so the plugin can run and
be tested without an external combat system.

To connect a real combat system, implement `CombatCheck` by delegating to that system's API and
pass your implementation instead of `StubCombatCheck` in `LuxFeedPlugin#onEnable`. The check is
called on the main thread, before the cooldown is checked and again just before the player is fed.

## Tests

```bash
./gradlew test
```

The tests use JUnit 6 and Mockito and do not need a running server. SQLite tests use real database
files in temporary folders.

| Test class | Covers |
|------------|--------|
| `CooldownSettingsTest` | Default cooldown, shortest matching tier, `0` minutes, rejection of invalid tiers and fallback default. |
| `CooldownServiceTest` | Refusal before expiry with the exact remaining time, acceptance at expiry, persistence across a database restart, cancelling a use, simultaneous attempts granting a single use, database failure. |
| `SQLiteDatabaseTest` | Database creation, storing and replacing timestamps, persistence after reopening, errors on unusable files and after closing. |
| `FeedCommandTest` | Console refusal, permission, combat refusal, feeding, cooldown messages, database errors, player leaving or entering combat while the use is being stored. |
| `StubCombatCheckTest` | The stub never reports combat and does not inspect the player. |
| `MessageServiceTest` | English and French messages, placeholders, duration formatting, unknown languages, edited, invalid and missing language files, every message present in both languages. |

`FeedCommandTest` uses Mockito mocks of `Player` and `CommandSourceStack` and runs database tasks
on the calling thread. Thread hopping between the main thread and the database thread, and the
plugin lifecycle, are not covered by automated tests; they require a running Paper server.

## Project layout

```text
src/main/java/io/github/isynn/luxfeed/
├── LuxFeedPlugin.java          plugin startup and shutdown
├── command/FeedCommand.java    /feed and the order of checks
├── cooldown/                   cooldown tiers, cooldown checks and their outcome
├── database/SQLiteDatabase.java
├── combat/                     CombatCheck and StubCombatCheck
└── messages/                   message keys and language loading
```
