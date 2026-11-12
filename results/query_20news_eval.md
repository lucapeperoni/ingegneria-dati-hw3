# Query di test su 20 Newsgroups

Rilevanza approssimata: un documento è rilevante se appartiene al newsgroup indicato.

| # | query | newsgroup rilevanti | risultati | tempo (ms) | P@10 | P@50 |
|---|---|---|---|---|---|---|
| Q1 | `nome space shuttle` | sci.space | 994 | 11,4 | 1.00 | 1.00 |
| Q2 | `nome "for sale"` | misc.forsale | 494 | 4,9 | 0.80 | 0.92 |
| Q3 | `nome windows` | comp.os.ms-windows.misc comp.windows.x | 2015 | 0,6 | 1.00 | 1.00 |
| Q4 | `nome sci.med` | sci.med | 990 | 1,5 | 1.00 | 1.00 |
| Q5 | `contenuto "space shuttle"` | sci.space | 60 | 1,5 | 1.00 | 0.96 |
| Q6 | `contenuto "stanley cup"` | rec.sport.hockey | 102 | 1,2 | 1.00 | 1.00 |
| Q7 | `contenuto encryption clipper chip` | sci.crypt | 1058 | 2,2 | 1.00 | 1.00 |
| Q8 | `contenuto "second amendment"` | talk.politics.guns | 87 | 1,8 | 0.70 | 0.68 |
| Q9 | `contenuto +god -jesus` | alt.atheism soc.religion.christian talk.religion.misc | 1020 | 3,0 | 0.90 | 0.92 |
| Q10 | `contenuto doctor prescribing medications` | sci.med | 696 | 1,6 | 1.00 | 0.92 |
| Q11 | `tutto motorcycle helmet` | rec.motorcycles | 1065 | 1,9 | 1.00 | 1.00 |
| Q12 | `tutto +"engine oil" car` | rec.autos | 8 | 5,9 | 0.40 | 0.08 |
| | **media** | | | | **0.90** | **0.87** |
