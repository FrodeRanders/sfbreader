package se.fk.sfsreader;

import org.junit.Test;
import se.fk.sfsreader.model.Avdelning;
import se.fk.sfsreader.model.Kapitel;
import se.fk.sfsreader.model.Lag;
import se.fk.sfsreader.model.Paragraf;
import se.fk.sfsreader.model.Stycke;

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

    @Test
    public void keepsWrappedReferenceAndSeparatesHeadingsAndNumberedStycken() throws Exception {
        String input = "25 kap. Test\n10 § Första stycket.\n\n"
                + "Om mottagaren avses i 9 kap.\n2 § skatteförfarandelagen gäller detta.\n\n"
                + "Ersättning understigande 1 000 kronor\n16 § Om något gäller.\n"
                + "Semesterlön och semesterersättning\n\n17 § Nästa regel.";
        Lag lag = new TextProcessor().process(new StringReader(input)).orElseThrow();
        assertEquals(3, lag.getKapitel().iterator().next().get().size());
        Paragraf p10 = HybridSourceRepairTest.section(lag, "25", "10");
        assertEquals(List.of(1,2), p10.get().stream().map(Stycke::nummer).toList());
        assertTrue(flattened(p10).contains("9 kap. 2 § skatteförfarandelagen"));
        Paragraf p16 = HybridSourceRepairTest.section(lag, "25", "16");
        assertEquals("Om något gäller.", flattened(p16));
        assertTrue(p16.rubriker().contains("Ersättning understigande"));
    }

    @Test
    public void listAfterBlankLineStaysWithItsIntroduction() throws Exception {
        Lag lag = new TextProcessor().process(new StringReader(
                "25 kap. Test\n2 § Följande gäller:\n\n1. första punkten,\n2. andra punkten.\n\nAndra stycket."
        )).orElseThrow();
        Paragraf p = HybridSourceRepairTest.section(lag,"25","2");
        assertEquals(2, p.get().size());
        assertTrue(String.join(" ", p.get().iterator().next().get()).contains("2. andra punkten."));
        assertEquals(List.of(1,2), p.get().stream().map(Stycke::nummer).toList());
    }

    @Test
    public void inlineDivisionLabelsDoNotResetChapter() throws Exception {
        Lag lag = new TextProcessor().process(new StringReader(
                "AVD. A TEST\n5 kap. Test\n9 § Förmåner:\n\nAvdelning B Familjeförmåner\n1. förmån.\n10 § Nästa regel."
        )).orElseThrow();
        assertEquals(1, lag.getKapitel().size());
        assertTrue(HybridSourceRepairTest.body(HybridSourceRepairTest.section(lag,"5","9")).contains("Avdelning B"));
        assertNotNull(HybridSourceRepairTest.section(lag,"5","10"));
    }

    @Test
    public void wrappedChapterReferenceDoesNotMoveFollowingSections() throws Exception {
        Lag lag = new TextProcessor().process(new StringReader(
                "74 a kap. Test\n4 § Försäkringstid enligt\n59 kap. Försäkringstiden räknas.\n\n5 § Nästa regel."
        )).orElseThrow();
        assertEquals(1, lag.getKapitel().size());
        assertTrue(HybridSourceRepairTest.body(HybridSourceRepairTest.section(lag,"74a","4")).contains("59 kap. Försäkringstiden"));
        assertNotNull(HybridSourceRepairTest.section(lag,"74a","5"));
    }

    @Test
    public void transitionIdsAreLocalToTheDocumentAndAmendmentsArePreserved() throws Exception {
        String text = "1 kap. Test\n1 § Regel.\n\nÖvergångsbestämmelser\n\n2020:1\n\nDenna lag träder i kraft.\n\n2021:2\n\n1. Första punkten.\n\n2. Andra punkten.";
        for (int i = 0; i < 2; i++) {
            Lag lag = new TextProcessor().process(new StringReader(text)).orElseThrow();
            var transition = HybridSourceRepairTest.chapter(lag,"Ö1");
            assertEquals(2, transition.get().size());
            assertEquals("Denna lag träder i kraft.", HybridSourceRepairTest.body(HybridSourceRepairTest.section(lag,"Ö1","2020:1")));
            assertTrue(HybridSourceRepairTest.body(HybridSourceRepairTest.section(lag,"Ö1","2021:2")).contains("2. Andra punkten."));
        }
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
