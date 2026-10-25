package com.tripletriad;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plateau : dessin, glisser-déposer, triangle de tirage au sort / de tour, animation de
 * retournement (les cartes d'une même vague se retournent ensemble), IA ou adversaire distant (IRC).
 */
public class GameView extends View {

    public interface Listener { void onGameOver(int playerScore, int opponentScore); }
    public interface MoveSender { void onLocalMove(Card card, int pos); }

    private static final long FLIP_MS = 450, AI_DELAY_MS = 800, INTRO_MS = 2800;

    private final GameEngine engine;
    private final CardRenderer renderer;
    private final Sound sound;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final float dp, slop;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tri = new Path();
    private final RectF rect = new RectF();
    private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final int BG = 0xFF2A1A0B;
    private RectF bgDst;
    // options / IA / animations de texte
    private boolean hideHand;
    private List<Card> aiPool;
    private final List<String> popLines = new ArrayList<>();
    private final List<Integer> popColors = new ArrayList<>();
    private final Paint popPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float popT = -1f;
    private ValueAnimator popAnim;
    private Paint fadeTop, fadeBottom;

    // mode en ligne
    private boolean remote;
    private String oppLabel = "IA";
    private MoveSender sender;
    private final ArrayDeque<Object[]> pendingRemote = new ArrayDeque<>();

    // mise en page (px)
    private float hc, gap, boardLeft, boardTop, cell, playerY, aiY, handH;
    private final int N;          // 3 ou 5 (cases par côté)
    private int perRow = 5;       // cartes par rangée de la main

    // état
    private boolean busy = true, introStarted, introDone, finished, destroyed;
    private int selected = -1;
    private boolean pressedHand, dragging;
    private float downX, downY, dragX, dragY;

    // retournement
    private final Map<Integer, Integer> flipWave = new HashMap<>();
    private int waveCount, lastWaveSound;
    private float animT;
    private ValueAnimator anim, markerAnim;

    // triangle jaune
    private float markerX, markerY, markerSize, markerAngle;
    private Owner markerSide = Owner.NONE;

    public GameView(Context ctx, List<Card> playerDeck, List<Card> oppDeck, Rules rules,
                    Owner first, Sound sound, Listener l) {
        super(ctx);
        renderer = new CardRenderer(ctx);
        this.sound = sound;
        listener = l;
        dp = ctx.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        N = rules.size;
        engine = new GameEngine(playerDeck, oppDeck, rules, first);
    }

    /** Cache la main adverse (dos de cartes). */
    public void setHideOpponentHand(boolean h) { hideHand = h; }

    /** Cartes parmi lesquelles l'IA devine la main du joueur (elle ne la voit pas). */
    public void setAiPool(List<Card> pool) { aiPool = pool; }

    /** Cartes adverses capturées par le joueur (pour en récupérer une après une victoire). */
    public List<Card> capturedByPlayer() { return engine.capturedByPlayer(); }

    /** Active le mode "adversaire humain via IRC". */
    public void setOnline(String label, MoveSender s) {
        remote = true;
        oppLabel = label.length() > 10 ? label.substring(0, 10) : label;
        sender = s;
    }

    /** Coup reçu de l'adversaire distant. */
    public void onRemoteMove(String cardName, int pos) {
        pendingRemote.add(new Object[]{cardName, pos});
        drainRemote();
    }

    // ---------- flux de jeu ----------

    private void drainRemote() {
        if (!remote || busy || finished || !introDone || engine.getTurn() != Owner.AI) return;
        Object[] m = pendingRemote.poll();
        if (m == null) return;
        int pos = (Integer) m[1];
        if (pos < 0 || pos >= engine.board.length || engine.board[pos] != null) return;
        for (Card c : engine.aiHand) {
            if (c.name.equals(m[0])) { play(pos, c); return; }
        }
    }

    private void scheduleAi() {
        busy = true;
        handler.postDelayed(() -> {
            if (destroyed || finished) return;
            new Thread(() -> {
                final GameEngine.Move m = AiPlayer.choose(engine, rnd, aiPool);
                handler.post(() -> { if (!destroyed && !finished) play(m.pos, m.card); });
            }).start();
        }, AI_DELAY_MS);
    }

    private void afterIntro() {
        if (engine.getTurn() == Owner.AI) {
            if (remote) { busy = false; drainRemote(); } else scheduleAi();
        } else {
            busy = false;
        }
    }

    private void play(int pos, Card card) {
        busy = true;
        selected = -1;
        sound.play(Sound.Fx.PLACE);
        GameEngine.Result res = engine.place(pos, card);
        waveCount = res.waves.size();
        showPopup(res);
        flipWave.clear();
        waveCount = res.waves.size();
        for (int w = 0; w < waveCount; w++) for (int p : res.waves.get(w)) flipWave.put(p, w);
        invalidate();

        if (waveCount == 0) { afterMove(); return; }
        animT = 0;
        lastWaveSound = -1;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(FLIP_MS * waveCount);
        anim.setStartDelay(250); // laisse la carte "se poser"
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            animT = (Float) a.getAnimatedValue();
            int w = Math.min(waveCount - 1, (int) (animT * waveCount));
            if (w > lastWaveSound) { lastWaveSound = w; sound.play(Sound.Fx.FLIP); }
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (destroyed) return;
                flipWave.clear();
                afterMove();
            }
        });
        anim.start();
    }

    private void afterMove() {
        if (destroyed) return;
        if (engine.isOver()) {
            finished = true;
            busy = false;
            int p = engine.score(Owner.PLAYER), o = engine.score(Owner.AI);
            sound.play(p > o ? Sound.Fx.WIN : o > p ? Sound.Fx.LOSE : Sound.Fx.TURN);
            startEndAnim(p, o);
            handler.postDelayed(() -> { if (!destroyed) listener.onGameOver(p, o); }, p > o ? 3000 : 1600);
            return;
        }
        moveMarker(engine.getTurn());
        if (engine.getTurn() == Owner.AI) {
            if (remote) { busy = false; drainRemote(); } else scheduleAi();
        } else {
            sound.play(Sound.Fx.TURN);
            busy = false;
            invalidate();
        }
    }

    @Override protected void onDetachedFromWindow() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (anim != null) anim.cancel();
        if (markerAnim != null) markerAnim.cancel();
        if (popAnim != null) popAnim.cancel();
        if (endAnim != null) endAnim.cancel();
        super.onDetachedFromWindow();
    }

    // ---------- triangle jaune ----------

    /** Position du triangle à côté du score d'un joueur : {x, y, angle}. */
    private float[] sidePos(Owner o) {
        return o == Owner.PLAYER
                ? new float[]{22 * dp, 22 * dp, 90f}
                : new float[]{getWidth() - 22 * dp, 22 * dp, 270f};
    }

    private void startIntro() {
        introStarted = true;
        sound.play(Sound.Fx.START);
        final float finalAngle = engine.getTurn() == Owner.PLAYER ? 90f : 270f;
        final float cx = getWidth() / 2f, cy = boardTop + N * cell / 2f;
        final float big = cell * N * 0.18f, small = 9 * dp;
        final float[] tgt = sidePos(engine.getTurn());

        markerAnim = ValueAnimator.ofFloat(0f, 1f);
        markerAnim.setDuration(INTRO_MS);
        markerAnim.setInterpolator(new LinearInterpolator());
        markerAnim.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            if (t < 0.75f) {                       // il tourne sur lui-même en ralentissant
                float s = t / 0.75f, e = 1f - (1f - s) * (1f - s) * (1f - s);
                markerAngle = e * (1080f + finalAngle);
                markerX = cx; markerY = cy;
                markerSize = big * Math.min(1f, t * 8f);
            } else {                               // puis va se ranger à côté du joueur qui commence
                float u = (t - 0.75f) / 0.25f;
                u = u * u * (3f - 2f * u);
                markerAngle = finalAngle;
                markerX = cx + (tgt[0] - cx) * u;
                markerY = cy + (tgt[1] - cy) * u;
                markerSize = big + (small - big) * u;
            }
            invalidate();
        });
        markerAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (destroyed) return;
                markerAngle = finalAngle; markerX = tgt[0]; markerY = tgt[1]; markerSize = small;
                markerSide = engine.getTurn();
                introDone = true;
                invalidate();
                afterIntro();
            }
        });
        markerAnim.start();
    }

    private void moveMarker(Owner o) {
        if (o == markerSide) return;
        markerSide = o;
        final float[] t = sidePos(o);
        final float sx = markerX, sy = markerY, sa = markerAngle;
        markerAnim = ValueAnimator.ofFloat(0f, 1f);
        markerAnim.setDuration(380);
        markerAnim.addUpdateListener(a -> {
            float u = (Float) a.getAnimatedValue();
            u = u * u * (3f - 2f * u);
            markerX = sx + (t[0] - sx) * u;
            markerY = sy + (t[1] - sy) * u;
            markerAngle = sa + 180f * u;   // demi-tour pendant le trajet
            invalidate();
        });
        markerAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { markerAngle = t[2]; }
        });
        markerAnim.start();
    }

    private void drawMarker(Canvas c) {
        if (markerSize <= 0) return;
        c.save();
        c.translate(markerX, markerY);
        c.rotate(markerAngle);
        float s = markerSize;
        tri.reset();
        tri.moveTo(0, -s);
        tri.lineTo(s * 0.87f, s * 0.5f);
        tri.lineTo(-s * 0.87f, s * 0.5f);
        tri.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFD54A);
        c.drawPath(tri, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(0xFF9A7400);
        c.drawPath(tri, paint);
        c.restore();
    }

    // ---------- mise en page ----------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        gap = 6 * dp;
        float statusH = 64 * dp;
        int maxHand = Math.max(engine.playerHand.size(), engine.aiHand.size());
        int rows = maxHand > 5 ? 2 : 1;                       // 13 cartes : main sur 2 rangées
        perRow = (maxHand + rows - 1) / rows;
        hc = Math.min((w - 24 * dp - (perRow - 1) * gap) / perRow, h * 0.14f);
        handH = rows * hc + (rows - 1) * gap * 0.6f;
        aiY = statusH;
        boardTop = aiY + handH + 14 * dp;
        float remaining = h - boardTop - handH - 28 * dp;
        float bs = Math.min(w - 24 * dp, remaining);
        cell = bs / N;
        boardLeft = (w - bs) / 2f;
        playerY = boardTop + bs + (h - boardTop - bs - handH) / 2f;
        layoutBackground();
        if (!introStarted) startIntro();
    }

    private RectF handRect(int i, int n, float y) {
        int row = i / perRow, col = i % perRow;
        int inRow = Math.min(perRow, n - row * perRow);
        float total = inRow * hc + (inRow - 1) * gap;
        float x = (getWidth() - total) / 2f + col * (hc + gap);
        float yy = y + row * (hc + gap * 0.6f);
        return new RectF(x, yy, x + hc, yy + hc);
    }

    private RectF cellRect(int i) {
        float x = boardLeft + (i % N) * cell, y = boardTop + (i / N) * cell;
        return new RectF(x + 2 * dp, y + 2 * dp, x + cell - 2 * dp, y + cell - 2 * dp);
    }

    private int cellAt(float x, float y) {
        for (int i = 0; i < N * N; i++) if (cellRect(i).contains(x, y)) return i;
        return -1;
    }

    private int handIndexAt(float x, float y) {
        int n = engine.playerHand.size();
        for (int i = 0; i < n; i++) if (handRect(i, n, playerY).contains(x, y)) return i;
        return -1;
    }

    // ---------- dessin ----------

    /** Le cadre du tapis (la moitié de la largeur de l'image) est calé sur le plateau 3x3. */
    private void layoutBackground() {
        Bitmap bg = renderer.image("board-mat");
        if (bg == null) { bgDst = null; return; }
        float bs = cell * N, dw = bs * 2f, dh = dw * bg.getHeight() / bg.getWidth();
        float cx = boardLeft + bs / 2f, cy = boardTop + bs / 2f;
        bgDst = new RectF(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        float fade = 70 * dp;
        int clear = BG & 0x00FFFFFF;
        fadeTop = new Paint();
        fadeTop.setShader(new LinearGradient(0, bgDst.top, 0, bgDst.top + fade, BG, clear, Shader.TileMode.CLAMP));
        fadeBottom = new Paint();
        fadeBottom.setShader(new LinearGradient(0, bgDst.bottom - fade, 0, bgDst.bottom, clear, BG, Shader.TileMode.CLAMP));
    }

    private void drawBackground(Canvas c) {
        c.drawColor(BG);
        Bitmap bg = renderer.image("board-mat");
        if (bg == null || bgDst == null) return;
        c.drawBitmap(bg, null, bgDst, bgPaint);
        float fade = 70 * dp;
        c.drawRect(bgDst.left, bgDst.top, bgDst.right, bgDst.top + fade, fadeTop);
        c.drawRect(bgDst.left, bgDst.bottom - fade, bgDst.right, bgDst.bottom, fadeBottom);
    }

    // ---------- Mur cassé : portails sur les bords (même couleur = bords reliés) ----------

    private static final int[] PORTAL = {0xFF6FD6E8, 0xFFFFB86B, 0xFF7CE0A1, 0xFFD59CFF, 0xFFFF9CCB};

    private void arrow(Canvas c, float x, float y, float angle, float s) {
        c.save();
        c.translate(x, y);
        c.rotate(angle);
        tri.reset();
        tri.moveTo(0, -s);
        tri.lineTo(s * 0.9f, s * 0.6f);
        tri.lineTo(-s * 0.9f, s * 0.6f);
        tri.close();
        c.drawPath(tri, paint);
        c.restore();
    }

    private void drawWrapMarks(Canvas c) {
        float bs = cell * N, th = 4 * dp, off = 5 * dp, as = 4 * dp;
        paint.setStyle(Paint.Style.FILL);
        for (int k = 0; k < N; k++) {
            paint.setColor(PORTAL[k % PORTAL.length]);
            float mx = boardLeft + (k + 0.5f) * cell, my = boardTop + (k + 0.5f) * cell, half = cell * 0.3f;
            c.drawRoundRect(mx - half, boardTop - off, mx + half, boardTop - off + th, th, th, paint);                  // haut
            c.drawRoundRect(mx - half, boardTop + bs + off - th, mx + half, boardTop + bs + off, th, th, paint);       // bas
            c.drawRoundRect(boardLeft - off, my - half, boardLeft - off + th, my + half, th, th, paint);                // gauche
            c.drawRoundRect(boardLeft + bs + off - th, my - half, boardLeft + bs + off, my + half, th, th, paint);     // droite
            arrow(c, mx, boardTop - off - 2 * dp, 0f, as);                 // flèches vers l'extérieur :
            arrow(c, mx, boardTop + bs + off + 2 * dp, 180f, as);          // on sort d'un côté, on rentre de l'autre
            arrow(c, boardLeft - off - 2 * dp, my, 270f, as);
            arrow(c, boardLeft + bs + off + 2 * dp, my, 90f, as);
        }
    }

    // ---------- fin de partie : confettis + bannière ----------

    private String endText;
    private int endColor;
    private float endT;                 // secondes
    private float[][] confetti;         // {x0, y0, vx, vy, rot0, vrot, taille, couleur}
    private ValueAnimator endAnim;

    private void startEndAnim(int p, int o) {
        endText = p > o ? "VICTOIRE !" : o > p ? "DÉFAITE" : "ÉGALITÉ";
        endColor = p > o ? 0xFFFFD54A : o > p ? 0xFF9FB3FF : 0xFFFFFFFF;
        confetti = null;
        if (p > o) {
            int[] cols = {0xFFFFD54A, 0xFFFF7C75, 0xFF6E97FF, 0xFF7CE0A1, 0xFFD59CFF, 0xFFFFB86B};
            confetti = new float[80][8];
            for (float[] f : confetti) {
                f[0] = rnd.nextFloat() * getWidth();
                f[1] = -rnd.nextFloat() * getHeight() * 0.6f;
                f[2] = (rnd.nextFloat() - 0.5f) * 80 * dp;
                f[3] = (180 + rnd.nextFloat() * 240) * dp;
                f[4] = rnd.nextFloat() * 360f;
                f[5] = (rnd.nextFloat() - 0.5f) * 540f;
                f[6] = (4 + rnd.nextFloat() * 5) * dp;
                f[7] = cols[rnd.nextInt(cols.length)];
            }
        }
        endT = 0;
        endAnim = ValueAnimator.ofFloat(0f, 3.2f);
        endAnim.setDuration(3200);
        endAnim.setInterpolator(new LinearInterpolator());
        endAnim.addUpdateListener(a -> { endT = (Float) a.getAnimatedValue(); invalidate(); });
        endAnim.start();
    }

    private void drawEnd(Canvas c) {
        if (endText == null) return;
        float t = endT;
        if (confetti != null) {
            paint.setStyle(Paint.Style.FILL);
            for (float[] f : confetti) {
                float y = f[1] + f[3] * t;
                if (y < -20 * dp || y > getHeight() + 20 * dp) continue;
                float x = f[0] + f[2] * t + (float) Math.sin(t * 3 + f[4]) * 8 * dp;
                paint.setColor((int) f[7]);
                c.save();
                c.translate(x, y);
                c.rotate(f[4] + f[5] * t);
                c.drawRect(-f[6], -f[6] * 0.5f, f[6], f[6] * 0.5f, paint);
                c.restore();
            }
        }
        float cx = getWidth() / 2f, cy = boardTop + cell * N / 2f;
        float s = t < 0.4f ? 0.2f + 1.1f * (t / 0.4f) : t < 0.6f ? 1.3f - 0.3f * ((t - 0.4f) / 0.2f)
                : 1f + 0.05f * (float) Math.sin((t - 0.6f) * 6f);
        int alpha = Math.min(255, (int) (t * 600));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha * 150 / 255, 0, 0, 0));
        c.drawRect(0, cy - 60 * dp, getWidth(), cy + 60 * dp, paint);
        popPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        popPaint.setTextAlign(Paint.Align.CENTER);
        popPaint.setTextSize(54 * dp);
        c.save();
        c.scale(s, s, cx, cy);
        popPaint.setStyle(Paint.Style.STROKE);
        popPaint.setStrokeWidth(9 * dp);
        popPaint.setColor(Color.argb(alpha, 0, 0, 0));
        c.drawText(endText, cx, cy + 19 * dp, popPaint);
        popPaint.setStyle(Paint.Style.FILL);
        popPaint.setColor((endColor & 0x00FFFFFF) | (alpha << 24));
        c.drawText(endText, cx, cy + 19 * dp, popPaint);
        c.restore();
    }

    // ---------- texte "arcade" : IDENTIQUE ! / PLUS ! / MUR ! / COMBO x2 ! ----------

    private void addPop(String text, int color) { popLines.add(text); popColors.add(color); }

    private void showPopup(GameEngine.Result res) {
        if (popAnim != null) popAnim.cancel();
        popLines.clear();
        popColors.clear();
        if (res.same) addPop(res.wall ? "MÊME MUR !" : "IDENTIQUE !", 0xFF6FD6E8);
        if (res.plus) addPop("PLUS !", 0xFF7CE0A1);
        if (res.waves.size() >= 2) addPop("COMBO x" + (res.waves.size() - 1) + " !", 0xFFFFB86B);
        if (popLines.isEmpty()) return;
        sound.play(Sound.Fx.COMBO);
        popT = -1f;
        popAnim = ValueAnimator.ofFloat(0f, 1f);
        popAnim.setDuration(1100);
        popAnim.setStartDelay(250);   // en même temps que les retournements
        popAnim.addUpdateListener(a -> { popT = (Float) a.getAnimatedValue(); invalidate(); });
        popAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { popT = -1f; popLines.clear(); popColors.clear(); invalidate(); }
        });
        popAnim.start();
    }

    private void drawPopup(Canvas c) {
        if (popLines.isEmpty() || popT < 0f || popT >= 1f) return;
        float t = popT;
        float scale = t < 0.2f ? 0.3f + 0.95f * (t / 0.2f) : t < 0.35f ? 1.25f - 0.25f * ((t - 0.2f) / 0.15f) : 1f;
        int alpha = t > 0.75f ? (int) (255 * (1f - (t - 0.75f) / 0.25f)) : 255;
        float cx = getWidth() / 2f, cy = boardTop + cell * N / 2f, lineH = 46 * dp;
        popPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        popPaint.setTextAlign(Paint.Align.CENTER);
        popPaint.setTextSize(40 * dp);
        c.save();
        c.rotate(-6f, cx, cy);
        c.scale(scale, scale, cx, cy);
        int n = popLines.size();
        for (int i = 0; i < n; i++) {
            float y = cy + (i - (n - 1) / 2f) * lineH + 14 * dp;
            popPaint.setStyle(Paint.Style.STROKE);
            popPaint.setStrokeWidth(8 * dp);
            popPaint.setColor(Color.argb(alpha, 0, 0, 0));
            c.drawText(popLines.get(i), cx, y, popPaint);
            popPaint.setStyle(Paint.Style.FILL);
            popPaint.setColor((popColors.get(i) & 0x00FFFFFF) | (alpha << 24));
            c.drawText(popLines.get(i), cx, y, popPaint);
        }
        c.restore();
    }

    private float flipProgress(int wave) {
        return Math.max(0f, Math.min(1f, animT * waveCount - wave));
    }

    @Override protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawStatus(canvas);

        int hover = dragging ? cellAt(dragX, dragY - cell * 0.7f) : -1;
        for (int i = 0; i < N * N; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(hover == i && engine.board[i] == null ? 0x55FFD54A : 0x22FFFFFF);
            canvas.drawRoundRect(cellRect(i), 10 * dp, 10 * dp, paint);
        }
        if (engine.rules.broken) drawWrapMarks(canvas);
        for (int i = 0; i < N * N; i++) {
            Card c = engine.board[i];
            if (c == null) continue;
            Integer w = flipWave.get(i);
            float sx = 1f;
            boolean back = false;
            Owner shown = c.owner;
            if (w != null) {                       // toutes les cartes d'une même vague : même progression
                float pr = flipProgress(w);
                sx = (float) Math.abs(Math.cos(Math.PI * pr));
                back = pr > 0.4f && pr < 0.6f;     // au milieu du demi-tour : le dos de la carte
                if (pr < 0.5f) shown = c.owner.opposite();
            }
            rect.set(cellRect(i));
            rect.inset(2 * dp, 2 * dp);
            if (back) renderer.drawBack(canvas, rect, sx);
            else renderer.draw(canvas, c, rect, shown, sx, false);
        }

        List<Card> ah = engine.aiHand;
        for (int i = 0; i < ah.size(); i++) {
            RectF r = handRect(i, ah.size(), aiY);
            if (hideHand && !finished) renderer.drawBack(canvas, r, 1f);
            else renderer.draw(canvas, ah.get(i), r, Owner.AI, 1f, false);
        }

        List<Card> ph = engine.playerHand;
        for (int i = 0; i < ph.size(); i++) {
            if (dragging && i == selected) continue;
            RectF r = handRect(i, ph.size(), playerY);
            if (i == selected) r.offset(0, -10 * dp);
            renderer.draw(canvas, ph.get(i), r, Owner.PLAYER, 1f, i == selected);
        }
        if (dragging && selected >= 0 && selected < ph.size()) {
            float s = cell * 0.92f, cx = dragX, cy = dragY - cell * 0.7f;
            rect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            renderer.draw(canvas, ph.get(selected), rect, Owner.PLAYER, 1f, true);
        }
        drawMarker(canvas);
        drawPopup(canvas);
        drawEnd(canvas);
    }

    private void drawStatus(Canvas canvas) {
        int w = getWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(22 * dp);
        paint.setColor(0xFF6E97FF);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Vous  " + engine.score(Owner.PLAYER), 40 * dp, 30 * dp, paint);
        paint.setColor(0xFFFF7C75);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(oppLabel + "  " + engine.score(Owner.AI), w - 40 * dp, 30 * dp, paint);

        String msg = finished ? "Partie terminée"
                : !introDone ? "Tirage au sort…"
                : engine.getTurn() == Owner.PLAYER ? "À vous de jouer"
                : remote ? "Tour de " + oppLabel : "L'IA réfléchit…";
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(15 * dp);
        paint.setColor(0xFFFFD54A);
        canvas.drawText(msg, w / 2f, 54 * dp, paint);
    }

    // ---------- interactions ----------

    private void playSelected(int cellIndex) {
        if (selected < 0 || selected >= engine.playerHand.size()) return;
        if (engine.board[cellIndex] != null) return;
        Card card = engine.playerHand.get(selected);
        if (sender != null) sender.onLocalMove(card, cellIndex);
        play(cellIndex, card);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (busy || finished || !introDone || engine.getTurn() != Owner.PLAYER) return true;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = x; downY = y; dragX = x; dragY = y;
                int hit = handIndexAt(x, y);
                if (hit >= 0) {
                    selected = hit;
                    pressedHand = true;
                } else {
                    int c = cellAt(x, y);
                    if (c >= 0 && selected >= 0) playSelected(c); // toucher la carte puis la case
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (pressedHand) {
                    if (!dragging && Math.hypot(x - downX, y - downY) > slop) dragging = true;
                    dragX = x; dragY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                boolean wasDragging = dragging;
                dragging = false;
                pressedHand = false;
                if (wasDragging) {
                    int c = cellAt(dragX, dragY - cell * 0.7f);
                    if (c >= 0 && engine.board[c] == null) playSelected(c);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                pressedHand = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
