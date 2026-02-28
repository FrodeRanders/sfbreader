package se.fk.sfsreader;

import se.fk.sfsreader.model.*;

import java.util.*;

public class HybridReconciler {
    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum Category {
        STRUCTURAL,
        CONTENT,
        FORMAT_ONLY
    }

    public record Finding(
            String key,
            String type,
            Severity severity,
            Category category,
            String message,
            String htmlPeriodisering,
            String textPeriodisering
    ) {
        public Finding(
                String key,
                String type,
                Severity severity,
                Category category,
                String message
        ) {
            this(key, type, severity, category, message, null, null);
        }
    }

    public record Result(
            int findingCount,
            Map<String, Integer> byType,
            Map<Severity, Integer> bySeverity,
            List<Finding> findings
    ) {
        public String asText() {
            StringBuilder sb = new StringBuilder();
            sb.append("Hybrid reconciliation report\n");
            sb.append("Findings: ").append(findingCount).append("\n\n");

            if (!bySeverity.isEmpty()) {
                sb.append("Summary by severity:\n");
                for (Severity severity : Severity.values()) {
                    int count = bySeverity.getOrDefault(severity, 0);
                    if (count > 0) {
                        sb.append("- ").append(severity).append(": ").append(count).append("\n");
                    }
                }
                sb.append("\n");
            }

            if (!byType.isEmpty()) {
                sb.append("Summary by type:\n");
                for (Map.Entry<String, Integer> entry : byType.entrySet()) {
                    sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }

            for (Finding finding : findings) {
                sb.append("- [")
                        .append(finding.severity()).append("] ")
                        .append(finding.type())
                        .append(" key=").append(finding.key())
                        .append(" :: ")
                        .append(finding.message())
                        .append("\n");
            }
            return sb.toString();
        }
    }

    public Result reconcile(Lag htmlLag, Lag textLag) {
        Map<String, ChapterView> html = index(htmlLag);
        Map<String, ChapterView> text = index(textLag);

        List<Finding> findings = new ArrayList<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<Severity, Integer> bySeverity = new EnumMap<>(Severity.class);

        Set<String> chapterIds = new TreeSet<>(new ChapterIdComparator());
        chapterIds.addAll(html.keySet());
        chapterIds.addAll(text.keySet());

        for (String chapterId : chapterIds) {
            ChapterView h = html.get(chapterId);
            ChapterView t = text.get(chapterId);

            if (h == null) {
                addFinding(findings, byType, bySeverity,
                        new Finding(
                                "chapter_missing_html:K" + chapterId,
                                "chapter_missing_html",
                                Severity.HIGH,
                                Category.STRUCTURAL,
                                "Chapter missing in HTML: " + chapterId + " (" + t.name + ")"
                        ));
                continue;
            }
            if (t == null) {
                addFinding(findings, byType, bySeverity,
                        new Finding(
                                "chapter_missing_text:K" + chapterId,
                                "chapter_missing_text",
                                Severity.HIGH,
                                Category.STRUCTURAL,
                                "Chapter missing in text: " + chapterId + " (" + h.name + ")"
                        ));
                continue;
            }

            Set<String> paragraphIds = new TreeSet<>(new ParagraphIdComparator());
            paragraphIds.addAll(h.paragraphs.keySet());
            paragraphIds.addAll(t.paragraphs.keySet());

            for (String paragraphId : paragraphIds) {
                List<ParagraphVariant> hVariants = h.paragraphs.get(paragraphId);
                List<ParagraphVariant> tVariants = t.paragraphs.get(paragraphId);
                String location = "K" + chapterId + " P" + paragraphId;

                if (hVariants == null) {
                    addFinding(findings, byType, bySeverity,
                            new Finding(
                                    "paragraph_missing_html:" + location,
                                    "paragraph_missing_html",
                                    Severity.HIGH,
                                    Category.STRUCTURAL,
                                    "Paragraph missing in HTML: " + location
                    ));
                    continue;
                }
                if (tVariants == null) {
                    addFinding(findings, byType, bySeverity,
                            new Finding(
                                    "paragraph_missing_text:" + location,
                                    "paragraph_missing_text",
                                    Severity.HIGH,
                                    Category.STRUCTURAL,
                                    "Paragraph missing in text: " + location
                            ));
                    continue;
                }

                if (hVariants.size() != tVariants.size()) {
                    addFinding(findings, byType, bySeverity,
                            new Finding(
                                    "paragraph_variant_count:" + location,
                                    "paragraph_variant_count",
                                    Severity.HIGH,
                                    Category.STRUCTURAL,
                                    "Different paragraph variant count at " + location
                                            + " (html=" + hVariants.size() + ", text=" + tVariants.size() + ")"
                            ));
                }

                List<VariantPair> alignedVariants = alignVariants(hVariants, tVariants);
                for (int i = 0; i < alignedVariants.size(); i++) {
                    ParagraphVariant htmlVariant = alignedVariants.get(i).html();
                    ParagraphVariant textVariant = alignedVariants.get(i).text();
                    String hb = htmlVariant.body();
                    String tb = textVariant.body();
                    String variantKey = location + "#V" + (i + 1);
                    String hp = normalizePeriodisering(htmlVariant.periodisering());
                    String tp = normalizePeriodisering(textVariant.periodisering());

                    addPeriodiseringValidityFindings(findings, byType, bySeverity, variantKey, location, htmlVariant, textVariant);

                    if (!hp.isEmpty() && !tp.isEmpty() && !hp.equals(tp)) {
                        addFinding(findings, byType, bySeverity,
                                new Finding(
                                        "paragraph_periodisering_mismatch:" + variantKey,
                                        "paragraph_periodisering_mismatch",
                                        Severity.MEDIUM,
                                        Category.STRUCTURAL,
                                        "Paragraph periodisering mismatch at " + location
                                                + " [variant " + (i + 1) + "]"
                                                + " htmlPeriodisering=\"" + snippet(htmlVariant.periodisering()) + "\""
                                                + " textPeriodisering=\"" + snippet(textVariant.periodisering()) + "\"",
                                        metadataPeriodisering(htmlVariant.periodisering()),
                                        metadataPeriodisering(textVariant.periodisering())
                                ));
                    }

                    if (hb.isBlank() && !tb.isBlank()) {
                        addFinding(findings, byType, bySeverity,
                                new Finding(
                                        "paragraph_empty_html:" + variantKey,
                                        "paragraph_empty_html",
                                        Severity.MEDIUM,
                                        Category.CONTENT,
                                        "HTML paragraph body empty while text has content at " + location
                                                + " [variant " + (i + 1) + "]",
                                        metadataPeriodisering(htmlVariant.periodisering()),
                                        metadataPeriodisering(textVariant.periodisering())
                                ));
                        continue;
                    }

                    if (!equivalentText(hb, tb)) {
                        if (formatEquivalent(hb, tb)) {
                            addFinding(findings, byType, bySeverity,
                                    new Finding(
                                            "paragraph_text_format_only:" + variantKey,
                                            "paragraph_text_format_only",
                                            Severity.LOW,
                                            Category.FORMAT_ONLY,
                                        "Paragraph format-only mismatch at " + location
                                                + " [variant " + (i + 1) + "]"
                                                + periodiseringNote(htmlVariant, textVariant)
                                                + " html=\"" + snippet(hb) + "\""
                                                + " text=\"" + snippet(tb) + "\"",
                                            metadataPeriodisering(htmlVariant.periodisering()),
                                            metadataPeriodisering(textVariant.periodisering())
                                    ));
                        } else {
                            addFinding(findings, byType, bySeverity,
                                    new Finding(
                                            "paragraph_text_mismatch:" + variantKey,
                                            "paragraph_text_mismatch",
                                            Severity.MEDIUM,
                                            Category.CONTENT,
                                        "Paragraph text mismatch at " + location
                                                + " [variant " + (i + 1) + "]"
                                                + periodiseringNote(htmlVariant, textVariant)
                                                + " html=\"" + snippet(hb) + "\""
                                                + " text=\"" + snippet(tb) + "\"",
                                            metadataPeriodisering(htmlVariant.periodisering()),
                                            metadataPeriodisering(textVariant.periodisering())
                                    ));
                        }
                    }
                }
            }
        }

        return new Result(findings.size(), byType, bySeverity, findings);
    }

    private static Map<String, ChapterView> index(Lag lag) {
        Map<String, ChapterView> out = new HashMap<>();
        Set<Kapitel> seenKapitel = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Avdelning avdelning : lag.get()) {
            for (Kapitel kapitel : avdelning.get()) {
                if (!seenKapitel.add(kapitel)) {
                    continue;
                }

                String chapterId = normalizeId(kapitel.id());
                ChapterView chapter = out.computeIfAbsent(chapterId, id -> new ChapterView(kapitel.namn()));
                for (Paragraf paragraf : kapitel.get()) {
                    String paragraphId = normalizeId(paragraf.nummer());
                    String body = paragraphBody(paragraf);
                    String periodisering = normalize(paragraf.getPeriodisering().orElse(""));
                    String versionIdentity = normalize(paragraf.getVersionIdentity().orElse(""));
                    String versionStatus = normalize(paragraf.getVersionStatus().orElse("UNTAGGED"));
                    ParagraphVariant variant = new ParagraphVariant(body, periodisering, versionIdentity, versionStatus);
                    List<ParagraphVariant> variants = chapter.paragraphs.computeIfAbsent(paragraphId, ignored -> new ArrayList<>());
                    if (!variants.contains(variant)) {
                        variants.add(variant);
                    }
                }
            }
        }
        return out;
    }

