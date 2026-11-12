---
title: "Ingegneria dei Dati 2026/2027 – Homework 3"
subtitle: "Indicizzazione e ricerca di file .txt con Apache Lucene"
author: "Luca Peperoni"
date: "13 novembre 2026"
lang: it
geometry: margin=2.2cm
fontsize: 10pt
---

**Repository del progetto:** <https://github.com/lucapeperoni/ingegneria-dati-hw3>

# 1. Il sistema in breve

Il progetto è scritto in Java 11 con Apache Lucene 9.12.1 e si compila con Maven in un unico jar
(`target/hw3.jar`). Ha quattro comandi:

| comando | classe | cosa fa |
|---|---|---|
| `index <dir> <indice>` | `Indexer` | indicizza ricorsivamente tutti i `.txt` della directory |
| `search <indice>` | `Searcher` | legge query da console (o da file), interroga l'indice, stampa i risultati |
| `evaluate …` | `Evaluator` | calcola le metriche di efficacia sulla collezione Cranfield |
| `benchmark <dir> …` | `Benchmark` | misura i tempi di indicizzazione al crescere del numero di file |

Ogni file diventa un documento Lucene con due campi indicizzati, ognuno con il proprio analyzer
(`PerFieldAnalyzerWrapper`):

- **`nome`**: il nome del file senza estensione `.txt`;
- **`contenuto`**: il testo del file (letto come UTF-8, con ripiego su ISO-8859-1 se non valido).

Entrambi i campi sono anche memorizzati, così da poterli mostrare con gli snippet evidenziati
(`UnifiedHighlighter`). Il percorso è salvato in un campo solo memorizzato (`path`). Il nome della
configurazione di analyzer usata viene salvato nei metadati del commit dell'indice: in questo modo il
`Searcher` analizza le query con gli **stessi analyzer** usati in indicizzazione. Il ranking usa BM25
(il default di Lucene, k1 = 1.2, b = 0.75). La sezione 6.1 lo confronta con le altre `Similarity` di
Lucene.

# 2. Costruzione dei dataset

Per misurare i tempi su un numero significativo di file e per valutare il sistema con delle metriche
(in particolare la **recall**, che richiede di sapere *tutti* i documenti rilevanti) ho usato due
dataset pubblici. Li converto in file `.txt` con uno script riproducibile
(`scripts/prepare_data.py`).

**a) Cranfield, 1.400 file: valutazione dell'efficacia.** È una collezione di test classica
dell'Information Retrieval: 1.400 abstract di articoli di aerodinamica, 225 query in linguaggio
naturale e 1.612 giudizi di rilevanza, graduati da 1 (risposta completa) a 4 (interesse minimo).
I giudizi con codice −1, che indicano un documento non rilevante, sono scartati.
Ogni documento diventa un file:

- *nome* = `<id a 4 cifre>_<titolo dell'articolo>`;
- *contenuto* = l'abstract.

```
0001_experimental-investigation-of-the-aerodynamics-of-a-wing-in-a-slipstream.txt
```

Il nome del file contiene quindi un'informazione testuale vera, il titolo, e la valutazione può
riguardare anche il campo `nome`. Le query della collezione hanno id non consecutivi (001, 002, 004…),
mentre i qrels usano la numerazione 1…225: lo script le rinumera in ordine.

**b) 20 Newsgroups, 18.846 file (34,2 MB): tempi e query da console.** Sono messaggi reali di 20
newsgroup Usenet (versione "bydate", train + test). Ogni messaggio diventa un file:

- *nome* = `<newsgroup>_<id>_<subject>`;
- *contenuto* = il messaggio completo (header + corpo).

```
sci.space_60854_space-shuttle-information-wanted.txt
```

Il newsgroup di appartenenza fornisce un **giudizio di rilevanza approssimato** per calcolare P@k
sulle query di test (sezione 5).

# 3. Analyzer scelti

## Campo `nome`: `FilenameAnalyzer` (un `CustomAnalyzer` definito apposta)

Un nome di file non è una frase: le parole sono unite da `_`, `-`, `.` oppure scritte in camelCase
(`RelazioneFinale2024.txt`). Lo `StandardAnalyzer` tratta il trattino basso come parte della parola,
per cui `0001_experimental` o `sci.space_60854` diventano un solo token, e non spezza il camelCase.
La catena scelta è:

