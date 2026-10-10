# Binary Search Tree and Hash Table Comparison in Java

Repository: [AssigmentBonus-daa-Aslan](https://github.com/asikxz008/AssigmentBonus-daa-Aslan)

## 1. Implementation

This project implements two sets of unique integer keys in **Java 17 or later**, using only the JDK.
Both implement `IntSet`: `insert(int)`, `search(int)`, `delete(int)` and `size()`.
Insertion returns false for duplicates, search returns membership, and deletion returns false for a missing key.
`HashSet` is used only as a reference in correctness tests.

**BinarySearchTree** is an ordinary unbalanced tree. Smaller keys go left and larger keys go right.
Insertion and search follow comparisons. Deletion removes a leaf, links its single child to the parent,
or replaces a two-child node with its inorder successor (the smallest key in its right subtree),
then removes that successor. Root deletion is handled too. All operations and diagnostics are iterative,
so a 10,000-node chain does not require recursive calls.

**HashTable** has an array of buckets and linked-list separate chaining. The default hash is the key itself;
`Math.floorMod(hash(key), capacity)` handles negative integers safely. Capacity starts at 8 and doubles
before the load factor exceeds 0.75. Resizing relinks entries directly in linear time, without calling
insertion again. Deletion unlinks one entry and does not shrink the bucket array.

## 2. Complexity analysis

Let n be the stored key count, h the tree height, m the bucket count and alpha = n/m.
Integer comparisons and hashing are treated as constant-time operations.

| Structure | Operation | Best single operation | Expected case | Worst single operation |
|---|---|---|---|---|
| Unbalanced BST | Insert | Theta(1) | O(log n), random insertion order | Theta(n) |
| Unbalanced BST | Search | Theta(1) | O(log n), random insertion order | Theta(n) |
| Unbalanced BST | Delete | Theta(1) | O(log n), random insertion order | Theta(n) |
| Chained Hash Table | Insert | Theta(1) | Expected amortized O(1), well-spread hashes and bounded load | Theta(n) |
| Chained Hash Table | Search | Theta(1) | Expected O(1), well-spread hashes and bounded load | Theta(n) |
| Chained Hash Table | Delete | Theta(1) | Expected O(1), well-spread hashes and bounded load | Theta(n) |

BST operations cost O(h + 1). Empty-tree insertion, root search, or deleting a root with at most one child
can be constant time. Sorted insertion makes a chain of height n - 1, allowing linear operations.
The tree performs no balancing; logarithmic expected performance under random insertion is not a guarantee
for arbitrary input. See [BST analysis](https://opendatastructures.org/ods-cpp/6_2_Unbalanced_Binary_Searc.html)
and [random BST analysis](https://opendatastructures.org/versions/edition-0.1c/ods-java/node40.html).

Hashing costs expected O(1 + alpha) without resizing. Bounded load gives expected O(1) search and deletion.
A growth operation costs Theta(n), but geometric doubling spreads this cost over many insertions.
Thus insertion is expected amortized O(1) with well-spread hashes, while one insertion can still be linear.
With all keys colliding, even duplicate checking can take Theta(n).
See [chaining and resizing analysis](https://opendatastructures.org/ods-cpp/5_1_Hashing_with_Chaining.html).

The BST stores O(n) nodes and operations use O(1) auxiliary space. Its diagnostic traversals use a stack.
The Hash Table stores O(n + m) entries and buckets; resizing temporarily needs O(m) extra bucket references.
Construction from the default capacity uses O(n) space. Since there is no shrinking, bucket memory after
deletion depends on the peak size, not only the current number of keys.

These are bounds for one operation. Complete search batches on a chain take Theta(n^2) work.
Deleting a chain's head repeatedly can be linear overall; shuffled deletion on a chain has quadratic expected cost.
Each deletion reduces the structure's size.

## 3. Experimental method

Sizes are **100, 1,000 and 10,000**. Each fresh structure receives the same arrays within a trial:
insert every key from 0 through n - 1; search for all stored keys in shuffled order; search for n through
2n - 1 in shuffled order (all misses); delete every stored key in shuffled order.

| Scenario | Insertion order | Hash | Purpose |
|---|---|---|---|
| random | Shuffled | Identity | Ordinary comparison for these keys |
| sorted | Ascending | Identity | BST degeneration |
| collisions | Shuffled | Always zero | One long hash chain |

Identity hashing is particularly favorable for these consecutive keys: final capacity exceeds n,
so stored keys occupy distinct buckets. It is not a universal hash for arbitrary integer patterns.
All miss keys are above the maximum BST key, making sorted-tree misses walk the entire right chain.

The recorded run uses **5 measured trials per case** (90 records, 360 timed batches), with seeds
20261010 through 20261014. BST and Hash Table execution order alternates between trials.
Before measurement, both implementations receive **8 complete warm-up rounds at every size and scenario**
to exercise the JVM and JIT compiler. Warm-up reduces startup effects but does not guarantee steady-state performance.

[System.nanoTime](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#nanoTime())
measures elapsed time around each complete batch. Its nanosecond unit does not guarantee nanosecond resolution.
Timed work includes calls, loops, counting successful returns, entry allocation and table resizing.
Constructors, input generation, structural inspection, correctness checks and file writes are outside the intervals.
Return values and final state are checked and consumed to reduce the risk of unused-work optimization.

Default JVM garbage collection remains enabled; no System.gc calls are made. Raw CSV includes GC count/time
changes across each whole trial, including its untimed checks, so those columns do not isolate GC within a batch.
CSV summaries give medians, minimum/maximum batch times and median microseconds per operation.

## 4. Results and comparison

Measured on **2026-10-10T15:03:47.318848900Z**, using **Java 25.0.1 (Oracle Corporation)**, Java HotSpot(TM) 64-Bit Server VM 25.0.1+8-LTS-27 on Windows 11 10.0 (amd64). Processor: `Intel64 Family 6 Model 183 Stepping 1, GenuineIntel`; 16 logical CPUs. JVM flags: `[-Xms512m, -Xmx512m]`.

Every value below is the **median of 5 complete n-operation batches in milliseconds**. Lower is faster.

### Random insertion with identity hashing

| n | Structure | Insert ms | Search hit ms | Search miss ms | Delete ms |
|---:|---|---:|---:|---:|---:|
| 100 | BST | 0.003500 | 0.001700 | 0.000500 | 0.002500 | 
| 100 | HashTable | 0.002600 | 0.000200 | 0.000200 | 0.000300 | 
| 1,000 | BST | 0.045600 | 0.028700 | 0.004800 | 0.031400 | 
| 1,000 | HashTable | 0.023900 | 0.001700 | 0.001400 | 0.002200 | 
| 10,000 | BST | 0.722300 | 0.419700 | 0.089700 | 0.466400 | 
| 10,000 | HashTable | 0.271800 | 0.019800 | 0.063900 | 0.025100 | 

### Sorted insertion with identity hashing

| n | Structure | Insert ms | Search hit ms | Search miss ms | Delete ms |
|---:|---|---:|---:|---:|---:|
| 100 | BST | 0.006900 | 0.008900 | 0.016900 | 0.005100 | 
| 100 | HashTable | 0.002000 | 0.000200 | 0.000200 | 0.000300 | 
| 1,000 | BST | 0.570800 | 0.798900 | 1.611100 | 0.396200 | 
| 1,000 | HashTable | 0.015700 | 0.001700 | 0.001400 | 0.002200 | 
| 10,000 | BST | 88.593500 | 118.950100 | 243.110300 | 72.650300 | 
| 10,000 | HashTable | 0.157400 | 0.019400 | 0.064700 | 0.025200 | 

### Random insertion with forced hash collisions

| n | Structure | Insert ms | Search hit ms | Search miss ms | Delete ms |
|---:|---|---:|---:|---:|---:|
| 100 | BST | 0.003200 | 0.001900 | 0.000500 | 0.002600 | 
| 100 | HashTable | 0.008000 | 0.007000 | 0.012100 | 0.004500 | 
| 1,000 | BST | 0.043900 | 0.024700 | 0.003800 | 0.030700 | 
| 1,000 | HashTable | 0.711700 | 0.562600 | 1.142300 | 0.297300 | 
| 10,000 | BST | 0.648200 | 0.411200 | 0.086100 | 0.466500 | 
| 10,000 | HashTable | 85.252800 | 89.306800 | 183.454900 | 57.017500 | 

### Comparison

For 10,000 random keys, successful search took **0.419700 ms** in the BST and **0.019800 ms** in the Hash Table. Deletion took **0.466400 ms** and **0.025100 ms**. Insertion took **0.722300 ms** and **0.271800 ms**. Hash-table growth is included.

Sorted BST insertion took **88.593500 ms**, compared with **0.157400 ms** for hashing. Sorted-tree height is 9,999. With identity hashing, the final table has 16,384 buckets, maximum chain length 1 and 11 resizes. Forcing every key into one bucket makes the chain length 10,000; its successful-search batch took **89.306800 ms**.

The 100- and 1,000-key rows show the trend toward the larger workload. Very short timings are noisy. The long paths in a sorted tree and the long collision chain require many more comparisons. Building either adverse structure performs Theta(n^2) work, while random BST construction is expected O(n log n) and suitably distributed hashing is expected O(n). Measurements illustrate these bounds; they do not prove them.

## 5. Why performance differs in practice

Tree height determines the number of BST comparisons. Hash distribution and load factor determine chain lengths.
Resizing occasionally makes an insertion expensive. Different fixed costs also matter: hashing computes an index,
while the tree follows comparisons and links. Both allocate linked nodes, so memory layout and pointer access can
affect cache behavior. JVM compilation, garbage collection, CPU scheduling and background applications can change
elapsed time. This experiment does not separately measure those effects.

A BST also provides ordered traversal and can support range queries. This Hash Table provides membership operations.
A self-balancing BST would guarantee logarithmic height but would perform additional balancing work.

## 6. Conclusions and limits

Hashing is useful for membership queries with suitably distributed keys. An unbalanced BST is sensitive to insertion
order; a Hash Table is sensitive to collisions. Both permit linear worst-case single operations.
The measured results apply to this implementation, JVM, machine and workload. A small number of trials and three sizes illustrate
trends, rather than guaranteeing timings for other applications. This is an educational batch benchmark, not a JMH
study with independently forked JVMs. No memory benchmark, string keys, mixed long-running updates or balanced tree
comparison was performed. Very short batches are especially sensitive to timer overhead and system noise.

## 7. Compile, test and reproduce

Install a **JDK 17 or later**, then run these commands from the repository root:

```text
javac --release 17 -d out *.java
java -cp out StructureTests
java -Xms512m -Xmx512m -cp out Benchmark --repeats 5 --warmup 8
java -cp out ReportGenerator --check
```

No Maven, Gradle or external libraries are required. The recorded run used Java 25.0.1 and compiled for Java 17.
Rerunning Benchmark replaces the recorded results and README; timings will vary. After a rerun, update the short
Word report to match the new measurements. ReportGenerator checks coverage, seeds, source SHA-256 hashes and the
generated summaries. GitHub Actions compiles on Java 17, runs the correctness tests and checks the recorded report.

| File | Contents |
|---|---|
| IntSet.java | Shared integer set interface |
| BinarySearchTree.java | Iterative BST implementation and diagnostics |
| HashTable.java | Chaining, growth and membership operations |
| StructureTests.java | Seven correctness test groups, including randomized reference comparisons |
| Benchmark.java | Warm-up, measurement, correctness checks and environment recording |
| ReportGenerator.java | Validation and README/CSV generation |
| results/raw_trials.csv | All measured trials and structural/GC metrics |
| results/summary.csv | Median, minimum, maximum and per-operation times |
| results/environment.properties | JVM, OS, flags, seeds and source hashes |
| [report.docx](report.docx) | Short explanation with the measured Java results |
