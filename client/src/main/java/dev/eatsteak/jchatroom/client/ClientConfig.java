package dev.eatsteak.jchatroom.client;

import java.util.Locale;

record ClientConfig(String host, int port) {
    static final String DEFAULT_HOST = "127.0.0.1";
    static final int DEFAULT_PORT = 5000;

    ClientConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        validatePort(port);
    }

    static ClientConfig defaults() {
        return new ClientConfig(DEFAULT_HOST, DEFAULT_PORT);
    }

    static ClientConfig fromArgs(String... args) {
        ClientConfig defaults = defaults();
        String host = defaults.host();
        int port = defaults.port();

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
                case "host" -> host = value;
                case "port" -> port = parsePort(flag, value);
                default -> throw new IllegalArgumentException("Unknown option: --" + flag);
            }
        }

        return new ClientConfig(host, port);
    }

    String address() {
        return host + ":" + port;
    }

    private static int parsePort(String flag, String value) {
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
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }
}
