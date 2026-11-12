package idd.hw3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.QueryBuilder;

/**
 * Sintassi semplice per le query da console.
 *
 * <pre>
 *   query    := campo elemento+
 *   campo    := "nome" | "contenuto" | "tutto"        (anche "nome:" ecc.)
 *   elemento := [+|-] parola | [+|-] "frase tra virgolette"
 * </pre>
 *
 * <ul>
 *   <li>più elementi sono in OR (i documenti che ne contengono di più salgono nel ranking);</li>
 *   <li>{@code +} rende obbligatorio un elemento, {@code -} lo esclude;</li>
 *   <li>un testo tra virgolette diventa una PhraseQuery (termini adiacenti e in ordine);</li>
 *   <li>{@code tutto} cerca sia nel nome sia nel contenuto.</li>
 * </ul>
 * Ogni elemento è analizzato con lo stesso analyzer usato in indicizzazione per quel campo.
 */
public final class ConsoleQueryParser {

    /** Risultato del parsing. */
    public static final class Parsed {
        public final String field;   // nome | contenuto | tutto
        public final Query query;

        Parsed(String field, Query query) {
            this.field = field;
            this.query = query;
        }
    }

    private final QueryBuilder builder;

    public ConsoleQueryParser(Analyzer perFieldAnalyzer) {
        this.builder = new QueryBuilder(perFieldAnalyzer);
    }

    /** Un elemento della query: testo, se è una frase, e l'operatore (+, -, nessuno). */
    static final class Element {
        final String text;
        final boolean phrase;
        final Occur occur;

        Element(String text, boolean phrase, Occur occur) {
            this.text = text;
            this.phrase = phrase;
            this.occur = occur;
        }
    }

    /** Spezza la parte dopo il campo in parole e frasi tra virgolette. */
    static List<Element> tokenize(String s) {
        List<Element> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= n) break;
            Occur occ = Occur.SHOULD;
            char c = s.charAt(i);
            if ((c == '+' || c == '-') && i + 1 < n && !Character.isWhitespace(s.charAt(i + 1))) {
                occ = c == '+' ? Occur.MUST : Occur.MUST_NOT;
                i++;
            }
            if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '“' || s.charAt(i) == '”')) {
                int end = i + 1;
                while (end < n && s.charAt(end) != '"' && s.charAt(end) != '”' && s.charAt(end) != '“') end++;
                out.add(new Element(s.substring(i + 1, end), true, occ));
                i = Math.min(end + 1, n);
            } else {
                int end = i;
                while (end < n && !Character.isWhitespace(s.charAt(end))) end++;
                out.add(new Element(s.substring(i, end), false, occ));
                i = end;
            }
        }
        return out;
    }

    static String normalizeField(String f) {
        f = f.toLowerCase(Locale.ROOT);
        if (f.endsWith(":")) f = f.substring(0, f.length() - 1);
        switch (f) {
            case "nome":
            case "name":
                return Analyzers.FIELD_NAME;
            case "contenuto":
            case "content":
                return Analyzers.FIELD_CONTENT;
            case "tutto":
            case "all":
                return "tutto";
            default:
                return null;
        }
    }

    /** Query su un singolo campo per un singolo elemento (null se l'analyzer non produce termini). */
    private Query elementQuery(String field, Element e) {
        if (e.phrase) {
            return builder.createPhraseQuery(field, e.text);
        }
        // una "parola" che l'analyzer spezza in più token (es. sci.space) richiede tutti i pezzi
        return builder.createBooleanQuery(field, e.text, Occur.MUST);
    }

    public Parsed parse(String line) {
        String trimmed = line.trim();
        int sp = trimmed.indexOf(' ');
        if (sp < 0) {
            throw new IllegalArgumentException("La query deve essere: <nome|contenuto|tutto> termini...");
        }
        String field = normalizeField(trimmed.substring(0, sp));
        if (field == null) {
            throw new IllegalArgumentException("Campo sconosciuto '" + trimmed.substring(0, sp)
                    + "': usare nome, contenuto oppure tutto");
        }
        List<Element> elems = tokenize(trimmed.substring(sp + 1));
        if (elems.isEmpty()) {
            throw new IllegalArgumentException("Nessun termine di ricerca");
        }

        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        int added = 0;
        for (Element e : elems) {
            Query q;
            if ("tutto".equals(field)) {
                Query qn = elementQuery(Analyzers.FIELD_NAME, e);
                Query qc = elementQuery(Analyzers.FIELD_CONTENT, e);
                BooleanQuery.Builder both = new BooleanQuery.Builder();
                if (qn != null) both.add(qn, Occur.SHOULD);
                if (qc != null) both.add(qc, Occur.SHOULD);
                q = (qn == null && qc == null) ? null : both.build();
            } else {
                q = elementQuery(field, e);
            }
            if (q != null) { // null = solo stopword
                bq.add(q, e.occur);
                added++;
            }
        }
        if (added == 0) {
            throw new IllegalArgumentException("La query contiene solo stopword: nessun termine da cercare");
        }
        return new Parsed(field, bq.build());
    }
}
