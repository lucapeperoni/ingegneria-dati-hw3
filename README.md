# Ingegneria dei Dati 2026/27 – Homework 3

Indicizzazione e ricerca di file `.txt` con **Apache Lucene 9.12** (Java 11, Maven).
Due campi: `nome` (nome del file) e `contenuto` (testo), ciascuno con un analyzer dedicato.
La relazione è in [`relazione.md`](relazione.md) / `relazione.pdf`.

## Struttura

```
src/main/java/idd/hw3/
  Analyzers.java           analyzer dei due campi (FilenameAnalyzer, EnglishAnalyzer, varianti di confronto)
  Indexer.java             indicizza ricorsivamente i .txt di una directory
  ConsoleQueryParser.java  sintassi delle query: <nome|contenuto|tutto> parole "frasi" +obbligatorio -escluso
  Searcher.java            console interattiva / esecuzione di un file di query
  Evaluator.java           MAP, P@k, R@k, nDCG@10 + randomization test su Cranfield
  Benchmark.java           tempi di indicizzazione al crescere del numero di file
scripts/prepare_data.py    costruisce i dataset .txt (Cranfield, 20 Newsgroups)
scripts/eval_20news.py     esegue le query di test e calcola P@10/P@50
eval/                      query e giudizi di rilevanza
results/                   risultati delle esecuzioni riportate nella relazione
```

## Esecuzione

```bash
# 1. dataset (scarica e converte in .txt)
mkdir -p raw/cran raw/20news
curl -L http://ir.dcs.gla.ac.uk/resources/test_collections/cran/cran.tar.gz | tar xz -C raw/cran
curl -L http://qwone.com/~jason/20Newsgroups/20news-bydate.tar.gz | tar xz -C raw/20news
python3 scripts/prepare_data.py

# 2. build
mvn package

# 3. indicizzazione e ricerca
java -jar target/hw3.jar index data/20news index/20news
java -jar target/hw3.jar search index/20news
```

Esempi di query:

```
query> nome space shuttle
query> contenuto "stanley cup"
query> contenuto +god -jesus
query> tutto motorcycle helmet
query> :k 20        (numero di risultati)   :q  (esci)
```

Valutazione e benchmark:

```bash
java -jar target/hw3.jar evaluate data/cranfield eval/cranfield_queries.tsv eval/cranfield_qrels.tsv results
java -jar target/hw3.jar benchmark data/20news results
python3 scripts/eval_20news.py
```
