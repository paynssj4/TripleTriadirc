package com.tripletriad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Moteur de jeu pur Java (aucune dépendance Android) : plateau 3x3 ou 5x5, mains, règles, captures. */
public final class GameEngine {

    public static final class Move {
        public final Card card;
        public final int pos;
        public Move(Card card, int pos) { this.card = card; this.pos = pos; }
    }

    /** Résultat d'un coup : les captures, regroupées par "vague" (pour l'animation). */
    public static final class Result {
        public final int pos;
        public final Owner mover;
        public final List<List<Integer>> waves = new ArrayList<>();
        public boolean same, plus, wall;   // règles déclenchées par ce coup (pour les animations)
        Result(int pos, Owner mover) { this.pos = pos; this.mover = mover; }
    }

    public final Rules rules;
    public final int size;
    public final Card[] board;
    public final List<Card> playerHand = new ArrayList<>();
    public final List<Card> aiHand = new ArrayList<>();
    private Owner turn;

    public GameEngine(List<Card> player, List<Card> ai, Rules rules, Owner first) {
        this.rules = rules;
        this.size = rules.size;
        this.board = new Card[size * size];
        for (Card c : player) { Card k = c.copy(); k.owner = Owner.PLAYER; k.origin = Owner.PLAYER; playerHand.add(k); }
        for (Card c : ai)     { Card k = c.copy(); k.owner = Owner.AI; k.origin = Owner.AI; aiHand.add(k); }
        this.turn = first;
    }

    private GameEngine(GameEngine o) {
        rules = o.rules;
        size = o.size;
        board = new Card[o.board.length];
        for (int i = 0; i < board.length; i++) if (o.board[i] != null) board[i] = o.board[i].copy();
        for (Card c : o.playerHand) playerHand.add(c.copy());
        for (Card c : o.aiHand) aiHand.add(c.copy());
        turn = o.turn;
    }

    public GameEngine copy() { return new GameEngine(this); }

    /** Remplace la main d'un joueur (utilisé par l'IA pour *deviner* la main adverse). */
    public void setHand(Owner o, List<Card> cards) {
        List<Card> h = handOf(o);
        h.clear();
        for (Card c : cards) { Card k = c.copy(); k.owner = o; k.origin = o; h.add(k); }
    }

    /** Cartes de l'adversaire passées de mon côté à la fin de la partie (pour choisir une carte à récupérer). */
    public List<Card> capturedByPlayer() {
        List<Card> l = new ArrayList<>();
        for (Card c : board) if (c != null && c.origin == Owner.AI && c.owner == Owner.PLAYER) l.add(c.copy());
        return l;
    }

    public Owner getTurn() { return turn; }

    public List<Card> handOf(Owner o) { return o == Owner.PLAYER ? playerHand : aiHand; }

    public boolean isOver() {
        for (Card c : board) if (c == null) return false;
        return true;
    }

