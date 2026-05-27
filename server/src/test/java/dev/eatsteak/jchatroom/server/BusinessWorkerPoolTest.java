package dev.eatsteak.jchatroom.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessWorkerPoolTest {
    @Test
    @Timeout(5)
    void shutdownAllowsRunningTaskToCompleteBeforeForcingStop() throws Exception {
        BusinessWorkerPool pool = new BusinessWorkerPool(testConfig(), Duration.ofSeconds(1));
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();

        pool.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(100);
                completed.set(true);
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));

        pool.shutdown();

        assertTrue(pool.awaitTermination(Duration.ofSeconds(1)));
        assertTrue(completed.get());
        assertFalse(interrupted.get());
    }

    @Test
    @Timeout(5)
    void shutdownForcesStopAfterGracePeriodExpires() throws Exception {
        BusinessWorkerPool pool = new BusinessWorkerPool(testConfig(), Duration.ofMillis(50));
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        pool.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));

        pool.shutdown();

        assertTrue(pool.awaitTermination(Duration.ofSeconds(1)));
        assertTrue(interrupted.get());
    }

    private static ServerConfig testConfig() {
        return new ServerConfig(0, 1, 1, 4, 4, 600);
    }
}
