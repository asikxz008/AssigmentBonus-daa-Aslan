import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/** Batch benchmark with shared inputs, warm-up, result checks and raw nanoseconds. */
public final class Benchmark {
    static final int[] SIZES = {100, 1000, 10000};
    static final String[] SCENARIOS = {"random", "sorted", "collisions"};
    static final String[] SOURCES = {"IntSet.java", "BinarySearchTree.java", "HashTable.java",
                                    "StructureTests.java", "Benchmark.java", "ReportGenerator.java"};
    static final long SEED = 20261010L;
    private static volatile long checksum;
    private record Orders(int[] insertion, int[] hits, int[] misses, int[] deletion) {}
    private record Timing(long ns, int successes) {}

    private static int[] sequence(int first, int n) {
        int[] result = new int[n];
        for (int i = 0; i < n; i++) result[i] = first + i;
        return result;
    }
    private static void shuffle(int[] keys, Random random) {
        for (int i = keys.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = keys[i]; keys[i] = keys[j]; keys[j] = temp;
        }
    }
    private static Orders orders(int n, String scenario, long seed) {
        Random random = new Random(seed);
        int[] insertion = sequence(0, n);
        if (!scenario.equals("sorted")) shuffle(insertion, random);
        int[] hits = sequence(0, n);
        int[] misses = sequence(n, n);
        int[] deletion = sequence(0, n);
        shuffle(hits, random); shuffle(misses, random); shuffle(deletion, random);
        return new Orders(insertion, hits, misses, deletion);
    }
    private static Timing timeBatch(IntSet set, int[] keys, int operation) {
        int successes = 0;
        long start = System.nanoTime();
        switch (operation) {
            case 0 -> { for (int key : keys) if (set.insert(key)) successes++; }
            case 1 -> { for (int key : keys) if (set.search(key)) successes++; }
            case 2 -> { for (int key : keys) if (set.delete(key)) successes++; }
            default -> throw new IllegalArgumentException("Unknown operation");
        }
        return new Timing(System.nanoTime() - start, successes);
    }
    private static long gcTotal(boolean time) {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = time ? gc.getCollectionTime() : gc.getCollectionCount();
            if (value >= 0) total += value;
        }
        return total;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static String trial(String name, String scenario, int n, int number, long seed, Orders orders) {
        IntSet set = name.equals("BST") ? new BinarySearchTree()
                : scenario.equals("collisions") ? new HashTable(key -> 0) : new HashTable();
        long gcCount = gcTotal(false);
        long gcTime = gcTotal(true);
        Timing insertion = timeBatch(set, orders.insertion(), 0);
        require(insertion.successes() == n && set.size() == n, "Insertion failed");
        String metrics;
        if (set instanceof BinarySearchTree tree) {
            require(Arrays.equals(tree.inorder(), sequence(0, n)), "BST ordering failed");
            metrics = tree.height() + ",,,";
        } else {
            HashTable table = (HashTable) set;
            metrics = "," + table.capacity() + "," + table.maxChainLength() + "," + table.resizeCount();
        }
        Timing hits = timeBatch(set, orders.hits(), 1);
        Timing misses = timeBatch(set, orders.misses(), 1);
        Timing deletion = timeBatch(set, orders.deletion(), 2);
        require(hits.successes() == n && misses.successes() == 0, "Search failed");
        require(deletion.successes() == n && set.size() == 0, "Deletion failed");
        for (int key : orders.hits()) require(!set.search(key), "Deleted key remains");
        if (set instanceof BinarySearchTree tree) require(tree.height() == -1, "Root remains");
        else require(((HashTable) set).maxChainLength() == 0, "Chain remains");
        checksum ^= insertion.successes() + hits.successes() + deletion.successes() + set.size();
        return scenario + "," + n + "," + name + "," + number + "," + seed + ","
                + insertion.ns() + "," + hits.ns() + "," + misses.ns() + "," + deletion.ns()
                + "," + metrics + "," + (gcTotal(false) - gcCount) + "," + (gcTotal(true) - gcTime);
    }
    static String sourceHash(String filename) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(filename))));
    }
    public static void main(String[] args) throws Exception {
        int repeats = 5;
        int warmup = 8;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) throw new IllegalArgumentException("Expected --repeats N or --warmup N");
            int value = Integer.parseInt(args[i + 1]);
            switch (args[i]) {
                case "--repeats" -> repeats = value;
                case "--warmup" -> warmup = value;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (repeats < 1 || repeats % 2 == 0 || warmup < 0) {
            throw new IllegalArgumentException("Repeats must be positive and odd; warmup must be nonnegative");
        }
        Properties environment = new Properties();
        environment.setProperty("started_utc", Instant.now().toString());
        environment.setProperty("java_version", System.getProperty("java.version"));
        environment.setProperty("java_vendor", System.getProperty("java.vendor"));
        environment.setProperty("vm_name", System.getProperty("java.vm.name"));
        environment.setProperty("vm_version", System.getProperty("java.vm.version"));
        environment.setProperty("os_name", System.getProperty("os.name"));
        environment.setProperty("os_version", System.getProperty("os.version"));
        environment.setProperty("os_arch", System.getProperty("os.arch"));
        environment.setProperty("processor", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "not reported"));
        environment.setProperty("logical_processors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        environment.setProperty("max_heap_bytes", String.valueOf(Runtime.getRuntime().maxMemory()));
        environment.setProperty("jvm_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
        environment.setProperty("garbage_collectors", ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).toList().toString());
        environment.setProperty("timer", "System.nanoTime; elapsed batch time; nanoseconds do not imply nanosecond resolution");
        environment.setProperty("repeats", String.valueOf(repeats));
        environment.setProperty("warmup_rounds_per_case", String.valueOf(warmup));
        environment.setProperty("sizes", "100,1000,10000");
        environment.setProperty("seed_base", String.valueOf(SEED));
        environment.setProperty("gc_policy", "Default JVM GC; not disabled and no System.gc calls");
        for (String source : SOURCES) environment.setProperty("sha256." + source, sourceHash(source));
        System.out.println("Warm-up: " + warmup + " full rounds at every size and scenario");
        for (int round = 0; round < warmup; round++) {
            for (String scenario : SCENARIOS) for (int n : SIZES) {
                Orders input = orders(n, scenario, SEED + 1000 + round);
                trial("BST", scenario, n, 0, 0, input);
                trial("HashTable", scenario, n, 0, 0, input);
            }
        }
        environment.setProperty("measurement_started_utc", Instant.now().toString());
        List<String> rows = new ArrayList<>();
        rows.add("scenario,n,structure,trial,seed,insert_ns,search_hit_ns,search_miss_ns,delete_ns,"
                 + "tree_height,capacity,max_chain,resizes,gc_collections,gc_time_ms");
        for (String scenario : SCENARIOS) for (int n : SIZES) {
            for (int number = 1; number <= repeats; number++) {
                long seed = SEED + number - 1;
                Orders input = orders(n, scenario, seed);
                String[] names = number % 2 == 1 ? new String[]{"BST", "HashTable"} : new String[]{"HashTable", "BST"};
                for (String name : names) rows.add(trial(name, scenario, n, number, seed, input));
            }
            System.out.println("Measured and validated: " + scenario + ", n=" + n);
        }
        environment.setProperty("finished_utc", Instant.now().toString());
        environment.setProperty("trial_records", String.valueOf(rows.size() - 1));
        Path results = Path.of("results");
        Files.createDirectories(results);
        Files.writeString(results.resolve("raw_trials.csv"), String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
        try (BufferedWriter writer = Files.newBufferedWriter(results.resolve("environment.properties"), StandardCharsets.UTF_8)) {
            environment.store(writer, "Java benchmark environment and source provenance");
        }
        ReportGenerator.generate(false);
        System.out.println("Saved " + (rows.size() - 1) + " trials; checksum=" + checksum);
    }
}
