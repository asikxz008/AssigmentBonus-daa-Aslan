import java.util.ArrayDeque;

/** An ordinary unbalanced BST. Operations are iterative, even on a long chain. */
public final class BinarySearchTree implements IntSet {
    private static final class Node {
        int key;
        Node left;
        Node right;
        Node(int key) { this.key = key; }
    }
    private record Visit(Node node, int depth) {}
    private Node root;
    private int size;

    @Override public int size() { return size; }

    @Override public boolean insert(int key) {
        if (root == null) {
            root = new Node(key);
            size++;
            return true;
        }
        Node current = root;
        while (true) {
            if (key == current.key) return false;
            if (key < current.key) {
                if (current.left == null) {
                    current.left = new Node(key);
                    break;
                }
                current = current.left;
            } else {
                if (current.right == null) {
                    current.right = new Node(key);
                    break;
                }
                current = current.right;
            }
        }
        size++;
        return true;
    }

    @Override public boolean search(int key) {
        Node current = root;
        while (current != null) {
            if (key == current.key) return true;
            current = key < current.key ? current.left : current.right;
        }
        return false;
    }

    @Override public boolean delete(int key) {
        Node parent = null;
        Node current = root;
        while (current != null && current.key != key) {
            parent = current;
            current = key < current.key ? current.left : current.right;
        }
        if (current == null) return false;
        if (current.left != null && current.right != null) {
            // Replace with the smallest key in the right subtree, then unlink it.
            Node successorParent = current;
            Node successor = current.right;
            while (successor.left != null) {
                successorParent = successor;
                successor = successor.left;
            }
            current.key = successor.key;
            parent = successorParent;
            current = successor; // The successor has at most one child.
        }
        Node child = current.left != null ? current.left : current.right;
        if (parent == null) root = child;
        else if (parent.left == current) parent.left = child;
        else parent.right = child;
        size--;
        return true;
    }

    /** Diagnostic traversal, outside benchmark timing. */
    public int[] inorder() {
        int[] keys = new int[size];
        int index = 0;
        ArrayDeque<Node> stack = new ArrayDeque<>();
        Node current = root;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current);
                current = current.left;
            }
            current = stack.pop();
            keys[index++] = current.key;
            current = current.right;
        }
        return keys;
    }

    /** Height in edges: an empty tree is -1 and a single node is 0. */
    public int height() {
        if (root == null) return -1;
        int height = 0;
        ArrayDeque<Visit> stack = new ArrayDeque<>();
        stack.push(new Visit(root, 0));
        while (!stack.isEmpty()) {
            Visit visit = stack.pop();
            height = Math.max(height, visit.depth());
            if (visit.node().left != null) stack.push(new Visit(visit.node().left, visit.depth() + 1));
            if (visit.node().right != null) stack.push(new Visit(visit.node().right, visit.depth() + 1));
        }
        return height;
    }
}
