package com.tripletriad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout rootLayout, deckStrip;
    private TextView goldText, rulesSummary;
    private CheckBox same, plus, combo, wall, broken, big;
    private SharedPreferences prefs;
    private CardRepository repo;
    private CardRenderer renderer;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tt", Context.MODE_PRIVATE);
        repo = new CardRepository(this);
        renderer = new CardRenderer(this);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0F2A1D);
        int pad = (int) (20 * dp);
        root.setPadding(pad, (int) (36 * dp), pad, pad);

        root.addView(label("TRIPLE TRIAD", 34, 0xFFFFD54A));
        goldText = label("", 16, 0xFFFFFFFF);
        root.addView(goldText);

        // lien vers le tutoriel
        TextView howTo = label("Comment jouer ? (tutoriel)", 14, 0xFFFFD54A);
        howTo.setPaintFlags(howTo.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        howTo.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        howTo.setOnClickListener(v -> openTutorial(0));
        root.addView(howTo);

        root.addView(space(10));
        root.addView(label("Votre deck", 14, 0xFFB5C7BB));
        deckStrip = new LinearLayout(this);
        deckStrip.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(deckStrip, new LinearLayout.LayoutParams(-1, -2));

        // ----- règles : liste déroulante (repliée par défaut, résumé toujours visible) -----
        root.addView(space(12));
        LinearLayout rulesBody = new LinearLayout(this);
        rulesBody.setOrientation(LinearLayout.VERTICAL);
        Runnable onRule = this::updateRulesSummary;
        same = check("Identique (deux côtés égaux)", "r_same", false, onRule);
        plus = check("Plus (sommes égales)", "r_plus", false, onRule);
        combo = check("Combo (captures en chaîne)", "r_combo", false, onRule);
        wall = check("Même Mur (Identique + bords = 10)", "r_wall", false, onRule);
        broken = check("Mur cassé (plateau sans bords) [bêta]", "r_broken", false, onRule);
        big = check("Plateau 5×5 (13 cartes en main)", "r_big", false, onRule);
        rulesBody.addView(ruleRow(same, "same"));
        rulesBody.addView(ruleRow(plus, "plus"));
        rulesBody.addView(ruleRow(combo, "combo"));
        rulesBody.addView(ruleRow(wall, "wall"));
        rulesBody.addView(ruleRow(broken, "broken"));
        rulesBody.addView(ruleRow(big, "big"));
        rulesSummary = new TextView(this);
        rulesSummary.setTextSize(12);
        rulesSummary.setTextColor(0xFFB5C7BB);
        root.addView(section("Règles (partie solo)", "open_rules", rulesBody, rulesSummary));
        updateRulesSummary();

        // ----- options -----
        root.addView(space(8));
        LinearLayout optionsBody = new LinearLayout(this);
        optionsBody.setOrientation(LinearLayout.VERTICAL);
        optionsBody.addView(check("Cacher la main adverse", "hide_hand", false, null));
        optionsBody.addView(check("Sons et musique", "sound", true, null));
        root.addView(section("Options", "open_options", optionsBody, null));

        root.addView(space(14));
        Button play = button("JOUER CONTRE LE BOT", 0xFF2F9E5B);
        play.setOnClickListener(v -> {
            Rules rules = currentRules();
            if (!repo.hasDeck(rules.handSize())) { needDeck(rules.handSize()); return; }
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("mode", "bot");
            i.putExtra("bits", rules.bits());
            startActivity(i);
        });
        root.addView(play);
        root.addView(space(10));
        Button online = button("EN LIGNE / DÉFIER UN AMI", 0xFFC9822B);
        online.setOnClickListener(v -> startActivity(new Intent(this, LobbyActivity.class)));
        root.addView(online);
        root.addView(space(10));
        Button deck = button("MON DECK", 0xFF3A6BD8);
        deck.setOnClickListener(v -> startActivity(new Intent(this, DeckActivity.class)));
        root.addView(deck);
        root.addView(space(10));
        Button deck13 = button("MA MAIN 5×5 (13 cartes)", 0xFF6A4FB6);
        deck13.setOnClickListener(v -> startActivity(new Intent(this, DeckActivity.class).putExtra("hand", 13)));
        root.addView(deck13);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFF0F2A1D);
        sv.addView(root);
        setContentView(sv);
        if (!repo.hasDeck()) needDeck();   // nouveau joueur : choisir ses 5 cartes
    }

    private Rules currentRules() {
        return new Rules(same.isChecked(), plus.isChecked(), combo.isChecked(), wall.isChecked(), broken.isChecked(), big.isChecked());
    }

    private void updateRulesSummary() {
        rulesSummary.setText(currentRules().describe());
    }

    private void openTutorial(int page) {
        startActivity(new Intent(this, TutorialActivity.class).putExtra("page", page));
    }

    private void needDeck() { needDeck(5); }

    private void needDeck(int hand) {
        android.widget.Toast.makeText(this, "Complétez d'abord votre main de " + hand + " cartes", android.widget.Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, DeckActivity.class).putExtra("hand", hand));
    }

    @Override protected void onResume() {
        super.onResume();
        goldText.setText("Or : " + repo.gold());
        deckStrip.removeAllViews();
        List<Card> deck = repo.deck();
        for (Card c : deck)
            deckStrip.addView(new CardView(this, c, renderer), new LinearLayout.LayoutParams(0, -2, 1f));
    }

    // ---------- composants ----------

    /** Bloc repliable : un en-tête (titre + résumé + flèche) et un contenu qui se déplie au toucher. */
    private LinearLayout section(String title, final String openKey, final View body, TextView summary) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int) (16 * dp), (int) (10 * dp), (int) (8 * dp), (int) (10 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x26FFFFFF);
        bg.setCornerRadius(14 * dp);
        header.setBackground(bg);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        texts.addView(t);
        if (summary != null) texts.addView(summary);
        header.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        final TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(0xFFFFD54A);
        arrow.setTextSize(30);
        arrow.setGravity(Gravity.CENTER);
        boolean open = prefs.getBoolean(openKey, false);
        arrow.setRotation(open ? 90 : 0);
        header.addView(arrow, new LinearLayout.LayoutParams((int) (40 * dp), (int) (40 * dp)));

        body.setVisibility(open ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            TransitionManager.beginDelayedTransition(rootLayout);
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.animate().rotation(show ? 90 : 0).setDuration(150).start();
            prefs.edit().putBoolean(openKey, show).apply();
        });

        box.addView(header, new LinearLayout.LayoutParams(-1, -2));
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    /** Une règle : la case à cocher + une bulle « ? » qui l'explique. */
    private View ruleRow(CheckBox cb, final String key) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cb, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView help = new TextView(this);
        help.setText("?");
        help.setTextColor(0xFFFFD54A);
        help.setTextSize(15);
        help.setTypeface(null, android.graphics.Typeface.BOLD);
        help.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke((int) (1.5f * dp), 0xFFFFD54A);
        help.setBackground(ring);
        help.setOnClickListener(v -> showRuleHelp(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (30 * dp), (int) (30 * dp));
        lp.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        row.addView(help, lp);
        return row;
    }

    private void showRuleHelp(String key) {
        final RuleTexts.Info info = RuleTexts.of(key);
        if (info == null) return;
        new AlertDialog.Builder(this)
                .setTitle(info.title)
                .setMessage(info.text)
                .setPositiveButton("OK", null)
                .setNeutralButton("Voir un exemple", (d, w) -> openTutorial(info.page))
                .show();
    }

    private TextView label(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 6, 0, 6);
        return t;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, (int) (dp * getResources().getDisplayMetrics().density)));
        return v;
    }

    private CheckBox check(String s, final String key, boolean def, final Runnable onChange) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(Color.WHITE);
        c.setChecked(prefs.getBoolean(key, def));
        c.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            if (onChange != null) onChange.run();
        });
        return c;
    }

    private Button button(String s, int color) {
        float dp = getResources().getDisplayMetrics().density;
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(40 * dp);
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (52 * dp)));
        return b;
    }
}
