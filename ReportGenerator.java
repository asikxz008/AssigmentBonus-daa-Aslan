import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Validates raw measurements and source hashes; generates the README and summary. */
public final class ReportGenerator {
    private static final String[] OPERATIONS = {"insert", "search_hit", "search_miss", "delete"};
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static long median(List<Map<String, String>> rows, String column) {
        long[] values = rows.stream().mapToLong(row -> Long.parseLong(row.get(column))).sorted().toArray();
        return values[values.length / 2];
    }
    private static String ms(long ns) { return String.format(Locale.ROOT, "%.6f", ns / 1_000_000.0); }
    private static void saveOrCheck(Path path, String content, boolean checkOnly) throws Exception {
        if (checkOnly) {
            require(Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n").equals(content),
                    "Generated file differs: " + path);
        } else Files.writeString(path, content, StandardCharsets.UTF_8);
    }
    static void generate(boolean checkOnly) throws Exception {
        Properties env = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("results/environment.properties"), StandardCharsets.UTF_8)) {
            env.load(reader);
        }
        for (String file : Benchmark.SOURCES) {
            require(Benchmark.sourceHash(file).equals(env.getProperty("sha256." + file)), "Measured source changed: " + file);
        }
        int repeats = Integer.parseInt(env.getProperty("repeats"));
        require(repeats > 0 && repeats % 2 == 1, "Invalid repeat count");
        List<String> lines = Files.readAllLines(Path.of("results/raw_trials.csv"), StandardCharsets.UTF_8);
        String[] columns = lines.get(0).split(",", -1);
        List<Map<String, String>> raw = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] values = line.split(",", -1);
            require(values.length == columns.length, "Malformed CSV row");
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < columns.length; i++) row.put(columns[i], values[i]);
            int trial = Integer.parseInt(row.get("trial"));
            require(trial >= 1 && trial <= repeats, "Invalid trial");
            require(Long.parseLong(row.get("seed")) == Benchmark.SEED + trial - 1, "Invalid seed");
            for (String op : OPERATIONS) require(Long.parseLong(row.get(op + "_ns")) > 0, "Nonpositive time");
            int n = Integer.parseInt(row.get("n"));
            require(Long.parseLong(row.get("gc_collections")) >= 0 && Long.parseLong(row.get("gc_time_ms")) >= 0,
                    "Invalid GC metrics");
            if (row.get("structure").equals("BST")) {
                int height = Integer.parseInt(row.get("tree_height"));
                require(height >= 0 && height < n, "Invalid tree height");
                if (row.get("scenario").equals("sorted")) require(height == n - 1, "Sorted tree is not a chain");
                require(row.get("capacity").isEmpty() && row.get("max_chain").isEmpty() && row.get("resizes").isEmpty(),
                        "Unexpected BST hash metrics");
            } else {
                int capacity = Integer.parseInt(row.get("capacity"));
                int chain = Integer.parseInt(row.get("max_chain"));
                require(row.get("tree_height").isEmpty() && capacity > n && (capacity & (capacity - 1)) == 0,
                        "Invalid hash capacity");
                require(chain == (row.get("scenario").equals("collisions") ? n : 1), "Unexpected hash chain length");
                require(Integer.parseInt(row.get("resizes")) == Integer.numberOfTrailingZeros(capacity) - 3,
                        "Invalid resize count");
            }
            require(identities.add(row.get("scenario") + ":" + row.get("n") + ":" + row.get("structure") + ":" + trial),
                    "Duplicate trial");
            raw.add(row);
        }
        require(raw.size() == 18 * repeats && raw.size() == Integer.parseInt(env.getProperty("trial_records")), "Wrong coverage");
        StringBuilder summary = new StringBuilder("scenario,n,structure,trials");
        for (String op : OPERATIONS) summary.append(',').append(op).append("_median_ms,").append(op)
                .append("_median_us_per_op,").append(op).append("_min_ms,").append(op).append("_max_ms");
        summary.append(",tree_height_median,capacity_median,max_chain_median,resizes_median\n");
        Map<String, long[]> measured = new HashMap<>();
        StringBuilder report = new StringBuilder(HEADER.replace("$REPEATS", String.valueOf(repeats))
                .replace("$RECORDS", String.valueOf(raw.size())).replace("$BATCHES", String.valueOf(raw.size() * 4))
                .replace("$SEED_END", String.valueOf(Benchmark.SEED + repeats - 1))
                .replace("$WARMUP", env.getProperty("warmup_rounds_per_case")));
        report.append("Measured on **").append(env.getProperty("measurement_started_utc")).append("**, using **Java ")
                .append(env.getProperty("java_version")).append(" (").append(env.getProperty("java_vendor"))
                .append(")**, ").append(env.getProperty("vm_name")).append(" ").append(env.getProperty("vm_version"))
                .append(" on ").append(env.getProperty("os_name")).append(" ").append(env.getProperty("os_version"))
                .append(" (").append(env.getProperty("os_arch")).append("). Processor: `")
                .append(env.getProperty("processor")).append("`; ").append(env.getProperty("logical_processors"))
                .append(" logical CPUs. JVM flags: `").append(env.getProperty("jvm_arguments")).append("`.\n\n")
                .append("Every value below is the **median of ").append(repeats)
                .append(" complete n-operation batches in milliseconds**. Lower is faster.\n\n");
        for (String scenario : Benchmark.SCENARIOS) {
            report.append("### ").append(switch (scenario) {
                case "random" -> "Random insertion with identity hashing";
                case "sorted" -> "Sorted insertion with identity hashing";
                default -> "Random insertion with forced hash collisions";
            }).append("\n\n| n | Structure | Insert ms | Search hit ms | Search miss ms | Delete ms |\n")
              .append("|---:|---|---:|---:|---:|---:|\n");
            for (int n : Benchmark.SIZES) for (String structure : new String[]{"BST", "HashTable"}) {
                List<Map<String, String>> group = raw.stream().filter(row -> row.get("scenario").equals(scenario)
                        && row.get("n").equals(String.valueOf(n)) && row.get("structure").equals(structure)).toList();
                require(group.size() == repeats, "Missing case: " + scenario + "/" + n + "/" + structure);
                long[] times = new long[4];
                summary.append(scenario).append(',').append(n).append(',').append(structure).append(',').append(repeats);
                report.append("| ").append(String.format(Locale.ROOT, "%,d", n)).append(" | ").append(structure).append(" | ");
                for (int i = 0; i < OPERATIONS.length; i++) {
                    String column = OPERATIONS[i] + "_ns";
                    times[i] = median(group, column);
                    long minimum = group.stream().mapToLong(row -> Long.parseLong(row.get(column))).min().orElseThrow();
                    long maximum = group.stream().mapToLong(row -> Long.parseLong(row.get(column))).max().orElseThrow();
                    summary.append(',').append(ms(times[i])).append(',')
                           .append(String.format(Locale.ROOT, "%.6f", times[i] / (1000.0 * n)))
                           .append(',').append(ms(minimum)).append(',').append(ms(maximum));
                    report.append(ms(times[i])).append(" | ");
                }
                for (String metric : new String[]{"tree_height", "capacity", "max_chain", "resizes"}) {
                    summary.append(',');
                    if (!group.get(0).get(metric).isEmpty()) summary.append(median(group, metric));
                }
                summary.append('\n'); report.append('\n');
                measured.put(scenario + ":" + n + ":" + structure, times);
            }
            report.append('\n');
        }
        long[] bst = measured.get("random:10000:BST");
        long[] hash = measured.get("random:10000:HashTable");
        report.append("### Comparison\n\nFor 10,000 random keys, successful search took **").append(ms(bst[1]))
                .append(" ms** in the BST and **").append(ms(hash[1])).append(" ms** in the Hash Table. Deletion took **")
                .append(ms(bst[3])).append(" ms** and **").append(ms(hash[3])).append(" ms**. Insertion took **")
                .append(ms(bst[0])).append(" ms** and **").append(ms(hash[0])).append(" ms**. Hash-table growth is included.\n\n")
                .append("Sorted BST insertion took **").append(ms(measured.get("sorted:10000:BST")[0]))
                .append(" ms**, compared with **").append(ms(measured.get("sorted:10000:HashTable")[0]))
                .append(" ms** for hashing. Sorted-tree height is 9,999. With identity hashing, the final table has ")
                .append("16,384 buckets, maximum chain length 1 and 11 resizes. Forcing every key into one bucket ")
                .append("makes the chain length 10,000; its successful-search batch took **")
                .append(ms(measured.get("collisions:10000:HashTable")[1])).append(" ms**.\n\n")
                .append("The 100- and 1,000-key rows show the trend toward the larger workload. Very short timings are noisy. ")
                .append("The long paths in a sorted tree and the long collision chain require many more comparisons. ")
                .append("Building either adverse structure performs Theta(n^2) work, while random BST construction is expected ")
                .append("O(n log n) and suitably distributed hashing is expected O(n). Measurements illustrate these bounds; ")
                .append("they do not prove them.\n\n").append(FOOTER);
        saveOrCheck(Path.of("results/summary.csv"), summary.toString(), checkOnly);
        saveOrCheck(Path.of("README.md"), report.toString(), checkOnly);
        System.out.println("Verified " + raw.size() + " raw trials and 18 summaries; " + (checkOnly ? "generated files match" : "updated README"));
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--check"))) throw new IllegalArgumentException("Use [--check]");
        generate(args.length == 1);
    }
    private static final String HEADER = """
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

            The recorded run uses **$REPEATS measured trials per case** ($RECORDS records, $BATCHES timed batches), with seeds
            20261010 through $SEED_END. BST and Hash Table execution order alternates between trials.
            Before measurement, both implementations receive **$WARMUP complete warm-up rounds at every size and scenario**
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

            """;
    private static final String FOOTER = """
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
            """;
}
