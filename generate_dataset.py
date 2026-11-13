import os
import re
from sklearn.datasets import fetch_20newsgroups

TOTAL_DOCS = 10000
CORPUS_DIR = "data/corpus"
GT_FILE = "data/ground_truth.tsv"

CATEGORIES = {
    "space": "sci.space",
    "med": "sci.med",
    "tech": "comp.graphics",
    "sport": "rec.sport.baseball",
    "politics": "talk.politics.guns"
}

# Parole usate nelle nostre 10 query: le rimuoviamo dal testo base per avere un conteggio esatto dei rilevanti
RESERVED = re.compile(
    r"\b(lunar|module|apollo11|rtx4090|rtx3080|rtx|4090|vaccin\w*|magnetic|resonance|imaging|retriev\w*|information|ballistic|missile\w*|nuclear)\b",
    re.IGNORECASE
)

os.makedirs(CORPUS_DIR, exist_ok=True)
for f in os.listdir(CORPUS_DIR):
    if f.endswith(".txt"):
        os.remove(os.path.join(CORPUS_DIR, f))

print("Scaricamento 20 Newsgroups da scikit-learn...")
texts = {}
for short_name, cat_name in CATEGORIES.items():
    data = fetch_20newsgroups(subset="all", categories=[cat_name], remove=("headers", "footers", "quotes"))
    clean_list = []
    for doc in data.data:
        cleaned = RESERVED.sub("system", doc)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        if len(cleaned) > 100:
            clean_list.append(cleaned[:1200])
    texts[short_name] = clean_list

gt = {f"Q{i}": [] for i in range(1, 11)}
docs_per_cat = TOTAL_DOCS // len(CATEGORIES)
doc_id = 1

print(f"Creazione di {TOTAL_DOCS} file .txt in {CORPUS_DIR}...")

for cat in ["space", "med", "tech", "sport", "politics"]:
    pool = texts[cat]
    for i in range(1, docs_per_cat + 1):
        body = pool[(i - 1) % len(pool)]
        filename = f"{cat}_doc_{doc_id:05d}.txt"
        extra = ""

        # File con frasi note per testare le 10 query e calcolare Precision e Recall
        if cat == "space":
            if 1 <= i <= 45:
                filename = f"space_apollo11_lunar-module_{doc_id:05d}.txt"
                gt["Q1"].append(filename)
                gt["Q2"].append(filename)
            elif 46 <= i <= 60:
                filename = f"space_lunar_rover_command_module_{doc_id:05d}.txt"
                gt["Q1"].append(filename)
            elif 61 <= i <= 95:
                extra = " Observations measure planetary magnetic fields and orbital resonance phenomena."

        elif cat == "tech":
            if 1 <= i <= 50:
                filename = f"tech_gpu_rtx4090_benchmark_{doc_id:05d}.txt"
                gt["Q3"].append(filename)
                gt["Q4"].append(filename)
            elif 51 <= i <= 70:
                filename = f"tech_gpu_rtx3080_benchmark_{doc_id:05d}.txt"
            elif 71 <= i <= 95:
                extra = " High dynamic range digital imaging sensors calibrate pixel luminance."
            elif 96 <= i <= 125:
                extra = " Search engines accelerate the fast retrieval of information in databases."
                gt["Q9"].append(filename)
            elif 126 <= i <= 140:
                extra = " Inverted indices are retrieving the information with low latency."
                gt["Q9"].append(filename)
            elif 141 <= i <= 145:
                extra = " Protocols govern the retrieval and archival storage of confidential information."

        elif cat == "med":
            if 1 <= i <= 30:
                extra = " Clinics scheduled seasonal vaccinations for vulnerable patients."
                gt["Q5"].append(filename)
            elif 31 <= i <= 55:
                extra = " Records confirm that vaccinated individuals developed strong immunity."
                gt["Q5"].append(filename)
            elif 56 <= i <= 80:
                extra = " Medical teams are vaccinating hospital staff during the campaign."
                gt["Q5"].append(filename)
            elif 81 <= i <= 114:
                extra = " Radiology departments use magnetic resonance imaging to detect lesions."
                gt["Q6"].append(filename)
                gt["Q7"].append(filename)
                gt["Q8"].append(filename)
            elif 115 <= i <= 120:
                extra = " Researchers apply magnetic resonance functional imaging to map brain activity."
                gt["Q6"].append(filename)
                gt["Q7"].append(filename)
                gt["Q8"].append(filename)

        elif cat == "politics":
            if 1 <= i <= 45:
                extra = " Ministries signed an agreement on conventional ballistic missile defense."
                gt["Q10"].append(filename)
            elif 46 <= i <= 50:
                extra = " Analysts reviewed conventional ballistic missile systems under non-nuclear treaties."
                gt["Q10"].append(filename)
            elif 51 <= i <= 80:
                extra = " Cold war doctrines relied on intercontinental ballistic missile fleets with nuclear warheads."

        with open(os.path.join(CORPUS_DIR, filename), "w", encoding="utf-8") as f:
            f.write(body + extra + "\n")

        doc_id += 1

queries = [
    ("Q1", "nome lunar"),
    ("Q2", 'nome "lunar module"'),
    ("Q3", "nome rtx 4090"),
    ("Q4", "nome +rtx +4090"),
    ("Q5", "contenuto vaccinations"),
    ("Q6", "contenuto magnetic resonance imaging"),
    ("Q7", 'contenuto "magnetic resonance imaging"'),
    ("Q8", 'contenuto "magnetic resonance imaging"~2'),
    ("Q9", 'contenuto "retrieval of information"'),
    ("Q10", "contenuto +ballistic +missile -nuclear")
]

with open(GT_FILE, "w", encoding="utf-8") as f:
    for qid, qstr in queries:
        f.write(f"{qid}\t{qstr}\t{','.join(gt[qid])}\n")

print("Fatto! File generati e Ground Truth salvata in", GT_FILE)