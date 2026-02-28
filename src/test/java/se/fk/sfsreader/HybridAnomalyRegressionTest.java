package se.fk.sfsreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import se.fk.sfsreader.model.Lag;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class HybridAnomalyRegressionTest {

    @Test
    public void fixtureContainsKnownAnomalyFindings() throws Exception {
        Path fixture = Path.of("data/sfs-2010-110.txt.xml");
        assumeTrue("Fixture missing: " + fixture, Files.exists(fixture));

        DocumentSources sources = DocumentSources.from(fixture, StandardCharsets.UTF_8);
        assertTrue("Expected HTML payload in fixture", sources.openHtmlStream().isPresent());
        assertTrue("Expected text payload in fixture", sources.openTextStream().isPresent());

        Lag htmlLag;
        Lag textLag;
        HtmlProcessor htmlProcessor = new HtmlProcessor(
                sources.title().orElse("Socialförsäkringsbalk"),
                sources.id().orElse("2010:110")
        );
        TextProcessor textProcessor = new TextProcessor();

        try (InputStream htmlStream = sources.openHtmlStream().orElseThrow()) {
            Document doc = Jsoup.parse(htmlStream, StandardCharsets.UTF_8.name(), "http://nope.local");
            htmlLag = htmlProcessor.process(doc).orElseThrow();
        }
        try (InputStream textStream = sources.openTextStream().orElseThrow();
             Reader reader = new InputStreamReader(textStream, StandardCharsets.UTF_8)) {
            textLag = textProcessor.process(reader).orElseThrow();
        }

        HybridReconciler.Result result = new HybridReconciler().reconcile(htmlLag, textLag);
        Map<String, HybridReconciler.Finding> byKey = result.findings().stream()
                .collect(Collectors.toMap(HybridReconciler.Finding::key, Function.identity(), (a, b) -> a));
        Set<String> keys = byKey.keySet();

        // Anchors from sfb-anomalier.txt to prevent accidental "fixes" that drop known detections.
        //assertTrue(keys.contains("paragraph_variant_count:K5 P9"));
        //assertFalse("K6 P6 HTML pre-wrapping anomaly should be fixed",
        //        keys.contains("paragraph_text_mismatch:K6 P6#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K27 P46#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K55 P8#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K59 P2#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K60 P2#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K61 P2#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K87 P1#V1"));
        assertTrue(keys.contains("paragraph_text_mismatch:K97 P23a#V1"));

        //HybridReconciler.Finding k5p9 = byKey.get("paragraph_variant_count:K5 P9");
        //assertNotNull(k5p9);
        //assertEquals(HybridReconciler.Severity.HIGH, k5p9.severity());
        //assertEquals(HybridReconciler.Category.STRUCTURAL, k5p9.category());
    }

    @Test
    public void fixtureCapturesTemporalVariantPairsForSameParagraph() throws Exception {
        Path fixture = Path.of("data/sfs-2010-110.txt.xml");
        assumeTrue("Fixture missing: " + fixture, Files.exists(fixture));

        DocumentSources sources = DocumentSources.from(fixture, StandardCharsets.UTF_8);
        TextProcessor textProcessor = new TextProcessor();
        Lag textLag;
        try (InputStream textStream = sources.openTextStream().orElseThrow();
             Reader reader = new InputStreamReader(textStream, StandardCharsets.UTF_8)) {
            textLag = textProcessor.process(reader).orElseThrow();
        }

        Map<String, Set<String>> paragraphPeriodisering = textLag.get().stream()
                .flatMap(a -> a.get().stream())
                .flatMap(k -> k.get().stream().map(p -> Map.entry("K" + k.id() + " P" + p.nummer(), p.getPeriodisering().orElse(""))))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));

        Set<String> k6p3 = paragraphPeriodisering.get("K6 P3");
        assertNotNull("Expected K6 P3 in text fixture", k6p3);
        assertTrue(k6p3.stream().anyMatch(s -> s.contains("U:")));
        assertTrue(k6p3.stream().anyMatch(s -> s.contains("I:")));

        Set<String> k12p13 = paragraphPeriodisering.get("K12 P13");
        assertNotNull("Expected K12 P13 in text fixture", k12p13);
        assertTrue(k12p13.stream().anyMatch(s -> s.contains("U:")));
        assertTrue(k12p13.stream().anyMatch(s -> s.contains("I:")));
    }
}
