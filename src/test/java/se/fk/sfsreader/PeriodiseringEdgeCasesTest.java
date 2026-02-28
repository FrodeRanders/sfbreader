package se.fk.sfsreader;

import org.junit.Test;
import se.fk.sfsreader.model.Lag;
import se.fk.sfsreader.model.Paragraf;
import se.fk.sfsreader.model.Stycke;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PeriodiseringEdgeCasesTest {

    @Test
    public void validDatedPairCanBeSelectedByEffectiveDate() throws Exception {
        Lag lag = parseText(String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "1 kap. Rubrik",
                "1 § /Upphör att gälla U:2028-07-01/ Gammal lydelse",
                "1 § /Träder i kraft I:2028-07-01/ Ny lydelse"
        ));

        PeriodiseringValidator.Result validation = PeriodiseringValidator.validate(lag);
        assertEquals(0, validation.invalidCount());
        assertEquals(0, validation.unresolvedCount());
        assertEquals(0, validation.inlineInTextCount());

        EffectiveDateFilter.apply(lag, LocalDate.parse("2028-07-01"));
        List<Paragraf> p1 = paragraphVariants(lag, "1", "1");
        assertEquals(1, p1.size());
        assertEquals("Träder i kraft I:2028-07-01", p1.getFirst().getPeriodisering().orElse(""));
        assertEquals("Ny lydelse", flattened(p1.getFirst()));
    }

    @Test
    public void verbCodeMismatchIsInvalid() throws Exception {
        Lag lag = parseText(String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "1 kap. Rubrik",
                "1 § /Träder i kraft U:2028-07-01/ Felkodad markör"
        ));
        PeriodiseringValidator.Result validation = PeriodiseringValidator.validate(lag);
        assertEquals(1, validation.invalidCount());
        assertEquals(0, validation.unresolvedCount());
    }

    @Test
    public void relativeExpressionIsUnresolved() throws Exception {
        Lag lag = parseText(String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "1 kap. Rubrik",
                "1 § /Upphör att gälla U:den dag regeringen bestämmer/ Relativ markör"
        ));
        PeriodiseringValidator.Result validation = PeriodiseringValidator.validate(lag);
        assertEquals(0, validation.invalidCount());
        assertEquals(1, validation.unresolvedCount());
    }

    @Test
    public void inlineMarkerLeftInBodyIsDetected() throws Exception {
        Lag lag = parseText(String.join("\n",
                "AVD. A TEST",
                "I  Underavdelning",
                "1 kap. Rubrik",
                "1 § Vanlig text /Upphör att gälla U:2028-07-01/ kvar i body"
        ));
        PeriodiseringValidator.Result validation = PeriodiseringValidator.validate(lag);
        assertEquals(1, validation.inlineInTextCount());
    }

    private static Lag parseText(String text) throws Exception {
        return new TextProcessor().process(new StringReader(text)).orElseThrow();
    }

    private static List<Paragraf> paragraphVariants(Lag lag, String chapterId, String paragraphId) {
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
