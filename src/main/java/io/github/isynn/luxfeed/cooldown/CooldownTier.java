package io.github.isynn.luxfeed.cooldown;

import java.time.Duration;

/**
 * A cooldown applied to players holding {@code permission}.
 *
 * @param name key of the tier in config.yml, used in log messages
 */
public record CooldownTier(String name, String permission, Duration duration) {
}
