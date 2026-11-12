package idd.hw3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Indicizza tutti i file .txt di una directory (ricorsivamente) creando due campi:
 * {@code nome} (nome del file senza estensione) e {@code contenuto} (testo del file),
 * ciascuno con il proprio analyzer (vedi {@link Analyzers}).
 */
public final class Indexer {

    /** Chiave salvata nei metadati del commit: il Searcher usa così lo stesso analyzer. */
    public static final String COMMIT_KEY_CONFIG = "analyzerConfig";

    /** Statistiche di una indicizzazione. */
    public static final class Stats {
        public int files;
        public long bytes;
        public long readNanos;
        public long indexNanos;
        public long commitNanos;
        public long totalNanos;
        public long indexBytesOnDisk;

        public double totalSeconds() { return totalNanos / 1e9; }
        public double docsPerSecond() { return files / totalSeconds(); }
        public double mbPerSecond() { return (bytes / 1048576.0) / totalSeconds(); }

        @Override
        public String toString() {
            return String.format(
                    "File indicizzati: %d (%.1f MB)%n"
                            + "Tempo totale:     %.2f s  (lettura %.2f s, analisi+indicizzazione %.2f s, commit %.2f s)%n"
                            + "Throughput:       %.0f doc/s, %.2f MB/s%n"
                            + "Dimensione indice: %.1f MB",
                    files, bytes / 1048576.0,
                    totalSeconds(), readNanos / 1e9, indexNanos / 1e9, commitNanos / 1e9,
                    docsPerSecond(), mbPerSecond(),
                    indexBytesOnDisk / 1048576.0);
        }
    }

    private Indexer() {}

    /** Elenca (in ordine) i file .txt contenuti nella directory e nelle sottodirectory. */
    public static List<Path> listTxtFiles(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Legge un file come UTF-8; se non è UTF-8 valido ripiega su ISO-8859-1. */
    static String readText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    static String baseName(Path file) {
        String n = file.getFileName().toString();
        return n.substring(0, n.length() - 4); // toglie ".txt"
    }

    /** Indicizza i file indicati nella directory Lucene data (che viene ricreata da zero). */
    public static Stats index(List<Path> files, Directory dir, String config) throws IOException {
        IndexWriterConfig iwc = new IndexWriterConfig(Analyzers.perField(config));
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(256);

        Stats st = new Stats();
        long t0 = System.nanoTime();
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
            for (Path f : files) {
                long r0 = System.nanoTime();
                byte[] bytes = Files.readAllBytes(f);
                String text = readText(bytes);
                long r1 = System.nanoTime();

                Document doc = new Document();
                doc.add(new StoredField(Analyzers.FIELD_PATH, f.toString()));
                doc.add(new TextField(Analyzers.FIELD_NAME, baseName(f), Field.Store.YES));
                doc.add(new TextField(Analyzers.FIELD_CONTENT, text, Field.Store.YES));
                w.addDocument(doc);
                long r2 = System.nanoTime();

                st.files++;
                st.bytes += bytes.length;
                st.readNanos += r1 - r0;
                st.indexNanos += r2 - r1;
            }
            long c0 = System.nanoTime();
            w.setLiveCommitData(Collections.singletonMap(COMMIT_KEY_CONFIG, config).entrySet());
            w.commit();
            st.commitNanos = System.nanoTime() - c0;
        }
        st.totalNanos = System.nanoTime() - t0;
        for (String f : dir.listAll()) {
            st.indexBytesOnDisk += dir.fileLength(f);
        }
        return st;
    }

    /** Uso: index &lt;dirDocumenti&gt; &lt;dirIndice&gt; [configurazione] */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: index <dirDocumenti> <dirIndice> [configurazione: " + Analyzers.CONFIGS.keySet() + "]");
            System.exit(1);
        }
        Path docs = Path.of(args[0]);
        Path idx = Path.of(args[1]);
        String config = args.length > 2 ? args[2] : Analyzers.DEFAULT_CONFIG;

        List<Path> files = listTxtFiles(docs);
        System.out.printf("Trovati %d file .txt in %s%n", files.size(), docs.toAbsolutePath());
        System.out.printf("Analyzer: %s%n", Analyzers.CONFIGS.get(config));
        Files.createDirectories(idx);
        try (Directory dir = FSDirectory.open(idx)) {
            Stats st = index(files, dir, config);
            System.out.println(st);
        }
    }
}
