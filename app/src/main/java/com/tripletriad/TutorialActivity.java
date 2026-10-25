package com.tripletriad;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.tripletriad.DiagramView.Board;
import static com.tripletriad.DiagramView.Cell;

/** Tutoriel des règles : pages avec explications + petits schémas « avant / après ». */
public class TutorialActivity extends Activity {

    private static final class Page {
        final String title, body, caption;
        final Board before, after;
        final boolean legend;
        Page(String title, String body, Board before, Board after, boolean legend, String caption) {
            this.title = title; this.body = body; this.before = before; this.after = after;
            this.legend = legend; this.caption = caption;
        }
    }

    private final List<Page> pages = new ArrayList<>();
    private int index;
    private TextView titleView, bodyView, captionView, counter;
    private DiagramView diagram;
    private Button prev, next;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildPages();
        index = Math.max(0, Math.min(pages.size() - 1, getIntent().getIntExtra("page", 0)));
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F2A1D);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (20 * dp), (int) (36 * dp), (int) (8 * dp), (int) (4 * dp));
        TextView head = new TextView(this);
        head.setText("COMMENT JOUER");
        head.setTextColor(0xFFFFD54A);
        head.setTextSize(18);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(head, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(0xFFFFFFFF);
        close.setTextSize(30);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams((int) (52 * dp), (int) (48 * dp)));
        root.addView(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (20 * dp), (int) (8 * dp), (int) (20 * dp), (int) (16 * dp));
        titleView = new TextView(this);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(24);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(titleView);
        bodyView = new TextView(this);
        bodyView.setTextColor(0xFFE3EDE7);
        bodyView.setTextSize(16);
        bodyView.setLineSpacing(0, 1.15f);
        bodyView.setPadding(0, (int) (10 * dp), 0, (int) (14 * dp));
        content.addView(bodyView);
        diagram = new DiagramView(this);
        content.addView(diagram, new LinearLayout.LayoutParams(-1, -2));
        captionView = new TextView(this);
        captionView.setTextColor(0xFFFFD54A);
        captionView.setTextSize(14);
        captionView.setGravity(Gravity.CENTER);
        captionView.setPadding(0, (int) (6 * dp), 0, 0);
        content.addView(captionView);
        scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (16 * dp));
        prev = pill("PRÉCÉDENT", 0xFF3A6BD8);
        prev.setOnClickListener(v -> go(index - 1));
        counter = new TextView(this);
        counter.setTextColor(0xFFB5C7BB);
        counter.setTextSize(14);
        counter.setGravity(Gravity.CENTER);
        next = pill("SUIVANT", 0xFF2F9E5B);
        next.setOnClickListener(v -> { if (index >= pages.size() - 1) finish(); else go(index + 1); });
        foot.addView(prev, new LinearLayout.LayoutParams(0, (int) (48 * dp), 1f));
        foot.addView(counter, new LinearLayout.LayoutParams((int) (64 * dp), -2));
        foot.addView(next, new LinearLayout.LayoutParams(0, (int) (48 * dp), 1f));
        root.addView(foot);

        setContentView(root);
        show();
    }

    private void go(int i) {
        if (i < 0 || i >= pages.size()) return;
        index = i;
        show();
        scroll.scrollTo(0, 0);
    }

    private void show() {
        Page p = pages.get(index);
        titleView.setText(p.title);
        bodyView.setText(p.body);
        if (p.legend) { diagram.setVisibility(View.VISIBLE); diagram.setLegend(); }
        else if (p.before != null) { diagram.setVisibility(View.VISIBLE); diagram.setDiagram(p.before, p.after); }
        else diagram.setVisibility(View.GONE);
        captionView.setText(p.caption == null ? "" : p.caption);
        captionView.setVisibility(p.caption == null ? View.GONE : View.VISIBLE);
        counter.setText((index + 1) + " / " + pages.size());
        prev.setEnabled(index > 0);
        prev.setAlpha(index > 0 ? 1f : 0.35f);
        next.setText(index >= pages.size() - 1 ? "TERMINER" : "SUIVANT");
    }

    private Button pill(String s, int color) {
        float dp = getResources().getDisplayMetrics().density;
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(40 * dp);
        b.setBackground(g);
        return b;
    }

    // ---------- contenu ----------

    private void add(String title, String body, Board before, Board after, String caption) {
        pages.add(new Page(title, body, before, after, false, caption));
    }

    private void buildPages() {
        pages.add(new Page("Le but du jeu",
                "Triple Triad est un duel de cartes sur un plateau de 3×3 cases. Chaque joueur a 5 cartes : "
                        + "vous êtes en bleu, l'adversaire en rouge.\n\n"
                        + "Chaque carte porte 4 nombres : haut, droite, bas et gauche.\n\n"
                        + "À tour de rôle, on pose une carte sur une case vide. Poser une carte peut capturer les cartes "
                        + "voisines, qui changent alors de couleur.\n\n"
                        + "Quand le plateau est plein, la partie s'arrête : votre score est le nombre de vos cartes sur le "
                        + "plateau, plus celles qui vous restent en main. Le plus haut score gagne.",
                null, null, true, null));

        add("Capturer (règle de base)",
                "Quand vous posez une carte contre une carte adverse, on compare les deux nombres qui se touchent.\n\n"
                        + "Si le vôtre est strictement plus grand, la carte adverse est capturée. "
                        + "S'il est égal ou plus petit, rien ne se passe.\n\n"
                        + "Cette règle est toujours active. Les autres règles s'ajoutent par-dessus.",
                new Board(1, 2, Cell.red(4, 3, 6, 2), Cell.played(5, 2, 3, 7)),
                new Board(1, 2, Cell.red(4, 3, 6, 2), Cell.played(5, 2, 3, 7)).after(0),
                "Votre 7 bat le 3 : la carte rouge est capturée.");

        add("Identique (option)",
                "Si votre carte touche au moins deux cartes dont le nombre en contact est exactement égal au sien, "
                        + "toutes les cartes adverses concernées sont capturées, même si leurs nombres sont plus grands.\n\n"
                        + "Il faut donc au moins deux égalités à la fois.",
                new Board(2, 2, Cell.red(6, 4, 5, 2), Cell.empty(), Cell.played(5, 5, 3, 2), Cell.red(3, 6, 4, 5)),
                new Board(2, 2, Cell.red(6, 4, 5, 2), Cell.empty(), Cell.played(5, 5, 3, 2), Cell.red(3, 6, 4, 5)).after(0, 3),
                "5 = 5 en haut et 5 = 5 à droite : les deux cartes rouges sont capturées.");

        add("Plus (option)",
                "Pour chaque carte voisine, additionnez votre nombre et celui qui le touche.\n\n"
                        + "Si au moins deux voisines donnent la même somme, les cartes adverses concernées sont capturées, "
                        + "même si chaque nombre pris seul est plus faible.",
                new Board(2, 2, Cell.red(4, 3, 5, 2), Cell.empty(), Cell.played(3, 2, 6, 4), Cell.red(5, 4, 2, 6)),
                new Board(2, 2, Cell.red(4, 3, 5, 2), Cell.empty(), Cell.played(3, 2, 6, 4), Cell.red(5, 4, 2, 6)).after(0, 3),
                "3 + 5 = 8 et 2 + 6 = 8 : même somme, les deux cartes sont capturées.");

        add("Combo (option)",
                "Les cartes qui viennent d'être capturées attaquent à leur tour leurs propres voisines, avec la règle de base : "
                        + "si leur nombre est plus grand, elles capturent aussi.\n\n"
                        + "Les captures peuvent ainsi s'enchaîner sur tout le plateau.",
                new Board(1, 3, Cell.played(3, 7, 2, 1), Cell.red(4, 6, 3, 3), Cell.red(2, 5, 5, 2)),
                new Board(1, 3, Cell.played(3, 7, 2, 1), Cell.red(4, 6, 3, 3), Cell.red(2, 5, 5, 2)).after(1, 2),
                "7 bat 3, puis la carte capturée attaque : 6 bat 2.");

        add("Même Mur (option)",
                "Variante d'Identique : les bords du plateau comptent comme une carte de valeur A (10).\n\n"
                        + "Un côté A tourné vers un bord compte donc comme une égalité. Il faut au moins deux égalités au total "
                        + "(un bord et une carte, ou deux bords) pour capturer les cartes adverses dont le nombre est égal.",
                new Board(2, 2, Cell.played(10, 4, 3, 2), Cell.red(5, 3, 2, 4), Cell.empty(), Cell.empty()).wallTop(),
                new Board(2, 2, Cell.played(10, 4, 3, 2), Cell.red(5, 3, 2, 4), Cell.empty(), Cell.empty()).wallTop().after(1),
                "A = A contre le bord, et 4 = 4 contre la carte rouge : elle est capturée.");

        add("Mur cassé (option, bêta)",
                "Il n'y a plus de bords : le plateau se referme sur lui-même. La colonne de gauche touche celle de droite, "
                        + "et la ligne du haut touche celle du bas.\n\n"
                        + "Une carte posée sur un bord a donc toujours quatre voisines. "
                        + "Dans ce mode, Même Mur n'a plus d'effet.",
                new Board(3, 3, Cell.empty(), Cell.empty(), Cell.empty(),
                        Cell.played(2, 3, 2, 8), Cell.empty(), Cell.red(4, 3, 5, 2),
                        Cell.empty(), Cell.empty(), Cell.empty()).wrap(),
                new Board(3, 3, Cell.empty(), Cell.empty(), Cell.empty(),
                        Cell.played(2, 3, 2, 8), Cell.empty(), Cell.red(4, 3, 5, 2),
                        Cell.empty(), Cell.empty(), Cell.empty()).wrap().after(5),
                "Le 8 de gauche touche le 3 de droite : la carte rouge est capturée.");

        add("Plateau 5×5 (option)",
                "Le plateau 5×5 compte 25 cases et chaque joueur a 13 cartes en main.\n\n"
                        + "Composez cette main avec le bouton « MA MAIN 5×5 » de l'écran principal. "
                        + "Les règles sont les mêmes que sur le plateau 3×3, et le score compte aussi les cartes restant en main.",
                null, null, null);

        add("Après la partie",
                "Victoire : vous gagnez 10 pièces d'or et vous pouvez garder UNE des cartes adverses que vous avez "
                        + "capturées (si vous ne l'avez pas déjà). Elle rejoint votre collection.\n\n"
                        + "Au départ, vous choisissez 25 cartes de niveau 1 à 5. Les cartes de niveau élevé sont les plus fortes : "
                        + "gagnez-les en battant vos adversaires.\n\n"
                        + "« MON DECK » sert à choisir les 5 cartes de votre main parmi celles que vous possédez.",
                null, null, null);

        add("Jouer en ligne",
                "« EN LIGNE / DÉFIER UN AMI » vous connecte à un salon de discussion IRC.\n\n"
                        + "Touchez un joueur de la liste, choisissez les règles : il reçoit votre défi et peut l'accepter ou le refuser. "
                        + "Pour jouer ensemble, rejoignez le même serveur et le même salon.\n\n"
                        + "Les deux joueurs doivent avoir la même version des cartes (même fichier cards.json), "
                        + "sinon les captures ne seront pas identiques des deux côtés.",
                null, null, null);
    }
}
