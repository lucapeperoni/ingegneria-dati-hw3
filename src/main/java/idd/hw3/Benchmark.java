package idd.hw3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Misura i tempi di indicizzazione su disco al crescere del numero di file e per ogni
 * configurazione di analyzer. Ogni misura è la mediana di {@code REPS} ripetizioni (dopo un
 * giro di warm-up della JVM).
 *
 * <pre>benchmark &lt;dirDocumenti&gt; &lt;dirOutput&gt;</pre>
 */
public final class Benchmark {

    static final int REPS = 3;
    static final int[] SIZES = {1000, 2500, 5000, 10000};

    private Benchmark() {}

    static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        }
    }

    static Indexer.Stats run(List<Path> files, String cfg, Path tmp) throws IOException {
        deleteRecursive(tmp);
        Files.createDirectories(tmp);
        try (Directory dir = FSDirectory.open(tmp)) {
            return Indexer.index(files, dir, cfg);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: benchmark <dirDocumenti> <dirOutput>");
            System.exit(1);
        }
        List<Path> all = new ArrayList<>(Indexer.listTxtFiles(Path.of(args[0])));
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);
        Path tmp = Files.createTempDirectory("hw3-bench");

        // sottoinsiemi casuali (seme fisso) per non indicizzare solo i primi newsgroup in ordine alfabetico
        Collections.shuffle(all, new Random(7));
        List<Integer> sizes = new ArrayList<>();
        for (int s : SIZES) if (s < all.size()) sizes.add(s);
        sizes.add(all.size());

        run(all.subList(0, Math.min(2000, all.size())), Analyzers.DEFAULT_CONFIG, tmp); // warm-up

        StringBuilder md = new StringBuilder();
        md.append("# Tempi di indicizzazione\n\n");
        md.append(String.format("Dataset: %s, mediana di %d ripetizioni, indice su disco (SSD), RAM buffer 256 MB.%n",
                args[0], REPS));
        md.append(String.format("JVM: %s %s, CPU disponibili: %d.%n%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors()));
        md.append("| configurazione | file | MB | tempo totale (s) | di cui lettura (s) | analisi+indicizz. (s) | commit (s) | doc/s | MB/s | indice (MB) |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");

        for (String cfg : Analyzers.CONFIGS.keySet()) {
            for (int n : sizes) {
                List<Path> subset = all.subList(0, n);
                Indexer.Stats[] st = new Indexer.Stats[REPS];
                for (int r = 0; r < REPS; r++) st[r] = run(subset, cfg, tmp);
                Arrays.sort(st, Comparator.comparingLong(s -> s.totalNanos));
                Indexer.Stats m = st[REPS / 2];
                String row = String.format("| `%s` | %d | %.1f | %.2f | %.2f | %.2f | %.2f | %.0f | %.2f | %.1f |",
                        cfg, m.files, m.bytes / 1048576.0, m.totalSeconds(), m.readNanos / 1e9,
                        m.indexNanos / 1e9, m.commitNanos / 1e9, m.docsPerSecond(), m.mbPerSecond(),
                        m.indexBytesOnDisk / 1048576.0);
                System.out.println(row);
                md.append(row).append('\n');
            }
        }
        deleteRecursive(tmp);
        Files.writeString(outDir.resolve("indexing_times.md"), md.toString(), StandardCharsets.UTF_8);
    }
}
