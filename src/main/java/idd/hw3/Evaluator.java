package idd.hw3;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;

/**
 * Valutazione dell'efficacia sulla collezione Cranfield (1400 documenti, 225 query, giudizi di
 * rilevanza graduati 1-4). Per ogni configurazione di analyzer e per ogni campo interrogato calcola
 * MAP, P@5, P@10, R@10, R@100, R@1000 e nDCG@10, più un test di significatività (randomization
 * test appaiato) rispetto alla baseline StandardAnalyzer.
 *
 * <pre>evaluate &lt;dirCranfieldTxt&gt; &lt;queries.tsv&gt; &lt;qrels.tsv&gt; &lt;dirOutput&gt;</pre>
 */
public final class Evaluator {

    static final int DEPTH = 1000;
    static final String[] MODES = {Analyzers.FIELD_CONTENT, Analyzers.FIELD_NAME, "tutto"};
    static final String[] SIM_MODES = {Analyzers.FIELD_CONTENT, "tutto"};

    /** Funzioni di ranking confrontate (viste a lezione): BM25 (default), TF-IDF, Language Model. */
    static final Map<String, Similarity> SIMILARITIES = new LinkedHashMap<>();

    static {
        SIMILARITIES.put("BM25 (k1=1.2, b=0.75) - default", new BM25Similarity());
        SIMILARITIES.put("ClassicSimilarity (TF-IDF)", new ClassicSimilarity());
        SIMILARITIES.put("LMDirichlet (mu=2000)", new LMDirichletSimilarity());
        SIMILARITIES.put("LMJelinekMercer (lambda=0.7)", new LMJelinekMercerSimilarity(0.7f));
    }

    static final String[] METRICS = {"MAP", "P@5", "P@10", "R@10", "R@100", "R@1000", "nDCG@10"};

    private Evaluator() {}

    /** Metriche di una singola query. */
    static double[] metrics(int[] ranking, Map<Integer, Integer> rel) {
        int nRel = rel.size();
        double ap = 0;
        int hits = 0;
        int hits5 = 0, hits10 = 0, hits100 = 0;
        double dcg = 0;
        for (int i = 0; i < ranking.length; i++) {
            Integer g = rel.get(ranking[i]);
            if (g != null) {
                hits++;
                ap += (double) hits / (i + 1);
                if (i < 5) hits5++;
                if (i < 10) {
                    hits10++;
                    dcg += gain(g) / log2(i + 2);
                }
                if (i < 100) hits100++;
            }
        }
        // nDCG ideale: documenti rilevanti ordinati per guadagno decrescente
        int[] gains = rel.values().stream().mapToInt(Evaluator::gainInt).sorted().toArray();
        double idcg = 0;
        for (int i = 0; i < Math.min(10, gains.length); i++) {
            idcg += gains[gains.length - 1 - i] / log2(i + 2);
        }
        return new double[] {
                ap / nRel,
                hits5 / 5.0,
                hits10 / 10.0,
                (double) hits10 / nRel,
                (double) hits100 / nRel,
                (double) hits / nRel,
                idcg == 0 ? 0 : dcg / idcg
        };
    }

    /** Grado Cranfield 1 = risposta completa ... 4 = interesse minimo  =>  guadagno 4..1. */
    static double gain(int grade) { return 5 - grade; }
    static int gainInt(int grade) { return 5 - grade; }
    static double log2(double x) { return Math.log(x) / Math.log(2); }

    /** Randomization test appaiato a due code sulle differenze per-query (10000 permutazioni). */
    static double pValue(double[] a, double[] b) {
        int n = a.length;
        double[] d = new double[n];
        double obs = 0;
        for (int i = 0; i < n; i++) { d[i] = a[i] - b[i]; obs += d[i]; }
        obs = Math.abs(obs / n);
        Random rnd = new Random(42);
        int trials = 10000, extreme = 0;
        for (int t = 0; t < trials; t++) {
            double s = 0;
            for (int i = 0; i < n; i++) s += rnd.nextBoolean() ? d[i] : -d[i];
            if (Math.abs(s / n) >= obs - 1e-12) extreme++;
        }
        return (extreme + 1.0) / (trials + 1.0);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Uso: evaluate <dirCranfieldTxt> <queries.tsv> <qrels.tsv> <dirOutput>");
            System.exit(1);
        }
        Path docsDir = Path.of(args[0]);
        Path outDir = Path.of(args[3]);
        Files.createDirectories(outDir);

