#!/usr/bin/env python3
"""Costruisce i dataset di file .txt usati nell'Homework 3.

1) data/cranfield/   1400 file .txt dalla collezione Cranfield.
   Nome file  = <id a 4 cifre>_<titolo dell'articolo>.txt
   Contenuto  = abstract (campo .W della collezione originale)
   Inoltre:   eval/cranfield_queries.tsv  (id \t testo query)
              eval/cranfield_qrels.tsv    (id query \t id doc \t grado 1-4)

2) data/20news/      18.846 file .txt dal dataset 20 Newsgroups (bydate).
   Nome file  = <newsgroup>_<id messaggio>_<subject>.txt
   Contenuto  = messaggio completo (header + corpo), convertito in UTF-8

Uso: python3 scripts/prepare_data.py   (dalla root del progetto)
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw"
DATA = ROOT / "data"
EVAL = ROOT / "eval"

MAX_NAME_WORDS = 12


def slug(text: str, max_words: int = MAX_NAME_WORDS) -> str:
    """Trasforma un titolo/subject in un nome di file leggibile: parole separate da '-'."""
    text = text.lower()
    text = re.sub(r"^(re|fwd?)\s*:\s*", "", text)  # toglie Re:/Fwd: dei subject
    words = re.findall(r"[a-z0-9]+", text)
    return "-".join(words[:max_words]) or "senza-titolo"


# ---------------------------------------------------------------- Cranfield
def parse_cranfield_blocks(path: Path):
    """Restituisce una lista di dict {campo: testo} per ogni record .I."""
    records, cur, field = [], None, None
    for line in path.read_text(encoding="latin-1").splitlines():
        if line.startswith(".I "):
            if cur is not None:
                records.append(cur)
            cur, field = {"I": line[3:].strip()}, None
        elif re.match(r"^\.[TABW]$", line.strip()):
            field = line.strip()[1]
            cur[field] = ""
        elif field is not None:
            cur[field] += line + "\n"
    if cur is not None:
        records.append(cur)
    return records


def build_cranfield():
    out = DATA / "cranfield"
    out.mkdir(parents=True, exist_ok=True)
    docs = parse_cranfield_blocks(RAW / "cran" / "cran.all.1400")
    for d in docs:
        doc_id = int(d["I"])
        title = " ".join(d.get("T", "").split()).rstrip(" .")
        body = d.get("W", "").strip()
        name = f"{doc_id:04d}_{slug(title)}.txt"
        (out / name).write_text(body + "\n", encoding="utf-8")

    # Le query in cran.qry hanno id non consecutivi (001, 002, 004, ...),
    # mentre i qrels usano la numerazione progressiva 1..225: si rinumerano.
    EVAL.mkdir(exist_ok=True)
    queries = parse_cranfield_blocks(RAW / "cran" / "cran.qry")
    with open(EVAL / "cranfield_queries.tsv", "w", encoding="utf-8") as f:
        for i, q in enumerate(queries, start=1):
            f.write(f"{i}\t{' '.join(q['W'].split())}\n")

    n_rel = 0
    with open(EVAL / "cranfield_qrels.tsv", "w", encoding="utf-8") as f:
        for line in (RAW / "cran" / "cranqrel").read_text().splitlines():
            qid, did, grade = line.split()
            if 1 <= int(grade) <= 4:  # -1 = giudizio "non rilevante": scartato
                f.write(f"{qid}\t{did}\t{grade}\n")
                n_rel += 1
    print(f"Cranfield: {len(docs)} documenti, {len(queries)} query, {n_rel} giudizi di rilevanza")


# ------------------------------------------------------------ 20 Newsgroups
def build_20news():
    out = DATA / "20news"
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for split in ("20news-bydate-train", "20news-bydate-test"):
        for group_dir in sorted((RAW / "20news" / split).iterdir()):
            if not group_dir.is_dir():
                continue
            for f in sorted(group_dir.iterdir()):
                text = f.read_bytes().decode("latin-1")
                m = re.search(r"^Subject:\s*(.*)$", text, re.M)
                subject = slug(m.group(1) if m else "", 8)
                name = f"{group_dir.name}_{f.name}_{subject}.txt"
                (out / name).write_text(text, encoding="utf-8")
                n += 1
    print(f"20 Newsgroups: {n} file")


if __name__ == "__main__":
    which = sys.argv[1:] or ["cranfield", "20news"]
    if "cranfield" in which:
        build_cranfield()
    if "20news" in which:
        build_20news()
