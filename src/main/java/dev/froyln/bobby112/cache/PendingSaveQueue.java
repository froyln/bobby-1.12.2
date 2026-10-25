package dev.froyln.bobby112.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class PendingSaveQueue {
    private final int capacity;
    private final LinkedHashMap<String, Runnable> tasks = new LinkedHashMap<>();

    PendingSaveQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized boolean offer(String key, Runnable task) {
        tasks.remove(key);
        tasks.put(key, task);
        if (tasks.size() <= capacity) {
            return false;
        }
        Iterator<Map.Entry<String, Runnable>> iterator = tasks.entrySet().iterator();
        iterator.next();
        iterator.remove();
        return true;
    }

    synchronized Runnable poll() {
        Iterator<Map.Entry<String, Runnable>> iterator = tasks.entrySet().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Runnable task = iterator.next().getValue();
        iterator.remove();
        return task;
    }

    synchronized int size() {
        return tasks.size();
    }
}
