package dev.eatsteak.jchatroom.server;

import java.util.Locale;

public record ServerConfig(
        int port,
        int reactorThreads,
        int businessThreads,
        int maxClients,
        int outboundQueueCapacity,
        int idleTimeoutSeconds
) {
    static final int DEFAULT_PORT = 5000;
    static final int DEFAULT_BUSINESS_THREADS = 32;
    static final int DEFAULT_MAX_CLIENTS = 1024;
    static final int DEFAULT_OUTBOUND_QUEUE_CAPACITY = 100;
    static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 600;

    public ServerConfig {
        validatePort(port);
        requirePositive("reactorThreads", reactorThreads);
        requirePositive("businessThreads", businessThreads);
        requirePositive("maxClients", maxClients);
        requirePositive("outboundQueueCapacity", outboundQueueCapacity);
        requirePositive("idleTimeoutSeconds", idleTimeoutSeconds);
    }

    public static ServerConfig defaults() {
        return new ServerConfig(
                DEFAULT_PORT,
                defaultReactorThreads(),
                DEFAULT_BUSINESS_THREADS,
                DEFAULT_MAX_CLIENTS,
                DEFAULT_OUTBOUND_QUEUE_CAPACITY,
                DEFAULT_IDLE_TIMEOUT_SECONDS
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

            int parsedValue = parseInteger(flag, value);
            switch (normalizeFlag(flag)) {
                case "port" -> port = parsedValue;
                case "reactor-threads" -> reactorThreads = parsedValue;
                case "business-threads" -> businessThreads = parsedValue;
                case "max-clients" -> maxClients = parsedValue;
                case "queue-capacity" -> outboundQueueCapacity = parsedValue;
                case "idle-timeout-seconds" -> idleTimeoutSeconds = parsedValue;
                default -> throw new IllegalArgumentException("Unknown option: --" + flag);
            }
        }

        return new ServerConfig(
                port,
                reactorThreads,
                businessThreads,
                maxClients,
                outboundQueueCapacity,
                idleTimeoutSeconds
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
