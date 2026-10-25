package com.tripletriad;

/** Une carte. numbers = {Nord, Est, Sud, Ouest}. */
public final class Card {
    public static final int N = 0, E = 1, S = 2, W = 3;

    public final String name;
    public final String attribute;
    public final String image;      // nom de fichier dans assets/Img (ex: "001.png")
    public final int[] numbers;
    public final int level;                  // 1..10 (cartes 001-011 = niv. 1, 012-022 = niv. 2, ...)
    public Owner owner = Owner.NONE;
    public Owner origin = Owner.NONE;        // à qui appartenait la carte au départ

    public Card(String name, String attribute, String image, int[] numbers) {
        this.name = name;
        this.attribute = attribute;
        this.image = image;
        this.numbers = numbers.clone();
        int lv = 1;
        try { lv = Math.max(1, Math.min(10, (Integer.parseInt(name) - 1) / 11 + 1)); }
        catch (NumberFormatException ignored) {}
        this.level = lv;
    }

    /** Copie indépendante (le propriétaire est mutable pendant une partie). */
    public Card copy() {
        Card c = new Card(name, attribute, image, numbers);
        c.owner = owner;
        c.origin = origin;
        return c;
    }
}
