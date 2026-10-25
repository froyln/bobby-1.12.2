package dev.froyln.bobby112.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

final class PendingSaveQueue<T> {
    private final int capacity;
    private final LinkedHashMap<String, Entry<T>> tasks = new LinkedHashMap<>();
    private final Map<String, Entry<T>> latest = new HashMap<>();

    PendingSaveQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized boolean offer(String key, T value, Runnable task) {
        Entry<T> entry = new Entry<>(value, task);
        tasks.remove(key);
        tasks.put(key, entry);
        latest.put(key, entry);
        if (tasks.size() <= capacity) {
            return false;
        }
        Iterator<Map.Entry<String, Entry<T>>> iterator = tasks.entrySet().iterator();
        Map.Entry<String, Entry<T>> oldest = iterator.next();
        latest.remove(oldest.getKey(), oldest.getValue());
        iterator.remove();
        return true;
    }

    synchronized T latest(String key) {
        Entry<T> entry = latest.get(key);
        return entry == null ? null : entry.value;
    }

    synchronized Runnable poll() {
        Iterator<Map.Entry<String, Entry<T>>> iterator = tasks.entrySet().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Map.Entry<String, Entry<T>> first = iterator.next();
        String key = first.getKey();
        Entry<T> entry = first.getValue();
        iterator.remove();
        return () -> {
            try {
                entry.task.run();
            } finally {
                synchronized (PendingSaveQueue.this) {
                    latest.remove(key, entry);
                }
            }
        };
    }

    synchronized int size() {
        return tasks.size();
    }

    private static final class Entry<T> {
        private final T value;
        private final Runnable task;

        private Entry(T value, Runnable task) {
            this.value = value;
            this.task = task;
        }
    }
}
