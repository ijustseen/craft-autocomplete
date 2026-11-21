package dev.ijustseen.craftautocomplete.recent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Ring of item ids whose total count just grew. The first snapshot is a baseline,
 * so joining a world does not mark the whole inventory as freshly picked up.
 */
public final class RecentItems {
    public static final int CAPACITY = 32;
    public static final long MEMORY_MS = 60_000L;

    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private Map<Integer, Integer> baseline;
    private boolean primed;

    public void clear() {
        events.clear();
        baseline = null;
        primed = false;
    }

    public void observe(Map<Integer, Integer> totals, long nowMillis) {
        if (!primed) {
            primed = true;
            baseline = totals;
            return;
        }
        for (Map.Entry<Integer, Integer> entry : totals.entrySet()) {
            int previous = baseline.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > previous) {
                push(entry.getKey(), nowMillis);
            }
        }
        baseline = totals;
    }

    /** Newest first, one id per item, ignoring events older than {@link #MEMORY_MS}. */
    public int[] newestFirst(long nowMillis) {
        int[] ids = new int[events.size()];
        int count = 0;
        HashSet<Integer> seen = new HashSet<>();
        var iterator = events.descendingIterator();
        while (iterator.hasNext()) {
            Event event = iterator.next();
            if (nowMillis - event.timeMillis() > MEMORY_MS) {
                continue;
            }
            if (seen.add(event.itemId())) {
                ids[count++] = event.itemId();
            }
        }
        if (count == ids.length) {
            return ids;
        }
        int[] trimmed = new int[count];
        System.arraycopy(ids, 0, trimmed, 0, count);
        return trimmed;
    }

    /** Item id to the newest timestamp still inside the memory window. */
    public Map<Integer, Long> timestamps(long nowMillis) {
        Map<Integer, Long> times = new HashMap<>();
        for (Event event : events) {
            if (nowMillis - event.timeMillis() > MEMORY_MS) {
                continue;
            }
            times.merge(event.itemId(), event.timeMillis(), Math::max);
        }
        return times;
    }

    private void push(int itemId, long nowMillis) {
        if (events.size() == CAPACITY) {
            events.removeFirst();
        }
        events.addLast(new Event(itemId, nowMillis));
    }

    private record Event(int itemId, long timeMillis) {}
}
