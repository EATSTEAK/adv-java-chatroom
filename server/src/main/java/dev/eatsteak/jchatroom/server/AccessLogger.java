package dev.eatsteak.jchatroom.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

final class AccessLogger implements AutoCloseable {
    private final Path logFile;
    private final BufferedWriter writer;
    private boolean closed;

    AccessLogger(Path logFile) throws IOException {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        Path parent = logFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = Files.newBufferedWriter(
                logFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        );
    }

    Path logFile() {
        return logFile;
    }

    void info(String event, Object... details) {
        write("INFO", event, null, details);
    }

    void error(String event, Throwable failure, Object... details) {
        write("ERROR", event, failure, details);
    }

    private synchronized void write(String level, String event, Throwable failure, Object... details) {
        if (closed) {
            return;
        }
        try {
            writer.write(format(level, event, failure, details));
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            System.err.println("access log write failed: " + exception.getMessage());
        }
    }

    private String format(String level, String event, Throwable failure, Object... details) {
        if (details.length % 2 != 0) {
            throw new IllegalArgumentException("details must be key/value pairs");
        }

        StringBuilder line = new StringBuilder()
                .append("timestamp=").append(quote(Instant.now()))
                .append(" level=").append(quote(level))
                .append(" event=").append(quote(event));

        for (int i = 0; i < details.length; i += 2) {
            line.append(' ')
                    .append(key(details[i]))
                    .append('=')
                    .append(quote(details[i + 1]));
        }

        if (failure != null) {
            line.append(" exception=").append(quote(failure.getClass().getName()))
                    .append(" error=").append(quote(failure.getMessage()));
        }
        return line.toString();
    }

    private static String key(Object value) {
        String key = Objects.toString(value, "");
        if (key.isBlank()) {
            throw new IllegalArgumentException("detail key must not be blank");
        }
        return key;
    }

    private static String quote(Object value) {
        String raw = Objects.toString(value, "");
        StringBuilder escaped = new StringBuilder(raw.length() + 2);
        escaped.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(current)) {
                        escaped.append('?');
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.flush();
        } catch (IOException exception) {
            System.err.println("access log flush failed: " + exception.getMessage());
        }
        try {
            writer.close();
        } catch (IOException exception) {
            System.err.println("access log close failed: " + exception.getMessage());
        }
    }
}
