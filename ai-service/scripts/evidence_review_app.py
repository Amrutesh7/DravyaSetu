import csv
import tkinter as tk
from tkinter import ttk, messagebox
from pathlib import Path
from collections import defaultdict
import webbrowser
import urllib.parse
import html

BASE = Path(r"D:\DravyaSetu\ai-service\data\DravyaSetu_v2_1492")
META = BASE / "metadata"
REVIEW_CSV = META / "full_evidence_audit" / "full_observation_evidence_review_merged.csv"
SHEET_DIR = META / "evidence_audit_sheets"
DECISIONS_CSV = META / "full_evidence_audit" / "evidence_review_decisions.csv"

LABELS = [
    "WHOLE_PLANT", "LEAF", "FLOWER", "FRUIT",
    "STEM_BARK", "MULTI_PART", "OTHER", "UNCLEAR"
]
CONFIDENCES = ["HIGH", "MEDIUM", "LOW"]

def read_csv(path):
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))

def write_csv(path, rows, fields):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    with temp.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    temp.replace(path)

class Reviewer:
    def __init__(self, root):
        self.root = root
        root.title("DravyaSetu — Evidence Review")
        root.geometry("1000x760")

        if not REVIEW_CSV.exists():
            messagebox.showerror("Missing file", f"Review CSV not found:\n{REVIEW_CSV}")
            root.destroy()
            return

        self.rows = read_csv(REVIEW_CSV)
        self.by_key = {
            (r["class_name"], r["observation_id"]): r
            for r in self.rows
        }

        self.sheets = {}
        for p in SHEET_DIR.glob("*.jpg"):
            stem = p.stem
            if "_obs_" in stem:
                cls, obs = stem.rsplit("_obs_", 1)
                self.sheets[(cls, obs)] = p

        self.decisions = {}
        if DECISIONS_CSV.exists():
            for r in read_csv(DECISIONS_CSV):
                key = (r["class_name"], r["observation_id"])
                if key in self.by_key:
                    self.decisions[key] = r

        self.keys = sorted(
            [k for k in self.by_key if k in self.sheets],
            key=lambda k: (k[0].lower(), int(k[1]) if k[1].isdigit() else k[1])
        )
        self.index = 0

        top = ttk.Frame(root, padding=10)
        top.pack(fill="x")
        self.progress = ttk.Label(top, text="")
        self.progress.pack(side="left")
        ttk.Button(top, text="Open sheet image", command=self.open_image).pack(side="right")

        self.plant = ttk.Label(root, text="", font=("Segoe UI", 17, "bold"))
        self.plant.pack(pady=(12, 4))
        self.obs = ttk.Label(root, text="", font=("Segoe UI", 11))
        self.obs.pack()

        self.image_info = ttk.Label(root, text="", wraplength=900)
        self.image_info.pack(pady=5)

        form = ttk.Frame(root, padding=12)
        form.pack(fill="x")

        ttk.Label(form, text="Evidence type").grid(row=0, column=0, sticky="w", pady=6)
        self.label_var = tk.StringVar(value="UNCLEAR")
        ttk.Combobox(
            form, textvariable=self.label_var, values=LABELS,
            state="readonly", width=24
        ).grid(row=0, column=1, sticky="w", padx=10)

        ttk.Label(form, text="Confidence").grid(row=1, column=0, sticky="w", pady=6)
        self.conf_var = tk.StringVar(value="MEDIUM")
        ttk.Combobox(
            form, textvariable=self.conf_var, values=CONFIDENCES,
            state="readonly", width=24
        ).grid(row=1, column=1, sticky="w", padx=10)

        ttk.Label(form, text="Notes").grid(row=2, column=0, sticky="nw", pady=6)
        self.notes = tk.Text(form, width=72, height=4, wrap="word")
        self.notes.grid(row=2, column=1, sticky="ew", padx=10)
        form.columnconfigure(1, weight=1)

        buttons = ttk.Frame(root, padding=10)
        buttons.pack(fill="x")
        ttk.Button(buttons, text="← Previous", command=self.previous).pack(side="left")
        ttk.Button(buttons, text="Save decision", command=self.save).pack(side="left", padx=8)
        ttk.Button(buttons, text="Save + Next →", command=self.save_next).pack(side="left")
        ttk.Button(buttons, text="Next →", command=self.next).pack(side="left", padx=8)

        self.status = ttk.Label(root, text=f"Decisions file: {DECISIONS_CSV}", wraplength=900)
        self.status.pack(pady=8)

        self.show_current()

    def current_key(self):
        return self.keys[self.index] if self.keys else None

    def open_image(self):
        key = self.current_key()
        if key:
            webbrowser.open(self.sheets[key].resolve().as_uri())

    def show_current(self):
        if not self.keys:
            self.plant.config(text="No matching sheets found")
            return

        key = self.current_key()
        row = self.by_key[key]
        decision = self.decisions.get(key)
        self.plant.config(text=key[0])
        self.obs.config(text=f"Observation ID: {key[1]}  |  Images in manifest: {row.get('image_count', '')}")
        self.image_info.config(text=f"Sheet: {self.sheets[key].name}\nOpen sheet image to inspect it at full size.")

        if decision:
            self.label_var.set(decision.get("evidence_type") or "UNCLEAR")
            self.conf_var.set(decision.get("confidence") or "MEDIUM")
            self.notes.delete("1.0", "end")
            self.notes.insert("1.0", decision.get("notes", ""))
        else:
            self.label_var.set("UNCLEAR")
            self.conf_var.set("MEDIUM")
            self.notes.delete("1.0", "end")

        self.progress.config(
            text=f"Sheet {self.index + 1} of {len(self.keys)} | "
                 f"Saved decisions: {len(self.decisions)} | "
                 f"Original reviewed: {sum(r.get('reviewed','').upper() == 'TRUE' for r in self.rows)}"
        )

    def save(self):
        key = self.current_key()
        if not key:
            return

        label = self.label_var.get()
        confidence = self.conf_var.get()
        notes = self.notes.get("1.0", "end").strip()

        self.decisions[key] = {
            "class_name": key[0],
            "observation_id": key[1],
            "evidence_type": label,
            "confidence": confidence,
            "notes": notes,
            "reviewed": "TRUE",
        }

        fields = ["class_name", "observation_id", "evidence_type", "confidence", "notes", "reviewed"]
        ordered = [self.decisions[k] for k in sorted(self.decisions)]
        write_csv(DECISIONS_CSV, ordered, fields)
        self.status.config(text=f"Saved: {key[0]} / {key[1]}")

    def save_next(self):
        self.save()
        self.next()

    def previous(self):
        if self.index > 0:
            self.index -= 1
            self.show_current()

    def next(self):
        if self.index < len(self.keys) - 1:
            self.index += 1
            self.show_current()

if __name__ == "__main__":
    root = tk.Tk()
    app = Reviewer(root)
    root.mainloop()
