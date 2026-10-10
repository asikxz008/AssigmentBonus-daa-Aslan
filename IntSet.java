/** A set of unique integer keys. No library set is used by either implementation. */
public interface IntSet {
    boolean insert(int key);
    boolean search(int key);
    boolean delete(int key);
    int size();
}
