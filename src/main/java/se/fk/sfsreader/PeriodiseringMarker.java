package se.fk.sfsreader;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodiseringMarker {
    private static final Pattern MARKER_RE = Pattern.compile("^(Upphör att gälla|Träder i kraft)\\s+([UI]):\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_RE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern INLINE_RE = Pattern.compile("/(?:Upphör att gälla|Träder i kraft)\\s+[UI]:[^/]+/");

    public enum Kind {
        UPPHOR,
        IKRAFT
    }

    public enum Status {
        NONE,
        VALID_DATED,
        VALID_RELATIVE,
        INVALID
    }

    public record Parsed(
            Status status,
            Kind kind,
            String raw,
            String tail,
            LocalDate date
    ) {
        boolean isInvalid() {
            return status == Status.INVALID;
        }

        boolean isUnresolved() {
            return status == Status.VALID_RELATIVE;
        }

        Optional<Boolean> isActiveOn(LocalDate effectiveDate) {
            if (status != Status.VALID_DATED || date == null || kind == null) {
                return Optional.empty();
            }
            if (kind == Kind.IKRAFT) {
                return Optional.of(!effectiveDate.isBefore(date));
            }
            return Optional.of(effectiveDate.isBefore(date));
        }
    }

    private PeriodiseringMarker() {
    }

    public static Parsed parse(String value) {
        if (value == null || value.isBlank()) {
            return new Parsed(Status.NONE, null, value, null, null);
        }

        String s = value.trim();
        Matcher matcher = MARKER_RE.matcher(s);
        if (!matcher.matches()) {
            return new Parsed(Status.INVALID, null, s, null, null);
        }

        String verb = matcher.group(1).trim().toLowerCase(Locale.ROOT);
        String code = matcher.group(2).trim().toUpperCase(Locale.ROOT);
        String tail = matcher.group(3).trim();
        if (tail.isEmpty()) {
            return new Parsed(Status.INVALID, null, s, tail, null);
        }

        Kind kind = switch (verb) {
            case "upphör att gälla" -> Kind.UPPHOR;
            case "träder i kraft" -> Kind.IKRAFT;
            default -> null;
        };
        if (kind == null) {
            return new Parsed(Status.INVALID, null, s, tail, null);
        }
        if ((kind == Kind.UPPHOR && !"U".equals(code)) || (kind == Kind.IKRAFT && !"I".equals(code))) {
            return new Parsed(Status.INVALID, kind, s, tail, null);
        }

        Matcher dateMatcher = DATE_RE.matcher(tail);
        if (dateMatcher.find()) {
            try {
                LocalDate date = LocalDate.parse(dateMatcher.group(1));
                return new Parsed(Status.VALID_DATED, kind, s, tail, date);
            } catch (DateTimeParseException ignored) {
                return new Parsed(Status.INVALID, kind, s, tail, null);
            }
        }

        return new Parsed(Status.VALID_RELATIVE, kind, s, tail, null);
    }

    public static boolean containsInlineMarker(String text) {
        return text != null && INLINE_RE.matcher(text).find();
    }
}
