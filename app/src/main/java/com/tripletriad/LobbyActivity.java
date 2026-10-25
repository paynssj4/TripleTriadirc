package com.tripletriad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client IRC pour défier : connexion, puis discussion du salon (gauche) + colonne des pseudos (droite).
 * Toucher un pseudo (ou le Bot) ouvre le choix des règles et lance le défi.
 */
public class LobbyActivity extends Activity implements IrcSession.Listener {
    private static final String BOT = "Bot (IA)";
    private static final int[] PALETTE = {0xFF6E97FF, 0xFFFF7C75, 0xFF7CE0A1, 0xFFFFB86B,
            0xFFD59CFF, 0xFF6FD6E8, 0xFFE8E26F, 0xFFFF9CCB};

    private final IrcSession irc = IrcSession.get();
    private final List<String> entries = new ArrayList<>();
    private final java.util.LinkedList<String> log = new java.util.LinkedList<>();
    private SharedPreferences prefs;
    private CardRepository repo;
    private float dp;

    private LinearLayout connectPanel, chatPanel;
    private EditText serverEt, nickEt, channelEt, chatInput;
    private CheckBox sslCb;
    private TextView status, chanTitle, usersHeader, chatTv;
    private ScrollView chatScroll;
    private ArrayAdapter<String> adapter;

