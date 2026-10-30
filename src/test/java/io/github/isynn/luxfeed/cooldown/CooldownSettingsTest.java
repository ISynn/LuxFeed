package io.github.isynn.luxfeed.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.isynn.luxfeed.testing.LogCapture;
import java.time.Duration;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CooldownSettingsTest {

    private static final String EXAMPLE_CONFIG = """
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
            """;

    private final LogCapture log = new LogCapture();

    @Test
    void playerWithoutAnyTierPermissionGetsTheDefaultCooldown() {
        CooldownSettings settings = parse(EXAMPLE_CONFIG);

        assertEquals(Duration.ofMinutes(60), settings.cooldownFor(permissions()));
    }

    @Test
    void playerWithOnlyTheDefaultPermissionGetsTheDefaultCooldown() {
        CooldownSettings settings = parse(EXAMPLE_CONFIG);

        assertEquals(Duration.ofMinutes(60), settings.cooldownFor(permissions("luxfeed.cooldown.default")));
    }

    @Test
    void singleMatchingTierApplies() {
        CooldownSettings settings = parse(EXAMPLE_CONFIG);

        assertEquals(Duration.ofMinutes(30),
                settings.cooldownFor(permissions("luxfeed.cooldown.default", "luxfeed.cooldown.vip")));
    }

    @Test
    void shortestCooldownWinsWhenSeveralTiersMatch() {
        CooldownSettings settings = parse(EXAMPLE_CONFIG);

        assertEquals(Duration.ofMinutes(15), settings.cooldownFor(permissions(
                "luxfeed.cooldown.default", "luxfeed.cooldown.premium", "luxfeed.cooldown.vip")));
    }

    @Test
    void matchingTierLongerThanTheDefaultStillCompetesForTheShortest() {
        CooldownSettings settings = parse("""
                cooldowns:
                  default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
                  slow: {permission: "luxfeed.cooldown.slow", duration-minutes: 120}
                """);

        assertEquals(Duration.ofMinutes(60),
                settings.cooldownFor(permissions("luxfeed.cooldown.default", "luxfeed.cooldown.slow")));
        assertEquals(Duration.ofMinutes(120), settings.cooldownFor(permissions("luxfeed.cooldown.slow")));
    }

    @Test
    void zeroMinutesMeansNoCooldown() {
        CooldownSettings settings = parse("""
                cooldowns:
                  default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
                  staff: {permission: "luxfeed.cooldown.staff", duration-minutes: 0}
                """);

        assertEquals(Duration.ZERO, settings.cooldownFor(permissions("luxfeed.cooldown.staff")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "1.5", "\"30\"", "thirty", "99999999999", "~"})
    void invalidDurationIgnoresTheTierWithAWarning(String duration) {
        CooldownSettings settings = parse("""
                cooldowns:
                  default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
                  vip: {permission: "luxfeed.cooldown.vip", duration-minutes: %s}
                """.formatted(duration));

        assertEquals(Duration.ofMinutes(60), settings.cooldownFor(permissions("luxfeed.cooldown.vip")));
        assertTrue(log.contains("Ignoring 'cooldowns.vip'"));
    }

    @Test
    void tierWithoutPermissionIsIgnoredWithAWarning() {
        CooldownSettings settings = parse("""
                cooldowns:
                  default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
                  vip: {duration-minutes: 30}
                """);

        assertEquals(1, settings.tiers().size());
        assertTrue(log.contains("'permission' is missing"));
    }

    @Test
    void invalidDefaultTierIsReplacedByTheBuiltInDefault() {
        CooldownSettings settings = parse("""
                cooldowns:
                  default: {permission: "luxfeed.cooldown.default", duration-minutes: -1}
                  vip: {permission: "luxfeed.cooldown.vip", duration-minutes: 30}
                """);

        assertEquals(Duration.ofMinutes(60), settings.cooldownFor(permissions()));
        assertEquals(Duration.ofMinutes(30), settings.cooldownFor(permissions("luxfeed.cooldown.vip")));
        assertTrue(log.contains("No valid 'cooldowns.default' tier"));
    }

    @Test
    void missingSectionFallsBackToTheBuiltInDefault() {
        CooldownSettings settings = CooldownSettings.fromConfig(null, log.logger());

        assertEquals(CooldownSettings.BUILT_IN_DEFAULT.duration(), settings.cooldownFor(permissions()));
        assertTrue(log.contains("No 'cooldowns' section"));
    }

    @Test
    void tierThatIsNotASectionIsIgnored() {
        CooldownSettings settings = parse("""
                cooldowns:
                  default: {permission: "luxfeed.cooldown.default", duration-minutes: 60}
                  vip: 30
                """);

        assertEquals(1, settings.tiers().size());
        assertTrue(log.contains("Ignoring 'cooldowns.vip'"));
    }

    private CooldownSettings parse(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException(exception);
        }
        return CooldownSettings.fromConfig(configuration.getConfigurationSection("cooldowns"), log.logger());
    }

    private static Predicate<String> permissions(String... granted) {
        return Set.of(granted)::contains;
    }
}
