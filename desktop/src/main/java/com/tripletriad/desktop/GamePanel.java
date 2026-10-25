package com.tripletriad.desktop;

import com.tripletriad.AiPlayer;
import com.tripletriad.Card;
import com.tripletriad.GameEngine;
import com.tripletriad.Owner;
import com.tripletriad.Rules;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Une partie : contre le bot (opp == null) ou contre un humain via IRC (opp = son pseudo). */
final class GamePanel extends JPanel implements IrcClient.Listener {
    private final Assets assets;
    private final Profile profile;
    private final GameEngine engine;
    private final String opp;
    private final IrcClient irc;
    private final Runnable onReplay, onExit;
    private final boolean hideHand;
    private final Random rnd = new Random();
    private final ArrayDeque<Object[]> pendingRemote = new ArrayDeque<>();

    private final JPanel oppHandPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
    private final JPanel boardPanel = new JPanel();
    private final JPanel myHandPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);

    private final int cw, ch, hw, hh;     // tailles des cartes (plateau / main), adaptées à l'écran
    private Card selected;
    private boolean busy, over, ended, disposed;
    private String lastEvent = "";

    GamePanel(Assets assets, Profile profile, List<Card> mine, List<Card> theirs, Rules rules, Owner first,
              String opp, IrcClient irc, Runnable onReplay, Runnable onExit) {
        this.assets = assets;
        this.profile = profile;
        this.opp = opp;
        this.irc = irc;
        this.onReplay = onReplay;
        this.onExit = onExit;
        this.hideHand = profile.getBool("hide_hand", false);
        this.engine = new GameEngine(mine, theirs, rules, first);

        setLayout(new BorderLayout());
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(new Color(24, 26, 34));
        for (JPanel p : new JPanel[]{oppHandPanel, boardPanel, myHandPanel}) {
            p.setOpaque(false);
            p.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(p);
        }
        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
        JButton quit = new JButton(opp != null ? "Abandonner" : "Menu");
        quit.addActionListener(e -> {
            if (!over && opp != null
                    && JOptionPane.showConfirmDialog(this, "Abandonner la partie ?", "Triple Triad",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            dispose();
            onExit.run();
        });
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        south.add(status, BorderLayout.CENTER);
        south.add(quit, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        // Tailles fixes : la fenêtre ne bouge plus pendant la partie, et tout tient dans la hauteur de l'écran
        int size = rules.size, hs = rules.handSize();
        int bcw = size == 3 ? 110 : 84, bch = size == 3 ? 132 : 100;
        int bhw = size == 3 ? 90 : 64, bhh = size == 3 ? 108 : 78;
        int baseH = 2 * (bhh + 8) + size * bch + (size - 1) * 4 + 44 + 24;
        double sc = Math.max(0.5, Math.min(1.0, (availableHeight() - 60.0) / baseH));
        cw = (int) (bcw * sc); ch = (int) (bch * sc);
        hw = (int) (bhw * sc); hh = (int) (bhh * sc);
        int handW = hs * (hw + 6) + 12, handH = hh + 8;
        int boardW = size * cw + (size - 1) * 4, boardH = size * ch + (size - 1) * 4;
        for (JPanel hp : new JPanel[]{oppHandPanel, myHandPanel}) fix(hp, handW, handH);
        fix(boardPanel, boardW, boardH);
        status.setPreferredSize(new Dimension(10, 44));
        int w = Math.max(780, Math.max(handW, boardW) + 20);
        setPreferredSize(new Dimension(w, 2 * handH + boardH + 44 + 24));

        refresh();
        SwingUtilities.invokeLater(this::nextTurn);
    }

    /** Hauteur utilisable à l'écran (hors barre des tâches). -Dtt.maxHeight=700 pour la forcer. */
    static int availableHeight() {
        String forced = System.getProperty("tt.maxHeight");
        if (forced != null) try { return Integer.parseInt(forced.trim()); } catch (NumberFormatException ignored) { }
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
        } catch (HeadlessException | NullPointerException e) {
            return 1080;
        }
    }

    private static void fix(JComponent c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
    }

    /** À appeler quand on quitte l'écran de jeu : prévient l'adversaire si la partie n'est pas finie. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        if (opp != null && !over && !ended && irc != null) irc.send(opp, "QUIT");
    }

    // ---------- affichage ----------

    private void refresh() {
        int size = engine.size;

        boardPanel.removeAll();
        boardPanel.setLayout(new GridLayout(size, size, 4, 4));
        for (int i = 0; i < engine.board.length; i++) {
            final int pos = i;
            CardComp c = new CardComp(engine.board[i], assets, false, false, cw, ch);
            c.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { onCell(pos); }
            });
            boardPanel.add(c);
        }
        oppHandPanel.removeAll();
        for (Card c : engine.aiHand) oppHandPanel.add(new CardComp(c, assets, hideHand, false, hw, hh));
        myHandPanel.removeAll();
        for (final Card c : engine.playerHand) {
            CardComp cc = new CardComp(c, assets, false, c == selected, hw, hh);
            cc.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { onHand(c); }
            });
            myHandPanel.add(cc);
        }
        updateStatus();
        revalidate();
        repaint();
    }

    private String oppName() { return opp != null ? opp : "IA"; }

    private void updateStatus() {
        String score = "Toi " + engine.score(Owner.PLAYER) + "  —  " + oppName() + " " + engine.score(Owner.AI);
        String msg;
        if (over) {
            int p = engine.score(Owner.PLAYER), a = engine.score(Owner.AI);
            msg = (p > a ? "Victoire !" : p < a ? "Défaite..." : "Égalité") + "   (" + score + ")";
        } else if (engine.getTurn() == Owner.AI) {
            msg = (opp != null ? "Tour de " + opp : "L'IA réfléchit...") + "   " + score;
        } else {
            msg = (selected == null ? "Choisis une carte, puis une case.   " : "Choisis une case.   ") + score;
        }
        status.setText("<html><div style='text-align:center'>" + (lastEvent.isEmpty() ? "&nbsp;" : lastEvent) + "<br>" + msg + "</div></html>");
    }

    private String describe(String who, GameEngine.Result r) {
        int flips = 0;
        for (List<Integer> w : r.waves) flips += w.size();
        StringBuilder sb = new StringBuilder(who).append(" : ").append(flips).append(" carte(s) retournée(s)");
        if (r.wall) sb.append(" — Même Mur !");
        else if (r.same) sb.append(" — Identique !");
        if (r.plus) sb.append(" — Plus !");
        if (r.waves.size() > 1) sb.append(" — Combo x").append(r.waves.size() - 1);
        return sb.toString();
    }

    // ---------- coups du joueur local ----------

    private void onHand(Card c) {
        if (busy || over || ended || engine.getTurn() != Owner.PLAYER) return;
        selected = c;
        refresh();
    }

    private void onCell(int pos) {
        if (busy || over || ended || engine.getTurn() != Owner.PLAYER || selected == null || engine.board[pos] != null) return;
        Card card = selected;
        selected = null;
        GameEngine.Result r = engine.place(pos, card);
        if (opp != null) irc.send(opp, "MOVE " + card.name + " " + pos);
        lastEvent = describe("Toi", r);
        afterMove();
    }

    // ---------- coups de l'adversaire (IA ou humain distant) ----------

    private void afterMove() {
        refresh();
        if (engine.isOver()) { finish(); return; }
        nextTurn();
    }

    private void nextTurn() {
        if (disposed || over || ended || engine.getTurn() != Owner.AI) return;
        if (opp == null) scheduleAi(); else drainRemote();
    }

    private void scheduleAi() {
        busy = true;
        updateStatus();
        final GameEngine e = engine;
        new SwingWorker<GameEngine.Move, Void>() {
            @Override protected GameEngine.Move doInBackground() throws Exception {
                Thread.sleep(600);
                return AiPlayer.choose(e, rnd, assets.cards);
            }
            @Override protected void done() {
                busy = false;
                if (disposed || over) return;
                try {
                    GameEngine.Move m = get();
                    GameEngine.Result r = engine.place(m.pos, m.card);
                    lastEvent = describe("IA", r);
                } catch (Exception ex) {
                    lastEvent = "Erreur IA : " + ex;
                }
                afterMove();
            }
        }.execute();
    }

    /** Coup reçu de l'adversaire distant (même format que l'appli Android : nom de carte + case). */
    void onRemoteMove(String cardName, int pos) {
        pendingRemote.add(new Object[]{cardName, pos});
        drainRemote();
    }

    private void drainRemote() {
        if (opp == null || disposed || over || ended || busy || engine.getTurn() != Owner.AI) return;
        Object[] m;
        while ((m = pendingRemote.poll()) != null) {
            int pos = (Integer) m[1];
            if (pos < 0 || pos >= engine.board.length || engine.board[pos] != null) continue;
            for (Card c : engine.aiHand) {
                if (c.name.equals(m[0])) {
                    GameEngine.Result r = engine.place(pos, c);
                    lastEvent = describe(opp, r);
                    afterMove();
                    return;
                }
            }
        }
        updateStatus();
    }

    // ---------- fin de partie ----------

    private void finish() {
        over = true;
        busy = true;
        updateStatus();
        Timer t = new Timer(900, e -> onGameOver());
        t.setRepeats(false);
        t.start();
    }

    private void onGameOver() {
        if (disposed) return;
        int p = engine.score(Owner.PLAYER), o = engine.score(Owner.AI);
        if (p > o) {
            profile.addGold(10);
            List<Card> pick = new ArrayList<>();
            for (Card c : engine.capturedByPlayer()) if (!profile.isOwned(c)) pick.add(c);
            if (!pick.isEmpty()) {
                List<Card> r = CardPicker.pick(this, assets, "Récupère 1 carte capturée",
                        "Clique la carte à garder dans ta collection, puis valide (valide sans rien choisir pour passer).",
                        pick, new ArrayList<Card>(), 1, false, c -> false, false);
                if (r != null && !r.isEmpty()) profile.addToCollection(r.get(0));
            }
        }
        String title = p > o ? "Victoire ! (+10 or)" : o > p ? "Défaite" : "Égalité";
        String msg = "Toi " + p + "  –  " + o + " " + oppName();
        Object[] opts = opp != null ? new Object[]{"Retour au lobby"} : new Object[]{"Rejouer", "Menu"};
        int ch = JOptionPane.showOptionDialog(this, msg, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        dispose();
        if (opp == null && ch == 0 && onReplay != null) onReplay.run(); else onExit.run();
    }

    // ---------- messages IRC pendant la partie ----------

    @Override public void onGameMessage(String from, String cmd, String arg) {
        if (cmd.equals("CHALLENGE")) { irc.send(from, "BUSY"); return; }
        if (opp == null || !from.equalsIgnoreCase(opp)) return;
        if (cmd.equals("MOVE")) {
            String[] p = arg.split(" ");
            if (p.length == 2) {
                try { onRemoteMove(p[0], Integer.parseInt(p[1])); } catch (NumberFormatException ignored) { }
            }
        } else if (cmd.equals("QUIT")) {
            netEnd(opp + " a quitté la partie.");
        }
    }

    @Override public void onUsers(List<String> users) {
        if (opp == null || over || ended) return;
        for (String u : users) if (u.equalsIgnoreCase(opp)) return;
        netEnd(opp + " s'est déconnecté.");
    }

    @Override public void onDisconnected() {
        if (opp != null && !over) netEnd("Connexion IRC perdue.");
    }

    private void netEnd(String msg) {
        if (ended || over || disposed) return;
        ended = true;
        JOptionPane.showMessageDialog(this, msg, "Triple Triad", JOptionPane.INFORMATION_MESSAGE);
        disposed = true;
        onExit.run();
    }
}
