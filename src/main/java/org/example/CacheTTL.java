package org.example;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class CacheTTL<K, V> implements Map<K, V> {
    private int n, m;
    private AtomicInteger hit = new AtomicInteger(0), misses = new AtomicInteger(0);

    CacheTTL(int n, int m) {
        this.n = n;
        this.m = m;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
                clean();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    class CacheElement {
        V value;
        long createdAt;
        long lastAccessedAt;

        CacheElement(V value) {
            this.value = value;
            long now = System.currentTimeMillis();
            createdAt = now;
            lastAccessedAt = now;
        }

        public boolean isExpired() {
            long now = System.currentTimeMillis();
            return (now - createdAt) >= n * 1000L || (now - lastAccessedAt) >= m * 1000L;
        }
    }

    ConcurrentHashMap<K, CacheElement> map = new ConcurrentHashMap<>();

    public void clean() {
        for (Map.Entry<K, CacheElement> entry : map.entrySet()) {
            if (entry.getValue().isExpired()) {
                remove(entry.getKey());
            }
        }
    }

    public void print() {
        for (Map.Entry<K, CacheElement> entry : map.entrySet()) {
            System.out.print(entry.getKey() + " ");
        }
        System.out.println();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public double getHitRate() {
        double hitD = (double) hit.get(), missesD = (double) misses.get();
        if (hit.get() + misses.get() == 0) {
            return 0;
        }
        return (hitD * 100.0) / (hitD + missesD);
    }

    @Override
    public V get(Object key) {
        CacheElement c = map.get(key);
        if (c == null) {
            misses.incrementAndGet();
            return null;
        }
        if (c.isExpired()) {
            remove(key);
            misses.incrementAndGet();
            return null;
        } else {
            hit.incrementAndGet();
            c.lastAccessedAt = System.currentTimeMillis();
            return c.value;
        }
    }

    @Override
    public V put(K key, V value) {
        CacheElement object = map.put(key, new CacheElement(value));
        return object != null ? object.value : null;
    }

    @Override
    public V remove(Object key) {
        return map.remove(key).value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
    }

    @Override
    public void clear() {

    }

    @Override
    public Set<K> keySet() {
        return Set.of();
    }

    @Override
    public Collection<V> values() {
        return List.of();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return Set.of();
    }
}