package dev.froyln.bobby112.cache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChunkStorageTest {
    @Test
    public void flushWaitsForActiveAndQueuedSaves() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger written = new AtomicInteger();
        ChunkStorage.enqueueSave("fixture-active", null, () -> {
            started.countDown();
            await(release);
            written.incrementAndGet();
        });
        FutureTask<Void> flush = new FutureTask<>(() -> { ChunkStorage.flush(); return null; });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 1000; i++) {
                ChunkStorage.enqueueSave("fixture-" + i, null, written::incrementAndGet);
            }
            Thread waiter = new Thread(flush);
            waiter.setDaemon(true);
            waiter.start();
            assertFalse(flush.isDone());
        } finally {
            release.countDown();
        }
        flush.get(10, TimeUnit.SECONDS);
        assertEquals(1001, written.get());
    }

    @Test
    public void aFailedSaveDoesNotSkipTheRemainingQueue() {
        AtomicInteger written = new AtomicInteger();
        ChunkStorage.enqueueSave("fixture-failure", null, () -> { throw new IllegalStateException("expected fixture failure"); });
        ChunkStorage.enqueueSave("fixture-after-failure", null, written::incrementAndGet);
        assertFalse(ChunkStorage.flush());
        assertEquals(1, written.get());
        assertTrue(ChunkStorage.flush());
    }

    @Test
    public void flushPreservesInterruptWithoutAbandoningAcceptedSaves() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ChunkStorage.enqueueSave("fixture-interrupted", null, () -> { started.countDown(); await(release); });
        FutureTask<Boolean> flush = new FutureTask<>(() -> {
            Thread.currentThread().interrupt();
            ChunkStorage.flush();
            return Thread.currentThread().isInterrupted();
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread waiter = new Thread(flush);
            waiter.setDaemon(true);
            waiter.start();
        } finally {
            release.countDown();
        }
        assertTrue(flush.get(10, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
