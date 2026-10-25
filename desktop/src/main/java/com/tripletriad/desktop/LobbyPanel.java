package com.tripletriad.desktop;

import com.tripletriad.Rules;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Défis en ligne : même protocole IRC que l'appli Android (messages privés "TT1 ..."), donc PC et Android
 * peuvent s'affronter dans le même salon.
 */
final class LobbyPanel extends JPanel implements IrcClient.Listener {
    interface Host {
        boolean ensureDeck(int handSize);
        void startBot(Rules rules);
        void startOnline(String opp, String oppDeckCsv, String bits, boolean localStarts);
        void back();
    }

    private static final String BOT = "Bot (IA)";
    private static final Color[] PALETTE = {new Color(0x6E97FF), new Color(0xFF7C75), new Color(0x7CE0A1),
            new Color(0xFFB86B), new Color(0xD59CFF), new Color(0x6FD6E8), new Color(0xE8E26F), new Color(0xFF9CCB)};
    private static final Color BG = new Color(0x0F2A1D), GOLD = new Color(0xFFD54A);

    private final IrcClient irc;
    private final Profile profile;
    private final Host host;
    private final LinkedList<String> log = new LinkedList<>();

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JTextField serverField, nickField, channelField, chatInput = new JTextField();
    private final JCheckBox sslBox = new JCheckBox("Connexion sécurisée (SSL)");
    private final JTextArea statusArea = new JTextArea(5, 40);
    private final JLabel chanTitle = new JLabel(" "), usersHeader = new JLabel(" ");
    private final JTextPane chatPane = new JTextPane();
    private final DefaultListModel<String> users = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(users);

    private String pendingOut, pendingBits;      // défi que j'ai envoyé
    private String incomingFrom, incomingBits;   // défi que j'ai accepté
    private JDialog dialog;

