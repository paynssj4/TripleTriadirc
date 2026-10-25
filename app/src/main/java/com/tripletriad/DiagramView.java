package com.tripletriad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Petit schéma « avant / après » du tutoriel : cartes dessinées à la main (aucune image nécessaire). */
public class DiagramView extends View {
    public static final int EMPTY = 0, BLUE = 1, RED = 2;
    private static final int GOLD = 0xFFFFD54A;

    /** Une case : valeurs {haut, droite, bas, gauche} (10 = A), propriétaire, et « carte qu'on vient de poser ». */
    public static final class Cell {
        final int owner;
        final boolean played;
        final int[] v;
        Cell(int owner, boolean played, int n, int e, int s, int w) {
            this.owner = owner; this.played = played; this.v = new int[]{n, e, s, w};
        }
        public static Cell empty() { return new Cell(EMPTY, false, 0, 0, 0, 0); }
        public static Cell red(int n, int e, int s, int w) { return new Cell(RED, false, n, e, s, w); }
        public static Cell blue(int n, int e, int s, int w) { return new Cell(BLUE, false, n, e, s, w); }
        public static Cell played(int n, int e, int s, int w) { return new Cell(BLUE, true, n, e, s, w); }
    }

    public static final class Board {
        final int rows, cols;
        final Cell[] cells;
        boolean wallTop, wrap;
        public Board(int rows, int cols, Cell... cells) { this.rows = rows; this.cols = cols; this.cells = cells; }
        public Board wallTop() { wallTop = true; return this; }
        public Board wrap() { wrap = true; return this; }
        /** Même plateau après le coup : les cases listées passent au bleu, la carte posée perd sa bordure dorée. */
        public Board after(int... captured) {
            Cell[] c = cells.clone();
            for (int i = 0; i < c.length; i++)
                if (c[i].played) c[i] = Cell.blue(c[i].v[0], c[i].v[1], c[i].v[2], c[i].v[3]);
            for (int i : captured)
                c[i] = Cell.blue(c[i].v[0], c[i].v[1], c[i].v[2], c[i].v[3]);
            Board b = new Board(rows, cols, c);
            b.wallTop = wallTop;
            b.wrap = wrap;
            return b;
        }
    }

    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Board before, after;
    private boolean legend;

    public DiagramView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setDiagram(Board before, Board after) {
        this.before = before; this.after = after; this.legend = false;
        requestLayout(); invalidate();
    }

    public void setLegend() {
        this.legend = true; this.before = null; this.after = null;
        requestLayout(); invalidate();
    }

    private float cellSize(int w, Board b) {
        float half = (w - 40 * dp) / 2f - 10 * dp;   // marge pour ne pas coller aux bords de l'écran
        return Math.min(half / b.cols, 66 * dp);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float h;
        if (legend || before == null) {
            h = 190 * dp;
        } else {
            h = 30 * dp + cellSize(w, before) * before.rows + (before.wrap ? 30 * dp : 8 * dp);
        }
        setMeasuredDimension(w, (int) h);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (legend) { drawLegend(canvas); return; }
        if (before == null) return;
        int w = getWidth();
        float cell = cellSize(w, before);
        float bw = cell * before.cols, bh = cell * before.rows;
        float gap = 40 * dp;
        float half = (w - gap) / 2f;
        float x1 = (half - bw) / 2f, x2 = half + gap + (half - bw) / 2f, top = 30 * dp;
        drawBoard(canvas, before, x1, top, cell, "AVANT");
        drawBoard(canvas, after, x2, top, cell, "APRÈS");
        text.setColor(0xFFB5C7BB);
        text.setTextSize(26 * dp);
        canvas.drawText("→", half + gap / 2f, top + bh / 2f + 9 * dp, text);
    }

