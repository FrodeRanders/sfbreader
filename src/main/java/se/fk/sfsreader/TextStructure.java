package se.fk.sfsreader;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative structural observations over the unmodified text payload.
 * Line numbers are one-based; they refer to the XML-decoded text payload.
 */
final class TextStructure {
    static final Pattern CHAPTER = Pattern.compile("^(\\d+\\s*[a-z]?)\\s+kap\\.\\s+([A-ZÅÄÖ].+)$");
    static final Pattern SECTION = Pattern.compile("^(\\d+\\s*[a-z]?)\\s*§(?!§)\\s*(.*)$");
    private static final Pattern DIVISION = Pattern.compile("(?i)^(AVD\\.|AVDELNING)\\s+.+$");
    private static final Pattern SUBDIVISION = Pattern.compile("^[IVX]+\\s{2,}.+$");

    record Section(String chapter, String number, int line, String opening) {}
    record Heading(String chapter, int firstLine, int lastLine, String text) {}
    record ChapterHeading(String chapter, int firstLine, int lastLine, String text) {}
    final List<String> lines;
    final List<Section> sections = new ArrayList<>();
    final Map<Integer, Heading> headings = new LinkedHashMap<>();
    final Set<Integer> headingContinuations = new HashSet<>();
    final Map<Integer, ChapterHeading> chapterHeadings = new LinkedHashMap<>();
    final Set<Integer> chapterContinuations = new HashSet<>();

    TextStructure(String source) {
        lines = Arrays.stream(source.split("\\R", -1)).map(s -> s.replace('\u00a0', ' ').strip()).toList();
        String chapter = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher cm = CHAPTER.matcher(line);
            if (isChapterStart(lines, i)) {
                cm.matches(); chapter = number(cm.group(1));
                int end = i;
                while (end + 1 < lines.size() && !lines.get(end + 1).isBlank()
                        && !structural(lines.get(end + 1))
                        && !lines.get(end + 1).startsWith("/")) end++;
                // A continuation may itself look like a chapter citation, e.g.
                // "52 kap. inkomstskattelagen". The complete title is independent
                // evidence for repairing a split HTML heading.
                chapterHeadings.put(i + 1, new ChapterHeading(chapter, i + 1, end + 1,
                        normalize(String.join(" ", lines.subList(i, end + 1)))));
                for (int j = i + 2; j <= end + 1; j++) chapterContinuations.add(j);
            }
            if (line.equalsIgnoreCase("Övergångsbestämmelser")) break;
            if (isSectionStart(lines, i)) {
                Matcher pm = SECTION.matcher(line);
                pm.matches();
                sections.add(new Section(chapter, number(pm.group(1)), i + 1, line));
                findPrecedingHeadings(i, chapter);
            }
        }
    }

    static boolean isChapterStart(List<String> lines, int index) {
        if (!CHAPTER.matcher(lines.get(index)).matches()) return false;
        return index == 0 || lines.get(index - 1).isBlank()
                || DIVISION.matcher(lines.get(index - 1)).matches()
                || SUBDIVISION.matcher(lines.get(index - 1)).matches();
    }

    static boolean isSectionStart(List<String> lines, int index) {
        Matcher matcher = SECTION.matcher(lines.get(index));
        if (!matcher.matches()) return false;
        // A soft wrap after "9 kap." must not create a new "2 §".
        if (index > 0 && lines.get(index - 1).matches(".*\\bkap\\.$")) return false;
        // Tab-separated columns after the marker belong to a table, not to a
        // new provision (the first cell is frequently a section reference).
        if (lines.get(index).matches("^\\d+\\s*[a-z]?\\s*§[ \\t]*\\t.*")) return false;
        String body = matcher.group(2);
        // A bare marker on the next physical line can finish a wrapped citation.
        if (body.isEmpty() && index > 0
                && lines.get(index - 1).matches("(?i).*\\b(?:i|enligt|och|samt|se)\\s*$")) return false;
        // Lowercase text, punctuation and item numbers after § are reference continuations.
        return body.isEmpty() || Character.isUpperCase(body.codePointAt(0))
                || body.matches("^/(Upphör|Träder|Rubriken).*" );
    }

    static boolean isContentsEntry(String line) {
        return normalize(line).matches("^\\d+\\s*[a-z]?\\s+kap\\.\\s*[-–—]\\s+.+");
    }

    static boolean isContentsDivision(List<String> lines, int index) {
        // The title may wrap over several lines before the chapter list starts.
        for (int next = index + 1; next < lines.size(); next++) {
            String line = lines.get(next);
            if (line.isBlank()) continue;
            if (isContentsEntry(line)) return true;
            if (structural(line) || !line.equals(line.toUpperCase(Locale.ROOT))) return false;
        }
        return false;
    }

    private void findPrecedingHeadings(int sectionLine, String chapter) {
        int end = sectionLine - 1;
        while (end >= 0) {
            while (end >= 0 && lines.get(end).isBlank()) end--;
            if (end < 0 || structural(lines.get(end)) || sentenceEnd(lines.get(end))) return;
            int start = end;
            while (start > 0 && !lines.get(start - 1).isBlank()
                    && !structural(lines.get(start - 1)) && !sentenceEnd(lines.get(start - 1))) start--;
            String text = String.join(" ", lines.subList(start, end + 1));
            if (text.isEmpty() || text.length() > 300 || text.contains("§")
                    || !Character.isUpperCase(text.codePointAt(0)) || text.startsWith("/")) return;
            Heading heading = new Heading(chapter, start + 1, end + 1, text);
            headings.put(start + 1, heading);
            for (int i = start + 2; i <= end + 1; i++) headingContinuations.add(i);
            end = start - 1;
        }
    }

    private static boolean structural(String line) {
        return CHAPTER.matcher(line).matches() || SECTION.matcher(line).matches()
                || DIVISION.matcher(line).matches() || SUBDIVISION.matcher(line).matches()
                || line.equalsIgnoreCase("Övergångsbestämmelser");
    }

    private static boolean sentenceEnd(String line) {
        String cleaned = line.replaceFirst("(?i)(m\\.m\\.|m\\.fl\\.|o\\.d\\.)$", "");
        return cleaned.endsWith(".") || cleaned.endsWith(":") || cleaned.endsWith(";")
                || cleaned.endsWith(",") || line.startsWith("/");
    }

    static String number(String value) { return value.replaceAll("\\s+", ""); }
    static String normalize(String value) { return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip(); }
}
