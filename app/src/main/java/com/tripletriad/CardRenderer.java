package com.tripletriad;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.LruCache;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Dessine une carte (fond coloré selon le propriétaire, image, 4 chiffres). */
public final class CardRenderer {
    private static final int BLUE = 0xFF2F63D9, BLUE_DARK = 0xFF16358C;
    private static final int RED = 0xFFD2403A, RED_DARK = 0xFF8A1F1B;
    private static final int NEUTRAL = 0xFFB08A3E, NEUTRAL_DARK = 0xFF6B5222;

    private final AssetManager assets;
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    private final Set<String> missing = new HashSet<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF inner = new RectF();
    private final float dp;

    public CardRenderer(Context ctx) {
        assets = ctx.getAssets();
        dp = ctx.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        line.setStyle(Paint.Style.STROKE);
    }

    /** Cherche l'image par son nom, quelle que soit l'extension (webp, jpg, jpeg, png). */
    private Bitmap bitmap(String file) {
        if (file == null || file.isEmpty()) return null;
        Bitmap hit = cache.get(file);
        if (hit != null) return hit;
        if (missing.contains(file)) return null;
        String base = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        Bitmap b = null;
        for (String ext : new String[]{".webp", ".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG"}) {
            b = decode("Img/" + base + ext);
            if (b != null) break;
        }
        if (b == null) missing.add(file); else cache.put(file, b);
        return b;
    }

    /** Image des assets par nom sans extension (ex: "board-mat", "card-back"), ou null si absente. */
    public Bitmap image(String nameWithoutExt) { return bitmap(nameWithoutExt); }

    /** Dos de carte (animation de retournement). */
    public void drawBack(Canvas c, RectF r, float scaleX) {
        c.save();
        c.scale(Math.max(scaleX, 0.02f), 1f, r.centerX(), r.centerY());
        float rad = r.width() * 0.10f;
        Bitmap b = bitmap("card-back");
        if (b != null) {
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            c.drawBitmap(b, null, r, img);
            c.restore();
        } else {
            fill.setColor(0xFF6B4A1E);
            c.drawRoundRect(r, rad, rad, fill);
        }
        line.setColor(0x88000000);
        line.setStrokeWidth(1.5f * dp);
        c.drawRoundRect(r, rad, rad, line);
        c.restore();
    }

    /** Décode en réduisant les grosses images (~400 px max) pour ménager la mémoire. */
    private Bitmap decode(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = assets.open(path)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 320 && o.outHeight / (sample * 2) >= 320) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            try (InputStream in = assets.open(path)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) {
            return null; // fichier absent : on dessinera un placeholder
        }
    }

    /**
     * @param shown  couleur affichée (peut différer du propriétaire pendant l'animation de retournement)
     * @param scaleX 1 = normal, 0 = carte vue de profil (animation)
     */
    public void draw(Canvas c, Card card, RectF r, Owner shown, float scaleX, boolean highlight) {
        float w = r.width(), h = r.height();
        c.save();
        c.scale(Math.max(scaleX, 0.02f), 1f, r.centerX(), r.centerY());

        int base = shown == Owner.PLAYER ? BLUE : shown == Owner.AI ? RED : NEUTRAL;
        int dark = shown == Owner.PLAYER ? BLUE_DARK : shown == Owner.AI ? RED_DARK : NEUTRAL_DARK;
        float rad = w * 0.10f;

        fill.setColor(base);
        c.drawRoundRect(r, rad, rad, fill);

        inner.set(r.left + w * 0.07f, r.top + h * 0.07f, r.right - w * 0.07f, r.bottom - h * 0.07f);
        float irad = rad * 0.6f;
        fill.setColor(dark);
        c.drawRoundRect(inner, irad, irad, fill);

        Bitmap b = bitmap(card.image);
        if (b != null) {
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(inner, irad, irad, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            c.drawBitmap(b, null, inner, img);
            c.restore();
        } else {
            // Placeholder : couleur dérivée de l'attribut + initiale + nom
            float[] hsv = {Math.abs(card.attribute.hashCode()) % 360, 0.45f, 0.60f};
            fill.setColor(Color.HSVToColor(hsv));
            c.drawRoundRect(inner, irad, irad, fill);
            text.setStyle(Paint.Style.FILL);
            text.setColor(0x55FFFFFF);
            text.setTextSize(inner.height() * 0.55f);
            c.drawText(card.attribute.substring(0, 1).toUpperCase(), inner.centerX(),
                    inner.centerY() + inner.height() * 0.19f, text);
            text.setColor(0xCCFFFFFF);
            text.setTextSize(w * 0.11f);
            c.drawText(card.attribute, inner.centerX(), inner.bottom - h * 0.17f, text);
        }

        line.setColor(highlight ? 0xFFFFD54A : 0x55FFFFFF);
        line.setStrokeWidth(highlight ? 3 * dp : 1.2f * dp);
        c.drawRoundRect(r, rad, rad, line);

        float ts = w * 0.25f;
        num(c, card.numbers[Card.N], r.centerX(), r.top + h * 0.08f + ts * 0.85f, ts);
        num(c, card.numbers[Card.S], r.centerX(), r.bottom - h * 0.08f, ts);
        num(c, card.numbers[Card.W], r.left + w * 0.17f, r.centerY() + ts * 0.35f, ts);
        num(c, card.numbers[Card.E], r.right - w * 0.17f, r.centerY() + ts * 0.35f, ts);
        c.restore();
    }

    private void num(Canvas c, int n, float x, float y, float size) {
        String s = String.valueOf(n);
        text.setTextSize(size);
        text.setStyle(Paint.Style.STROKE);
        text.setStrokeWidth(size * 0.2f);
        text.setColor(0xFF000000);
        c.drawText(s, x, y, text);
        text.setStyle(Paint.Style.FILL);
        text.setColor(0xFFFFFFFF);
        c.drawText(s, x, y, text);
    }
}
