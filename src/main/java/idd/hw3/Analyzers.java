package idd.hw3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilterFactory;
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory;
import org.apache.lucene.analysis.en.KStemFilterFactory;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.pattern.PatternTokenizerFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;

/**
 * Definizione degli analyzer usati per i due campi dell'indice.
 *
 * <p>Esistono più "configurazioni" (coppie analyzer-nome / analyzer-contenuto) così da poterle
 * confrontare sperimentalmente con l'{@link Evaluator}. La configurazione usata di default è
 * {@link #DEFAULT_CONFIG}.
 */
public final class Analyzers {

    public static final String FIELD_NAME = "nome";
    public static final String FIELD_CONTENT = "contenuto";
    public static final String FIELD_PATH = "path";

    public static final String DEFAULT_CONFIG = "scelta";

    /** Descrizione leggibile delle configurazioni disponibili (ordine = ordine nelle tabelle). */
    public static final Map<String, String> CONFIGS = new LinkedHashMap<>();

    static {
        CONFIGS.put("standard", "nome: StandardAnalyzer | contenuto: StandardAnalyzer (nessuna stopword, nessuno stemming)");
        CONFIGS.put("stop", "nome: FilenameAnalyzer | contenuto: Standard + stopword inglesi (senza stemming)");
        CONFIGS.put("kstem", "nome: FilenameAnalyzer | contenuto: Standard + stopword + KStem (stemming leggero)");
        CONFIGS.put("scelta", "nome: FilenameAnalyzer | contenuto: EnglishAnalyzer (stopword + Porter stemmer)");
    }

    private Analyzers() {}

    /**
     * Analyzer per il nome del file. I nomi di file non sono frasi: le parole sono unite da
     * separatori come '_', '-', '.', oppure scritte in camelCase ("RelazioneFinale2024").
     * <ul>
     *   <li>PatternTokenizer: spezza su spazi, '_', '-', '.'</li>
     *   <li>WordDelimiterGraphFilter: spezza camelCase e confini lettere/cifre</li>
     *   <li>LowerCase + ASCIIFolding: ricerca case-insensitive e senza accenti</li>
     *   <li>EnglishMinimalStem: solo singolare/plurale (stemming aggressivo inutile su testi così brevi)</li>
     * </ul>
     * Non rimuove stopword: in un nome di file ogni parola è stata scelta da chi l'ha scritto.
     */
    public static Analyzer filenameAnalyzer() {
        try {
            Map<String, String> wdgf = new HashMap<>();
            wdgf.put("generateWordParts", "1");
            wdgf.put("generateNumberParts", "1");
            wdgf.put("splitOnCaseChange", "1");
            wdgf.put("splitOnNumerics", "1");
            wdgf.put("preserveOriginal", "0");
            return CustomAnalyzer.builder()
                    .withTokenizer(PatternTokenizerFactory.class, "pattern", "[\\s._\\-]+", "group", "-1")
                    .addTokenFilter(WordDelimiterGraphFilterFactory.class, wdgf)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .addTokenFilter(ASCIIFoldingFilterFactory.class)
                    .addTokenFilter(EnglishMinimalStemFilterFactory.class)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Standard + stopword inglesi, opzionalmente con KStem. */
    private static Analyzer contentCustom(boolean kstem) {
        try {
            CustomAnalyzer.Builder b = CustomAnalyzer.builder()
                    .withTokenizer(StandardTokenizerFactory.class)
                    .addTokenFilter(EnglishPossessiveFilterFactory.class)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .addTokenFilter(StopFilterFactory.class); // default: stopword inglesi di Lucene
            if (kstem) {
                b.addTokenFilter(KStemFilterFactory.class);
            }
            return b.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Analyzer del campo contenuto per la configurazione indicata. */
    public static Analyzer contentAnalyzer(String config) {
        switch (config) {
            case "standard":
                return new StandardAnalyzer(CharArraySet.EMPTY_SET);
            case "stop":
                return contentCustom(false);
            case "kstem":
                return contentCustom(true);
            case "scelta":
                return new EnglishAnalyzer();
            default:
                throw new IllegalArgumentException("Configurazione sconosciuta: " + config + " (valide: " + CONFIGS.keySet() + ")");
        }
    }

    /** Analyzer del campo nome per la configurazione indicata. */
    public static Analyzer nameAnalyzer(String config) {
        if (!CONFIGS.containsKey(config)) {
            throw new IllegalArgumentException("Configurazione sconosciuta: " + config + " (valide: " + CONFIGS.keySet() + ")");
        }
        return "standard".equals(config) ? new StandardAnalyzer(CharArraySet.EMPTY_SET) : filenameAnalyzer();
    }

    /** Analyzer complessivo: un analyzer diverso per ciascun campo. */
    public static Analyzer perField(String config) {
        Map<String, Analyzer> m = new HashMap<>();
        m.put(FIELD_NAME, nameAnalyzer(config));
        m.put(FIELD_CONTENT, contentAnalyzer(config));
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), m);
    }
}
