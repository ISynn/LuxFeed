package io.github.isynn.luxfeed.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessageServiceTest {

    @Test
    void resolvesEnglishAndFrenchMessages() {
        assertEquals("Only players can use this command.", plain(load("en").get("players-only")));
        assertEquals("Seuls les joueurs peuvent utiliser cette commande.", plain(load("fr").get("players-only")));
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        assertEquals("Only players can use this command.", plain(load("de").get("players-only")));
    }

    @Test
    void formatsDurationsInBothLanguages() {
        Duration duration = Duration.ofHours(1).plusMinutes(5).plusSeconds(3);

        assertEquals("1h 5m 3s", plain(load("en").duration(duration)));
        assertEquals("1 h 5 min 3 s", plain(load("fr").duration(duration)));
        assertEquals("1s", plain(load("en").duration(Duration.ofMillis(200))));
    }

    @Test
    void frenchFileDefinesEveryEnglishMessage() {
        YamlConfiguration english = bundled("en");
        YamlConfiguration french = bundled("fr");

        for (String key : english.getKeys(true)) {
            assertEquals(english.isString(key), french.isString(key), "fr.yml does not match en.yml for " + key);
        }
    }

    private static MessageService load(String language) {
        return new MessageService(language, new File("no-editable-files"),
                MessageServiceTest.class.getClassLoader()::getResourceAsStream, Logger.getAnonymousLogger());
    }

    private static YamlConfiguration bundled(String language) {
        InputStream input = MessageServiceTest.class.getClassLoader()
                .getResourceAsStream(MessageService.resourcePath(language));
        return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