    private static String paragraphBody(Paragraf paragraf) {
        StringBuilder sb = new StringBuilder();
        boolean firstStycke = true;
        for (Stycke stycke : paragraf.get()) {
            if (!firstStycke) {
                sb.append(" || ");
            }
            firstStycke = false;

            boolean firstLine = true;
            for (String line : stycke.get()) {
                if (!firstLine) {
                    sb.append(' ');
                }
                firstLine = false;
                sb.append(line);
            }
        }
        return normalize(sb.toString());
    }

    private static String normalize(String s) {
        return s.replace("||", " ").replaceAll("\\s+", " ").trim();
    }

    private static String normalizeLoose(String s) {
        return normalize(s)
                .replaceAll("(?i)\\bLag\\s*\\(\\d{4}:\\d+\\)\\.?", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        return id.replaceAll("\\s+", "").trim();
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= 80) {
            return s;
        }
        return s.substring(0, 80) + "...";
    }

    private static List<VariantPair> alignVariants(List<ParagraphVariant> htmlVariants, List<ParagraphVariant> textVariants) {
        int min = Math.min(htmlVariants.size(), textVariants.size());
        List<VariantPair> out = new ArrayList<>(min);
        boolean[] usedText = new boolean[textVariants.size()];

        for (int i = 0; i < min; i++) {
            ParagraphVariant hv = htmlVariants.get(i);
            int ti = chooseTextVariantIndex(hv, textVariants, usedText, i);
            if (ti < 0) {
                break;
            }
            usedText[ti] = true;
            out.add(new VariantPair(hv, textVariants.get(ti)));
        }
        return out;
    }

