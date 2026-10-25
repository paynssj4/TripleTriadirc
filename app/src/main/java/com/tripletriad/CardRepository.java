package com.tripletriad;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** cards.json (assets) + collection, deck et or du joueur (SharedPreferences). */
public final class CardRepository {
    private static final String PREFS = "tt";
    public static final int DECK_SIZE = 5;
    /** Niveaux accessibles dès le départ ; les cartes de niveau supérieur se gagnent (ou s'achèteront plus tard). */
    public static final int FREE_MAX_LEVEL = 5;

    private final SharedPreferences prefs;
    private final Map<String, Card> byName = new LinkedHashMap<>();

    public CardRepository(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try (InputStream in = ctx.getAssets().open("cards.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONArray arr = new JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray nums = o.getJSONArray("numbers");
                int[] numbers = new int[4];
                for (int k = 0; k < 4; k++) numbers[k] = nums.getInt(k);
                String image = o.optString("image", "");
                image = image.substring(image.lastIndexOf('/') + 1);
                byName.put(o.getString("name"),
                        new Card(o.getString("name"), o.getString("attribute"), image, numbers));
            }
        } catch (Exception e) {
            throw new RuntimeException("Impossible de lire assets/cards.json", e);
        }
    }

    public List<Card> all() { return new ArrayList<>(byName.values()); }

    // ---------- collection (25 cartes au départ, puis cartes gagnées / achetées) ----------

    public static final int COLLECTION_SIZE = 25;

    private List<Card> storedCollection() { return cardsFromCsv(prefs.getString("collection", "")); }

    /** Collection du joueur, dans l'ordre des numéros de carte. */
    public List<Card> collection() {
        Set<String> names = new HashSet<>();
        for (Card c : storedCollection()) names.add(c.name);
        List<Card> l = new ArrayList<>();
        for (Card c : all()) if (names.contains(c.name)) l.add(c);
        return l;
    }

    public boolean hasCollection() { return collection().size() >= COLLECTION_SIZE; }

    public boolean isOwned(Card c) {
        for (Card o : storedCollection()) if (o.name.equals(c.name)) return true;
        return false;
    }

    /** Cartes que l'on peut choisir en créant sa collection (niveaux bas à moyens). */
    public boolean isStartable(Card c) { return c.level <= FREE_MAX_LEVEL; }

    public void setCollection(List<Card> cards) { prefs.edit().putString("collection", csv(cards)).apply(); }

    public void addToCollection(Card c) {
        Card canon = byName.get(c.name);
        if (canon == null || isOwned(canon)) return;
        List<Card> l = storedCollection();
        l.add(canon);
        setCollection(l);
    }

    // ---------- mains : 5 cartes (plateau 3x3) ou 13 cartes (plateau 5x5) ----------

    public static final int BIG_DECK_SIZE = 13;

    public List<Card> deck() { return deck(DECK_SIZE); }

    /** Main de handSize cartes issues de la collection, ou liste vide si elle n'est pas (ou plus) valide. */
    public List<Card> deck(int handSize) {
        List<Card> d = cardsFromCsv(prefs.getString(handSize == BIG_DECK_SIZE ? "deck13" : "deck", ""));
        if (d.size() != handSize) return new ArrayList<>();
        Set<String> owned = new HashSet<>();
        for (Card c : storedCollection()) owned.add(c.name);
        for (Card c : d) if (!owned.contains(c.name)) return new ArrayList<>();
        return d;
    }

    public boolean hasDeck() { return hasDeck(DECK_SIZE); }
    public boolean hasDeck(int handSize) { return deck(handSize).size() == handSize; }

    public void saveDeck(List<Card> deck) {
        prefs.edit().putString(deck.size() == BIG_DECK_SIZE ? "deck13" : "deck", csv(deck)).apply();
    }

    /** Deck du bot : tiré au hasard à chaque partie, niveaux proches de ceux du joueur (±1). */
    public List<Card> aiDeckFor(List<Card> playerDeck) {
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

    public int gold() { return prefs.getInt("gold", 100); }
    public void addGold(int n) { prefs.edit().putInt("gold", gold() + n).apply(); }

    // ---------- utilitaires ----------

    public List<Card> cardsFromCsv(String csv) {
        List<Card> l = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return l;
        for (String n : csv.split(",")) {
            Card c = byName.get(n);
            if (c != null) l.add(c);
        }
        return l;
    }

    public static String csv(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) { if (sb.length() > 0) sb.append(','); sb.append(c.name); }
        return sb.toString();
    }
}
