# LuxFeed

A small Paper plugin adding `/feed`, which fully restores a player's hunger and saturation, limited
by a permission-based cooldown stored in SQLite.

## Compatibility and requirements

- Minecraft Java Edition 26.2, Paper 26.2 (built against `paper-api 26.2.build.135-stable`).
- Java 25 to run and to build. The Gradle wrapper is included.

## Build and install

```bash
./gradlew build
```

Copy `build/libs/LuxFeed-<version>.jar` into the server's `plugins/` folder and start the server.
LuxFeed creates `plugins/LuxFeed/` with `config.yml`, `lang/en.yml`, `lang/fr.yml` and `luxfeed.db`.
Existing files are never overwritten.

## Command and permissions

`/feed` works for players only. It feeds the player once all these checks pass, in this order:
permission, not in combat, cooldown over, use recorded in the database. Otherwise the player gets
the matching message and nothing changes.

| Permission | Default | Effect |
|------------|---------|--------|
| `luxfeed.feed` | everyone | Allows using `/feed`. |
| `luxfeed.cooldown.default` | everyone | `default` cooldown tier. |
| `luxfeed.cooldown.vip` / `.premium` / `.staff` | nobody | Example cooldown tiers. |

## Configuration

```yaml
language: "en"            # "en" or "fr"

cooldowns:
  default:
    permission: "luxfeed.cooldown.default"
    duration-minutes: 60
  vip:
    permission: "luxfeed.cooldown.vip"
    duration-minutes: 30
```

- Tiers are free to add or remove. A player holding several tier permissions gets the shortest
  duration; a player holding none gets the `default` duration.
- `duration-minutes` must be a whole number, `0` or more (`0` = no cooldown). Invalid tiers are
  ignored with a console warning; without a valid `default` tier, 60 minutes is used.
- Messages are edited in `lang/<language>.yml` (MiniMessage format). An unknown language falls back
  to English, and a missing or invalid message falls back to the text bundled in the plugin.

## SQLite

`luxfeed.db` holds one row per player: `player_uuid` and `last_used_at` (epoch milliseconds) of the
last successful `/feed`. The table is created if missing and existing data is kept, so cooldowns
survive disconnections, restarts and reloads.

All queries are prepared statements and run on a dedicated database thread, never the main thread.
That thread runs one task at a time, so `/feed` sent several times at once can only succeed once.
The use is recorded before the player is fed; if the player leaves or enters combat in between, it
is cancelled. If the database fails, `/feed` is refused with an error message and the error is logged.

The SQLite driver is not bundled: Paper 26.2 already provides `org.xerial:sqlite-jdbc` 3.49.1.0.

## Combat check

LuxFeed does **not** detect combat. `CombatCheck` is the interface a combat system must implement;
`/feed` is refused while it reports the player in combat. The bundled `StubCombatCheck` performs no
detection and always answers "not in combat", so the plugin works without a combat system. To
connect one, implement `CombatCheck` and pass it instead of `StubCombatCheck` in `LuxFeedPlugin`.

## Tests

`./gradlew test` runs the JUnit tests, which need no Minecraft server:

- `CooldownServiceTest`: default cooldown, shortest matching tier, invalid configuration, refusal
  before expiry, use after expiry, simultaneous attempts, database error.
- `SQLiteDatabaseTest`: timestamp persistence after reopening, error on an unusable file.
- `FeedCommandTest`: console refusal, combat refusal, feeding then cooldown refusal.
- `StubCombatCheckTest`: the stub never reports combat.
- `MessageServiceTest`: English and French messages, unknown language fallback, durations.