1. `PatternTokenizer` sul pattern `[\s._\-]+`: separa sugli spazi e sui separatori tipici dei nomi di file;
2. `WordDelimiterGraphFilter`: spezza camelCase e confini lettere/cifre (`Relazione`, `Finale`, `2024`);
3. `LowerCaseFilter` + `ASCIIFoldingFilter`: ricerca indifferente a maiuscole e accenti;
4. `EnglishMinimalStemFilter`: riduce solo i plurali (`waves` → `wave`). Uno stemming aggressivo
   su testi di poche parole produce più collisioni che benefici.

Non rimuovo le stopword: un nome di file è breve e ogni parola è stata scelta da chi l'ha scritto.

## Campo `contenuto`: `EnglishAnalyzer`

Entrambi i corpora sono in inglese. L'`EnglishAnalyzer` applica `StandardTokenizer`, rimozione del
possessivo, minuscole, stopword inglesi e stemmer di Porter. Lo stemming fa corrispondere le diverse
forme di una parola (`prescribing`/`prescribed`, `medications`/`medication`), e questo aumenta la
recall. Per un corpus in italiano la scelta naturale sarebbe l'`ItalianAnalyzer`, che segue la stessa
logica con stopword ed elisioni italiane.

## Verifica sperimentale della scelta

Non ho scelto gli analyzer solo "a priori": ho confrontato quattro configurazioni su Cranfield
(tabella completa nella sezione 6). Sul campo `contenuto`, l'`EnglishAnalyzer` migliora la MAP del
**+10,2%** rispetto allo `StandardAnalyzer` (0,2956 contro 0,2683, p = 0,0003). Le sole stopword non
cambiano nulla (p = 0,87). Il guadagno viene quindi dallo **stemming**: Porter supera di poco KStem in
MAP e nDCG@10. Sul campo `nome`, il `FilenameAnalyzer` migliora la MAP del **+12,7%** rispetto allo
`StandardAnalyzer` (0,2110 contro 0,1873, p = 0,0007).

# 4. Numero di file indicizzati e tempi di indicizzazione

Macchina: Apple M4 Pro, 24 GB RAM, SSD, OpenJDK 11.0.26. Indice su disco, RAM buffer 256 MB, un
solo thread. Ogni valore è la **mediana di 3 ripetizioni**, dopo un giro di warm-up della JVM.
I sottoinsiemi sono estratti a caso, con seme fisso, dai 18.846 file di 20 Newsgroups.

| file | MB | tempo totale (s) | lettura (s) | analisi + indicizzazione (s) | commit (s) | doc/s | MB/s | indice (MB) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1.000 | 1,9 | 0,12 | 0,02 | 0,07 | 0,03 | 8.304 | 15,6 | 2,0 |
| 2.500 | 4,5 | 0,28 | 0,05 | 0,16 | 0,08 | 8.796 | 15,9 | 4,9 |
| 5.000 | 8,9 | 0,56 | 0,11 | 0,33 | 0,11 | 8.991 | 16,0 | 9,4 |
| 10.000 | 17,9 | 1,02 | 0,21 | 0,65 | 0,16 | 9.850 | 17,6 | 18,9 |
| **18.846** | **34,2** | **1,96** | 0,39 | 1,28 | 0,29 | **9.616** | 17,5 | 35,5 |

*Configurazione scelta. I valori per le altre configurazioni sono in `results/indexing_times.md`.*

- **Indice completo: 18.846 file in circa 2 secondi**, cioè circa 9.600 documenti al secondo. Il tempo
  cresce in modo **lineare** con il numero di file: il throughput è quasi costante fra 2.500 e 18.846
  documenti. Sotto i mille file pesano i costi fissi, come l'apertura dell'indice e il commit.
- Circa due terzi del tempo è **analisi e indicizzazione**, un quinto è lettura da disco, il resto è il
  commit finale.
- L'indice occupa più o meno quanto i dati, perché entrambi i campi sono anche memorizzati.
- Le quattro configurazioni hanno tempi simili, fra 1,80 e 1,96 s sull'intero dataset: lo stemming
  costa circa il 9% in più rispetto alla sola rimozione delle stopword.
- Cranfield, 1.400 file e 1,6 MB, si indicizza in circa 0,1 s.
- La prima esecuzione "a freddo" del comando `index`, che include l'avvio della JVM e la compilazione
  JIT, impiega 3,25 s.

