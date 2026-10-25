package com.tripletriad;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deux modes :
 *  - "collection" (nouveau joueur) : choisir 25 cartes de niveau 1 à 5 parmi les 110 ;
 *  - "hand" : choisir les 5 cartes de la main dans sa collection.
 */
public class DeckActivity extends Activity {
    private final List<CardView> views = new ArrayList<>();
    private TextView header;
    private CardRepository repo;
    private boolean collectionMode;
    private int limit;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = new CardRepository(this);
        CardRenderer renderer = new CardRenderer(this);
        float dp = getResources().getDisplayMetrics().density;
        collectionMode = "collection".equals(getIntent().getStringExtra("mode")) || !repo.hasCollection();
        final int hand = getIntent().getIntExtra("hand", CardRepository.DECK_SIZE);
        limit = collectionMode ? CardRepository.COLLECTION_SIZE : hand;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F2A1D);

        header = new TextView(this);
        header.setTextColor(0xFFFFD54A);
        header.setTextSize(18);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, (int) (32 * dp), 0, (int) (2 * dp));
        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText(collectionMode
                ? "Bienvenue ! Choisissez les 25 cartes de votre collection (niveaux 1 à " + CardRepository.FREE_MAX_LEVEL
                  + "). Vous en gagnerez d'autres en jouant."
                : "Choisissez les " + hand + " cartes de votre main" + (hand == 13 ? " (plateau 5x5)" : "") + " parmi votre collection (" + repo.collection().size() + " cartes).");
        hint.setTextColor(0xFFB5C7BB);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding((int) (12 * dp), 0, (int) (12 * dp), (int) (8 * dp));
        root.addView(hint);

        int cols = 3;
        int cardW = (int) ((getResources().getDisplayMetrics().widthPixels - 24 * dp) / cols);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        grid.setPadding((int) (6 * dp), 0, (int) (6 * dp), 0);

        List<Card> source = collectionMode ? repo.all() : repo.collection();
        List<Card> current = collectionMode ? repo.collection() : repo.deck(hand);
        for (Card c : source) {
            CardView v = new CardView(this, c, renderer);
            if (collectionMode) v.setLocked(!repo.isStartable(c));
            for (Card s : current) if (s.name.equals(c.name) && !v.isLocked()) v.setSelectedCard(true);
            v.setOnClickListener(x -> toggle((CardView) x));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW;
            lp.height = cardW;
            grid.addView(v, lp);
            views.add(v);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, (int) (52 * dp));
        bl.setMargins((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (14 * dp));
        if (collectionMode) {
            Button random = pill("AU HASARD", 0xFF3A6BD8, dp);
            random.setOnClickListener(x -> fillRandom());
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -1, 1f);
            rl.rightMargin = (int) (8 * dp);
            buttons.addView(random, rl);
        }
        Button save = pill("VALIDER", 0xFF2F9E5B, dp);
        save.setOnClickListener(x -> save());
        buttons.addView(save, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(buttons, bl);

        setContentView(root);
        refreshHeader();
    }

    private Button pill(String text, int color, float dp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(40 * dp);
        b.setBackground(g);
        return b;
    }

    private int count() {
        int n = 0;
        for (CardView v : views) if (v.isSelected()) n++;
        return n;
    }

    private void toggle(CardView v) {
        if (v.isLocked()) {
            Toast.makeText(this, "Carte verrouillée (niveau " + v.getCard().level + ") : gagnez-la en battant un adversaire",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!v.isSelected() && count() >= limit) {
            Toast.makeText(this, limit + " cartes maximum", Toast.LENGTH_SHORT).show();
            return;
        }
        v.setSelectedCard(!v.isSelected());
        refreshHeader();
    }

    /** Complète la sélection avec des cartes tirées au hasard. */
    private void fillRandom() {
        List<CardView> pool = new ArrayList<>();
        for (CardView v : views) if (!v.isLocked() && !v.isSelected()) pool.add(v);
        Collections.shuffle(pool);
        for (CardView v : pool) {
            if (count() >= limit) break;
            v.setSelectedCard(true);
        }
        refreshHeader();
    }

    private void refreshHeader() {
        header.setText((collectionMode ? "Votre collection" : "Votre main") + "  (" + count() + "/" + limit + ")");
    }

    private void save() {
        if (count() != limit) {
            Toast.makeText(this, "Il faut exactement " + limit + " cartes", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Card> chosen = new ArrayList<>();
        for (CardView v : views) if (v.isSelected()) chosen.add(v.getCard());
        if (collectionMode) {
            repo.setCollection(chosen);
            startActivity(new Intent(this, DeckActivity.class));   // étape suivante : la main de 5 cartes
        } else {
            repo.saveDeck(chosen);
        }
        finish();
    }
}
