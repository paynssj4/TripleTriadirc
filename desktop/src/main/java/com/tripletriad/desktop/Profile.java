package com.tripletriad.desktop;

import com.tripletriad.Card;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Équivalent PC de CardRepository : collection, mains, or et préférences (fichier ~/.tripletriad/profile.properties). */
final class Profile {
    static final int COLLECTION_SIZE = 25, DECK_SIZE = 5, BIG_DECK_SIZE = 13, FREE_MAX_LEVEL = 5;

    private final Properties props = new Properties();
    private final Path file = Paths.get(System.getProperty("user.home"), ".tripletriad", "profile.properties");
    private final Map<String, Card> byName = new LinkedHashMap<>();

    Profile(List<Card> all) {
        for (Card c : all) byName.put(c.name, c);
        if (Files.isRegularFile(file)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException ignored) { }
        }
    }

    // ---------- préférences ----------
    String get(String k, String def) { return props.getProperty(k, def); }
    boolean getBool(String k, boolean def) { return Boolean.parseBoolean(props.getProperty(k, String.valueOf(def))); }
    void set(String k, String v) { props.setProperty(k, v); save(); }
    void setBool(String k, boolean v) { set(k, String.valueOf(v)); }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { props.store(w, "Triple Triad"); }
        } catch (IOException ignored) { }
    }

    // ---------- collection ----------
    List<Card> all() { return new ArrayList<>(byName.values()); }

    private List<Card> stored() { return cardsFromCsv(get("collection", "")); }

    List<Card> collection() {
        Set<String> names = new HashSet<>();
        for (Card c : stored()) names.add(c.name);
        List<Card> l = new ArrayList<>();
        for (Card c : all()) if (names.contains(c.name)) l.add(c);
        return l;
    }

    boolean hasCollection() { return collection().size() >= COLLECTION_SIZE; }

    boolean isOwned(Card c) {
        for (Card o : stored()) if (o.name.equals(c.name)) return true;
        return false;
    }

    void setCollection(List<Card> cards) { set("collection", csv(cards)); }

    void addToCollection(Card c) {
        Card canon = byName.get(c.name);
        if (canon == null || isOwned(canon)) return;
        List<Card> l = stored();
        l.add(canon);
        setCollection(l);
    }

    // ---------- mains (5 cartes en 3x3, 13 en 5x5) ----------
    List<Card> deck(int handSize) {
        List<Card> d = cardsFromCsv(get(handSize == BIG_DECK_SIZE ? "deck13" : "deck", ""));
        if (d.size() != handSize) return new ArrayList<>();
        Set<String> owned = new HashSet<>();
        for (Card c : stored()) owned.add(c.name);
        for (Card c : d) if (!owned.contains(c.name)) return new ArrayList<>();
        return d;
    }

    boolean hasDeck(int handSize) { return deck(handSize).size() == handSize; }

    void saveDeck(List<Card> deck) { set(deck.size() == BIG_DECK_SIZE ? "deck13" : "deck", csv(deck)); }

    /** Main du bot : tirée au hasard, niveaux proches de ceux du joueur (±1). */
    List<Card> aiDeckFor(List<Card> playerDeck) {
        double avg = 0;
        for (Card c : playerDeck) avg += c.level;
        avg = playerDeck.isEmpty() ? 3 : avg / playerDeck.size();
        int lo = Math.max(1, (int) Math.round(avg) - 1), hi = Math.min(10, (int) Math.round(avg) + 1);
        List<Card> band = new ArrayList<>();
        for (Card c : all()) if (c.level >= lo && c.level <= hi) band.add(c);
        int n = Math.max(DECK_SIZE, playerDeck.size());
        if (band.size() < n) band = all();
        Collections.shuffle(band);
        return new ArrayList<>(band.subList(0, n));
    }

    // ---------- or ----------
    int gold() { try { return Integer.parseInt(get("gold", "100")); } catch (NumberFormatException e) { return 100; } }
    void addGold(int n) { set("gold", String.valueOf(gold() + n)); }

    // ---------- utilitaires ----------
    List<Card> cardsFromCsv(String csv) {
        List<Card> l = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return l;
        for (String n : csv.split(",")) {
            Card c = byName.get(n);
            if (c != null) l.add(c);
        }
        return l;
    }

    static String csv(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) { if (sb.length() > 0) sb.append(','); sb.append(c.name); }
        return sb.toString();
    }
}
