package com.tripletriad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * IA : recherche à 2 coups. Elle ne connaît PAS la main adverse : elle en tire une au hasard
 * dans le "pool" (niveaux proches des siens). pool == null => mode "tricheur" (utilisé seulement par les tests).
 */
public final class AiPlayer {
    private AiPlayer() {}

    public static GameEngine.Move choose(GameEngine e, Random rnd) { return choose(e, rnd, null); }

    public static GameEngine.Move choose(GameEngine e, Random rnd, List<Card> pool) {
        Owner me = e.getTurn();
        Owner opp = me.opposite();
        GameEngine base = e;
        if (pool != null && !e.handOf(opp).isEmpty()) {
            base = e.copy();
            base.setHand(opp, guessHand(e, pool, opp, rnd));
        }
        int handSize = base.handOf(me).size();
        List<Integer> cells = base.emptyCells();

        // 1er passage : valeur de chaque coup à 1 coup d'avance
        List<Cand> cands = new ArrayList<>();
        for (int i = 0; i < handSize; i++) {
            for (int pos : cells) {
                GameEngine s1 = base.copy();
                s1.place(pos, s1.handOf(me).get(i));
                Cand c = new Cand(i, pos, s1, eval(s1, me) + rnd.nextDouble() * 0.05);
                cands.add(c);
            }
        }
        // 5x5 : trop de coups, on n'approfondit que les meilleurs (sinon le téléphone rame)
        if (cands.size() > 30) {
            Collections.sort(cands, (x, y) -> Double.compare(y.value, x.value));
            cands = new ArrayList<>(cands.subList(0, 12));
        }
        // 2e passage : meilleure réponse adverse
        double bestValue = -1e9;
        int bestCard = cands.get(0).card, bestPos = cands.get(0).pos;
        for (Cand c : cands) {
            double v = c.value;
            if (!c.state.isOver()) {
                double worst = 1e9;
                List<Integer> cells2 = c.state.emptyCells();
                int h2 = c.state.handOf(opp).size();
                for (int j = 0; j < h2; j++) {
                    for (int q : cells2) {
                        GameEngine s2 = c.state.copy();
                        s2.place(q, s2.handOf(opp).get(j));
                        worst = Math.min(worst, eval(s2, me));
                    }
                }
                if (worst < 1e9) v = worst + (c.value - Math.floor(c.value)) * 0.05;
            }
            if (v > bestValue) { bestValue = v; bestCard = c.card; bestPos = c.pos; }
        }
        return new GameEngine.Move(e.handOf(me).get(bestCard), bestPos);
    }

    /** Main adverse supposée : cartes inconnues (ni sur le plateau, ni dans ma main) de niveau proche du mien. */
    private static List<Card> guessHand(GameEngine e, List<Card> pool, Owner opp, Random rnd) {
        List<Card> mine = e.handOf(opp.opposite());
        Set<String> known = new HashSet<>();
        for (Card c : e.board) if (c != null) known.add(c.name);
        for (Card c : mine) known.add(c.name);
        double avg = 0;
        for (Card c : mine) avg += c.level;
        avg = mine.isEmpty() ? 5 : avg / mine.size();

        List<Card> near = new ArrayList<>(), all = new ArrayList<>();
        for (Card c : pool) {
            if (known.contains(c.name)) continue;
            all.add(c);
            if (Math.abs(c.level - avg) <= 2) near.add(c);
        }
        int n = e.handOf(opp).size();
        List<Card> src = near.size() >= n ? near : all;
        Collections.shuffle(src, rnd);
        return new ArrayList<>(src.subList(0, Math.min(n, src.size())));
    }

    private static final class Cand {
        final int card, pos;
        final GameEngine state;
        final double value;
        Cand(int card, int pos, GameEngine state, double value) { this.card = card; this.pos = pos; this.state = state; this.value = value; }
    }

    private static double eval(GameEngine e, Owner me) {
        return e.boardCount(me) - e.boardCount(me.opposite());
    }
}
