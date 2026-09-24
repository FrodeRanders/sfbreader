package se.fk.sfsreader;

import org.jsoup.nodes.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Repairs only uniquely aligned missing boundaries. Never inserts wording from one source
 * into the other. The report preserves the original text-node contents and source location.
 */
public final class HybridSourceRepair {
    public record Finding(String status, String kind, String chapter, String paragraph,
                          int textLine, int htmlTextNode, int start, int end,
                          String originalHtmlText, String sourceText, String reason) {}
    public record Report(String textSha256, String htmlDomSha256, String offsetConvention,
                         List<Finding> findings) {}
    private record Located(TextNode node, String chapter, int ordinal) {}
    private record Match(Located location, int start, int end) {}

    public Report repair(Document html, String sourceText) {
        String htmlHash = sha256(html.outerHtml());
        TextStructure structure = new TextStructure(sourceText);
        List<Located> originalNodes = textNodes(html);
        List<Finding> findings = new ArrayList<>();
        Set<String> anchors = new HashSet<>();
        for (Element anchor : html.select("a.paragraf")) {
            anchors.add(anchor.hasAttr("id") ? anchor.id() : anchor.attr("name"));
        }
        Map<String, Long> occurrences = new HashMap<>();
        for (var section : structure.sections) occurrences.merge(key(section), 1L, Long::sum);
        // Collect edits against original nodes, then apply from right to left to retain offsets.
        Map<Located, List<MatchSection>> edits = new LinkedHashMap<>();
        for (var section : structure.sections) {
            String key = key(section);
            if (anchors.contains(key)) continue;
            Pattern opening = whitespacePattern(section.opening());
            List<Match> matches = new ArrayList<>();
            for (Located node : originalNodes) {
                if (!node.chapter.equals(section.chapter())) continue;
                Matcher m = opening.matcher(node.node.getWholeText());
                while (m.find()) matches.add(new Match(node, m.start(), m.end()));
            }
            if (occurrences.get(key) != 1 || matches.size() != 1) {
                findings.add(new Finding("unresolved", "missing_section_anchor", section.chapter(),
                        section.number(), section.line(), -1, -1, -1, null, section.opening(),
                        "Recovery requires one text occurrence and one matching HTML text span; found "
                                + occurrences.get(key) + " and " + matches.size()));
                continue;
            }
            Match match = matches.getFirst();
            // A match inside an existing provision can be an in-text citation. Require either
            // a source newline, or a preceding heading independently observed in the text.
            String before = match.location.node.getWholeText().substring(0, match.start);
            boolean lineStart = before.isBlank() || before.matches("(?s).*\\R[\\h]*");
            boolean headingPrefix = structure.headings.values().stream().anyMatch(h ->
                    h.chapter().equals(section.chapter()) && h.lastLine() == section.line() - 1
                            && TextStructure.normalize(before).equals(h.text()));
            if (!lineStart && !headingPrefix) {
                findings.add(finding("unresolved", section, match, "HTML match is not at a source line or heading boundary"));
                continue;
            }
            edits.computeIfAbsent(match.location, ignored -> new ArrayList<>())
                    .add(new MatchSection(match, section));
            findings.add(finding("repaired", section, match,
                    "Unique text section opening aligned with HTML in the same chapter"));
        }
        for (var entry : edits.entrySet()) {
            var sorted = entry.getValue();
            sorted.sort(Comparator.comparingInt((MatchSection e) -> e.match.start).reversed());
            TextNode original = entry.getKey().node;
            for (MatchSection edit : sorted) {
                // Split at the start and after the § marker, preserving the body verbatim.
                Matcher marker = Pattern.compile("\\d+[\\s\\u00a0]*[a-z]?[\\s\\u00a0]*§").matcher(original.getWholeText());
                marker.region(edit.match.start, original.getWholeText().length());
                if (!marker.lookingAt()) throw new IllegalStateException("Lost aligned section marker");
                int markerEnd = marker.end();
                TextNode tail = original.splitText(markerEnd);
                TextNode label = original.splitText(edit.match.start);
                Element anchor = new Element("a").addClass("paragraf").attr("name", key(edit.section));
                anchor.appendElement("b").text(label.getWholeText());
                label.replaceWith(anchor);
            }
        }
        // Recover unmarked headings only when a whole remaining HTML text node agrees with
        // a heading observed before a section in the text payload. Never strip body substrings.
        for (Located located : textNodes(html)) {
            String normalized = TextStructure.normalize(located.node.getWholeText());
            if (normalized.isEmpty()) continue;
            var candidates = structure.headings.values().stream().filter(h ->
                    h.chapter().equals(located.chapter) && h.text().equals(normalized)).toList();
            if (candidates.size() != 1) continue;
            var heading = candidates.getFirst();
            Element element = new Element("h4").attr("name", heading.text());
            element.appendElement("a").attr("name", heading.text()).text(heading.text());
            findings.add(new Finding("repaired", "unmarked_heading", located.chapter, null,
                    heading.firstLine(), located.ordinal, 0, located.node.getWholeText().length(),
                    located.node.getWholeText(), heading.text(), "Whole HTML text node matches a text heading"));
            located.node.replaceWith(element);
        }
        return new Report(sha256(sourceText), htmlHash,
                "textLine: 1-based in decoded XML text; section htmlTextNode: 0-based eligible node in original DOM; heading node: after section edits; start/end: UTF-16, end exclusive; htmlDomSha256: jsoup outerHtml before repairs",
                List.copyOf(findings));
    }

    private record MatchSection(Match match, TextStructure.Section section) {}
    private static Finding finding(String status, TextStructure.Section s, Match m, String reason) {
        return new Finding(status, "missing_section_anchor", s.chapter(), s.number(), s.line(),
                m.location.ordinal, m.start, m.end, m.location.node.getWholeText(), s.opening(), reason);
    }
    private static String key(TextStructure.Section s) {
        return (s.chapter().isEmpty() ? "" : "K" + s.chapter()) + "P" + s.number();
    }
    private static Pattern whitespacePattern(String text) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Arrays.stream(text.strip().split("\\s+"))
                .map(Pattern::quote).collect(java.util.stream.Collectors.joining("[\\s\\u00a0]+")));
    }
    private static List<Located> textNodes(Document document) {
        List<Located> out = new ArrayList<>();
        String[] chapter = {""};
        Element body = document.select("div:not(.sfstoc)").first();
        if (body == null) return out;
        body.forEachNode(node -> {
            if (node instanceof Element element && element.tagName().equals("h3")) {
                Matcher m = TextStructure.CHAPTER.matcher(element.text());
                chapter[0] = m.matches() ? TextStructure.number(m.group(1)) : "overgang";
            }
            if (node instanceof TextNode text && text.parent() instanceof Element parent
                    && parent.closest("h2,h3,h4,a,i,script,style,.sfstoc") == null) {
                out.add(new Located(text, chapter[0], out.size()));
            }
        });
        return out;
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
