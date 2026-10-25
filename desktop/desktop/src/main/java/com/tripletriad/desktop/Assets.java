package com.tripletriad.desktop;

import com.tripletriad.Card;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Charge cards.json et les images depuis le dossier assets de l'appli Android. */
final class Assets {
    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ATTR = Pattern.compile("\"attribute\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern IMAGE = Pattern.compile("\"image\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NUMBERS = Pattern.compile("\"numbers\"\\s*:\\s*\\[([^\\]]*)\\]");

    final Path dir;
    final List<Card> cards;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    Assets(Path dir) throws IOException {
        this.dir = dir;
        this.cards = parse(new String(Files.readAllBytes(dir.resolve("cards.json")), StandardCharsets.UTF_8));
        if (cards.isEmpty()) throw new IOException("cards.json vide ou illisible");
    }

    static List<Card> parse(String json) {
        List<Card> out = new ArrayList<>();
        Matcher o = OBJECT.matcher(json);
        while (o.find()) {
            String b = o.group();
            Matcher n = NUMBERS.matcher(b), name = NAME.matcher(b);
            if (!n.find() || !name.find()) continue;
            String[] p = n.group(1).split(",");
            if (p.length != 4) continue;
            int[] v = new int[4];
            for (int i = 0; i < 4; i++) v[i] = Integer.parseInt(p[i].trim());
            out.add(new Card(name.group(1), find(ATTR, b), find(IMAGE, b), v));
        }
        return out;
    }

    private static String find(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    /** Image de la carte, ou null si absente / format non lisible. */
    BufferedImage image(Card c) {
        if (c.image == null || c.image.isEmpty()) return null;
        if (cache.containsKey(c.image)) return cache.get(c.image);
        BufferedImage img = null;
        String base = c.image.replaceAll("\\.[A-Za-z0-9]+$", "");
        for (String rel : new String[]{c.image, base + ".webp", base + ".png", base + ".jpg"}) {
            File f = dir.resolve(rel).toFile();
            if (!f.isFile()) continue;
            try { img = ImageIO.read(f); } catch (Exception ignored) { }
            if (img != null) break;
        }
        cache.put(c.image, img);
        return img;
    }
}