    private String pendingOut, pendingBits;      // défi que j'ai envoyé
    private String incomingFrom, incomingBits;   // défi que j'ai accepté
    private AlertDialog dialog;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tt", Context.MODE_PRIVATE);
        repo = new CardRepository(this);
        dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F2A1D);
        int pad = (int) (12 * dp);
        root.setPadding(pad, (int) (32 * dp), pad, pad);
        root.addView(buildConnectPanel(), new LinearLayout.LayoutParams(-1, -1));
        root.addView(buildChatPanel(), new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    // ---------- construction de l'interface ----------

    private View buildConnectPanel() {
        connectPanel = new LinearLayout(this);
        connectPanel.setOrientation(LinearLayout.VERTICAL);
        connectPanel.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);

        TextView title = new TextView(this);
        title.setText("DÉFIS EN LIGNE");
        title.setTextSize(24);
        title.setTextColor(0xFFFFD54A);
        title.setGravity(Gravity.CENTER);
        connectPanel.addView(title);

        serverEt = edit("Serveur:port", prefs.getString("irc_server", "irc.quakenet.org:6667"));
        nickEt = edit("Pseudo", prefs.getString("irc_nick", "Joueur" + (100 + new Random().nextInt(900))));
        channelEt = edit("Salon (ex: #tt-nosamis)", prefs.getString("irc_channel", "#tripletriad-fr"));
        sslCb = new CheckBox(this);
        sslCb.setText("Connexion sécurisée (SSL)");
        sslCb.setTextColor(Color.WHITE);
        sslCb.setChecked(prefs.getBoolean("irc_ssl", false));
        connectPanel.addView(serverEt);
        connectPanel.addView(nickEt);
        connectPanel.addView(channelEt);
        connectPanel.addView(sslCb);

        Button connect = pillButton("SE CONNECTER", 0xFF3A6BD8);
        connect.setOnClickListener(v -> onConnectClicked());
        connectPanel.addView(connect, new LinearLayout.LayoutParams(-1, (int) (48 * dp)));

        Button bot = pillButton("JOUER CONTRE LE BOT (sans connexion)", 0xFF2F9E5B);
        bot.setOnClickListener(v -> showRulesDialog(BOT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (48 * dp));
        lp.topMargin = (int) (8 * dp);
        connectPanel.addView(bot, lp);

        status = new TextView(this);
        status.setTextColor(0xFFB5C7BB);
        status.setPadding(0, (int) (12 * dp), 0, 0);
        connectPanel.addView(status);
        return connectPanel;
    }

    private View buildChatPanel() {
        chatPanel = new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);

        // barre du haut : salon + pseudo, bouton Quitter
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        chanTitle = new TextView(this);
        chanTitle.setTextColor(0xFFFFD54A);
        chanTitle.setTextSize(16);
        chanTitle.setTypeface(Typeface.DEFAULT_BOLD);
        chanTitle.setSingleLine(true);
        chanTitle.setEllipsize(TextUtils.TruncateAt.END);
        Button leave = pillButton("Quitter", 0xFF8A3B38);
        leave.setTextSize(13);
        leave.setOnClickListener(v -> {
            irc.disconnect();
            log.clear();
            status.setText("Déconnecté");
            refreshUsers();
            updateConnectUi();
        });
        bar.addView(chanTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(leave, new LinearLayout.LayoutParams((int) (90 * dp), (int) (38 * dp)));
        chatPanel.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        // corps : [discussion + saisie] | [colonne des pseudos]
        LinearLayout body = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        chatTv = new TextView(this);
        chatTv.setTextSize(14);
        chatTv.setTextColor(Color.WHITE);
        chatTv.setPadding((int) (6 * dp), (int) (4 * dp), (int) (6 * dp), (int) (4 * dp));
        chatScroll = new ScrollView(this);
        chatScroll.setBackgroundColor(0x33000000);
        chatScroll.addView(chatTv);
        left.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        chatInput = edit("Message…", "");
        chatInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        chatInput.setOnEditorActionListener((v, a, e) -> { sendChat(); return true; });
        Button send = pillButton("OK", 0xFF3A6BD8);
        send.setOnClickListener(v -> sendChat());
        inputRow.addView(chatInput, new LinearLayout.LayoutParams(0, -2, 1f));
        inputRow.addView(send, new LinearLayout.LayoutParams((int) (56 * dp), (int) (40 * dp)));
        left.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setBackgroundColor(0x33000000);
        usersHeader = new TextView(this);
        usersHeader.setTextColor(0xFFFFD54A);
        usersHeader.setTypeface(Typeface.DEFAULT_BOLD);
        usersHeader.setPadding((int) (8 * dp), (int) (4 * dp), 0, (int) (4 * dp));
        right.addView(usersHeader);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, entries) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convert, parent);
                String n = entries.get(pos);
                boolean bot = n.equals(BOT), me = !bot && n.equalsIgnoreCase(irc.nick());
                t.setText(bot ? "🤖 " + n : me ? "★ " + n : n);
                t.setTextColor(bot ? 0xFFFFD54A : me ? 0xFF7CE0A1 : Color.WHITE);
                t.setTextSize(15);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setMinHeight((int) (40 * dp));
                return t;
            }
        };
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            String who = entries.get(pos);
            if (!who.equals(BOT) && who.equalsIgnoreCase(irc.nick()))
                Toast.makeText(this, "C'est vous", Toast.LENGTH_SHORT).show();
            else showRulesDialog(who);
        });
        right.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams((int) (122 * dp), -1);
        rl.leftMargin = (int) (6 * dp);
        body.addView(right, rl);
        chatPanel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        chatPanel.setVisibility(View.GONE);
        return chatPanel;
    }

    @Override protected void onResume() {
        super.onResume();
        irc.setListener(this);
        chatTv.setText("");
        for (String[] c : irc.chatHistory()) appendLine(c[0], c[1], c[2], c[3]);
        refreshUsers();
        updateConnectUi();
        if (!irc.isConnected() && status.getText().length() == 0)
            status.setText("Non connecté (le Bot reste jouable)");
    }

    @Override protected void onPause() {
        irc.clearListener(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (isFinishing()) irc.disconnect();
        super.onDestroy();
    }

    // ---------- connexion ----------

    private void onConnectClicked() {
        String server = serverEt.getText().toString().trim();
        int port = sslCb.isChecked() ? 6697 : 6667;
        String host = server;
        if (server.contains(":")) {
            host = server.substring(0, server.indexOf(':'));
            try { port = Integer.parseInt(server.substring(server.indexOf(':') + 1).trim()); }
            catch (NumberFormatException ignored) {}
        }
        String nick = nickEt.getText().toString().trim().replaceAll("[^A-Za-z0-9_\\-\\[\\]\\\\`^{}]", "_");
        String chan = channelEt.getText().toString().trim();
        if (host.isEmpty() || nick.isEmpty() || chan.isEmpty()) {
            Toast.makeText(this, "Serveur, pseudo et salon requis", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("irc_server", server).putString("irc_nick", nick).putString("irc_channel", chan)
                .putBoolean("irc_ssl", sslCb.isChecked()).apply();
        log.clear();
        chatTv.setText("");
        irc.connect(host, port, sslCb.isChecked(), nick, chan);
    }

    private void updateConnectUi() {
        boolean on = irc.isConnected();
        connectPanel.setVisibility(on ? View.GONE : View.VISIBLE);
        chatPanel.setVisibility(on ? View.VISIBLE : View.GONE);
        chanTitle.setText(irc.channel() + "  –  " + irc.nick());
    }

    private void refreshUsers() {
        entries.clear();
        entries.add(BOT);
        if (irc.isConnected()) {
            entries.add(irc.nick());
            entries.addAll(irc.users());
        }
        usersHeader.setText("Joueurs (" + (irc.isConnected() ? irc.users().size() + 1 : 0) + ")");
        adapter.notifyDataSetChanged();
    }

    // ---------- discussion ----------

    private void sendChat() {
        String t = chatInput.getText().toString().trim();
        if (t.isEmpty()) return;
        irc.sendChat(t);
        chatInput.setText("");
    }

    private int nickColor(String nick) {
        if (nick.equalsIgnoreCase(irc.nick())) return 0xFFFFD54A;
        return PALETTE[Math.abs(nick.toLowerCase().hashCode()) % PALETTE.length];
    }

    private void span(SpannableStringBuilder sb, String s, int color, boolean bold) {
        int st = sb.length();
        sb.append(s);
        sb.setSpan(new ForegroundColorSpan(color), st, sb.length(), 0);
        if (bold) sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), 0);
    }

    private void appendLine(String kind, String nick, String text, String time) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        span(sb, "[" + time + "] ", 0xFF7F9A8A, false);
        switch (kind) {
            case "MSG":
                span(sb, "<" + nick + "> ", nickColor(nick), true);
                span(sb, text, Color.WHITE, false);
                break;
            case "ACTION":
                span(sb, "* " + nick + " " + text, 0xFFD59CFF, false);
                break;
            case "JOIN":
                span(sb, "→ " + nick + " a rejoint " + text, 0xFF7CE0A1, false);
                break;
            case "PART":
                span(sb, "← " + nick + " est parti", 0xFF9AA79F, false);
                break;
            case "QUIT":
                span(sb, "← " + nick + " a quitté" + (text.isEmpty() ? "" : " (" + text + ")"), 0xFF9AA79F, false);
                break;
            case "NICK":
                span(sb, "* " + nick + " s'appelle maintenant " + text, 0xFF9AA79F, false);
                break;
            case "TOPIC":
                span(sb, "Sujet : " + text, 0xFFFFB86B, false);
                break;
            default:
                span(sb, "* " + text, 0xFFFFD54A, false);
        }
        sb.append('\n');
        chatTv.append(sb);
        Editable e = chatTv.getEditableText();
        if (e != null && e.length() > 30000) e.delete(0, e.length() - 20000);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- défis ----------

    private void showRulesDialog(final String who) {
        if (!repo.hasDeck()) {
            Toast.makeText(this, "Choisissez d'abord votre deck de 5 cartes", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, DeckActivity.class));
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (24 * dp), (int) (8 * dp), (int) (24 * dp), 0);
        final CheckBox same = rule("Identique", "r_same"), plus = rule("Plus", "r_plus"), combo = rule("Combo (chaîne)", "r_combo"),
                wall = rule("Même Mur (Identique + bords = 10)", "r_wall"),
                broken = rule("Mur cassé (plateau sans bords)", "r_broken"),
                big = rule("Plateau 5×5 (13 cartes)", "r_big");
        box.addView(same); box.addView(plus); box.addView(combo); box.addView(wall); box.addView(broken); box.addView(big);

        new AlertDialog.Builder(this)
                .setTitle("Défier " + who + " – règles")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Défier", (d, w) -> {
                    prefs.edit().putBoolean("r_same", same.isChecked()).putBoolean("r_plus", plus.isChecked())
                            .putBoolean("r_combo", combo.isChecked()).putBoolean("r_wall", wall.isChecked()).putBoolean("r_broken", broken.isChecked()).putBoolean("r_big", big.isChecked()).apply();
                    String bits = new Rules(same.isChecked(), plus.isChecked(), combo.isChecked(), wall.isChecked(), broken.isChecked(), big.isChecked()).bits();
                    int need = Rules.fromBits(bits).handSize();
                    if (!repo.hasDeck(need)) {
                        Toast.makeText(this, "Complétez d'abord votre main de " + need + " cartes", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, DeckActivity.class).putExtra("hand", need));
                        return;
                    }
                    if (who.equals(BOT)) startBot(bits);
                    else sendChallenge(who, bits);
                }).show();
    }

    private CheckBox rule(String label, String key) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(prefs.getBoolean(key, false));
        return c;
    }

    private void startBot(String bits) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("mode", "bot").putExtra("bits", bits);
        startActivity(i);
    }

    private void sendChallenge(final String who, String bits) {
        if (!irc.isConnected()) { Toast.makeText(this, "Connectez-vous d'abord", Toast.LENGTH_SHORT).show(); return; }
        pendingOut = who;
        pendingBits = bits;
        irc.send(who, "CHALLENGE " + bits);
        showWaiting("En attente de " + who + "…\nRègles : " + Rules.fromBits(bits).describe(), () -> {
            irc.send(who, "CANCEL");
            pendingOut = null;
        });
    }

    private void showWaiting(String msg, final Runnable onCancel) {
        dismissDialog();
        dialog = new AlertDialog.Builder(this).setMessage(msg).setCancelable(false)
                .setNegativeButton("Annuler", (d, w) -> onCancel.run()).show();
    }

    private void dismissDialog() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    private void launchOnline(String opp, String oppDeck, String bits, boolean localStarts) {
        dismissDialog();
        pendingOut = null;
        incomingFrom = null;
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("mode", "online").putExtra("opp", opp).putExtra("oppDeck", oppDeck)
                .putExtra("bits", bits).putExtra("localStarts", localStarts);
        startActivity(i);
    }

    // ---------- événements IRC ----------

    @Override public void onStatus(String text) {
        log.add(text);
        while (log.size() > 6) log.removeFirst();   // la cause d'un refus arrive avant la coupure : on garde l'historique
        StringBuilder sb = new StringBuilder();
        for (String l : log) { if (sb.length() > 0) sb.append('\n'); sb.append(l); }
        status.setText(sb);
        updateConnectUi();
    }

    @Override public void onChat(String kind, String nick, String text, String time) {
        appendLine(kind, nick, text, time);
    }

    @Override public void onUsers(List<String> users) { refreshUsers(); updateConnectUi(); }

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
                if (pendingOut != null || incomingFrom != null || !repo.hasDeck(Rules.fromBits(arg.trim()).handSize())) { irc.send(from, "BUSY"); return; }
                final String bits = arg.trim();
                dismissDialog();
                dialog = new AlertDialog.Builder(this)
                        .setTitle(from + " vous défie !")
                        .setMessage("Règles : " + Rules.fromBits(bits).describe())
                        .setCancelable(false)
                        .setNegativeButton("Refuser", (d, w) -> irc.send(from, "DECLINE"))
                        .setPositiveButton("Accepter", (d, w) -> {
                            incomingFrom = from;
                            incomingBits = bits;
                            irc.send(from, "ACCEPT " + CardRepository.csv(repo.deck(Rules.fromBits(bits).handSize())));
                            showWaiting("Démarrage de la partie avec " + from + "…", () -> {
                                irc.send(from, "DECLINE");
                                incomingFrom = null;
                            });
                        }).show();
                break;
            }
            case "ACCEPT":
                if (from.equalsIgnoreCase(pendingOut)) {
                    boolean iStart = new Random().nextBoolean();
                    irc.send(from, "START " + (iStart ? "C" : "O") + " " + CardRepository.csv(repo.deck(Rules.fromBits(pendingBits).handSize())));
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
                    Toast.makeText(this, from + (cmd.equals("BUSY") ? " est occupé" : " a refusé le défi"),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case "CANCEL":
                if (from.equalsIgnoreCase(incomingFrom) || (incomingFrom == null && dialog != null)) {
                    dismissDialog();
                    incomingFrom = null;
                    Toast.makeText(this, from + " a annulé le défi", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }

    // ---------- utilitaires UI ----------

    private EditText edit(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x88FFFFFF);
        return e;
    }

    private Button pillButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(40 * dp);
        b.setBackground(g);
        return b;
    }
}
