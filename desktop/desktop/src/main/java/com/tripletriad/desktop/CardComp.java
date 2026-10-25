package com.tripletriad.desktop;

import com.tripletriad.Card;
import com.tripletriad.Owner;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Une carte (ou un emplacement vide / une carte face cachée) dessinée en Java2D. */
final class CardComp extends JComponent {
    static final Color BLUE = new Color(40, 110, 230);
    static final Color RED = new Color(220, 60, 60);

    private final Card card;
    private final Assets assets;
    private final boolean faceDown;
    private boolean selected;
    private boolean locked;

    CardComp(Card card, Assets assets, boolean faceDown, boolean selected, int w, int h) {
        this.card = card;
        this.assets = assets;
        this.faceDown = faceDown;
        this.selected = selected;
        setPreferredSize(new Dimension(w, h));
        setOpaque(false);
    }

    void setSelected(boolean s) { selected = s; repaint(); }
    void setLocked(boolean l) { locked = l; repaint(); }
    boolean isLockedCard() { return locked; }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth() - 4, h = getHeight() - 4;
        g.translate(2, 2);
        int arc = Math.max(8, w / 8);
        Shape shape = new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, arc, arc);

        if (card == null) {                                   // emplacement vide
            g.setColor(new Color(30, 30, 40, 120));
            g.fill(shape);
            g.setColor(new Color(255, 255, 255, 60));
            g.draw(shape);
            g.dispose();
            return;
        }
        if (faceDown) {                                       // main adverse cachée
            g.setColor(new Color(60, 30, 40));
            g.fill(shape);
            g.setColor(RED);
            g.setStroke(new BasicStroke(3f));
            g.draw(shape);
            g.setFont(g.getFont().deriveFont(Font.BOLD, h / 3f));
            drawCentered(g, "?", w / 2, h / 2, new Color(255, 255, 255, 150), null);
            g.dispose();
            return;
        }

        Color tint = card.owner == Owner.AI ? RED : card.owner == Owner.PLAYER ? BLUE : new Color(130, 130, 145);
        Shape oldClip = g.getClip();
        g.clip(shape);
        g.setColor(new Color(50, 50, 60));
        g.fillRect(0, 0, w, h);
        BufferedImage img = assets.image(card);
        if (img != null) {                                     // image "cover" centrée
            double s = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
            int dw = (int) (img.getWidth() * s), dh = (int) (img.getHeight() * s);
            g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        } else {
            g.setFont(g.getFont().deriveFont(Font.BOLD, h / 4f));
            drawCentered(g, card.name, w / 2, h / 2, new Color(255, 255, 255, 110), null);
        }
        g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 85));
        g.fillRect(0, 0, w, h);
        if (locked) { g.setColor(new Color(0, 0, 0, 170)); g.fillRect(0, 0, w, h); }
        g.setClip(oldClip);

        // valeurs : N, E, S, O (10 affiché "A")
        g.setFont(g.getFont().deriveFont(Font.BOLD, h / 5f));
        drawCentered(g, val(card.numbers[Card.N]), w / 2, h / 6, Color.WHITE, Color.BLACK);
        drawCentered(g, val(card.numbers[Card.S]), w / 2, h - h / 6, Color.WHITE, Color.BLACK);
        drawCentered(g, val(card.numbers[Card.W]), w / 7, h / 2, Color.WHITE, Color.BLACK);
        drawCentered(g, val(card.numbers[Card.E]), w - w / 7, h / 2, Color.WHITE, Color.BLACK);

        g.setStroke(new BasicStroke(selected ? 5f : 3f));
        g.setColor(selected ? new Color(255, 215, 0) : tint);
        g.draw(shape);
        g.dispose();
    }

    private static String val(int n) { return n >= 10 ? "A" : String.valueOf(n); }

    private static void drawCentered(Graphics2D g, String s, int cx, int cy, Color fill, Color outline) {
        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(s) / 2;
        int y = cy + (fm.getAscent() - fm.getDescent()) / 2;
        if (outline != null) {
            g.setColor(outline);
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) g.drawString(s, x + dx, y + dy);
        }
        g.setColor(fill);
        g.drawString(s, x, y);
    }
}
