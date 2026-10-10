import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/** Dependency-free correctness tests; failures throw even without the -ea flag. */
public final class StructureTests {
    private static int checks;
    static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void emptyAndDuplicates() {
        for (IntSet set : new IntSet[]{new BinarySearchTree(), new HashTable(), new HashTable(k -> 0)}) {
            check(!set.search(7) && !set.delete(7) && set.size() == 0, "Empty structure");
            check(set.insert(7) && !set.insert(7) && set.size() == 1, "Duplicate insertion");
            check(set.search(7) && set.delete(7) && !set.delete(7), "Single deletion");
            check(!set.search(7) && set.size() == 0, "Empty after deletion");
        }
    }
    private static void bstDeletionCases() {
        int[][] inputs = {{5}, {5, 3}, {5, 7}, {5, 3, 7}, {10, 5, 20, 15, 25, 17},
                          {20, 10, 30, 5, 15, 13, 17}, {10, 5, 20, 15, 25}};
        int[] deleted = {5, 5, 5, 5, 10, 10, 15};
        for (int i = 0; i < inputs.length; i++) {
            BinarySearchTree tree = new BinarySearchTree();
            for (int key : inputs[i]) tree.insert(key);
            check(tree.delete(deleted[i]), "BST deletion case " + i);
            int removed = deleted[i];
            int[] expected = Arrays.stream(inputs[i]).filter(k -> k != removed).sorted().toArray();
            check(Arrays.equals(expected, tree.inorder()) && tree.size() == expected.length,
                  "Successor or parent link in case " + i);
        }
    }
    private static void sortedChain() {
        BinarySearchTree tree = new BinarySearchTree();
        for (int i = 0; i < 1500; i++) check(tree.insert(i), "Sorted insertion");
        check(tree.height() == 1499 && tree.search(1499) && !tree.search(1500), "Long chain");
        for (int i = 1499; i >= 0; i--) check(tree.delete(i), "Deepest deletion");
        check(tree.height() == -1 && tree.inorder().length == 0, "Empty tree diagnostics");
    }
    private static void collisionDeletion() {
        HashTable table = new HashTable(k -> 0);
        for (int i = 1; i <= 5; i++) table.insert(i);
        check(table.delete(5) && table.delete(3) && table.delete(1), "Chain head, middle, tail");
        check(table.search(2) && table.search(4) && table.maxChainLength() == 2, "Chain remains linked");
    }
    private static void resizing() {
        for (HashTable table : new HashTable[]{new HashTable(), new HashTable(k -> 0)}) {
            for (int i = 0; i < 1000; i++) table.insert(i);
            check(table.capacity() == 2048 && table.resizeCount() == 8, "Geometric growth");
            for (int i = 0; i < 1000; i++) check(table.search(i), "Key survives rehash");
            int oldCapacity = table.capacity();
            for (int i = 0; i < 1000; i++) check(table.delete(i), "Deletion after growth");
            check(table.size() == 0 && table.maxChainLength() == 0 && table.capacity() == oldCapacity,
                  "No dangling entries and no shrinking");
        }
    }
    private static void integerLimits() {
        int[] keys = {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, -1024, 1024};
        for (IntSet set : new IntSet[]{new BinarySearchTree(), new HashTable(), new HashTable(k -> -1)}) {
            for (int key : keys) check(set.insert(key) && set.search(key), "Integer range");
            for (int key : keys) check(set.delete(key), "Integer deletion");
        }
    }
    private static void differential(Supplier<IntSet> factory) {
        IntSet set = factory.get();
        Set<Integer> oracle = new HashSet<>(); // Only the tests use a library set.
        Random random = new Random(20261010L);
        for (int step = 0; step < 5000; step++) {
            int key = random.nextInt(401) - 200;
            int operation = random.nextInt(3);
            boolean expected;
            boolean actual;
            if (operation == 0) { expected = oracle.add(key); actual = set.insert(key); }
            else if (operation == 1) { expected = oracle.contains(key); actual = set.search(key); }
            else { expected = oracle.remove(key); actual = set.delete(key); }
            check(actual == expected && set.size() == oracle.size(), "Differential step " + step);
            if (step % 100 == 0) {
                for (int k = -200; k <= 200; k++) check(set.search(k) == oracle.contains(k), "Membership");
                if (set instanceof BinarySearchTree tree) {
                    check(Arrays.equals(tree.inorder(), oracle.stream().mapToInt(Integer::intValue).sorted().toArray()),
                          "Inorder matches reference");
                }
            }
        }
    }
    public static void main(String[] args) {
        emptyAndDuplicates();
        bstDeletionCases();
        sortedChain();
        collisionDeletion();
        resizing();
        integerLimits();
        differential(BinarySearchTree::new);
        differential(HashTable::new);
        differential(() -> new HashTable(k -> 0));
        System.out.println("PASS: 7 test groups, " + checks + " checks");
    }
}
