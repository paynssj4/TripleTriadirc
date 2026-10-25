package com.tripletriad.desktop;

import com.tripletriad.Rules;

import javax.swing.*;
import java.awt.*;

/** Choix des règles (mémorisées d'une fois sur l'autre, mêmes clés que l'appli Android). */
final class RulesDialog {
    private RulesDialog() { }

    /** @return les règles choisies, ou null si annulé. */
    static Rules ask(Component parent, Profile profile, String title) {
        JCheckBox same = box(profile, "Identique", "r_same"), plus = box(profile, "Plus", "r_plus"),
                combo = box(profile, "Combo (chaîne)", "r_combo"),
                wall = box(profile, "Même Mur (Identique + bords = 10)", "r_wall"),
                broken = box(profile, "Mur cassé (plateau sans bords)", "r_broken"),
                big = box(profile, "Plateau 5×5 (13 cartes)", "r_big");
        JPanel p = new JPanel(new GridLayout(0, 1, 2, 2));
        for (JCheckBox c : new JCheckBox[]{same, plus, combo, wall, broken, big}) p.add(c);
        if (JOptionPane.showConfirmDialog(parent, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return null;
        profile.setBool("r_same", same.isSelected());
        profile.setBool("r_plus", plus.isSelected());
        profile.setBool("r_combo", combo.isSelected());
        profile.setBool("r_wall", wall.isSelected());
        profile.setBool("r_broken", broken.isSelected());
        profile.setBool("r_big", big.isSelected());
        return new Rules(same.isSelected(), plus.isSelected(), combo.isSelected(),
                wall.isSelected(), broken.isSelected(), big.isSelected());
    }

    private static JCheckBox box(Profile profile, String label, String key) {
        JCheckBox c = new JCheckBox(label);
        c.setSelected(profile.getBool(key, false));
        return c;
    }
}
