package se.fk.sfsreader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DocumentSources {
    private static final Logger log = LogManager.getLogger(DocumentSources.class);
    private static final Pattern TITLE_WITH_ID_RE = Pattern.compile("(?m)^\\s*([^\\n]{3,200}?)\\s*\\((\\d{4}:\\d+[a-zA-Z]?)\\)\\s*$");
    private static final Pattern SFS_NR_RE = Pattern.compile("(?im)SFS\\s*nr\\s*:?\\s*(\\d{4}:\\d+[a-zA-Z]?)");
    private static final Pattern BARE_ID_RE = Pattern.compile("(?m)^\\s*(\\d{4}:\\d+[a-zA-Z]?)\\s*$");

    private final Optional<byte[]> text;
    private final Optional<byte[]> html;
    private final Optional<String> title;
    private final Optional<String> id;

    private DocumentSources(Optional<byte[]> text, Optional<byte[]> html, Optional<String> title, Optional<String> id) {
        this.text = text;
        this.html = html;
        this.title = title;
        this.id = id;
    }

    static DocumentSources from(Path inputFile, Charset charset) throws Exception {
        byte[] bytes = Files.readAllBytes(inputFile);
        String content = new String(bytes, charset);

        if (looksLikeRiksdagenXml(content)) {
            DocumentSources extracted = fromRiksdagenXml(content, charset);
            DocumentSources resolved = extracted.resolveMetadata(charset);
            log.info("Input '{}' detected as dokumentstatus XML (text={}, html={})",
                    inputFile.getFileName(),
                    resolved.text.map(b -> b.length).orElse(0),
                    resolved.html.map(b -> b.length).orElse(0));
            return resolved;
        }

        // Backward-compatible mode for raw HTML files.
        log.info("Input '{}' treated as HTML", inputFile.getFileName());
        return new DocumentSources(Optional.empty(), Optional.of(bytes), Optional.empty(), Optional.empty())
                .resolveMetadata(charset);
    }

    Optional<InputStream> openTextStream() {
        return text.map(ByteArrayInputStream::new);
    }

    Optional<InputStream> openHtmlStream() {
        return html.map(ByteArrayInputStream::new);
    }

    Optional<String> title() {
        return title;
    }

    Optional<String> id() {
        return id;
    }

    static boolean looksLikeRiksdagenXml(String content) {
        return content.contains("<dokumentstatus>")
                && content.contains("<dokument>")
                && content.contains("<text>")
                && content.contains("<html>");
    }

    private static DocumentSources fromRiksdagenXml(String xml, Charset charset) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);

        var builder = factory.newDocumentBuilder();
        var dom = builder.parse(new InputSource(new StringReader(xml)));
        var xpath = XPathFactory.newInstance().newXPath();

        Node textNode = (Node) xpath.evaluate("/dokumentstatus/dokument/text", dom, XPathConstants.NODE);
        Node htmlNode = (Node) xpath.evaluate("/dokumentstatus/dokument/html", dom, XPathConstants.NODE);
        Node titleNode = (Node) xpath.evaluate("/dokumentstatus/dokument/titel", dom, XPathConstants.NODE);
        Node idNode = (Node) xpath.evaluate("/dokumentstatus/dokument/beteckning", dom, XPathConstants.NODE);

        Optional<byte[]> textBytes = Optional.ofNullable(textNode)
                .map(Node::getTextContent)
                .filter(s -> !s.isBlank())
                .map(s -> s.getBytes(charset));
        Optional<byte[]> htmlBytes = Optional.ofNullable(htmlNode)
                .map(Node::getTextContent)
                .filter(s -> !s.isBlank())
                .map(s -> s.getBytes(charset));

        Optional<String> title = Optional.ofNullable(titleNode)
                .map(Node::getTextContent)
                .map(String::strip)
                .filter(s -> !s.isEmpty());
        Optional<String> id = Optional.ofNullable(idNode)
                .map(Node::getTextContent)
                .map(String::strip)
                .filter(s -> !s.isEmpty());

        return new DocumentSources(textBytes, htmlBytes, title, id);
    }

    private DocumentSources resolveMetadata(Charset charset) {
        String textContent = text.map(b -> new String(b, charset)).orElse("");
        String htmlContent = html.map(b -> new String(b, charset)).orElse("");

        Optional<String> resolvedId = id.or(() -> inferId(textContent)).or(() -> inferId(htmlContent));
        Optional<String> resolvedTitle = title.or(() -> inferTitle(textContent)).or(() -> inferTitle(htmlContent));

        if (resolvedTitle.isEmpty() && resolvedId.isPresent()) {
            resolvedTitle = Optional.of("SFS " + resolvedId.get());
        }
        return new DocumentSources(text, html, resolvedTitle, resolvedId);
    }

    private static Optional<String> inferTitle(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TITLE_WITH_ID_RE.matcher(content);
        if (matcher.find()) {
            return Optional.of((matcher.group(1).trim() + " (" + matcher.group(2).trim() + ")").trim());
        }
        return Optional.empty();
    }

    private static Optional<String> inferId(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Matcher sfs = SFS_NR_RE.matcher(content);
        if (sfs.find()) {
            return Optional.of(sfs.group(1).trim());
        }
        Matcher bare = BARE_ID_RE.matcher(content);
        if (bare.find()) {
            return Optional.of(bare.group(1).trim());
        }
        return Optional.empty();
    }
}
