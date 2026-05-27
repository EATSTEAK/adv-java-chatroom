package dev.eatsteak.jchatroom.server;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record ServerConfig(
        int port,
        int reactorThreads,
        int businessThreads,
        int maxClients,
        int outboundQueueCapacity,
        int idleTimeoutSeconds,
        Path accessLogFile
) {
    static final int DEFAULT_PORT = 5000;
    static final int DEFAULT_BUSINESS_THREADS = 32;
    static final int DEFAULT_MAX_CLIENTS = 1024;
    static final int DEFAULT_OUTBOUND_QUEUE_CAPACITY = 100;
    static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 600;
    static final Path DEFAULT_ACCESS_LOG_FILE = Path.of("logs", "access.log");

    public ServerConfig {
        validatePort(port);
        requirePositive("reactorThreads", reactorThreads);
        requirePositive("businessThreads", businessThreads);
        requirePositive("maxClients", maxClients);
        requirePositive("outboundQueueCapacity", outboundQueueCapacity);
        requirePositive("idleTimeoutSeconds", idleTimeoutSeconds);
        Objects.requireNonNull(accessLogFile, "accessLogFile");
    }

    public ServerConfig(
            int port,
            int reactorThreads,
            int businessThreads,
            int maxClients,
            int outboundQueueCapacity,
            int idleTimeoutSeconds
    ) {
        this(
                port,
                reactorThreads,
                businessThreads,
                maxClients,
                outboundQueueCapacity,
                idleTimeoutSeconds,
                DEFAULT_ACCESS_LOG_FILE
        );
    }

    public static ServerConfig defaults() {
        return new ServerConfig(
                DEFAULT_PORT,
                defaultReactorThreads(),
                DEFAULT_BUSINESS_THREADS,
                DEFAULT_MAX_CLIENTS,
                DEFAULT_OUTBOUND_QUEUE_CAPACITY,
                DEFAULT_IDLE_TIMEOUT_SECONDS,
                DEFAULT_ACCESS_LOG_FILE
        );
    }

    public static ServerConfig fromArgs(String... args) {
        ServerConfig defaults = defaults();
        int port = defaults.port();
        int reactorThreads = defaults.reactorThreads();
        int businessThreads = defaults.businessThreads();
        int maxClients = defaults.maxClients();
        int outboundQueueCapacity = defaults.outboundQueueCapacity();
        int idleTimeoutSeconds = defaults.idleTimeoutSeconds();
        Path accessLogFile = defaults.accessLogFile();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }

            String flag;
            String value;
            int separator = arg.indexOf('=');
            if (separator >= 0) {
                flag = arg.substring(2, separator);
                value = arg.substring(separator + 1);
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Missing value for --" + flag);
                }
            } else {
                flag = arg.substring(2);
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --" + flag);
                }
                value = args[++i];
            }

            switch (normalizeFlag(flag)) {
                case "port" -> port = parseInteger(flag, value);
                case "reactor-threads" -> reactorThreads = parseInteger(flag, value);
                case "business-threads" -> businessThreads = parseInteger(flag, value);
                case "max-clients" -> maxClients = parseInteger(flag, value);
                case "queue-capacity" -> outboundQueueCapacity = parseInteger(flag, value);
                case "idle-timeout-seconds" -> idleTimeoutSeconds = parseInteger(flag, value);
                case "log-file" -> accessLogFile = parsePath(flag, value);
                default -> throw new IllegalArgumentException("Unknown option: --" + flag);
            }
        }

        return new ServerConfig(
                port,
                reactorThreads,
                businessThreads,
                maxClients,
                outboundQueueCapacity,
                idleTimeoutSeconds,
                accessLogFile
        );
    }

    private static int defaultReactorThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private static int parseInteger(String flag, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for --" + flag + ": " + value, exception);
        }
    }

    private static String normalizeFlag(String flag) {
        return flag.toLowerCase(Locale.ROOT);
    }

    private static Path parsePath(String flag, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing value for --" + flag);
        }
        return Path.of(value);
    }

    private static void validatePort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
    }

    private static void requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
