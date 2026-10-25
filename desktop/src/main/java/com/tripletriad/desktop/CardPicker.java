package com.tripletriad.desktop;

import com.tripletriad.Card;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

/** Fenêtre de sélection de cartes (collection de 25, mains de 5 ou 13, carte à récupérer après une victoire). */
final class CardPicker {
    private CardPicker() { }

    /**
     * @param exact  true : il faut exactement {@code limit} cartes ; false : de 0 à {@code limit}.
     * @return les cartes choisies (dans l'ordre de {@code source}), ou null si annulé.
     */
    static List<Card> pick(Component parent, Assets assets, String title, String hint, List<Card> source,
                           List<Card> preselected, int limit, boolean exact, Predicate<Card> locked, boolean random) {
        final Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        final JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        final Set<String> sel = new LinkedHashSet<>();
        for (Card c : preselected) if (!locked.test(c) && sel.size() < limit) sel.add(c.name);
        final Map<String, CardComp> comps = new LinkedHashMap<>();
        final JLabel header = new JLabel("", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        final List<Card>[] result = new List[1];

        JPanel grid = new JPanel(new GridLayout(0, 6, 6, 6));
        grid.setBackground(new Color(24, 26, 34));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (final Card c : source) {
            final CardComp cc = new CardComp(c, assets, false, sel.contains(c.name), 96, 116);
            cc.setLocked(locked.test(c));
            if (cc.isLockedCard()) cc.setToolTipText("Verrouillée (niveau " + c.level + ") : à gagner en battant un adversaire");
            cc.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (locked.test(c)) { Toolkit.getDefaultToolkit().beep(); return; }
                    if (sel.contains(c.name)) sel.remove(c.name);
                    else if (sel.size() >= limit) { Toolkit.getDefaultToolkit().beep(); return; }
                    else sel.add(c.name);
                    cc.setSelected(sel.contains(c.name));
                    header.setText(sel.size() + " / " + limit);
                }
            });
            comps.put(c.name, cc);
            grid.add(cc);
        }
        header.setText(sel.size() + " / " + limit);

        JLabel hintLabel = new JLabel("<html><div style='text-align:center;width:560px'>" + hint + "</div></html>", SwingConstants.CENTER);
        JPanel north = new JPanel(new BorderLayout());
        north.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
        north.add(header, BorderLayout.NORTH);
        north.add(hintLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        if (random) {
            JButton rnd = new JButton("Au hasard");
            rnd.addActionListener(e -> {
                List<Card> free = new ArrayList<>();
                for (Card c : source) if (!locked.test(c) && !sel.contains(c.name)) free.add(c);
                Collections.shuffle(free);
                for (Card c : free) {
                    if (sel.size() >= limit) break;
                    sel.add(c.name);
                    comps.get(c.name).setSelected(true);
                }
                header.setText(sel.size() + " / " + limit);
            });
            buttons.add(rnd);
        }
        JButton ok = new JButton("Valider");
        ok.addActionListener(e -> {
            if (exact && sel.size() != limit) {
                JOptionPane.showMessageDialog(d, "Il faut exactement " + limit + " cartes.");
                return;
            }
            List<Card> out = new ArrayList<>();
            for (Card c : source) if (sel.contains(c.name)) out.add(c);
            result[0] = out;
            d.dispose();
        });
        JButton cancel = new JButton("Annuler");
        cancel.addActionListener(e -> d.dispose());
        buttons.add(ok);
        buttons.add(cancel);

        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setPreferredSize(new Dimension(660, Math.min(520, 40 + ((source.size() + 5) / 6) * 122)));
        d.add(north, BorderLayout.NORTH);
        d.add(scroll, BorderLayout.CENTER);
        d.add(buttons, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
        return result[0];
    }
}
