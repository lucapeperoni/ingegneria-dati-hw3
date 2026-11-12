package idd.hw3;

import java.util.Arrays;

/** Punto di ingresso: java -jar target/hw3.jar &lt;comando&gt; [argomenti]. */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Comandi:");
            System.err.println("  index     <dirDocumenti> <dirIndice> [configurazione]");
            System.err.println("  search    <dirIndice> [--file query.txt] [--k N]");
            System.err.println("  evaluate  <dirCranfieldTxt> <queries.tsv> <qrels.tsv> <dirOutput>");
            System.err.println("  benchmark <dirDocumenti> <dirOutput>");
            System.err.println("Configurazioni analyzer: " + Analyzers.CONFIGS.keySet() + " (default: " + Analyzers.DEFAULT_CONFIG + ")");
            System.exit(1);
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "index": Indexer.main(rest); break;
            case "search": Searcher.main(rest); break;
            case "evaluate": Evaluator.main(rest); break;
            case "benchmark": Benchmark.main(rest); break;
            default:
                System.err.println("Comando sconosciuto: " + args[0]);
                System.exit(1);
        }
    }
}
