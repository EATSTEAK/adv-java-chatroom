package dev.eatsteak.jchatroom.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger nextId = new AtomicInteger();

    NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, prefix + "-" + nextId.getAndIncrement());
        thread.setDaemon(false);
        return thread;
    }
}