# 5. Query usate per testare il sistema

**Sintassi.** `<campo> elementi…`

- `campo` è `nome`, `contenuto` oppure `tutto` (nome **o** contenuto).
- Un elemento è una parola oppure una frase tra virgolette, che diventa una `PhraseQuery`.
- Gli elementi sono in OR, ordinati per punteggio BM25. Il prefisso `+` rende un elemento
  obbligatorio, `-` lo esclude.
- Ogni elemento è analizzato con l'analyzer del campo, tramite `QueryBuilder` di Lucene.
- Una parola che l'analyzer spezza in più token (es. `sci.space`) richiede tutti i pezzi.
- `:k N` cambia il numero di risultati, `:q` esce.

L'output mostra la query Lucene generata, il numero di risultati, il tempo e, per ogni risultato,
punteggio, nome del file e snippet con i termini trovati fra parentesi quadre.

Ho eseguito 12 query sull'indice di 20 Newsgroups (`eval/query_20news.txt`). Per ognuna ho indicato in
anticipo il newsgroup "giusto" e ho calcolato P@10 e P@50 con `scripts/eval_20news.py`.
L'output completo è in `results/query_20news_output.txt`.

| # | query | aspetto testato | newsgroup rilevanti | risultati | P@10 | P@50 |
|---|---|---|---|---:|---:|---:|
| Q1 | `nome space shuttle` | termini in OR nel nome | sci.space | 994 | 1,00 | 1,00 |
| Q2 | `nome "for sale"` | phrase query nel nome | misc.forsale | 494 | 0,80 | 0,92 |
| Q3 | `nome windows` | termine singolo nel nome | comp.os.ms-windows.misc, comp.windows.x | 2.015 | 1,00 | 1,00 |
| Q4 | `nome sci.med` | separatori del nome file | sci.med | 990 | 1,00 | 1,00 |
| Q5 | `contenuto "space shuttle"` | phrase query + stemming | sci.space | 60 | 1,00 | 0,96 |
| Q6 | `contenuto "stanley cup"` | phrase query | rec.sport.hockey | 102 | 1,00 | 1,00 |
| Q7 | `contenuto encryption clipper chip` | OR pesato da BM25 | sci.crypt | 1.058 | 1,00 | 1,00 |
| Q8 | `contenuto "second amendment"` | phrase query ambigua | talk.politics.guns | 87 | 0,70 | 0,68 |
| Q9 | `contenuto +god -jesus` | operatori `+` e `-` | alt.atheism, soc.religion.christian, talk.religion.misc | 1.020 | 0,90 | 0,92 |
| Q10 | `contenuto doctor prescribing medications` | stemming | sci.med | 696 | 1,00 | 0,92 |
| Q11 | `tutto motorcycle helmet` | nome **o** contenuto | rec.motorcycles | 1.065 | 1,00 | 1,00 |
| Q12 | `tutto +"engine oil" car` | frase obbligatoria su due campi | rec.autos | 8 | 0,40 | 0,08 |
| | **media** | | | | **0,90** | **0,87** |

Tutte le query rispondono in pochi millisecondi (0,6–10 ms). Osservazioni:

- **Q8**: tre risultati su dieci vengono da `sci.crypt`. Sono copie, inviate a più newsgroup, del
  comunicato della Casa Bianca sul chip Clipper, che cita esplicitamente il secondo emendamento. Sono
  pertinenti alla frase cercata ma non al newsgroup: l'approssimazione "per newsgroup" quindi
  **sottostima** la precisione.
- **Q12**: la frase obbligatoria `"engine oil"` compare solo in 8 documenti, quindi P@10 e P@50 sono
  limitate dal numero di risultati. È un esempio di come un vincolo troppo stretto penalizzi la
  **recall**.
- **Q1–Q4**: i nomi dei file contengono il newsgroup (`sci.space_…`). Per questo le query sul nome sono
  "facili" e la parola `space` trova anche messaggi di sci.space che non parlano di shuttle. È un
  effetto del modo in cui ho costruito i nomi dei file, e lo segnalo per correttezza.
- **Phrase query e stopword**: `contenuto "freedom of speech"` diventa `contenuto:"freedom ? speech"`.
  La stopword lascia un "buco" di posizione, per cui la frase trova anche "freedom" *qualunque parola*
  "speech". Una frase fatta di sole stopword (`"to be or not to be"`) produce invece una query vuota,
  e il sistema lo segnala.

