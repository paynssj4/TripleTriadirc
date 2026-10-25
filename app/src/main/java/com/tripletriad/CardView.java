package com.tripletriad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Vue carrée d'une carte, avec pastille de niveau et cadenas si verrouillée. */
public class CardView extends View {
    private final Card card;
    private final CardRenderer renderer;
    private final RectF rect = new RectF();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean selected, locked;

    public CardView(Context ctx, Card card, CardRenderer renderer) {
        super(ctx);
        this.card = card;
        this.renderer = renderer;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public Card getCard() { return card; }
    public boolean isSelected() { return selected; }
    public boolean isLocked() { return locked; }

    public void setSelectedCard(boolean s) { selected = s; invalidate(); }
    public void setLocked(boolean l) { locked = l; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int size = View.MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas c) {
        float pad = 3 * getResources().getDisplayMetrics().density;
        rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
        renderer.draw(c, card, rect, Owner.PLAYER, 1f, selected);

        // pastille de niveau (coin haut-gauche)
        float r = rect.width() * 0.11f;
        float bx = rect.left + r + rect.width() * 0.03f, by = rect.top + r + rect.height() * 0.03f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(card.level <= 3 ? 0xFF2F9E5B : card.level <= 6 ? 0xFFC9A22B : card.level <= 8 ? 0xFFD9772B : 0xFFC93B3B);
        c.drawCircle(bx, by, r, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(r * 1.25f);
        c.drawText(String.valueOf(card.level), bx, by + r * 0.43f, p);

        if (locked) {
            p.setColor(0xB8000000);
            float rad = rect.width() * 0.10f;
            c.drawRoundRect(rect, rad, rad, p);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(rect.width() * 0.34f);
            c.drawText("🔒", rect.centerX(), rect.centerY() + rect.width() * 0.12f, p);
        }
    }
}
