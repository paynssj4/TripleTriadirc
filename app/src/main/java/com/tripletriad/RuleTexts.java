package com.tripletriad;

/** Explications courtes des règles (bulles "?" de l'écran principal) + page du tutoriel correspondante. */
final class RuleTexts {
    private RuleTexts() { }

    static final class Info {
        final String title, text;
        final int page;   // page du tutoriel qui détaille la règle
        Info(String title, String text, int page) { this.title = title; this.text = text; this.page = page; }
    }

    static Info of(String key) {
        switch (key) {
            case "same":
                return new Info("Identique",
                        "Si votre carte touche au moins deux cartes dont le nombre en contact est égal au sien, "
                                + "toutes les cartes adverses concernées sont capturées, même si leurs nombres sont plus grands.", 2);
            case "plus":
                return new Info("Plus",
                        "Pour chaque carte voisine, additionnez les deux nombres qui se touchent. "
                                + "Si au moins deux voisines donnent la même somme, les cartes adverses concernées sont capturées.", 3);
            case "combo":
                return new Info("Combo",
                        "Les cartes capturées attaquent à leur tour leurs propres voisines (règle de base) : "
                                + "les captures s'enchaînent tant qu'un nombre est plus grand.", 4);
            case "wall":
                return new Info("Même Mur",
                        "Comme Identique, mais les bords du plateau comptent comme une carte de valeur A (10). "
                                + "Un côté A tourné vers un bord compte comme une égalité.", 5);
            case "broken":
                return new Info("Mur cassé (bêta)",
                        "Plus de bords : le plateau se referme sur lui-même (gauche ↔ droite, haut ↔ bas). "
                                + "Toute carte a donc quatre voisines. Même Mur n'a plus d'effet dans ce mode.", 6);
            case "big":
                return new Info("Plateau 5×5",
                        "25 cases et 13 cartes en main (à composer avec « MA MAIN 5×5 »). "
                                + "Les règles sont les mêmes que sur le plateau 3×3.", 7);
            default:
                return null;
        }
    }
}
