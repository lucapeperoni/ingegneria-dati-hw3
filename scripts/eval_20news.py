#!/usr/bin/env python3
"""Esegue le query di eval/query_20news.txt sull'indice di 20 Newsgroups e calcola P@10 e P@50,
usando il newsgroup di appartenenza come giudizio di rilevanza (approssimazione).

Uso (dalla root del progetto, dopo aver costruito l'indice in index/20news):
    python3 scripts/eval_20news.py
Scrive results/query_20news_output.txt (output completo) e results/query_20news_eval.md.
"""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QFILE = ROOT / "eval" / "query_20news.txt"
K = 50

# query e newsgroup rilevanti
queries, rel = [], []
pending = None
for line in QFILE.read_text(encoding="utf-8").splitlines():
    m = re.match(r"#\s*rilevanti:\s*(.*)", line)
    if m:
        pending = set(m.group(1).split())
    elif line.strip() and not line.startswith("#"):
        queries.append(line.strip())
        rel.append(pending or set())
        pending = None

out = subprocess.run(
    ["java", "-jar", "target/hw3.jar", "search", "index/20news",
     "--file", "eval/query_20news.txt", "--k", str(K)],
    cwd=ROOT, capture_output=True, text=True, check=True).stdout
(ROOT / "results" / "query_20news_output.txt").write_text(out, encoding="utf-8")

blocks = re.split(r"\nQ\d+> ", out)[1:]
rows = []
for q, groups, block in zip(queries, rel, blocks):
    hits = re.search(r"Risultati: (\d+)\+? documenti in ([\d,.]+) ms", block)
    names = re.findall(r"^\s+\d+\. \[[\d,.]+\] (.+?)\.txt$", block, re.M)
    names = [n.replace("[", "").replace("]", "") for n in names]
    found = [n.split("_", 1)[0] in groups for n in names]
    p10 = sum(found[:10]) / 10
    p50 = sum(found[:50]) / 50
    rows.append((q, " ".join(sorted(groups)), int(hits.group(1)), hits.group(2), p10, p50))

md = ["# Query di test su 20 Newsgroups\n",
      "Rilevanza approssimata: un documento è rilevante se appartiene al newsgroup indicato.\n",
      "| # | query | newsgroup rilevanti | risultati | tempo (ms) | P@10 | P@50 |",
      "|---|---|---|---|---|---|---|"]
for i, (q, g, n, t, p10, p50) in enumerate(rows, 1):
    md.append(f"| Q{i} | `{q}` | {g} | {n} | {t} | {p10:.2f} | {p50:.2f} |")
mp10 = sum(r[4] for r in rows) / len(rows)
mp50 = sum(r[5] for r in rows) / len(rows)
md.append(f"| | **media** | | | | **{mp10:.2f}** | **{mp50:.2f}** |")
text = "\n".join(md) + "\n"
(ROOT / "results" / "query_20news_eval.md").write_text(text, encoding="utf-8")
print(text)
