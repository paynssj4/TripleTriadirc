package com.tripletriad.desktop;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Client IRC minimal (singleton, partagé entre le lobby et la partie).
 * Les défis et les coups passent par des messages privés préfixés "TT1 " :
 *   CHALLENGE <bits> | ACCEPT <deck> | START <C|O> <deck> | MOVE <carte> <case> | DECLINE | BUSY | CANCEL | QUIT
 */
public final class IrcClient {

    public interface Listener {
        default void onStatus(String text) {}
        default void onUsers(List<String> users) {}
        default void onGameMessage(String from, String cmd, String arg) {}
        default void onDisconnected() {}
        default void onChat(String kind, String nick, String text, String time) {}
    }

    
    

    private final Executor main = SwingUtilities::invokeLater;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); // thread "irc" seulement
    private final ArrayDeque<String[]> backlog = new ArrayDeque<>();
    private volatile List<String> snapshot = Collections.emptyList();
    private volatile boolean connected;
    private volatile int generation;
    private volatile Socket socket;
    private volatile BufferedWriter out;
    private volatile String nick = "", channel = "";
    private final ArrayDeque<String[]> chat = new ArrayDeque<>(); // {type, pseudo, texte, heure} (thread principal)
    private Listener listener; // thread principal seulement

    public IrcClient() {}

    public boolean isConnected() { return connected; }
    public String nick() { return nick; }
    public String channel() { return channel; }
    public List<String> users() { return snapshot; }
    public List<String[]> chatHistory() { return new ArrayList<>(chat); }

    /** Envoie un message dans le salon ("/me texte" = action). */
    public void sendChat(String text) {
        if (!connected || text.isEmpty()) return;
        if (text.startsWith("/me ")) {
            String a = text.substring(4);
            raw("PRIVMSG " + channel + " :\u0001ACTION " + a + "\u0001");
            postChat("ACTION", nick, a);
        } else {
            raw("PRIVMSG " + channel + " :" + text);
            postChat("MSG", nick, text);
        }
    }

    private void postChat(final String kind, final String who, final String text) {
        final String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        main.execute(() -> {
            chat.add(new String[]{kind, who, text, time});
            while (chat.size() > 200) chat.removeFirst();
            if (listener != null) listener.onChat(kind, who, text, time);
        });
    }

    public void setListener(Listener l) {
        listener = l;
        if (l == null || backlog.isEmpty()) return;
        List<String[]> pending = new ArrayList<>(backlog);
        backlog.clear();
        for (String[] m : pending) deliver(m[0], m[1], m[2]);
    }

    public void clearListener(Listener l) { if (listener == l) listener = null; }

    public void connect(final String host, final int port, final boolean ssl, String wantedNick, String chan) {
        disconnect();
        final int gen = ++generation;
        nick = wantedNick;
        channel = chan.startsWith("#") ? chan : "#" + chan;
        chat.clear();
        status("Connexion à " + host + "…");
        new Thread(() -> {
            try {
                Socket s = open(host, port, ssl);
                s.setKeepAlive(true);
                if (gen != generation) { s.close(); return; }
                socket = s;
                out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
                raw("NICK " + nick);
                raw("USER " + nick + " 0 * :Triple Triad");
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                String line;
                while (gen == generation && (line = r.readLine()) != null) handle(line);
                if (gen == generation) status("Le serveur a fermé la connexion.");
            } catch (Exception e) {
                if (gen == generation) status("Erreur : " + e);
            }
            if (gen == generation) {
                connected = false;
                names.clear();
                snapshot = Collections.emptyList();
                main.execute(() -> { if (listener != null) listener.onDisconnected(); });
            }
        }, "irc").start();
    }

    /** Essaie toutes les adresses du serveur (un nom IRC en regroupe souvent plusieurs), délai de 8 s chacune. */
    private static Socket open(String host, int port, boolean ssl) throws Exception {
        InetAddress[] addrs = InetAddress.getAllByName(host);
        StringBuilder tried = new StringBuilder();
        for (InetAddress a : addrs) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(a, port), 8000);
                if (!ssl) return s;
                SSLSocket t = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(s, host, port, true);
                t.startHandshake();
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, t.getSession()))
                    throw new SSLPeerUnverifiedException("certificat invalide pour " + host);
                return t;
            } catch (Exception e) {
                tried.append("\n").append(a.getHostAddress()).append(" : ").append(e.getMessage());
                try { s.close(); } catch (Exception ignored) {}
            }
        }
        throw new java.io.IOException("Aucune des " + addrs.length + " adresses n'a répondu :" + tried);
    }

    public void disconnect() {
        generation++;
        connected = false;
        final Socket s = socket;
        final BufferedWriter w = out;
        socket = null;
        out = null;
        snapshot = Collections.emptyList();
        if (s == null) return;
        writer.execute(() -> {
            try { if (w != null) { w.write("QUIT :bye\r\n"); w.flush(); } } catch (Exception ignored) {}
            try { s.close(); } catch (Exception ignored) {}
        });
    }

    /** Envoie un message de jeu privé à un joueur. */
    public void send(String to, String text) { raw("PRIVMSG " + to + " :TT1 " + text); }

    private void raw(final String line) {
        final BufferedWriter w = out;
        if (w == null) return;
        writer.execute(() -> {
            try { w.write(line + "\r\n"); w.flush(); } catch (Exception ignored) {}
        });
    }

    private void status(final String t) {
        main.execute(() -> { if (listener != null) listener.onStatus(t); });
    }

    private void publishUsers() {
        List<String> l = new ArrayList<>();
        for (String n : names) if (!n.equalsIgnoreCase(nick)) l.add(n);
        snapshot = l;
        final List<String> copy = new ArrayList<>(l);
        main.execute(() -> { if (listener != null) listener.onUsers(copy); });
    }

    private void handle(String line) {
        String prefix = "", rest = line;
        if (line.startsWith(":")) {
            int sp = line.indexOf(' ');
            if (sp < 0) return;
            prefix = line.substring(1, sp);
            rest = line.substring(sp + 1);
        }
        String trailing = null;
        int ti = rest.indexOf(" :");
        if (ti >= 0) { trailing = rest.substring(ti + 2); rest = rest.substring(0, ti); }
        String[] p = rest.split(" ");
        String from = prefix.contains("!") ? prefix.substring(0, prefix.indexOf('!')) : prefix;

        switch (p[0]) {
            case "PING":
                raw("PONG :" + (trailing != null ? trailing : p.length > 1 ? p[1] : ""));
                break;
            case "001":
                connected = true;
                raw("JOIN " + channel);
                status("Connecté. Entrée dans " + channel + "…");
                break;
            case "433":
                if (!connected) { nick = nick + "_"; raw("NICK " + nick); }
                break;
            case "353":
                if (trailing != null)
                    for (String n : trailing.split(" "))
                        if (!n.isEmpty()) names.add(n.replaceAll("^[@+%~&]+", ""));
                break;
            case "332":
                if (trailing != null) postChat("TOPIC", "", trailing);
                break;
            case "366":
                status("Dans " + channel);
                postChat("INFO", "", "Vous êtes dans " + channel + ". Touchez un pseudo dans la colonne de droite pour le défier.");
                publishUsers();
                break;
            case "JOIN":
                if (!from.equalsIgnoreCase(nick)) { names.add(from); postChat("JOIN", from, channel); publishUsers(); }
                break;
            case "PART": case "QUIT":
                if (names.remove(from)) postChat(p[0], from, trailing == null ? "" : trailing);
                publishUsers();
                break;
            case "KICK":
                if (p.length > 2) { names.remove(p[2]); publishUsers(); }
                break;
            case "NICK": {
                String nn = trailing != null ? trailing : p.length > 1 ? p[1] : null;
                if (nn != null) {
                    names.remove(from); names.add(nn);
                    postChat("NICK", from, nn);
                    if (from.equalsIgnoreCase(nick)) nick = nn;
                    publishUsers();
                }
                break;
            }
            case "PRIVMSG":
                if (p.length > 1 && p[1].equalsIgnoreCase(nick) && trailing != null && trailing.startsWith("TT1 ")) {
                    String body = trailing.substring(4);
                    int sp = body.indexOf(' ');
                    final String cmd = sp < 0 ? body : body.substring(0, sp);
                    final String arg = sp < 0 ? "" : body.substring(sp + 1);
                    final String sender = from;
                    main.execute(() -> deliver(sender, cmd, arg));
                } else if (p.length > 1 && p[1].equalsIgnoreCase(channel) && trailing != null) {
                    boolean act = trailing.startsWith("\u0001ACTION ");
                    postChat(act ? "ACTION" : "MSG", from, act ? trailing.substring(8).replace("\u0001", "") : trailing);
                }
                break;
            case "NOTICE":
                if (!connected && trailing != null && !trailing.startsWith("***")) status("Serveur : " + trailing);
                break;
            case "ERROR":
                status("Serveur : " + (trailing != null ? trailing : rest));
                break;
            default:
                if (p[0].matches("[45]\\d\\d")) status("Serveur : " + (trailing != null ? trailing : rest));
        }
    }

    private void deliver(String from, String cmd, String arg) {
        if (listener != null) {
            listener.onGameMessage(from, cmd, arg);
        } else if (cmd.equals("MOVE") || cmd.equals("QUIT")) {
            backlog.add(new String[]{from, cmd, arg}); // la partie n'est pas encore à l'écran
        } else if (cmd.equals("CHALLENGE")) {
            send(from, "BUSY");
        }
    }
}