        // query
        Map<Integer, String> queries = new LinkedHashMap<>();
        for (String l : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            String[] p = l.split("\t", 2);
            queries.put(Integer.parseInt(p[0]), p[1]);
        }
        // giudizi di rilevanza: query -> (doc -> grado)
        Map<Integer, Map<Integer, Integer>> qrels = new HashMap<>();
        for (String l : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
            String[] p = l.split("\t");
            qrels.computeIfAbsent(Integer.parseInt(p[0]), x -> new HashMap<>())
                    .put(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }
        List<Integer> qids = new ArrayList<>();
        for (Integer q : queries.keySet()) if (qrels.containsKey(q)) qids.add(q);

        List<Path> files = Indexer.listTxtFiles(docsDir);
        System.out.printf("Cranfield: %d documenti, %d query con giudizi di rilevanza%n", files.size(), qids.size());

        // risultati[config][mode] = matrice query x metrica
        Map<String, Map<String, double[][]>> results = new LinkedHashMap<>();
        Map<String, Indexer.Stats> idxStats = new LinkedHashMap<>();
        Map<String, Map<String, double[][]>> simResults = new LinkedHashMap<>();
        try (Directory warm = new ByteBuffersDirectory()) { // warm-up JVM per tempi confrontabili
            Indexer.index(files, warm, Analyzers.DEFAULT_CONFIG);
        }

        for (String cfg : Analyzers.CONFIGS.keySet()) {
            try (Directory dir = new ByteBuffersDirectory()) {
                idxStats.put(cfg, Indexer.index(files, dir, cfg));
                try (DirectoryReader reader = DirectoryReader.open(dir)) {
                    IndexSearcher s = new IndexSearcher(reader);
                    // mappa docId Lucene -> id Cranfield (prefisso numerico del nome del file)
                    int[] cranId = new int[reader.maxDoc()];
                    for (int d = 0; d < reader.maxDoc(); d++) {
                        String name = s.storedFields().document(d).get(Analyzers.FIELD_NAME);
                        cranId[d] = Integer.parseInt(name.substring(0, name.indexOf('_')));
                    }
                    Analyzer an = Analyzers.perField(cfg);
                    QueryBuilder qb = new QueryBuilder(an);
                    Map<String, double[][]> byMode = new LinkedHashMap<>();
                    for (String mode : MODES) {
                        byMode.put(mode, runMode(s, cranId, qb, mode, qids, queries, qrels));
                    }
                    if (Analyzers.DEFAULT_CONFIG.equals(cfg)) {
                        // stesso indice, diverse funzioni di ranking (Similarity) a tempo di ricerca
                        for (Map.Entry<String, Similarity> e : SIMILARITIES.entrySet()) {
                            s.setSimilarity(e.getValue());
                            Map<String, double[][]> bySim = new LinkedHashMap<>();
                            for (String mode : SIM_MODES) {
                                bySim.put(mode, runMode(s, cranId, qb, mode, qids, queries, qrels));
                            }
                            simResults.put(e.getKey(), bySim);
                        }
                        s.setSimilarity(new BM25Similarity());
                    }
                    results.put(cfg, byMode);
                }
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Valutazione su Cranfield\n\n");
        md.append(String.format("%d documenti, %d query, profondità ranking %d, similarità BM25 (k1=1.2, b=0.75).%n%n",
                files.size(), qids.size(), DEPTH));
        md.append("Configurazioni:\n\n");
        for (Map.Entry<String, String> e : Analyzers.CONFIGS.entrySet()) {
            md.append("- `").append(e.getKey()).append("`: ").append(e.getValue()).append('\n');
        }
        for (String mode : MODES) {
            md.append("\n## Query sul campo `").append(mode).append("`\n\n");
            md.append("| configurazione |");
            for (String m : METRICS) md.append(' ').append(m).append(" |");
            md.append(" p-value MAP vs standard |\n|---|");
            for (int i = 0; i <= METRICS.length; i++) md.append("---|");
            md.append('\n');
            double[] baseAp = column(results.get("standard").get(mode), 0);
            for (String cfg : results.keySet()) {
                double[][] m = results.get(cfg).get(mode);
                md.append("| `").append(cfg).append("` |");
                for (int j = 0; j < METRICS.length; j++) md.append(String.format(" %.4f |", mean(column(m, j))));
                if ("standard".equals(cfg)) md.append(" – |\n");
                else md.append(String.format(" %.4f |%n", pValue(column(m, 0), baseAp)));
            }
        }
        md.append("\n## Confronto tra funzioni di ranking (Similarity), configurazione `")
                .append(Analyzers.DEFAULT_CONFIG).append("`\n");
        String bm25 = SIMILARITIES.keySet().iterator().next();
        for (String mode : SIM_MODES) {
            md.append("\n### Query sul campo `").append(mode).append("`\n\n| similarity |");
            for (String m : METRICS) md.append(' ').append(m).append(" |");
            md.append(" p-value MAP vs BM25 |\n|---|");
            for (int i = 0; i <= METRICS.length; i++) md.append("---|");
            md.append('\n');
            double[] baseAp = column(simResults.get(bm25).get(mode), 0);
            for (String sim : simResults.keySet()) {
                double[][] m = simResults.get(sim).get(mode);
                md.append("| ").append(sim).append(" |");
                for (int j = 0; j < METRICS.length; j++) md.append(String.format(" %.4f |", mean(column(m, j))));
                if (sim.equals(bm25)) md.append(" – |\n");
                else md.append(String.format(" %.4f |%n", pValue(column(m, 0), baseAp)));
            }
        }
        md.append("\n## Tempi di indicizzazione (indice in RAM)\n\n| configurazione | tempo (s) | doc/s |\n|---|---|---|\n");
        for (Map.Entry<String, Indexer.Stats> e : idxStats.entrySet()) {
            md.append(String.format("| `%s` | %.2f | %.0f |%n", e.getKey(), e.getValue().totalSeconds(), e.getValue().docsPerSecond()));
        }
        System.out.println(md);
        Files.writeString(outDir.resolve("cranfield_eval.md"), md.toString(), StandardCharsets.UTF_8);

        // dettaglio per query della configurazione scelta, campo contenuto
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outDir.resolve("cranfield_per_query.csv"), StandardCharsets.UTF_8))) {
            pw.println("query_id,n_rilevanti," + String.join(",", METRICS) + ",testo");
            double[][] m = results.get(Analyzers.DEFAULT_CONFIG).get(Analyzers.FIELD_CONTENT);
            for (int i = 0; i < qids.size(); i++) {
                StringBuilder sb = new StringBuilder();
                sb.append(qids.get(i)).append(',').append(qrels.get(qids.get(i)).size());
                for (double v : m[i]) sb.append(String.format(java.util.Locale.ROOT, ",%.4f", v));
                sb.append(",\"").append(queries.get(qids.get(i)).replace("\"", "'")).append('"');
                pw.println(sb);
            }
        }
    }

    /** Esegue tutte le query su un campo (o su "tutto") e restituisce la matrice query x metrica. */
    static double[][] runMode(IndexSearcher s, int[] cranId, QueryBuilder qb, String mode, List<Integer> qids,
                              Map<Integer, String> queries, Map<Integer, Map<Integer, Integer>> qrels) throws IOException {
        double[][] m = new double[qids.size()][];
        for (int i = 0; i < qids.size(); i++) {
            String text = queries.get(qids.get(i));
            Query q;
            if ("tutto".equals(mode)) {
                BooleanQuery.Builder b = new BooleanQuery.Builder();
                Query a1 = qb.createBooleanQuery(Analyzers.FIELD_NAME, text);
                Query a2 = qb.createBooleanQuery(Analyzers.FIELD_CONTENT, text);
                if (a1 != null) b.add(a1, Occur.SHOULD);
                if (a2 != null) b.add(a2, Occur.SHOULD);
                q = b.build();
            } else {
                q = qb.createBooleanQuery(mode, text);
            }
            int[] ranking = new int[0];
            if (q != null) {
                TopDocs td = s.search(q, DEPTH);
                ScoreDoc[] sd = td.scoreDocs;
                ranking = new int[sd.length];
                for (int r = 0; r < sd.length; r++) ranking[r] = cranId[sd[r].doc];
            }
            m[i] = metrics(ranking, qrels.get(qids.get(i)));
        }
        return m;
    }

    static double[] column(double[][] m, int j) {
        double[] c = new double[m.length];
        for (int i = 0; i < m.length; i++) c[i] = m[i][j];
        return c;
    }

    static double mean(double[] a) {
        return Arrays.stream(a).average().orElse(0);
    }
}