    LobbyPanel(IrcClient irc, Profile profile, Host host) {
        this.irc = irc;
        this.profile = profile;
        this.host = host;
        serverField = new JTextField(profile.get("irc_server", "irc.quakenet.org:6667"), 24);
        nickField = new JTextField(profile.get("irc_nick", "Joueur" + (100 + new Random().nextInt(900))), 24);
        channelField = new JTextField(profile.get("irc_channel", "#tripletriad-fr"), 24);
        sslBox.setSelected(profile.getBool("irc_ssl", false));

        setLayout(new BorderLayout());
        setBackground(BG);
        setPreferredSize(new Dimension(900, 580));
        body.setOpaque(false);
        body.add(buildConnectPanel(), "connect");
        body.add(buildChatPanel(), "chat");
        add(body, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
    }

    // ---------- interface ----------

    private JComponent buildConnectPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        JLabel title = new JLabel("DÉFIS EN LIGNE", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(GOLD);
        p.add(title, c);
        String[] labels = {"Serveur:port", "Pseudo", "Salon"};
        JComponent[] fields = {serverField, nickField, channelField};
        c.gridwidth = 1;
        for (int i = 0; i < 3; i++) {
            c.gridy = i + 1; c.gridx = 0; c.weightx = 0;
            JLabel l = new JLabel(labels[i]);
            l.setForeground(Color.WHITE);
            p.add(l, c);
            c.gridx = 1; c.weightx = 1;
            p.add(fields[i], c);
        }
        c.gridy = 4; c.gridx = 1;
        sslBox.setOpaque(false);
        sslBox.setForeground(Color.WHITE);
        p.add(sslBox, c);

        JButton connect = new JButton("Se connecter");
        connect.addActionListener(e -> onConnectClicked());
        JButton bot = new JButton("Jouer contre le bot (sans connexion)");
        bot.addActionListener(e -> showRules(BOT));
        JButton back = new JButton("Retour au menu");
        back.addActionListener(e -> { irc.disconnect(); irc.clearListener(this); host.back(); });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setOpaque(false);
        btns.add(connect); btns.add(bot); btns.add(back);
        c.gridy = 5; c.gridx = 0; c.gridwidth = 2;
        p.add(btns, c);

        statusArea.setEditable(false);
        statusArea.setBackground(new Color(0, 0, 0, 60));
        statusArea.setForeground(new Color(0xB5C7BB));
        c.gridy = 6;
        p.add(statusArea, c);
        return p;
    }

    private JComponent buildChatPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setOpaque(false);

        chanTitle.setForeground(GOLD);
        chanTitle.setFont(chanTitle.getFont().deriveFont(Font.BOLD, 16f));
        JButton leave = new JButton("Quitter");
        leave.addActionListener(e -> {
            irc.disconnect();
            log.clear();
            statusArea.setText("Déconnecté");
            refreshUsers();
            updateConnectUi();
        });
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(chanTitle, BorderLayout.CENTER);
        bar.add(leave, BorderLayout.EAST);
        p.add(bar, BorderLayout.NORTH);

        chatPane.setEditable(false);
        chatPane.setBackground(new Color(10, 24, 18));
        chatPane.setForeground(Color.WHITE);
        chatPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JButton send = new JButton("OK");
        send.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        JPanel input = new JPanel(new BorderLayout(6, 0));
        input.setOpaque(false);
        input.add(chatInput, BorderLayout.CENTER);
        input.add(send, BorderLayout.EAST);
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(new JScrollPane(chatPane), BorderLayout.CENTER);
        left.add(input, BorderLayout.SOUTH);
        p.add(left, BorderLayout.CENTER);

        usersHeader.setForeground(GOLD);
        usersHeader.setFont(usersHeader.getFont().deriveFont(Font.BOLD));
        userList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel t = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                String n = (String) v;
                boolean bot = n.equals(BOT), me = !bot && n.equalsIgnoreCase(irc.nick());
                t.setText(me ? "★ " + n : n);
                if (!s) t.setForeground(bot ? GOLD : me ? new Color(0x7CE0A1) : Color.WHITE);
                if (!s) t.setBackground(new Color(10, 24, 18));
                return t;
            }
        });
        userList.setBackground(new Color(10, 24, 18));
        userList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) challengeSelected(); }
        });
        JButton defy = new JButton("Défier");
        defy.addActionListener(e -> challengeSelected());
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(190, 10));
        right.add(usersHeader, BorderLayout.NORTH);
        right.add(new JScrollPane(userList), BorderLayout.CENTER);
        right.add(defy, BorderLayout.SOUTH);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    /** L'écran du lobby passe au premier plan : on (ré)écoute IRC et on réaffiche l'historique. */
    void onShown() {
        irc.setListener(this);
        chatPane.setText("");
        for (String[] c : irc.chatHistory()) appendLine(c[0], c[1], c[2], c[3]);
        refreshUsers();
        updateConnectUi();
        if (!irc.isConnected() && statusArea.getText().isEmpty()) statusArea.setText("Non connecté (le bot reste jouable)");
    }

    void onHidden() {
        irc.clearListener(this);
        dismissDialog();
    }

    // ---------- connexion et discussion ----------

    private void onConnectClicked() {
        String server = serverField.getText().trim();
        boolean ssl = sslBox.isSelected();
        int port = ssl ? 6697 : 6667;
        String host = server;
        if (server.contains(":")) {
            host = server.substring(0, server.indexOf(':'));
            try { port = Integer.parseInt(server.substring(server.indexOf(':') + 1).trim()); }
            catch (NumberFormatException ignored) { }
        }
        String nick = nickField.getText().trim().replaceAll("[^A-Za-z0-9_\\-\\[\\]\\\\`^{}]", "_");
        String chan = channelField.getText().trim();
        if (host.isEmpty() || nick.isEmpty() || chan.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Serveur, pseudo et salon requis");
            return;
        }
        profile.set("irc_server", server);
        profile.set("irc_nick", nick);
        profile.set("irc_channel", chan);
        profile.setBool("irc_ssl", ssl);
        log.clear();
        chatPane.setText("");
        irc.connect(host, port, ssl, nick, chan);
    }

    private void updateConnectUi() {
        boolean on = irc.isConnected();
        cards.show(body, on ? "chat" : "connect");
        chanTitle.setText(irc.channel() + "  –  " + irc.nick());
    }

    private void refreshUsers() {
        users.clear();
        users.addElement(BOT);
        if (irc.isConnected()) {
            users.addElement(irc.nick());
            for (String u : irc.users()) users.addElement(u);
        }
        usersHeader.setText("Joueurs (" + (irc.isConnected() ? irc.users().size() + 1 : 0) + ")");
    }

    private void sendChat() {
        String t = chatInput.getText().trim();
        if (t.isEmpty()) return;
        irc.sendChat(t);
        chatInput.setText("");
    }

    private Color nickColor(String nick) {
        if (nick.equalsIgnoreCase(irc.nick())) return GOLD;
        return PALETTE[Math.abs(nick.toLowerCase().hashCode()) % PALETTE.length];
    }

    private void span(String s, Color color, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setBold(a, bold);
        StyledDocument doc = chatPane.getStyledDocument();
        try { doc.insertString(doc.getLength(), s, a); } catch (BadLocationException ignored) { }
    }

    private void appendLine(String kind, String nick, String text, String time) {
        span("[" + time + "] ", new Color(0x7F9A8A), false);
        switch (kind) {
            case "MSG": span("<" + nick + "> ", nickColor(nick), true); span(text, Color.WHITE, false); break;
            case "ACTION": span("* " + nick + " " + text, new Color(0xD59CFF), false); break;
            case "JOIN": span("→ " + nick + " a rejoint " + text, new Color(0x7CE0A1), false); break;
            case "PART": span("← " + nick + " est parti", new Color(0x9AA79F), false); break;
            case "QUIT": span("← " + nick + " a quitté" + (text.isEmpty() ? "" : " (" + text + ")"), new Color(0x9AA79F), false); break;
            case "NICK": span("* " + nick + " s'appelle maintenant " + text, new Color(0x9AA79F), false); break;
            case "TOPIC": span("Sujet : " + text, new Color(0xFFB86B), false); break;
            default: span("* " + text, GOLD, false);
        }
        span("\n", Color.WHITE, false);
        StyledDocument doc = chatPane.getStyledDocument();
        try { if (doc.getLength() > 30000) doc.remove(0, 10000); } catch (BadLocationException ignored) { }
        chatPane.setCaretPosition(doc.getLength());
    }

    // ---------- défis ----------

    private void challengeSelected() {
        String who = userList.getSelectedValue();
        if (who == null) return;
        if (!who.equals(BOT) && who.equalsIgnoreCase(irc.nick())) {
            JOptionPane.showMessageDialog(this, "C'est vous !");
            return;
        }
        showRules(who);
    }

    private void showRules(String who) {
        Rules r = RulesDialog.ask(this, profile, "Défier " + who + " – règles");
        if (r == null) return;
        if (!host.ensureDeck(r.handSize())) return;
        if (who.equals(BOT)) host.startBot(r);
        else sendChallenge(who, r.bits());
    }

    private void sendChallenge(final String who, String bits) {
        if (!irc.isConnected()) { JOptionPane.showMessageDialog(this, "Connectez-vous d'abord"); return; }
        pendingOut = who;
        pendingBits = bits;
        irc.send(who, "CHALLENGE " + bits);
        showDialog("Défi", "En attente de " + who + "…<br>Règles : " + Rules.fromBits(bits).describe(),
                new String[]{"Annuler"}, new Runnable[]{() -> { irc.send(who, "CANCEL"); pendingOut = null; }});
    }

    private void launchOnline(String opp, String oppDeck, String bits, boolean localStarts) {
        dismissDialog();
        pendingOut = null;
        incomingFrom = null;
        host.startOnline(opp, oppDeck, bits, localStarts);
    }

    /** Petite fenêtre non bloquante (les messages IRC continuent d'arriver pendant qu'elle est ouverte). */
    private void showDialog(String title, String html, String[] labels, final Runnable[] actions) {
        dismissDialog();
        final JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), title, Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JLabel l = new JLabel("<html>" + html + "</html>");
        l.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));
        JPanel b = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        for (int i = 0; i < labels.length; i++) {
            final Runnable a = actions[i];
            JButton btn = new JButton(labels[i]);
            btn.addActionListener(e -> { dismissDialog(); if (a != null) a.run(); });
            b.add(btn);
        }
        d.add(l, BorderLayout.CENTER);
        d.add(b, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        dialog = d;
        d.setVisible(true);
    }

    private void dismissDialog() {
        if (dialog != null) { dialog.dispose(); dialog = null; }
    }

    // ---------- événements IRC ----------

    @Override public void onStatus(String text) {
        log.add(text);
        while (log.size() > 6) log.removeFirst();
        statusArea.setText(String.join("\n", log));
        updateConnectUi();
    }

    @Override public void onChat(String kind, String nick, String text, String time) { appendLine(kind, nick, text, time); }

    @Override public void onUsers(List<String> list) { refreshUsers(); updateConnectUi(); }

    @Override public void onDisconnected() {
        onStatus("(déconnecté)");
        dismissDialog();
        pendingOut = null;
        incomingFrom = null;
        refreshUsers();
        updateConnectUi();
    }

    @Override public void onGameMessage(final String from, String cmd, String arg) {
        switch (cmd) {
            case "CHALLENGE": {
                final String bits = arg.trim();
                if (pendingOut != null || incomingFrom != null || dialog != null
                        || !profile.hasDeck(Rules.fromBits(bits).handSize())) { irc.send(from, "BUSY"); return; }
                showDialog(from + " vous défie !", "Règles : " + Rules.fromBits(bits).describe(),
                        new String[]{"Accepter", "Refuser"},
                        new Runnable[]{
                                () -> {
                                    incomingFrom = from;
                                    incomingBits = bits;
                                    irc.send(from, "ACCEPT " + Profile.csv(profile.deck(Rules.fromBits(bits).handSize())));
                                    showDialog("Défi", "Démarrage de la partie avec " + from + "…", new String[]{"Annuler"},
                                            new Runnable[]{() -> { irc.send(from, "DECLINE"); incomingFrom = null; }});
                                },
                                () -> irc.send(from, "DECLINE")});
                break;
            }
            case "ACCEPT":
                if (from.equalsIgnoreCase(pendingOut)) {
                    boolean iStart = new Random().nextBoolean();
                    irc.send(from, "START " + (iStart ? "C" : "O") + " "
                            + Profile.csv(profile.deck(Rules.fromBits(pendingBits).handSize())));
                    launchOnline(from, arg.trim(), pendingBits, iStart);
                }
                break;
            case "START":
                if (from.equalsIgnoreCase(incomingFrom)) {
                    String[] p = arg.trim().split(" ");
                    if (p.length == 2) launchOnline(from, p[1], incomingBits, p[0].equals("O"));
                }
                break;
            case "DECLINE": case "BUSY":
                if (from.equalsIgnoreCase(pendingOut)) {
                    dismissDialog();
                    pendingOut = null;
                    JOptionPane.showMessageDialog(this, from + (cmd.equals("BUSY") ? " est occupé" : " a refusé le défi"));
                }
                break;
            case "CANCEL":
                if (from.equalsIgnoreCase(incomingFrom) || (incomingFrom == null && dialog != null)) {
                    dismissDialog();
                    incomingFrom = null;
                    JOptionPane.showMessageDialog(this, from + " a annulé le défi");
                }
                break;
            default:
        }
    }
}
