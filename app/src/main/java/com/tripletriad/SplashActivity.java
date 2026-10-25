package com.tripletriad;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Intro de l'application : le tapis, trois cartes qui arrivent, puis le titre. Toucher l'écran pour passer. */
public class SplashActivity extends Activity {
    private Sound sound;
    private SplashView view;
    private boolean done;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sound = new Sound(this);
        view = new SplashView(this);
        view.setOnClickListener(v -> next());
        setContentView(view);
        handler.postDelayed(() -> sound.play(Sound.Fx.START), 400);
        handler.postDelayed(this::next, 4200);
    }

    private void next() {
        if (done) return;
        done = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        sound.release();
        super.onDestroy();
    }

    private static final class SplashView extends View {
        private final CardRenderer renderer;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final float dp;
        private float t;   // secondes
        private ValueAnimator anim;

        SplashView(Context ctx) {
            super(ctx);
            renderer = new CardRenderer(ctx);
            dp = ctx.getResources().getDisplayMetrics().density;
            anim = ValueAnimator.ofFloat(0f, 4.2f);
            anim.setDuration(4200);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
        private static float ease(float v) { v = clamp(v); return 1f - (1f - v) * (1f - v) * (1f - v); }

        private void text(Canvas c, String s, float x, float y, float size, int color, float alpha, boolean italic) {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(size);
            int a = (int) (255 * clamp(alpha));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.16f);
            paint.setColor(android.graphics.Color.argb(a, 0, 0, 0));
            c.drawText(s, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((color & 0x00FFFFFF) | (a << 24));
            c.drawText(s, x, y, paint);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawColor(0xFF2A1A0B);

            // tapis (même cadrage que le plateau de jeu), apparition en fondu
            Bitmap mat = renderer.image("board-mat");
            if (mat != null) {
                float dw = w * 1.9f, dh = dw * mat.getHeight() / mat.getWidth();
                bgPaint.setAlpha((int) (255 * ease(t / 0.9f)));
                c.drawBitmap(mat, null, new RectF(w / 2 - dw / 2, h * 0.45f - dh / 2, w / 2 + dw / 2, h * 0.45f + dh / 2), bgPaint);
            }

            // trois cartes qui arrivent du bas et se rangent en éventail
            float cw = w * 0.26f, baseY = h * 0.42f;
            float[] angle = {-14f, 0f, 14f}, dx = {-cw * 0.85f, 0f, cw * 0.85f};
            for (int i = 0; i < 3; i++) {
                float p = ease((t - 0.5f - i * 0.22f) / 0.9f);
                if (p <= 0f) continue;
                float cx = w / 2 + dx[i] * p, cy = baseY + (1f - p) * h * 0.6f - (i == 1 ? cw * 0.08f : 0f);
                float flip = i == 1 ? (float) Math.abs(Math.cos(Math.PI * clamp((t - 1.9f) / 0.7f))) : 1f;
                c.save();
                c.rotate(angle[i] * p + (1f - p) * 40f * (i - 1), cx, cy);
                r.set(cx - cw / 2, cy - cw / 2, cx + cw / 2, cy + cw / 2);
                renderer.drawBack(c, r, flip);
                c.restore();
            }

            // titre
            float ts = ease((t - 1.5f) / 0.7f);
            if (ts > 0f) {
                float s = 0.5f + 0.5f * ts + (ts < 1f ? 0.12f * (float) Math.sin(ts * Math.PI) : 0f);
                c.save();
                c.scale(s, s, w / 2, h * 0.68f);
                text(c, "TRIPLE TRIAD", w / 2, h * 0.68f, 40 * dp, 0xFFFFD54A, ts, true);
                c.restore();
            }
            float ws = ease((t - 2.1f) / 0.7f);
            text(c, "WORLD CARDS", w / 2, h * 0.68f + 46 * dp + (1f - ws) * 24 * dp, 30 * dp, 0xFFFFFFFF, ws, true);

            // mention fangame
            float fs = ease((t - 2.9f) / 0.6f);
            text(c, "un fangame", w / 2, h * 0.68f + 84 * dp, 18 * dp, 0xFFB5C7BB, fs, false);
            text(c, "Projet de fans, sans lien avec Square Enix", w / 2, h - 36 * dp, 12 * dp, 0xFF9AA79F, fs, false);
        }
    }
}
