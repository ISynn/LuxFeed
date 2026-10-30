package io.github.isynn.luxfeed.messages;

/**
 * Every player-facing text of the plugin, mapped to its path in the language files.
 */
public enum Message {
    COMMAND_DESCRIPTION("command-description"),
    FEED_SUCCESS("feed.success"),
    FEED_COOLDOWN("feed.cooldown"),
    FEED_IN_COMBAT("feed.in-combat"),
    FEED_ERROR("feed.error"),
    NO_PERMISSION("no-permission"),
    PLAYERS_ONLY("players-only"),
    TIME_HOURS("time.hours"),
    TIME_MINUTES("time.minutes"),
    TIME_SECONDS("time.seconds");

    private final String path;

    Message(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