    public List<Integer> emptyCells() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < board.length; i++) if (board[i] == null) l.add(i);
        return l;
    }

    public int boardCount(Owner o) {
        int n = 0;
        for (Card c : board) if (c != null && c.owner == o) n++;
        return n;
    }

    /** Score officiel : cartes du plateau + cartes restantes en main. */
    public int score(Owner o) { return boardCount(o) + handOf(o).size(); }

    /** Joue une carte de la main du joueur dont c'est le tour. */
    public Result place(int pos, Card card) {
        if (pos < 0 || pos >= board.length || board[pos] != null) throw new IllegalStateException("Case invalide : " + pos);
        Owner who = turn;
        if (!handOf(who).remove(card)) throw new IllegalStateException("Carte absente de la main");

        card.owner = who;
        board[pos] = card;
        Result res = new Result(pos, who);
        int[][] nb = neighbors(pos);

        // Vague 0 : Identique / Même Mur, Plus, puis règle de base (cumulables)
        Map<Integer, Boolean> wave0 = new LinkedHashMap<>();

        if (rules.same || rules.wall) {
            int before = wave0.size();
            List<int[]> eq = new ArrayList<>();
            for (int[] n : nb) {
                Card a = board[n[0]];
                if (a != null && card.numbers[n[1]] == a.numbers[n[2]]) eq.add(n);
            }
            int matches = eq.size();
            boolean wallHit = false;
            if (rules.wall && !rules.broken) {   // Même Mur : un bord du plateau compte comme une carte de valeur 10
                int r = pos / size, c = pos % size;
                if (r == 0 && card.numbers[Card.N] == 10) { matches++; wallHit = true; }
                if (r == size - 1 && card.numbers[Card.S] == 10) { matches++; wallHit = true; }
                if (c == 0 && card.numbers[Card.W] == 10) { matches++; wallHit = true; }
                if (c == size - 1 && card.numbers[Card.E] == 10) { matches++; wallHit = true; }
            }
            if (matches >= 2) for (int[] n : eq) markIfEnemy(wave0, n[0], who);
            if (wave0.size() > before) { res.same = true; res.wall = wallHit; }
        }
        if (rules.plus) {
            int before = wave0.size();
            Map<Integer, List<int[]>> bySum = new LinkedHashMap<>();
            for (int[] n : nb) {
                Card a = board[n[0]];
                if (a == null) continue;
                int sum = card.numbers[n[1]] + a.numbers[n[2]];
                if (!bySum.containsKey(sum)) bySum.put(sum, new ArrayList<int[]>());
                bySum.get(sum).add(n);
            }
            for (List<int[]> group : bySum.values())
                if (group.size() >= 2) for (int[] n : group) markIfEnemy(wave0, n[0], who);
            if (wave0.size() > before) res.plus = true;
        }
        for (int[] n : nb) {
            Card a = board[n[0]];
            if (a != null && a.owner != who && card.numbers[n[1]] > a.numbers[n[2]]) wave0.put(n[0], true);
        }
        List<Integer> frontier = new ArrayList<>(wave0.keySet());
        applyWave(res, frontier, who);

        // Combo : chaque carte retournée attaque à son tour ses voisines (règle de base)
        while (rules.combo && !frontier.isEmpty()) {
            List<Integer> next = new ArrayList<>();
            for (int f : frontier) {
                Card fc = board[f];
                for (int[] n : neighbors(f)) {
                    Card a = board[n[0]];
                    if (a != null && a.owner != who && !next.contains(n[0])
                            && fc.numbers[n[1]] > a.numbers[n[2]]) next.add(n[0]);
                }
            }
            applyWave(res, next, who);
            frontier = next;
        }

        turn = who.opposite();
        return res;
    }

    private void markIfEnemy(Map<Integer, Boolean> m, int pos, Owner who) {
        if (board[pos].owner != who) m.put(pos, true);
    }

    private void applyWave(Result res, List<Integer> wave, Owner who) {
        if (wave.isEmpty()) return;
        for (int p : wave) board[p].owner = who;
        res.waves.add(new ArrayList<>(wave));
    }

    /** Voisins d'une case : {case voisine, indice de mon côté, indice du côté adverse}. */
    int[][] neighbors(int pos) {
        int n = size, r = pos / n, c = pos % n;
        List<int[]> l = new ArrayList<>();
        if (rules.broken) {   // Mur cassé : le plateau est "infini", chaque case a 4 voisins
            l.add(new int[]{((r + n - 1) % n) * n + c, Card.N, Card.S});
            l.add(new int[]{r * n + (c + 1) % n, Card.E, Card.W});
            l.add(new int[]{((r + 1) % n) * n + c, Card.S, Card.N});
            l.add(new int[]{r * n + (c + n - 1) % n, Card.W, Card.E});
            return l.toArray(new int[0][]);
        }
        if (r > 0) l.add(new int[]{pos - n, Card.N, Card.S});
        if (c < n - 1) l.add(new int[]{pos + 1, Card.E, Card.W});
        if (r < n - 1) l.add(new int[]{pos + n, Card.S, Card.N});
        if (c > 0) l.add(new int[]{pos - 1, Card.W, Card.E});
        return l.toArray(new int[0][]);
    }
}
