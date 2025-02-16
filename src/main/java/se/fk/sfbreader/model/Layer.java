package se.fk.sfbreader.model;

/**
 * Lagren (Layer) används i en stack för att reda ut var man befinner sig
 * vid tolkning av lagtext från HTML. Lagren refererar grovt till strukturen
 * i lagtexten, men fångar också upp att man momentant hanterar en rubrik
 * eller en referens (som bara lösligt är en del av själva strukturen)
 * <p>
 * Struktur:
 *   1  Lag
 *   2  Avdelning
 *   3  Underavdelning
 *   4  Kapitel   [{Kapitelrubrik}, {Direktiv}]
 *   5  Paragraf  [{Paragrafrubrik}, {Direktiv}]
 *   6  Stycke    [{Referens}]
 *   7  (Punkt)
 */
public interface Layer {
    default String indent(int level, String text) {
        return String.format("<h%d>%s%s</h%d>",
                level, "&nbsp;".repeat(level-1), text, level
        );
    }

    default String indent(int level, String ref, String text) {
        return String.format("<h%d>%s%s&nbsp;%s</h%d>",
                level, "&nbsp;".repeat(level-1), ref, text, level
        );
    }

    default String type() {
        return getClass().getSimpleName();
    }

    default void prune() {
    }
}