# 6. Valutazione con metriche (Cranfield)

Valutare la recall su 20 Newsgroups richiederebbe di giudicare tutti i 18.846 documenti per ogni
query. Per questo la valutazione quantitativa usa Cranfield, dove i giudizi di rilevanza esistono già.
Per ognuna delle 225 query (testo in linguaggio naturale, termini in OR, BM25) recupero i primi 1.000
documenti e calcolo:

- **MAP**;
- **P@5** e **P@10**: precisione nei primi k risultati;
- **R@10**, **R@100** e **R@1000**: frazione dei documenti rilevanti trovati entro la posizione k;
- **nDCG@10**: tiene conto dei gradi di rilevanza, con guadagno = 5 − grado.

Per la significatività statistica uso un *randomization test* appaiato, a due code, sulla AP di
ciascuna query (10.000 permutazioni), rispetto alla baseline `standard`.

**Query sul campo `contenuto`**

| configurazione | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p (MAP) |
|-----------------|--------:|--------:|--------:|--------:|--------:|--------:|--------:|--------:|
| standard | 0,2683 | 0,2951 | 0,2164 | 0,3678 | 0,6975 | 0,9619 | 0,3338 | – |
| stop | 0,2686 | 0,3058 | 0,2196 | 0,3716 | 0,6979 | 0,9294 | 0,3330 | 0,868 |
| kstem | 0,2886 | 0,3227 | 0,2231 | 0,3788 | 0,7247 | 0,9522 | 0,3464 | 0,001 |
| **scelta** | **0,2956** | 0,3120 | **0,2289** | **0,3914** | **0,7307** | 0,9515 | **0,3589** | **0,0003** |

**Query sul campo `nome`**

| configurazione | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p (MAP) |
|-----------------|--------:|--------:|--------:|--------:|--------:|--------:|--------:|--------:|
| standard | 0,1873 | 0,2222 | 0,1533 | 0,2510 | 0,5302 | 0,8365 | 0,2505 | – |
| **Filename** | **0,2110** | **0,2284** | **0,1693** | **0,2768** | **0,5840** | **0,8911** | **0,2703** | **0,0007** |

**Query su nome e contenuto (`tutto`)**

| configurazione | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p (MAP) |
|-----------------|--------:|--------:|--------:|--------:|--------:|--------:|--------:|--------:|
| standard | 0,2774 | 0,2942 | 0,2142 | 0,3605 | 0,6971 | 0,9583 | 0,3421 | – |
| **scelta** | **0,3017** | **0,3067** | **0,2369** | **0,4068** | **0,7503** | **0,9624** | **0,3707** | **0,0006** |

*Configurazioni: `standard` = StandardAnalyzer su entrambi i campi; `stop` = stopword senza stemming;
`kstem` = stopword + KStem; `scelta` = FilenameAnalyzer + EnglishAnalyzer. Sul campo `nome` le ultime
tre configurazioni coincidono, perché usano tutte il FilenameAnalyzer. Il dettaglio per query è in
`results/cranfield_per_query.csv`.*

Osservazioni:

- **La recall cresce molto con la profondità.** Entro i primi 10 risultati si trova solo il 39% dei
  documenti rilevanti, entro 100 il 73%, entro 1.000 il 95%. La precisione resta bassa (P@10 circa 0,23)
  perché le query Cranfield hanno in media appena 7,2 documenti rilevanti su 1.400. Anche un sistema
  perfetto avrebbe P@10 medio pari a 0,61.
- **Lo stemming aumenta la recall nelle prime posizioni** (R@100 da 0,698 a 0,731). Rimuovere le
  stopword senza stemming abbassa invece R@1000 (0,93 contro 0,96): con meno termini in OR, alcune
  query recuperano meno documenti in assoluto.
- **Interrogare nome e contenuto insieme (`tutto`) dà il risultato migliore** (MAP 0,30). Il titolo
  nel nome del file aggiunge un segnale utile a quello dell'abstract.
- Il campo `nome` da solo rende meno del contenuto (MAP 0,21 contro 0,30): un titolo ha poche parole
  e molti documenti rilevanti non condividono termini col testo della query.

## 6.1 Confronto tra funzioni di ranking (Similarity)

