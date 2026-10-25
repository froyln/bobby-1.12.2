package dev.froyln.bobby112.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class PendingSaveQueueTest {
    @Test
    public void coalescesRepeatedChunkSavesToTheNewestSnapshot() {
        PendingSaveQueue<Integer> queue = new PendingSaveQueue<>(2);
        AtomicInteger saved = new AtomicInteger();
        queue.offer("world|1|2", 1, () -> saved.set(1));
        queue.offer("world|1|2", 1, () -> saved.set(2));

        queue.poll().run();

        assertEquals(2, saved.get());
        assertEquals(0, queue.size());
    }

    @Test
    public void keepsTheBacklogBoundedAndDropsTheOldestPendingSave() {
        PendingSaveQueue<Integer> queue = new PendingSaveQueue<>(2);
        AtomicInteger saved = new AtomicInteger();
        queue.offer("old", 1, () -> saved.addAndGet(1));
        queue.offer("middle", 1, () -> saved.addAndGet(10));

        assertTrue(queue.offer("new", 1, () -> saved.addAndGet(100)));

        queue.poll().run();
        queue.poll().run();
        assertEquals(110, saved.get());
        assertFalse(queue.offer("newer", 1, () -> {}));
        assertEquals(1, queue.size());
    }
    @Test
    public void readsLatestSnapshotWhileQueuedAndWhileWriterIsDelayed() {
        PendingSaveQueue<Integer> queue = new PendingSaveQueue<>(2);
        AtomicInteger disk = new AtomicInteger(10);
        queue.offer("chunk", 20, () -> disk.set(20));
        assertEquals(Integer.valueOf(20), queue.latest("chunk"));
        Runnable delayedWriter = queue.poll();
        assertEquals(10, disk.get());
        assertEquals(Integer.valueOf(20), queue.latest("chunk"));
        delayedWriter.run();
        assertNull(queue.latest("chunk"));
        assertEquals(20, disk.get());
    }

    @Test
    public void oldCompletionCannotClearANewerSnapshot() {
        PendingSaveQueue<Integer> queue = new PendingSaveQueue<>(1);
        queue.offer("chunk", 1, () -> {});
        Runnable firstWriter = queue.poll();
        queue.offer("chunk", 2, () -> {});
        firstWriter.run();
        assertEquals(Integer.valueOf(2), queue.latest("chunk"));
        queue.offer("other", 3, () -> {});
        assertNull(queue.latest("chunk"));
        assertEquals(Integer.valueOf(3), queue.latest("other"));
        queue.poll().run();
        assertNull(queue.latest("other"));
    }

    @Test
    public void failingWriteReleasesItsSnapshot() {
        PendingSaveQueue<Integer> queue = new PendingSaveQueue<>(1);
        queue.offer("chunk", 1, () -> { throw new IllegalStateException("fixture"); });
        try {
            queue.poll().run();
            org.junit.Assert.fail("expected fixture failure");
        } catch (IllegalStateException expected) {
            assertNull(queue.latest("chunk"));
        }
    }
}