    private void drawBoard(Canvas c, Board b, float x, float y, float cell, String label) {
        text.setShadowLayer(0, 0, 0, 0);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(11 * dp);
        text.setColor(0xFFB5C7BB);
        c.drawText(label, x, y - 12 * dp, text);
        if (b.wallTop) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(GOLD);
            c.drawText("bord = A", x + cell * b.cols, y - 12 * dp, text);
            stroke.setColor(GOLD);
            stroke.setStrokeWidth(3 * dp);
            c.drawLine(x, y - 5 * dp, x + cell * b.cols, y - 5 * dp, stroke);
        }
        text.setTextAlign(Paint.Align.CENTER);
        for (int r = 0; r < b.rows; r++)
            for (int k = 0; k < b.cols; k++)
                drawCard(c, b.cells[r * b.cols + k], x + k * cell, y + r * cell, cell);
        if (b.wrap) {
            float bottom = y + cell * b.rows + 4 * dp;
            float xl = x + cell / 2f, xr = x + cell * (b.cols - 0.5f);
            stroke.setColor(GOLD);
            stroke.setStrokeWidth(2 * dp);
            c.drawLine(xl, bottom, xl, bottom + 9 * dp, stroke);
            c.drawLine(xl, bottom + 9 * dp, xr, bottom + 9 * dp, stroke);
            c.drawLine(xr, bottom + 9 * dp, xr, bottom, stroke);
            text.setTextSize(10 * dp);
            text.setColor(GOLD);
            c.drawText("se rejoignent", x + cell * b.cols / 2f, bottom + 22 * dp, text);
        }
    }

    private void drawCard(Canvas c, Cell cell, float x, float y, float size) {
        float pad = size * 0.05f;
        RectF r = new RectF(x + pad, y + pad, x + size - pad, y + size - pad);
        float rad = size * 0.12f;
        if (cell.owner == EMPTY) {
            fill.setColor(0x33000000);
            c.drawRoundRect(r, rad, rad, fill);
            stroke.setColor(0x55FFFFFF);
            stroke.setStrokeWidth(1 * dp);
            c.drawRoundRect(r, rad, rad, stroke);
            return;
        }
        fill.setColor(cell.owner == RED ? 0xFFC94A4A : 0xFF2F6FDB);
        c.drawRoundRect(r, rad, rad, fill);
        stroke.setColor(cell.played ? GOLD : (cell.owner == RED ? 0xFF8F2B2B : 0xFF1B4795));
        stroke.setStrokeWidth(cell.played ? 3 * dp : 1.5f * dp);
        c.drawRoundRect(r, rad, rad, stroke);
        text.setTextSize(size * 0.27f);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(2 * dp, 0, 0, 0xFF000000);
        text.setTextAlign(Paint.Align.CENTER);
        float cx = x + size / 2f, cy = y + size / 2f;
        float off = -(text.ascent() + text.descent()) / 2f;
        c.drawText(num(cell.v[0]), cx, y + size * 0.27f + off, text);   // haut
        c.drawText(num(cell.v[1]), x + size * 0.77f, cy + off, text);   // droite
        c.drawText(num(cell.v[2]), cx, y + size * 0.73f + off, text);   // bas
        c.drawText(num(cell.v[3]), x + size * 0.23f, cy + off, text);   // gauche
        text.setShadowLayer(0, 0, 0, 0);
    }

    private static String num(int n) { return n >= 10 ? "A" : String.valueOf(n); }

    /** Une grande carte avec le nom de ses quatre côtés. */
    private void drawLegend(Canvas c) {
        float size = 96 * dp, x = (getWidth() - size) / 2f, y = 46 * dp;
        drawCard(c, Cell.blue(8, 4, 6, 2), x, y, size);
        text.setShadowLayer(0, 0, 0, 0);
        text.setTextSize(13 * dp);
        text.setColor(0xFFB5C7BB);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("haut", x + size / 2f, y - 8 * dp, text);
        c.drawText("bas", x + size / 2f, y + size + 20 * dp, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("gauche", x - 8 * dp, y + size / 2f + 5 * dp, text);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("droite", x + size + 8 * dp, y + size / 2f + 5 * dp, text);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(GOLD);
        c.drawText("A = 10", x + size / 2f, y + size + 42 * dp, text);
    }
}
