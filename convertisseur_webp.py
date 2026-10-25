"""
Convertisseur d'images PNG / JPG -> WebP
Interface simple : choisir des fichiers ou un dossier entier.

Installation (une seule fois) :
    pip install pillow

Lancement :
    python convertisseur_webp.py
"""

import queue
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, ttk

from PIL import Image, ImageOps

EXTENSIONS = {".png", ".jpg", ".jpeg"}


def convertir_image(src: Path, dest: Path, qualite: int, sans_perte: bool):
    """Convertit une image en WebP et l'enregistre dans dest."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(src) as img:
        img = ImageOps.exif_transpose(img)  # respecte l'orientation des photos
        # WebP gère RGB et RGBA ; on convertit les autres modes (P, CMYK, L...)
        if img.mode in ("P", "LA"):
            img = img.convert("RGBA")
        elif img.mode not in ("RGB", "RGBA"):
            img = img.convert("RGB")
        if sans_perte:
            img.save(dest, "WEBP", lossless=True, method=6)
        else:
            img.save(dest, "WEBP", quality=qualite, method=6)


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Convertisseur WebP")
        self.geometry("560x460")
        self.minsize(480, 400)

        self.file_queue = queue.Queue()
        self.en_cours = False

        # --- Boutons de sélection ---
        haut = ttk.Frame(self, padding=10)
        haut.pack(fill="x")
        ttk.Button(haut, text="📄 Choisir des images...", command=self.choisir_fichiers).pack(
            side="left", expand=True, fill="x", padx=(0, 5)
        )
        ttk.Button(haut, text="📁 Choisir un dossier...", command=self.choisir_dossier).pack(
            side="left", expand=True, fill="x", padx=(5, 0)
        )

        # --- Options ---
        opts = ttk.LabelFrame(self, text="Options", padding=10)
        opts.pack(fill="x", padx=10)

        ligne_q = ttk.Frame(opts)
        ligne_q.pack(fill="x")
        ttk.Label(ligne_q, text="Qualité :").pack(side="left")
        self.qualite = tk.IntVar(value=85)
        self.curseur = ttk.Scale(
            ligne_q, from_=1, to=100, variable=self.qualite, command=self._maj_qualite
        )
        self.curseur.pack(side="left", fill="x", expand=True, padx=8)
        self.lbl_qualite = ttk.Label(ligne_q, text="85", width=4)
        self.lbl_qualite.pack(side="left")

        self.sans_perte = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            opts, text="Sans perte (lossless, fichiers plus lourds)", variable=self.sans_perte,
            command=self._maj_etat_curseur,
        ).pack(anchor="w", pady=(6, 0))

        self.sous_dossiers = tk.BooleanVar(value=True)
        ttk.Checkbutton(
            opts, text="Inclure les sous-dossiers (pour un dossier)", variable=self.sous_dossiers
        ).pack(anchor="w")

        self.supprimer = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            opts, text="Supprimer les originaux après conversion (⚠ définitif)",
            variable=self.supprimer,
        ).pack(anchor="w")

        # --- Progression + journal ---
        self.barre = ttk.Progressbar(self, mode="determinate")
        self.barre.pack(fill="x", padx=10, pady=(10, 5))

        cadre_log = ttk.Frame(self)
        cadre_log.pack(fill="both", expand=True, padx=10, pady=(0, 10))
        self.log = tk.Text(cadre_log, height=10, state="disabled", wrap="none")
        scroll = ttk.Scrollbar(cadre_log, command=self.log.yview)
        self.log.configure(yscrollcommand=scroll.set)
        self.log.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        self.ecrire("Choisis des images ou un dossier pour commencer.")
        self.after(100, self._traiter_messages)

    # ---------- Petites fonctions utiles ----------
    def _maj_qualite(self, _=None):
        self.lbl_qualite.config(text=str(int(float(self.qualite.get()))))

    def _maj_etat_curseur(self):
        self.curseur.state(["disabled"] if self.sans_perte.get() else ["!disabled"])

    def ecrire(self, texte):
        self.log.config(state="normal")
        self.log.insert("end", texte + "\n")
        self.log.see("end")
        self.log.config(state="disabled")

    # ---------- Sélection ----------
    def choisir_fichiers(self):
        if self.en_cours:
            return
        chemins = filedialog.askopenfilenames(
            title="Choisir des images",
            filetypes=[("Images", "*.png *.jpg *.jpeg *.PNG *.JPG *.JPEG")],
        )
        if not chemins:
            return
        # Chaque image est convertie dans un sous-dossier "webp" à côté de l'original
        taches = []
        for c in chemins:
            p = Path(c)
            taches.append((p, p.parent / "webp" / (p.stem + ".webp")))
        self.lancer(taches)

    def choisir_dossier(self):
        if self.en_cours:
            return
        d = filedialog.askdirectory(title="Choisir un dossier d'images")
        if not d:
            return
        racine = Path(d)
        sortie = racine.parent / (racine.name + "_webp")  # ex : images -> images_webp
        pattern = racine.rglob("*") if self.sous_dossiers.get() else racine.glob("*")
        taches = []
        for p in pattern:
            if p.is_file() and p.suffix.lower() in EXTENSIONS:
                dest = sortie / p.relative_to(racine).with_suffix(".webp")
                taches.append((p, dest))
        if not taches:
            self.ecrire("Aucune image PNG/JPG trouvée dans ce dossier.")
            return
        self.lancer(taches)

    # ---------- Conversion (dans un thread pour ne pas figer la fenêtre) ----------
    def lancer(self, taches):
        self.en_cours = True
        self.barre.config(maximum=len(taches), value=0)
        self.ecrire(f"\n▶ Conversion de {len(taches)} image(s)...")
        q = int(float(self.qualite.get()))
        sans_perte = self.sans_perte.get()
        supprimer = self.supprimer.get()
        threading.Thread(
            target=self._travail, args=(taches, q, sans_perte, supprimer), daemon=True
        ).start()

    def _travail(self, taches, qualite, sans_perte, supprimer):
        ok, erreurs, avant, apres = 0, 0, 0, 0
        for src, dest in taches:
            try:
                convertir_image(src, dest, qualite, sans_perte)
                avant += src.stat().st_size
                apres += dest.stat().st_size
                if supprimer:
                    src.unlink()
                ok += 1
                self.file_queue.put(("log", f"✔ {src.name}"))
            except Exception as e:
                erreurs += 1
                self.file_queue.put(("log", f"✘ {src.name} : {e}"))
            self.file_queue.put(("progres", None))
        gain = (1 - apres / avant) * 100 if avant else 0
        resume = f"Terminé : {ok} converties, {erreurs} erreur(s)."
        if ok:
            resume += f" Poids : {avant/1e6:.1f} Mo → {apres/1e6:.1f} Mo ({gain:.0f}% de gagné)."
        self.file_queue.put(("fin", resume))

    def _traiter_messages(self):
        try:
            while True:
                type_, valeur = self.file_queue.get_nowait()
                if type_ == "log":
                    self.ecrire(valeur)
                elif type_ == "progres":
                    self.barre.step(1)
                elif type_ == "fin":
                    self.ecrire("\n" + valeur)
                    self.en_cours = False
        except queue.Empty:
            pass
        self.after(100, self._traiter_messages)


if __name__ == "__main__":
    App().mainloop()
