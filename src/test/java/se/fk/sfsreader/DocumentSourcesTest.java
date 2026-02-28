package se.fk.sfsreader;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.Assert.*;

public class DocumentSourcesTest {

    @Test
    public void extractsTextAndHtmlFromRiksdagenXml() throws Exception {
        Path file = Files.createTempFile("docsrc-", ".xml");
        String xml = """
                <dokumentstatus>
                  <dokument>
                    <titel>Testlag (2000:1)</titel>
                    <beteckning>2000:1</beteckning>
                    <text>AVD. A TEST\\n1 kap. Rubrik\\n1 § Text</text>
                    <html><![CDATA[<html><body><h1>Rubrik</h1></body></html>]]></html>
                  </dokument>
                </dokumentstatus>
                """;
        Files.writeString(file, xml, StandardCharsets.UTF_8);

        DocumentSources sources = DocumentSources.from(file, StandardCharsets.UTF_8);
        Optional<String> text = readUtf8(sources.openTextStream());
        Optional<String> html = readUtf8(sources.openHtmlStream());

        assertTrue(text.isPresent());
        assertTrue(html.isPresent());
        assertTrue(text.get().contains("1 § Text"));
        assertTrue(html.get().contains("<h1>Rubrik</h1>"));
        assertEquals("Testlag (2000:1)", sources.title().orElse(null));
        assertEquals("2000:1", sources.id().orElse(null));
    }

    @Test
    public void supportsXmlWhenOnePayloadIsMissing() throws Exception {
        Path file = Files.createTempFile("docsrc-", ".xml");
        String xml = """
                <dokumentstatus>
                  <dokument>
                    <text></text>
                    <html><![CDATA[<html><body>Only html</body></html>]]></html>
                  </dokument>
                </dokumentstatus>
                """;
        Files.writeString(file, xml, StandardCharsets.UTF_8);

        DocumentSources sources = DocumentSources.from(file, StandardCharsets.UTF_8);

        assertTrue(sources.openTextStream().isEmpty());
        Optional<String> html = readUtf8(sources.openHtmlStream());
        assertTrue(html.isPresent());
        assertTrue(html.get().contains("Only html"));
    }

    @Test
    public void fallsBackToHtmlForRawHtmlInput() throws Exception {
        Path file = Files.createTempFile("docsrc-", ".html");
        String rawHtml = "<html><body><p>Standalone html</p></body></html>";
        Files.writeString(file, rawHtml, StandardCharsets.UTF_8);

        DocumentSources sources = DocumentSources.from(file, StandardCharsets.UTF_8);
        Optional<String> html = readUtf8(sources.openHtmlStream());

        assertTrue(sources.openTextStream().isEmpty());
        assertTrue(html.isPresent());
        assertEquals(rawHtml, html.get());
    }

    private static Optional<String> readUtf8(Optional<InputStream> stream) throws Exception {
        if (stream.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream is = stream.get()) {
            return Optional.of(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
