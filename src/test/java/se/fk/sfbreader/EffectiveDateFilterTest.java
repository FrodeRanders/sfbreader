package se.fk.sfbreader;

import org.junit.Test;
import se.fk.sfbreader.model.Avdelning;
import se.fk.sfbreader.model.Kapitel;
import se.fk.sfbreader.model.Lag;
import se.fk.sfbreader.model.Paragraf;
import se.fk.sfbreader.model.Stycke;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EffectiveDateFilterTest {

    @Test
    public void picksOldVariantBeforeTransitionDate() {
        Lag lag = buildLagWithTransitionPair();
        EffectiveDateFilter.apply(lag, LocalDate.parse("2028-06-30"));

        List<Paragraf> p13 = paragraphs(lag, "12", "13");
        assertEquals(1, p13.size());
        assertEquals("Upphör att gälla U:2028-07-01", p13.getFirst().getPeriodisering().orElse(""));
        assertEquals("Old text", flattened(p13.getFirst()));
    }

    @Test
    public void picksNewVariantFromTransitionDate() {
        Lag lag = buildLagWithTransitionPair();
        EffectiveDateFilter.apply(lag, LocalDate.parse("2028-07-01"));

        List<Paragraf> p13 = paragraphs(lag, "12", "13");
        assertEquals(1, p13.size());
        assertEquals("Träder i kraft I:2028-07-01", p13.getFirst().getPeriodisering().orElse(""));
        assertEquals("New text", flattened(p13.getFirst()));
    }

    @Test
    public void keepsUnresolvedVariantsWhenDateCannotBeEvaluated() {
        Lag lag = new Lag("Testlag", "2000:1");
        Avdelning avdelning = new Avdelning("A", "TEST");
        lag.add(avdelning);
        Kapitel kapitel = new Kapitel("1", "Rubrik");
        avdelning.addKapitel(kapitel);
        kapitel.addParagraf(paragraf("2", "Old", "Upphör att gälla U:den dag regeringen bestämmer"));
        kapitel.addParagraf(paragraf("2", "New", "Träder i kraft I:den dag regeringen bestämmer"));

        EffectiveDateFilter.apply(lag, LocalDate.parse("2030-01-01"));
        List<Paragraf> p2 = paragraphs(lag, "1", "2");
        assertEquals(2, p2.size());
        assertTrue(p2.stream().anyMatch(p -> "Old".equals(flattened(p))));
        assertTrue(p2.stream().anyMatch(p -> "New".equals(flattened(p))));
    }

    private static Lag buildLagWithTransitionPair() {
        Lag lag = new Lag("Testlag", "2000:1");
        Avdelning avdelning = new Avdelning("A", "TEST");
        lag.add(avdelning);
        Kapitel kapitel = new Kapitel("12", "Rubrik");
        avdelning.addKapitel(kapitel);
        kapitel.addParagraf(paragraf("13", "Old text", "Upphör att gälla U:2028-07-01"));
        kapitel.addParagraf(paragraf("13", "New text", "Träder i kraft I:2028-07-01"));
        return lag;
    }

    private static Paragraf paragraf(String nummer, String body, String periodisering) {
        Paragraf p = new Paragraf(nummer);
        p.setPeriodisering(periodisering);
        Stycke s = new Stycke();
        s.add(body);
        p.add(s);
        return p;
    }

    private static List<Paragraf> paragraphs(Lag lag, String chapterId, String paragraphId) {
        List<Paragraf> out = new ArrayList<>();
        lag.get().forEach(avd -> avd.get().forEach(k -> {
            if (chapterId.equals(k.id())) {
                k.get().forEach(p -> {
                    if (paragraphId.equals(p.nummer())) {
                        out.add(p);
                    }
                });
            }
        }));
        return out;
    }

    private static String flattened(Paragraf paragraf) {
        StringBuilder sb = new StringBuilder();
        paragraf.get().forEach(stycke -> stycke.get().forEach(line -> {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(line);
        }));
        return sb.toString();
    }
}
