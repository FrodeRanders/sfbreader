package se.fk.sfbreader;

import org.junit.Test;
import se.fk.sfbreader.model.Avdelning;
import se.fk.sfbreader.model.Kapitel;
import se.fk.sfbreader.model.Lag;
import se.fk.sfbreader.model.Paragraf;
import se.fk.sfbreader.model.Stycke;

import static org.junit.Assert.*;

public class HybridReconcilerTest {

    @Test
    public void missingParagraphIsHighStructuralFinding() {
        Lag html = buildLagWithSingleParagraph("Text A");
        Lag text = buildLagWithSingleParagraph("Text A");
        Kapitel textKapitel = text.get().iterator().next().get().iterator().next();
        textKapitel.get().clear();

        HybridReconciler reconciler = new HybridReconciler();
        HybridReconciler.Result result = reconciler.reconcile(html, text);

        assertTrue(result.findingCount() > 0);
        assertTrue(result.findings().stream().anyMatch(f ->
                "paragraph_missing_text".equals(f.type())
                        && f.severity() == HybridReconciler.Severity.HIGH
                        && f.category() == HybridReconciler.Category.STRUCTURAL
        ));
    }

    @Test
    public void punctuationOnlyDifferenceIsLowFormatOnlyFinding() {
        Lag html = buildLagWithSingleParagraph("A, B.");
        Lag text = buildLagWithSingleParagraph("A B");

        HybridReconciler reconciler = new HybridReconciler();
        HybridReconciler.Result result = reconciler.reconcile(html, text);

        assertTrue(result.findings().stream().anyMatch(f ->
                "paragraph_text_format_only".equals(f.type())
                        && f.severity() == HybridReconciler.Severity.LOW
                        && f.category() == HybridReconciler.Category.FORMAT_ONLY
        ));
    }

    @Test
    public void alignsVariantsByPeriodiseringNotOnlyPosition() {
        Lag html = new Lag("Testlag", "2000:1");
        Avdelning htmlAvd = new Avdelning("A", "TEST");
        html.add(htmlAvd);
        Kapitel htmlKapitel = new Kapitel("1", "Rubrik");
        htmlAvd.addKapitel(htmlKapitel);
        htmlKapitel.addParagraf(paragraf("3", "gammal text", "Upphör att gälla U:2028-07-01"));
        htmlKapitel.addParagraf(paragraf("3", "ny text", "Träder i kraft I:2028-07-01"));

        Lag text = new Lag("Testlag", "2000:1");
        Avdelning textAvd = new Avdelning("A", "TEST");
        text.add(textAvd);
        Kapitel textKapitel = new Kapitel("1", "Rubrik");
        textAvd.addKapitel(textKapitel);
        // Reversed order on purpose.
        textKapitel.addParagraf(paragraf("3", "ny text", "Träder i kraft I:2028-07-01"));
        textKapitel.addParagraf(paragraf("3", "gammal text", "Upphör att gälla U:2028-07-01"));

        HybridReconciler.Result result = new HybridReconciler().reconcile(html, text);
        assertEquals(0, result.findingCount());
    }

    @Test
    public void reportsExplicitPeriodiseringMismatch() {
        Lag html = new Lag("Testlag", "2000:1");
        Avdelning htmlAvd = new Avdelning("A", "TEST");
        html.add(htmlAvd);
        Kapitel htmlKapitel = new Kapitel("1", "Rubrik");
        htmlAvd.addKapitel(htmlKapitel);
        htmlKapitel.addParagraf(paragraf("7", "Samma text", "Upphör att gälla U:2028-07-01"));

        Lag text = new Lag("Testlag", "2000:1");
        Avdelning textAvd = new Avdelning("A", "TEST");
        text.add(textAvd);
        Kapitel textKapitel = new Kapitel("1", "Rubrik");
        textAvd.addKapitel(textKapitel);
        textKapitel.addParagraf(paragraf("7", "Samma text", "Träder i kraft I:2028-07-01"));

        HybridReconciler.Result result = new HybridReconciler().reconcile(html, text);
        assertTrue(result.findings().stream().anyMatch(f ->
                "paragraph_periodisering_mismatch".equals(f.type())
                        && f.severity() == HybridReconciler.Severity.MEDIUM
                        && f.category() == HybridReconciler.Category.STRUCTURAL
                        && "paragraph_periodisering_mismatch:K1 P7#V1".equals(f.key())
                        && "Upphör att gälla U:2028-07-01".equals(f.htmlPeriodisering())
                        && "Träder i kraft I:2028-07-01".equals(f.textPeriodisering())
        ));
    }

    @Test
    public void reportsInvalidPeriodiseringMarkers() {
        Lag html = new Lag("Testlag", "2000:1");
        Avdelning htmlAvd = new Avdelning("A", "TEST");
        html.add(htmlAvd);
        Kapitel htmlKapitel = new Kapitel("1", "Rubrik");
        htmlAvd.addKapitel(htmlKapitel);
        htmlKapitel.addParagraf(paragraf("8", "Samma text", "Träder i kraft U:2028-07-01"));

        Lag text = new Lag("Testlag", "2000:1");
        Avdelning textAvd = new Avdelning("A", "TEST");
        text.add(textAvd);
        Kapitel textKapitel = new Kapitel("1", "Rubrik");
        textAvd.addKapitel(textKapitel);
        textKapitel.addParagraf(paragraf("8", "Samma text", "Träder i kraft I:2028-07-01"));

        HybridReconciler.Result result = new HybridReconciler().reconcile(html, text);
        assertTrue(result.findings().stream().anyMatch(f ->
                "paragraph_periodisering_invalid".equals(f.type())
                        && "paragraph_periodisering_invalid:K1 P8#V1".equals(f.key())
        ));
    }

    private static Lag buildLagWithSingleParagraph(String paragraphText) {
        Lag lag = new Lag("Testlag", "2000:1");
        Avdelning avdelning = new Avdelning("A", "TEST");
        lag.add(avdelning);

        Kapitel kapitel = new Kapitel("1", "Rubrik");
        avdelning.addKapitel(kapitel);

        Paragraf paragraf = new Paragraf("1");
        Stycke stycke = new Stycke();
        stycke.add(paragraphText);
        paragraf.add(stycke);
        kapitel.addParagraf(paragraf);
        return lag;
    }

    private static Paragraf paragraf(String nummer, String paragraphText, String periodisering) {
        Paragraf paragraf = new Paragraf(nummer);
        paragraf.setPeriodisering(periodisering);
        Stycke stycke = new Stycke();
        stycke.add(paragraphText);
        paragraf.add(stycke);
        return paragraf;
    }
}
