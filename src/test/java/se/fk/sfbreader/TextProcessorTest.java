package se.fk.sfbreader;

import org.junit.Test;
import se.fk.sfbreader.model.Avdelning;
import se.fk.sfbreader.model.Kapitel;
import se.fk.sfbreader.model.Lag;
import se.fk.sfbreader.model.Paragraf;
import se.fk.sfbreader.model.Stycke;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Optional;

import static org.junit.Assert.*;

public class TextProcessorTest {

    @Test
    public void parsesChapterAndParagraphWithNbspSpacing() throws Exception {
        String nbsp = "\u00A0";
        String input = String.join("\n",
                "AVD." + nbsp + "A" + nbsp + "TEST",
                "I" + nbsp + nbsp + "Underavdelning",
                "2" + nbsp + "kap." + nbsp + "Rubrik",
                "1" + nbsp + "§" + nbsp + "Paragraftext"
        );

        TextProcessor processor = new TextProcessor();
        Optional<Lag> maybeLag = processor.process(new StringReader(input));
        assertTrue(maybeLag.isPresent());

        Lag lag = maybeLag.get();
        Kapitel kapitel = firstChapter(lag);
        assertEquals("2", kapitel.id());

        Paragraf paragraf = firstParagraf(kapitel);
        assertEquals("1", paragraf.nummer());
    }

    @Test
    public void doesNotTreatInlineKapitelReferenceAsNewChapter() throws Exception {
        String input = String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "2 kap. Rubrik",
                "1 § Inledande text",
                "28 kap., smittbärarpenning i 46 kap. och handläggning av ärenden",
                "Avslutning."
        );

        TextProcessor processor = new TextProcessor();
        Lag lag = processor.process(new StringReader(input)).orElseThrow();

        Avdelning avdelning = firstAvdelning(lag);
        assertEquals("Expected one chapter only", 1, avdelning.get().size());
        Kapitel kapitel = firstChapter(lag);
        assertEquals("2", kapitel.id());
    }

    @Test
    public void doesNotTreatDoubleSectionReferenceAsNewParagraph() throws Exception {
        String input = String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "2 kap. Rubrik",
                "1 § Inledande text",
                "Detta följer av 3 §§ lagen (2000:000).",
                "Fortsatt text."
        );

        TextProcessor processor = new TextProcessor();
        Lag lag = processor.process(new StringReader(input)).orElseThrow();
        Kapitel kapitel = firstChapter(lag);

        assertEquals("Expected one paragraph only", 1, kapitel.get().size());
        Paragraf paragraf = firstParagraf(kapitel);
        assertEquals("1", paragraf.nummer());
    }

    @Test
    public void extractsInlinePeriodiseringFromParagraphBody() throws Exception {
        String input = String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "2 kap. Rubrik",
                "3 § /Upphör att gälla U:2028-07-01/ Gammal lydelse",
                "3 § /Träder i kraft I:2028-07-01/ Ny lydelse"
        );

        TextProcessor processor = new TextProcessor();
        Lag lag = processor.process(new StringReader(input)).orElseThrow();
        Kapitel kapitel = firstChapter(lag);

        List<Paragraf> p3 = new ArrayList<>();
        for (Paragraf paragraf : kapitel.get()) {
            if ("3".equals(paragraf.nummer())) {
                p3.add(paragraf);
            }
        }
        assertEquals("Expected two paragraph variants", 2, p3.size());

        Paragraf oldVariant = p3.get(0);
        assertEquals("Upphör att gälla U:2028-07-01", oldVariant.getPeriodisering().orElseThrow());
        assertEquals("DATED", oldVariant.getVersionStatus().orElseThrow());
        assertEquals("U", oldVariant.getVersionKind().orElseThrow());
        assertEquals("2028-07-01", oldVariant.getVersionDate().orElseThrow());
        assertEquals("U:2028-07-01", oldVariant.getVersionIdentity().orElseThrow());
        assertEquals("Gammal lydelse", flattened(oldVariant));

        Paragraf newVariant = p3.get(1);
        assertEquals("Träder i kraft I:2028-07-01", newVariant.getPeriodisering().orElseThrow());
        assertEquals("DATED", newVariant.getVersionStatus().orElseThrow());
        assertEquals("I", newVariant.getVersionKind().orElseThrow());
        assertEquals("2028-07-01", newVariant.getVersionDate().orElseThrow());
        assertEquals("I:2028-07-01", newVariant.getVersionIdentity().orElseThrow());
        assertEquals("Ny lydelse", flattened(newVariant));
    }

    private static Avdelning firstAvdelning(Lag lag) {
        Iterator<Avdelning> it = lag.get().iterator();
        assertTrue("Expected at least one avdelning", it.hasNext());
        return it.next();
    }

    private static Kapitel firstChapter(Lag lag) {
        Iterator<Kapitel> it = firstAvdelning(lag).get().iterator();
        assertTrue("Expected at least one chapter", it.hasNext());
        return it.next();
    }

    private static Paragraf firstParagraf(Kapitel kapitel) {
        Iterator<Paragraf> it = kapitel.get().iterator();
        assertTrue("Expected at least one paragraph", it.hasNext());
        return it.next();
    }

    private static String flattened(Paragraf paragraf) {
        StringBuilder sb = new StringBuilder();
        for (Stycke stycke : paragraf.get()) {
            for (String line : stycke.get()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
