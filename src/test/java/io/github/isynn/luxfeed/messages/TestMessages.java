package io.github.isynn.luxfeed.messages;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Builds a {@link MessageService} from the language files bundled in the plugin resources. */
public final class TestMessages {

    private TestMessages() {
    }

    public static MessageService bundled(String language) {
        Logger silent = Logger.getAnonymousLogger();
        silent.setUseParentHandlers(false);
        return MessageService.load(language, Path.of("no-editable-language-files"),
                TestMessages.class.getClassLoader()::getResourceAsStream, silent);
    }
}