    private static int chooseTextVariantIndex(ParagraphVariant htmlVariant, List<ParagraphVariant> textVariants, boolean[] usedText, int fallbackIndex) {
        String hvIdentity = normalizeVersionIdentity(htmlVariant.versionIdentity());
        if (!hvIdentity.isEmpty()) {
            for (int i = 0; i < textVariants.size(); i++) {
                if (usedText[i]) {
                    continue;
                }
                String tvIdentity = normalizeVersionIdentity(textVariants.get(i).versionIdentity());
                if (!tvIdentity.isEmpty() && tvIdentity.equals(hvIdentity)) {
                    return i;
                }
            }
        }

        String hp = normalizePeriodisering(htmlVariant.periodisering());
        if (!hp.isEmpty()) {
            for (int i = 0; i < textVariants.size(); i++) {
                if (usedText[i]) {
                    continue;
                }
                String tp = normalizePeriodisering(textVariants.get(i).periodisering());
                if (!tp.isEmpty() && tp.equals(hp)) {
                    return i;
                }
            }
        }

        if (fallbackIndex < textVariants.size() && !usedText[fallbackIndex]) {
            return fallbackIndex;
        }

        for (int i = 0; i < textVariants.size(); i++) {
            if (!usedText[i]) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizePeriodisering(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeVersionIdentity(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String periodiseringNote(ParagraphVariant htmlVariant, ParagraphVariant textVariant) {
        String hp = normalize(htmlVariant.periodisering());
        String tp = normalize(textVariant.periodisering());
        if (hp.isEmpty() && tp.isEmpty()) {
            return "";
        }
        if (hp.equals(tp)) {
            return " periodisering=\"" + snippet(hp) + "\"";
        }
        return " htmlPeriodisering=\"" + snippet(hp) + "\" textPeriodisering=\"" + snippet(tp) + "\"";
    }

    private static String metadataPeriodisering(String periodisering) {
        String normalized = normalize(periodisering == null ? "" : periodisering);
        return normalized.isEmpty() ? null : normalized;
    }

    private static void addPeriodiseringValidityFindings(
            List<Finding> findings,
            Map<String, Integer> byType,
            Map<Severity, Integer> bySeverity,
            String variantKey,
            String location,
            ParagraphVariant htmlVariant,
            ParagraphVariant textVariant
    ) {
        PeriodiseringMarker.Parsed html = PeriodiseringMarker.parse(htmlVariant.periodisering());
        PeriodiseringMarker.Parsed text = PeriodiseringMarker.parse(textVariant.periodisering());

        if (html.isInvalid() || text.isInvalid()) {
            addFinding(findings, byType, bySeverity,
                    new Finding(
                            "paragraph_periodisering_invalid:" + variantKey,
                            "paragraph_periodisering_invalid",
                            Severity.MEDIUM,
                            Category.STRUCTURAL,
                            "Invalid periodisering marker at " + location
                                    + " [variant " + variantKey.substring(variantKey.lastIndexOf("#V") + 2) + "]"
                                    + (html.isInvalid() ? " html=\"" + snippet(htmlVariant.periodisering()) + "\"" : "")
                                    + (text.isInvalid() ? " text=\"" + snippet(textVariant.periodisering()) + "\"" : ""),
                            metadataPeriodisering(htmlVariant.periodisering()),
                            metadataPeriodisering(textVariant.periodisering())
                    ));
        } else if (html.isUnresolved() || text.isUnresolved()) {
            addFinding(findings, byType, bySeverity,
                    new Finding(
                            "paragraph_periodisering_unresolved:" + variantKey,
                            "paragraph_periodisering_unresolved",
                            Severity.LOW,
                            Category.STRUCTURAL,
                            "Unresolved periodisering marker at " + location
                                    + " [variant " + variantKey.substring(variantKey.lastIndexOf("#V") + 2) + "]"
                                    + (html.isUnresolved() ? " html=\"" + snippet(htmlVariant.periodisering()) + "\"" : "")
                                    + (text.isUnresolved() ? " text=\"" + snippet(textVariant.periodisering()) + "\"" : ""),
                            metadataPeriodisering(htmlVariant.periodisering()),
                            metadataPeriodisering(textVariant.periodisering())
                    ));
        }
    }

    private static final class ChapterView {
        private final String name;
        private final Map<String, List<ParagraphVariant>> paragraphs = new HashMap<>();

        private ChapterView(String name) {
            this.name = name;
        }
    }

    private record ParagraphVariant(String body, String periodisering, String versionIdentity, String versionStatus) {}

    private record VariantPair(ParagraphVariant html, ParagraphVariant text) {}

    private static final class ChapterIdComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            int na = leadingNumber(a);
            int nb = leadingNumber(b);
            if (na != nb) {
                return Integer.compare(na, nb);
            }
            return a.compareTo(b);
        }
    }

    private static final class ParagraphIdComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            int na = leadingNumber(a);
            int nb = leadingNumber(b);
            if (na != nb) {
                return Integer.compare(na, nb);
            }
            return a.compareTo(b);
        }
    }

    private static int leadingNumber(String s) {
        if (s == null || s.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(s.substring(0, i));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean equivalentText(String a, String b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        String an = normalizeLoose(a);
        String bn = normalizeLoose(b);
        if (Objects.equals(an, bn)) {
            return true;
        }
        if (an.isEmpty() || bn.isEmpty()) {
            return false;
        }
        String shorter = an.length() <= bn.length() ? an : bn;
        String longer = an.length() > bn.length() ? an : bn;
        return longer.startsWith(shorter) && (double) shorter.length() / (double) longer.length() > 0.95;
    }

    private static boolean formatEquivalent(String a, String b) {
        String af = normalizeLoose(a)
                .replaceAll("[,.;:()\\-]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
        String bf = normalizeLoose(b)
                .replaceAll("[,.;:()\\-]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
        return Objects.equals(af, bf);
    }

    private static void addFinding(List<Finding> findings, Map<String, Integer> byType, Map<Severity, Integer> bySeverity, Finding finding) {
        byType.merge(finding.type(), 1, Integer::sum);
        bySeverity.merge(finding.severity(), 1, Integer::sum);
        findings.add(finding);
    }
}
