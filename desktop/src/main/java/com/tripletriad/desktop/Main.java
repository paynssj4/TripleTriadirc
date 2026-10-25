package com.tripletriad.desktop;

import com.tripletriad.Card;
import com.tripletriad.Owner;
import com.tripletriad.Rules;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Version PC (Swing) de Triple Triad. Réutilise le moteur et l'IA de l'appli Android sans les modifier, et parle
 * le même protocole IRC : on peut jouer contre les amis qui sont sur Android.
 */
public final class Main implements LobbyPanel.Host {
    private final Assets assets;
    private final Profile profile;
    private final IrcClient irc = new IrcClient();
    private final JFrame frame = new JFrame("Triple Triad");
    private final LobbyPanel lobby;
    private GamePanel game;
    private boolean lobbyShown;

    private Main(Assets assets) {
        this.assets = assets;
        this.profile = new Profile(assets.cards);
        this.lobby = new LobbyPanel(irc, profile, this);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        try {   // icône de la fenêtre / de la barre des tâches : le chocobo
            frame.setIconImage(ImageIO.read(Main.class.getResource("/icon.png")));
        } catch (Exception ignored) { }
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
        });
        showMenu();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /** Prévient l'adversaire et le serveur IRC avant de fermer. */
    private void quit() {
        if (game != null) game.dispose();
        irc.disconnect();
        Timer t = new Timer(400, e -> System.exit(0));
        t.setRepeats(false);
        t.start();
    }

    private void show(JComponent c) {
        if (lobbyShown && c != lobby) { lobby.onHidden(); lobbyShown = false; }
        frame.setContentPane(c);
        frame.pack();
        frame.revalidate();
        frame.repaint();
    }

    // ---------- menu ----------

    private void showMenu() {
        game = null;
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(0x0F2A1D));
        p.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(6, 0, 6, 0);

        JLabel title = new JLabel("TRIPLE TRIAD", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 34f));
        title.setForeground(new Color(0xFFD54A));
        p.add(title, c);
        JLabel gold = new JLabel("Or : " + profile.gold(), SwingConstants.CENTER);
        gold.setForeground(new Color(0xB5C7BB));
        p.add(gold, c);

        JButton bot = new JButton("Jouer contre l'IA");
        bot.addActionListener(e -> playBot());
        JButton online = new JButton("Défis en ligne (amis sur PC ou Android)");
        online.addActionListener(e -> showLobby());
        JButton deck = new JButton("Ma collection et mes mains");
        deck.addActionListener(e -> editDecks());
        for (JButton b : new JButton[]{bot, online, deck}) {
            b.setFont(b.getFont().deriveFont(Font.BOLD, 15f));
            b.setPreferredSize(new Dimension(340, 44));
            p.add(b, c);
        }
        JCheckBox hide = new JCheckBox("Cacher la main adverse", profile.getBool("hide_hand", false));
        hide.setOpaque(false);
        hide.setForeground(Color.WHITE);
        hide.addActionListener(e -> profile.setBool("hide_hand", hide.isSelected()));
        p.add(hide, c);
        show(p);
    }

    // ---------- collection et mains ----------

    /** Crée la collection (25 cartes) puis la main demandée si elles n'existent pas encore. */
    @Override public boolean ensureDeck(int handSize) {
        if (!profile.hasCollection() && !pickCollection()) return false;
        if (!profile.hasDeck(handSize) && !pickHand(handSize)) return false;
        return true;
    }

    private boolean pickCollection() {
        List<Card> chosen = CardPicker.pick(frame, assets, "Ta collection",
                "Bienvenue ! Choisis les " + Profile.COLLECTION_SIZE + " cartes de ta collection (niveaux 1 à "
                        + Profile.FREE_MAX_LEVEL + "). Tu en gagneras d'autres en battant des adversaires.",
                assets.cards, profile.collection(), Profile.COLLECTION_SIZE, true,
                c -> c.level > Profile.FREE_MAX_LEVEL, true);
        if (chosen == null) return false;
        profile.setCollection(chosen);
        return true;
    }

    private boolean pickHand(int handSize) {
        List<Card> chosen = CardPicker.pick(frame, assets, "Ta main de " + handSize + " cartes",
                "Choisis les " + handSize + " cartes de ta main" + (handSize == 13 ? " (plateau 5x5)" : "")
                        + " parmi ta collection (" + profile.collection().size() + " cartes).",
                profile.collection(), profile.deck(handSize), handSize, true, c -> false, true);
        if (chosen == null) return false;
        profile.saveDeck(chosen);
        return true;
    }

    private void editDecks() {
        String[] opts = {"Main 3x3 (5 cartes)", "Main 5x5 (13 cartes)", "Refaire ma collection", "Fermer"};
        int ch = JOptionPane.showOptionDialog(frame, "Que veux-tu modifier ?", "Collection et mains",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (ch == 2) { pickCollection(); return; }
        if (ch != 0 && ch != 1) return;
        if (!profile.hasCollection() && !pickCollection()) return;
        pickHand(ch == 0 ? Profile.DECK_SIZE : Profile.BIG_DECK_SIZE);
    }

    // ---------- parties ----------

    private void playBot() {
        Rules r = RulesDialog.ask(frame, profile, "Jouer contre l'IA – règles");
        if (r == null || !ensureDeck(r.handSize())) return;
        startBot(r);
    }

    @Override public void startBot(final Rules rules) {
        if (lobbyShown) irc.clearListener(lobby);
        List<Card> mine = profile.deck(rules.handSize());
        Owner first = new Random().nextBoolean() ? Owner.PLAYER : Owner.AI;
        game = new GamePanel(assets, profile, mine, profile.aiDeckFor(mine), rules, first, null, null,
                () -> startBot(rules),
                () -> { game = null; if (irc.isConnected()) showLobby(); else showMenu(); });
        show(game);
    }

    @Override public void startOnline(String opp, String oppDeckCsv, String bits, boolean localStarts) {
        Rules rules = Rules.fromBits(bits);
        List<Card> mine = profile.deck(rules.handSize());
        List<Card> theirs = profile.cardsFromCsv(oppDeckCsv);
        if (mine.size() != rules.handSize() || theirs.size() != rules.handSize()) {
            irc.send(opp, "QUIT");
            JOptionPane.showMessageDialog(frame, "Main invalide (la tienne ou celle de " + opp + ").");
            return;
        }
        lobby.onHidden();
        lobbyShown = false;
        final GamePanel g = new GamePanel(assets, profile, mine, theirs, rules,
                localStarts ? Owner.PLAYER : Owner.AI, opp, irc, null, this::showLobby);
        game = g;
        show(g);
        irc.setListener(g);   // livre aussi les coups arrivés avant l'ouverture de la partie
    }

    private void showLobby() {
        game = null;
        lobbyShown = true;
        show(lobby);
        lobby.onShown();
    }

    @Override public void back() { showMenu(); }

    // ---------- démarrage ----------

    public static void main(String[] args) {
        Path dir = Paths.get(args.length > 0 ? args[0] : "../app/src/main/assets");
        if (!Files.isRegularFile(dir.resolve("cards.json"))) dir = Paths.get("app/src/main/assets");
        final Path assetsDir = dir;
        SwingUtilities.invokeLater(() -> {
            try {
                new Main(new Assets(assetsDir));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Impossible de lire cards.json dans :\n" + assetsDir.toAbsolutePath()
                                + "\n\nPasse le dossier assets en argument.\n" + ex,
                        "Triple Triad", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
