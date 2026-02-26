package se.fk.sfbreader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import se.fk.sfbreader.model.Kapitel;
import se.fk.sfbreader.model.Lag;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class AdditionalActPortabilityTest {

    @Test
    public void canParseSfs2017900WithNonEmptyStructure() throws Exception {
        Path fixture = Path.of("data/sfs-2017-900.txt.xml");
        assumeTrue("Fixture missing: " + fixture, Files.exists(fixture));

        DocumentSources sources = DocumentSources.from(fixture, StandardCharsets.UTF_8);
        assertTrue("Expected html stream", sources.openHtmlStream().isPresent());
        assertTrue("Expected text stream", sources.openTextStream().isPresent());

        Lag htmlLag;
        Lag textLag;
        try (InputStream html = sources.openHtmlStream().orElseThrow()) {
            Document doc = Jsoup.parse(html, StandardCharsets.UTF_8.name(), "http://nope.local");
            htmlLag = new HtmlProcessor(
                    sources.title().orElse("Socialförsäkringsbalk"),
                    sources.id().orElse("2010:110")
            ).process(doc).orElseThrow();
        }
        try (InputStream text = sources.openTextStream().orElseThrow();
             Reader reader = new InputStreamReader(text, StandardCharsets.UTF_8)) {
            textLag = new TextProcessor().process(reader).orElseThrow();
        }

        int htmlParagraphs = countParagraphs(htmlLag);
        int textParagraphs = countParagraphs(textLag);
        assertTrue("Expected HTML parser to extract paragraphs", htmlParagraphs > 0);
        assertTrue("Expected text parser to extract paragraphs", textParagraphs > 0);

        htmlLag.prepareForSerialization();
        textLag.prepareForSerialization();
        assertEquals("Expected no chapter nodes in chapterless export (HTML)", 0, countChapters(htmlLag));
        assertEquals("Expected no chapter nodes in chapterless export (text)", 0, countChapters(textLag));
        assertTrue("Expected chapterless export paragraphs for HTML", htmlLag.getParagrafer().size() > 0);
        assertTrue("Expected chapterless export paragraphs for text", textLag.getParagrafer().size() > 0);
    }

    private static int countParagraphs(Lag lag) {
        int count = 0;
        for (var avd : lag.get()) {
            for (Kapitel kapitel : avd.get()) {
                count += kapitel.get().size();
            }
        }
        return count;
    }

    private static int countChapters(Lag lag) {
        int count = 0;
        for (var avd : lag.get()) {
            count += avd.get().size();
        }
        return count;
    }
}
