package io.github.isynn.luxfeed.cooldown;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Cooldown tiers read from the {@code cooldowns} section of config.yml.
 *
 * <p>The tier named {@code default} applies to players matching no tier. When several tiers match,
 * the shortest duration wins.
 */
public final class CooldownSettings {

    public static final String DEFAULT_TIER_NAME = "default";
    public static final CooldownTier BUILT_IN_DEFAULT =
            new CooldownTier(DEFAULT_TIER_NAME, "luxfeed.cooldown.default", Duration.ofMinutes(60));

    private final CooldownTier defaultTier;
    private final List<CooldownTier> tiers;

    private CooldownSettings(CooldownTier defaultTier, List<CooldownTier> tiers) {
        this.defaultTier = defaultTier;
        this.tiers = List.copyOf(tiers);
    }

    /**
     * Reads and validates the cooldown tiers. Invalid tiers are skipped and an invalid or missing
     * {@code default} tier is replaced by {@link #BUILT_IN_DEFAULT}, each time with a warning.
     *
     * @param section the {@code cooldowns} section, or {@code null} if absent
     */
    public static CooldownSettings fromConfig(ConfigurationSection section, Logger logger) {
        if (section == null) {
            logger.warning("No 'cooldowns' section in config.yml, using a " + describe(BUILT_IN_DEFAULT)
                    + " default cooldown.");
            return new CooldownSettings(BUILT_IN_DEFAULT, List.of(BUILT_IN_DEFAULT));
        }

        CooldownTier defaultTier = null;
        List<CooldownTier> tiers = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            CooldownTier tier = parseTier(section, name, logger);
            if (tier == null) {
                continue;
            }
            tiers.add(tier);
            if (name.equals(DEFAULT_TIER_NAME)) {
                defaultTier = tier;
            }
        }

        if (defaultTier == null) {
            logger.warning("No valid 'cooldowns." + DEFAULT_TIER_NAME + "' tier in config.yml, using a "
                    + describe(BUILT_IN_DEFAULT) + " default cooldown.");
            defaultTier = BUILT_IN_DEFAULT;
            tiers.add(BUILT_IN_DEFAULT);
        }
        return new CooldownSettings(defaultTier, tiers);
    }

    /** Shortest cooldown among the tiers the player has the permission for, or the default one. */
    public Duration cooldownFor(Predicate<String> hasPermission) {
        return tiers.stream()
                .filter(tier -> hasPermission.test(tier.permission()))
                .map(CooldownTier::duration)
                .min(Duration::compareTo)
                .orElse(defaultTier.duration());
    }

    public List<CooldownTier> tiers() {
        return tiers;
    }

    private static CooldownTier parseTier(ConfigurationSection section, String name, Logger logger) {
        String path = "cooldowns." + name;
        ConfigurationSection tierSection = section.getConfigurationSection(name);
        if (tierSection == null) {
            logger.warning("Ignoring '" + path + "' in config.yml: expected 'permission' and 'duration-minutes'.");
            return null;
        }

        String permission = tierSection.getString("permission");
        if (permission == null || permission.isBlank()) {
            logger.warning("Ignoring '" + path + "' in config.yml: 'permission' is missing or empty.");
            return null;
        }

        Object minutes = tierSection.get("duration-minutes");
        if (!(minutes instanceof Integer value) || value < 0) {
            logger.warning("Ignoring '" + path + "' in config.yml: 'duration-minutes' must be a whole number"
                    + " from 0 to " + Integer.MAX_VALUE + ", got '" + minutes + "'.");
            return null;
        }
        return new CooldownTier(name, permission.trim(), Duration.ofMinutes(value));
    }

    private static String describe(CooldownTier tier) {
        return tier.duration().toMinutes() + "-minute";
    }
}
