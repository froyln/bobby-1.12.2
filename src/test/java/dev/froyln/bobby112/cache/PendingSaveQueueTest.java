package dev.froyln.bobby112.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PendingSaveQueueTest {
    @Test
    public void coalescesRepeatedChunkSavesToTheNewestSnapshot() {
        PendingSaveQueue queue = new PendingSaveQueue(2);
        AtomicInteger saved = new AtomicInteger();
        queue.offer("world|1|2", () -> saved.set(1));
        queue.offer("world|1|2", () -> saved.set(2));

        queue.poll().run();

        assertEquals(2, saved.get());
        assertEquals(0, queue.size());
    }

    @Test
    public void keepsTheBacklogBoundedAndDropsTheOldestPendingSave() {
        PendingSaveQueue queue = new PendingSaveQueue(2);
        AtomicInteger saved = new AtomicInteger();
        queue.offer("old", () -> saved.addAndGet(1));
        queue.offer("middle", () -> saved.addAndGet(10));

        assertTrue(queue.offer("new", () -> saved.addAndGet(100)));

        queue.poll().run();
        queue.poll().run();
        assertEquals(110, saved.get());
        assertFalse(queue.offer("newer", () -> {}));
        assertEquals(1, queue.size());
    }
}
