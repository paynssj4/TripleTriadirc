package com.tripletriad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Partie contre le bot (mode "bot") ou contre un ami via IRC (mode "online"). */
public class GameActivity extends Activity implements IrcSession.Listener {
    private CardRepository repo;
    private Sound sound;
    private GameView view;
    private boolean online, gameOver, endedByNet;
    private String opp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repo = new CardRepository(this);
        sound = new Sound(this);

        Rules rules = Rules.fromBits(getIntent().getStringExtra("bits"));
        online = "online".equals(getIntent().getStringExtra("mode"));
        final int hs = rules.handSize();
        List<Card> mine = repo.deck(hs);
        if (mine.size() != hs) {
            Toast.makeText(this, "Complétez d'abord votre main de " + hs + " cartes", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        boolean hide = getSharedPreferences("tt", Context.MODE_PRIVATE).getBoolean("hide_hand", false);

        if (online) {
            opp = getIntent().getStringExtra("opp");
            List<Card> theirs = repo.cardsFromCsv(getIntent().getStringExtra("oppDeck"));
            if (theirs.size() != hs) {
                Toast.makeText(this, "Deck adverse invalide", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            Owner first = getIntent().getBooleanExtra("localStarts", true) ? Owner.PLAYER : Owner.AI;
            view = new GameView(this, mine, theirs, rules, first, sound, this::onGameOver);
            view.setOnline(opp, (card, pos) -> IrcSession.get().send(opp, "MOVE " + card.name + " " + pos));
            IrcSession.get().setListener(this); // vide aussi les coups arrivés avant l'ouverture
        } else {
            Owner first = new Random().nextBoolean() ? Owner.PLAYER : Owner.AI;
            // le bot change de cartes à chaque partie, avec des niveaux proches des vôtres
            view = new GameView(this, mine, repo.aiDeckFor(mine), rules, first, sound, this::onGameOver);
            view.setAiPool(repo.all());
        }
        view.setHideOpponentHand(hide);
        setContentView(view);
    }

    @Override protected void onResume() { super.onResume(); if (sound != null) sound.startMusic(); }
    @Override protected void onPause() { super.onPause(); if (sound != null) sound.pauseMusic(); }

    @Override protected void onDestroy() {
        if (online && !gameOver && !endedByNet && opp != null) IrcSession.get().send(opp, "QUIT");
        IrcSession.get().clearListener(this);
        if (sound != null) sound.release();
        super.onDestroy();
    }

    // ---------- fin de partie ----------

    private void onGameOver(final int player, final int other) {
        gameOver = true;
        if (player > other) {
            repo.addGold(10);
            // cartes adverses capturées qu'on ne possède pas encore : on peut en garder une
            List<Card> pick = new ArrayList<>();
            for (Card c : view.capturedByPlayer()) if (!repo.isOwned(c)) pick.add(c);
            if (!pick.isEmpty()) {
                showPick(pick, () -> showResult(player, other));
                return;
            }
        }
        showResult(player, other);
    }

    private void showPick(final List<Card> cards, final Runnable after) {
        float dp = getResources().getDisplayMetrics().density;
        CardRenderer renderer = new CardRenderer(this);
        LinearLayout row = new LinearLayout(this);
        row.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        final AlertDialog[] dlg = new AlertDialog[1];
        for (final Card c : cards) {
            CardView v = new CardView(this, c, renderer);
            row.addView(v, new LinearLayout.LayoutParams((int) (110 * dp), (int) (110 * dp)));
            v.setOnClickListener(x -> {
                repo.addToCollection(c);
                Toast.makeText(this, "Carte " + c.name + " (niveau " + c.level + ") ajoutée à votre collection !",
                        Toast.LENGTH_LONG).show();
                dlg[0].dismiss();
                after.run();
            });
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(row);
        dlg[0] = new AlertDialog.Builder(this)
                .setTitle("Récupérez 1 carte capturée")
                .setView(hs)
                .setCancelable(false)
                .setNegativeButton("Passer", (d, w) -> after.run())
                .create();
        dlg[0].show();
    }

    private void showResult(int player, int other) {
        String title = player > other ? "Victoire ! (+10 or)" : other > player ? "Défaite" : "Égalité";
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Vous " + player + "  –  " + other + " " + (online ? opp : "IA"))
                .setCancelable(false);
        if (online) {
            d.setPositiveButton("Retour au lobby", (x, w) -> finish());
        } else {
            d.setPositiveButton("Rejouer", (x, w) -> recreate()).setNegativeButton("Menu", (x, w) -> finish());
        }
        d.show();
    }

    // ---------- messages IRC pendant la partie ----------

    @Override public void onGameMessage(String from, String cmd, String arg) {
        if (cmd.equals("CHALLENGE")) { IrcSession.get().send(from, "BUSY"); return; }
        if (!online || opp == null || !from.equalsIgnoreCase(opp)) return;
        if (cmd.equals("MOVE")) {
            String[] p = arg.split(" ");
            if (p.length == 2) {
                try { view.onRemoteMove(p[0], Integer.parseInt(p[1])); } catch (NumberFormatException ignored) {}
            }
        } else if (cmd.equals("QUIT")) {
            netEnd(opp + " a quitté la partie.");
        }
    }

    @Override public void onUsers(List<String> users) {
        if (!online || gameOver || endedByNet) return;
        for (String u : users) if (u.equalsIgnoreCase(opp)) return;
        netEnd(opp + " s'est déconnecté.");
    }

    @Override public void onDisconnected() {
        if (online && !gameOver) netEnd("Connexion IRC perdue.");
    }

    private void netEnd(String msg) {
        if (endedByNet || gameOver) return;
        endedByNet = true;
        new AlertDialog.Builder(this).setMessage(msg).setCancelable(false)
                .setPositiveButton("Retour", (x, w) -> finish()).show();
    }
}
