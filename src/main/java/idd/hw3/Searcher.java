package idd.hw3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.FSDirectory;

/**
 * Legge query da console (o da file), interroga l'indice e stampa i risultati.
 *
 * <pre>
 *   search &lt;dirIndice&gt;                     modalità interattiva
 *   search &lt;dirIndice&gt; --file query.txt    esegue le query del file (una per riga)
 * </pre>
 */
public final class Searcher implements AutoCloseable {

    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    private final ConsoleQueryParser parser;
    private final UnifiedHighlighter highlighter;
    private final String config;

    public Searcher(Path indexDir) throws IOException {
        this.reader = DirectoryReader.open(FSDirectory.open(indexDir));
        this.searcher = new IndexSearcher(reader);
        String cfg = reader.getIndexCommit().getUserData().get(Indexer.COMMIT_KEY_CONFIG);
        this.config = cfg != null ? cfg : Analyzers.DEFAULT_CONFIG;
        Analyzer analyzer = Analyzers.perField(config);
        this.parser = new ConsoleQueryParser(analyzer);
        this.highlighter = UnifiedHighlighter.builder(searcher, analyzer)
                .withFormatter(new DefaultPassageFormatter("[", "]", " … ", false))
                .withMaxLength(20_000)
                .build();
    }

    public String config() { return config; }
    public int numDocs() { return reader.numDocs(); }

    /** Esegue una query e stampa i primi k risultati. */
    public void run(String line, int k, PrintStream out) throws IOException {
        ConsoleQueryParser.Parsed p;
        try {
            p = parser.parse(line);
        } catch (IllegalArgumentException e) {
            out.println("  ! " + e.getMessage());
            return;
        }
        long t0 = System.nanoTime();
        TopDocs top = searcher.search(p.query, k);
        double ms = (System.nanoTime() - t0) / 1e6;

        out.printf("  Query Lucene: %s%n", p.query);
        out.printf("  Risultati: %d%s documenti in %.1f ms%n",
                top.totalHits.value, top.totalHits.relation.toString().contains("GREATER") ? "+" : "", ms);

        String[] snipContent = highlighter.highlight(Analyzers.FIELD_CONTENT, p.query, top, 1);
        String[] snipName = highlighter.highlight(Analyzers.FIELD_NAME, p.query, top, 1);
        ScoreDoc[] sd = top.scoreDocs;
        for (int i = 0; i < sd.length; i++) {
            Document d = searcher.storedFields().document(sd[i].doc);
            String name = snipName[i] != null ? snipName[i] : d.get(Analyzers.FIELD_NAME);
            out.printf("  %2d. [%.3f] %s.txt%n", i + 1, sd[i].score, name);
            String s = snipContent[i];
            if (s != null) {
                s = s.replaceAll("\\s+", " ").trim();
                if (s.length() > 160) s = s.substring(0, 157) + "...";
                out.printf("        %s%n", s);
            }
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private static void help(PrintStream out) {
        out.println("Sintassi:  <campo> termini...   con campo = nome | contenuto | tutto");
        out.println("  nome relazione finale         parole in OR nel nome del file");
        out.println("  contenuto \"boundary layer\"    phrase query (termini adiacenti)");
        out.println("  contenuto +shuttle -nasa      + obbligatorio, - escluso");
        out.println("  :k 20  numero di risultati   |   :help   |   :q per uscire");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Uso: search <dirIndice> [--file query.txt] [--k N]");
            System.exit(1);
        }
        Path idx = Path.of(args[0]);
        Path file = null;
        int k = 10;
        for (int i = 1; i < args.length; i++) {
            if ("--file".equals(args[i])) file = Path.of(args[++i]);
            else if ("--k".equals(args[i])) k = Integer.parseInt(args[++i]);
        }
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try (Searcher s = new Searcher(idx)) {
            out.printf("Indice %s: %d documenti, analyzer '%s'%n", idx, s.numDocs(), s.config());
            if (file != null) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                int n = 0;
                for (String l : lines) {
                    if (l.isBlank() || l.startsWith("#")) continue;
                    out.printf("%nQ%d> %s%n", ++n, l);
                    s.run(l, k, out);
                }
                return;
            }
            help(out);
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            while (true) {
                out.print("\nquery> ");
                String l = in.readLine();
                if (l == null || l.trim().equals(":q")) break;
                l = l.trim();
                if (l.isEmpty()) continue;
                if (l.equals(":help")) { help(out); continue; }
                if (l.startsWith(":k ")) { k = Integer.parseInt(l.substring(3).trim()); out.println("  k = " + k); continue; }
                s.run(l, k, out);
            }
        }
    }
}
