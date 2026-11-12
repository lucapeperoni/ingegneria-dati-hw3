# Valutazione su Cranfield

1400 documenti, 225 query, profondità ranking 1000, similarità BM25 (k1=1.2, b=0.75).

Configurazioni:

- `standard`: nome: StandardAnalyzer | contenuto: StandardAnalyzer (nessuna stopword, nessuno stemming)
- `stop`: nome: FilenameAnalyzer | contenuto: Standard + stopword inglesi (senza stemming)
- `kstem`: nome: FilenameAnalyzer | contenuto: Standard + stopword + KStem (stemming leggero)
- `scelta`: nome: FilenameAnalyzer | contenuto: EnglishAnalyzer (stopword + Porter stemmer)

## Query sul campo `contenuto`

| configurazione | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p-value MAP vs standard |
|---|---|---|---|---|---|---|---|---|
| `standard` | 0,2683 | 0,2951 | 0,2164 | 0,3678 | 0,6975 | 0,9619 | 0,3338 | – |
| `stop` | 0,2686 | 0,3058 | 0,2196 | 0,3716 | 0,6979 | 0,9294 | 0,3330 | 0,8678 |
| `kstem` | 0,2886 | 0,3227 | 0,2231 | 0,3788 | 0,7247 | 0,9522 | 0,3464 | 0,0007 |
| `scelta` | 0,2956 | 0,3120 | 0,2289 | 0,3914 | 0,7307 | 0,9515 | 0,3589 | 0,0003 |

## Query sul campo `nome`

| configurazione | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p-value MAP vs standard |
|---|---|---|---|---|---|---|---|---|
| `standard` | 0,1873 | 0,2222 | 0,1533 | 0,2510 | 0,5302 | 0,8365 | 0,2505 | – |
| `stop` | 0,2110 | 0,2284 | 0,1693 | 0,2768 | 0,5840 | 0,8911 | 0,2703 | 0,0007 |
| `kstem` | 0,2110 | 0,2284 | 0,1693 | 0,2768 | 0,5840 | 0,8911 | 0,2703 | 0,0007 |
| `scelta` | 0,2110 | 0,2284 | 0,1693 | 0,2768 | 0,5840 | 0,8911 | 0,2703 | 0,0007 |

## Query sul campo `tutto`

| configurazione | MAP | P@5 | P@10 | R@10 | R@100 | R@1000 | nDCG@10 | p-value MAP vs standard |
|---|---|---|---|---|---|---|---|---|
| `standard` | 0,2774 | 0,2942 | 0,2142 | 0,3605 | 0,6971 | 0,9583 | 0,3421 | – |
| `stop` | 0,2836 | 0,3031 | 0,2253 | 0,3738 | 0,7194 | 0,9536 | 0,3474 | 0,2414 |
| `kstem` | 0,2987 | 0,3156 | 0,2329 | 0,3937 | 0,7425 | 0,9676 | 0,3650 | 0,0008 |
| `scelta` | 0,3017 | 0,3067 | 0,2369 | 0,4068 | 0,7503 | 0,9624 | 0,3707 | 0,0006 |

## Tempi di indicizzazione (indice in RAM)

| configurazione | tempo (s) | doc/s |
|---|---|---|
| `standard` | 0,13 | 11185 |
| `stop` | 0,12 | 12041 |
| `kstem` | 0,14 | 9974 |
| `scelta` | 0,09 | 16233 |