In Lucene il punteggio di un documento è calcolato da una `Similarity`, che si imposta
sull'`IndexSearcher` prima di eseguire le query. Ho confrontato le quattro viste a lezione, a parità di
indice e di analyzer (configurazione `scelta`):

- **BM25**: il default di Lucene;
- **ClassicSimilarity**: TF-IDF;
- due modelli basati su **Language Model**:
  - **LMDirichlet**, con il parametro di default mu = 2000;
  - **LMJelinekMercer**, con lambda = 0,7.

Ho potuto riusare lo stesso indice perché in Lucene 9 tutte queste Similarity memorizzano allo stesso
modo la lunghezza dei documenti. Il p-value è calcolato rispetto a BM25.

**Query sul campo `contenuto`**

| similarity | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p (MAP) |
|-----------------|--------:|--------:|--------:|--------:|--------:|--------:|--------:|--------:|
| **BM25** | **0,2956** | 0,3120 | **0,2289** | **0,3914** | 0,7307 | 0,9515 | **0,3589** | – |
| TF-IDF | 0,2925 | 0,3120 | 0,2249 | 0,3810 | 0,7373 | 0,9505 | 0,3534 | 0,630 |
| LM Dirichlet | 0,2343 | 0,2338 | 0,1844 | 0,3263 | 0,6620 | 0,9508 | 0,2905 | 0,0001 |
| LM Jelinek-Mercer | 0,2758 | 0,2996 | 0,2129 | 0,3740 | 0,7153 | 0,9523 | 0,3411 | 0,0006 |

**Query su nome e contenuto (`tutto`)**

| similarity | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p (MAP) |
|-----------------|--------:|--------:|--------:|--------:|--------:|--------:|--------:|--------:|
| **BM25** | **0,3017** | **0,3067** | **0,2369** | **0,4068** | **0,7503** | **0,9624** | **0,3707** | – |
| TF-IDF | 0,2585 | 0,2756 | 0,2067 | 0,3449 | 0,6952 | 0,9520 | 0,3228 | 0,0001 |
| LM Dirichlet | 0,2572 | 0,2569 | 0,1938 | 0,3435 | 0,6926 | 0,9619 | 0,3137 | 0,0001 |
| LM Jelinek-Mercer | 0,2875 | 0,2978 | 0,2231 | 0,3835 | 0,7296 | 0,9617 | 0,3529 | 0,0001 |

Osservazioni:

- **Sul solo contenuto BM25 e TF-IDF sono equivalenti.** La differenza di MAP (0,296 contro 0,293) non
  è statisticamente significativa (p = 0,63).
- **Interrogando due campi insieme BM25 è nettamente migliore**: TF-IDF perde il 14% di MAP. La causa
  più probabile è la diversa normalizzazione per lunghezza. In TF-IDF il peso di un termine trovato in
  un campo cortissimo come il titolo diventa molto alto e domina il punteggio. In BM25 il contributo di
  ogni termine ha un tetto (saturazione, parametro k1) e la normalizzazione è più moderata
  (parametro b).
- **LM Dirichlet con mu = 2000 è il peggiore**: −21% di MAP sul contenuto. Il parametro mu va scelto in
  proporzione alla lunghezza tipica dei documenti, mentre gli abstract Cranfield hanno in media circa
  160 parole. Con mu così grande il modello "liscia" troppo verso le statistiche dell'intera collezione,
  e perde la distinzione fra documenti che contengono molte volte i termini della query e documenti
  che li contengono poco.
- La **R@1000** è praticamente uguale per tutte (circa 0,95): le Similarity cambiano l'**ordine** dei
  risultati, non quali documenti vengono trovati. I documenti recuperati dipendono solo dalla query e
  dall'analyzer.
- Conclusione: **ho mantenuto BM25**, che è il default di Lucene, il migliore o alla pari in tutti i
  casi, e non richiede di tarare parametri sulla collezione.

# 7. Come riprodurre

```bash
python3 scripts/prepare_data.py     # crea data/cranfield e data/20news
mvn package                         # Java 11+, Maven 3
java -jar target/hw3.jar index data/20news index/20news
java -jar target/hw3.jar search index/20news   # console interattiva
java -jar target/hw3.jar evaluate data/cranfield \
     eval/cranfield_queries.tsv eval/cranfield_qrels.tsv results
java -jar target/hw3.jar benchmark data/20news results
python3 scripts/eval_20news.py
```
