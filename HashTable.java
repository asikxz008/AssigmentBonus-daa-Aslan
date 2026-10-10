import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Separate chaining, with geometric growth and no shrinking. */
public final class HashTable implements IntSet {
    private static final class Entry {
        final int key;
        Entry next;
        Entry(int key, Entry next) { this.key = key; this.next = next; }
    }
    private Entry[] buckets = new Entry[8];
    private final IntUnaryOperator hash;
    private int size;
    private int resizeCount;

    public HashTable() { this(key -> key); }
    public HashTable(IntUnaryOperator hash) { this.hash = Objects.requireNonNull(hash); }
    @Override public int size() { return size; }
    public int capacity() { return buckets.length; }
    public int resizeCount() { return resizeCount; }

    private int bucketIndex(int key, int capacity) {
        // floorMod also handles negative keys and Integer.MIN_VALUE correctly.
        return Math.floorMod(hash.applyAsInt(key), capacity);
    }

    @Override public boolean insert(int key) {
        int index = bucketIndex(key, buckets.length);
        for (Entry entry = buckets[index]; entry != null; entry = entry.next) {
            if (entry.key == key) return false;
        }
        if (size + 1 > buckets.length * 0.75) {
            resize();
            index = bucketIndex(key, buckets.length);
        }
        buckets[index] = new Entry(key, buckets[index]);
        size++;
        return true;
    }

    private void resize() {
        if (buckets.length >= (1 << 30)) throw new IllegalStateException("Maximum capacity reached");
        Entry[] larger = new Entry[buckets.length * 2];
        for (Entry head : buckets) {
            while (head != null) {
                Entry next = head.next;
                int index = bucketIndex(head.key, larger.length);
                // Relink directly: calling insert here would scan duplicates again.
                head.next = larger[index];
                larger[index] = head;
                head = next;
            }
        }
        buckets = larger;
        resizeCount++;
    }

    @Override public boolean search(int key) {
        for (Entry entry = buckets[bucketIndex(key, buckets.length)]; entry != null; entry = entry.next) {
            if (entry.key == key) return true;
        }
        return false;
    }

    @Override public boolean delete(int key) {
        int index = bucketIndex(key, buckets.length);
        Entry previous = null;
        Entry current = buckets[index];
        while (current != null) {
            if (current.key == key) {
                if (previous == null) buckets[index] = current.next;
                else previous.next = current.next;
                size--;
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    /** Diagnostic inspection, outside benchmark timing. */
    public int maxChainLength() {
        int maximum = 0;
        for (Entry head : buckets) {
            int length = 0;
            for (Entry current = head; current != null; current = current.next) length++;
            maximum = Math.max(maximum, length);
        }
        return maximum;
    }
}
