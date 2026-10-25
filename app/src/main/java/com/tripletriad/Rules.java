package com.tripletriad;

/** Règles optionnelles (la règle de base est toujours active). */
public final class Rules {
    public final boolean same;    // Identique
    public final boolean plus;    // Plus
    public final boolean combo;   // Captures en chaîne
    public final boolean wall;    // Même Mur : Identique + les bords du plateau comptent comme un 10 (A)
    public final boolean broken;  // Mur cassé : plus de bords, le plateau se rejoint (gauche-droite, haut-bas)
    public final int size;        // 3 (plateau 3x3, 5 cartes) ou 5 (plateau 5x5, 13 cartes)

    public Rules(boolean same, boolean plus, boolean combo) { this(same, plus, combo, false, false, false); }
    public Rules(boolean same, boolean plus, boolean combo, boolean wall) { this(same, plus, combo, wall, false, false); }
    public Rules(boolean same, boolean plus, boolean combo, boolean wall, boolean broken) {
        this(same, plus, combo, wall, broken, false);
    }

    public Rules(boolean same, boolean plus, boolean combo, boolean wall, boolean broken, boolean big) {
        this.same = same;
        this.plus = plus;
        this.combo = combo;
        this.wall = wall;
        this.broken = broken;
        this.size = big ? 5 : 3;
    }

    /** Cartes en main : 5 sur 3x3, 13 sur 5x5. */
    public int handSize() { return size == 5 ? 13 : 5; }

    /** Encodage compact pour l'IRC et les Intent : "101001" = Identique + Combo + 5x5. */
    public String bits() {
        return (same ? "1" : "0") + (plus ? "1" : "0") + (combo ? "1" : "0") + (wall ? "1" : "0")
                + (broken ? "1" : "0") + (size == 5 ? "1" : "0");
    }

    public static Rules fromBits(String b) {
        if (b == null || b.length() < 3) return new Rules(false, false, false);
        return new Rules(b.charAt(0) == '1', b.charAt(1) == '1', b.charAt(2) == '1',
                b.length() > 3 && b.charAt(3) == '1', b.length() > 4 && b.charAt(4) == '1',
                b.length() > 5 && b.charAt(5) == '1');
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(size == 5 ? "Plateau 5x5 : Base" : "Base");
        if (same) sb.append(" + Identique");
        if (plus) sb.append(" + Plus");
        if (combo) sb.append(" + Combo");
        if (wall) sb.append(" + Même Mur");
        if (broken) sb.append(" + Mur cassé");
        return sb.toString();
    }
}
