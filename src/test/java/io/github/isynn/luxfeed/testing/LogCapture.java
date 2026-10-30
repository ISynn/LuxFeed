package io.github.isynn.luxfeed.testing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** A logger that keeps its messages in memory instead of printing them. */
public final class LogCapture {

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    public LogCapture() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    public Logger logger() {
        return logger;
    }

    public boolean contains(String fragment) {
        return messages.stream().anyMatch(message -> message.contains(fragment));
    }
}
