package se.fk.sfbreader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.sfbreader.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextProcessor {
    private static final Logger log = LoggerFactory.getLogger(TextProcessor.class);
    private static final String WS = "[\\s\\u00A0]";
    private static final Pattern AVDELNING_RE = Pattern.compile("^AVD\\." + WS + "+([A-Z])" + WS + "+(.+)$");
    private static final Pattern UNDERAVDELNING_RE = Pattern.compile("^([IVX]+)" + WS + "{2,}(.+)$");
    private static final Pattern KAPITEL_RE = Pattern.compile("^(\\d+" + WS + "*[a-z]?)" + WS + "+kap\\." + WS + "+([A-ZÅÄÖ].+)$");
    private static final Pattern PARAGRAF_RE = Pattern.compile("^(\\d+" + WS + "*[a-z]?)" + WS + "*§(?!§)" + WS + "*(.*)$");
    private static final Pattern PERIODISERING_PREFIX_RE = Pattern.compile("^/(.+?)/\\s*(.*)$");
    private final String lagName;
    private final String lagId;

    public TextProcessor() {
        this("Unknown law", "unknown");
    }

    public TextProcessor(String lagName, String lagId) {
        this.lagName = lagName;
        this.lagId = lagId;
    }

    public Optional<Lag> process(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");

        Lag lag = new Lag(lagName, lagId);

        Avdelning currentAvdelning = null;
        Underavdelning currentUnderavdelning = null;
        Kapitel currentKapitel = null;
        Paragraf currentParagraf = null;
        Stycke currentStycke = null;
        boolean pendingNewStycke = false;
        boolean sawRealChapter = false;

        try (BufferedReader br = new BufferedReader(reader)) {
            String raw;
            while ((raw = br.readLine()) != null) {
                String line = raw.strip();
                if (line.isEmpty()) {
                    if (currentParagraf != null && currentStycke != null && !currentStycke.isEmpty()) {
                        pendingNewStycke = true;
                    }
                    continue;
                }

                Matcher avdMatcher = AVDELNING_RE.matcher(line);
                if (avdMatcher.find()) {
                    currentAvdelning = new Avdelning(avdMatcher.group(1), avdMatcher.group(2));
                    lag.add(currentAvdelning);
                    lag.setAktuellAvdelning(currentAvdelning);

                    currentUnderavdelning = null;
                    currentKapitel = null;
                    currentParagraf = null;
                    currentStycke = null;
                    pendingNewStycke = false;
                    continue;
                }

                Matcher underavdMatcher = UNDERAVDELNING_RE.matcher(line);
                if (underavdMatcher.find()) {
                    if (currentAvdelning != null) {
                        currentUnderavdelning = new Underavdelning(underavdMatcher.group(1), underavdMatcher.group(2));
                        currentAvdelning.setAktuellUnderavdelning(currentUnderavdelning);
                    }
                    currentKapitel = null;
                    currentParagraf = null;
                    currentStycke = null;
                    pendingNewStycke = false;
                    continue;
                }

                Matcher kapMatcher = KAPITEL_RE.matcher(line);
                if (kapMatcher.find()) {
                    currentKapitel = new Kapitel(normalizeNumberToken(kapMatcher.group(1)), kapMatcher.group(2));
                    sawRealChapter = true;
                    if (currentAvdelning != null) {
                        currentAvdelning.addKapitel(currentKapitel);
                    } else {
                        lag.addKapitel(currentKapitel);
                    }
                    if (currentUnderavdelning != null) {
                        currentKapitel.setAktuellUnderavdelning(currentUnderavdelning);
                    }

                    currentParagraf = null;
                    currentStycke = null;
                    pendingNewStycke = false;
                    continue;
                }

                if ("Övergångsbestämmelser".equalsIgnoreCase(line)) {
                    currentKapitel = new Overgang(line, !sawRealChapter);
                    if (currentAvdelning != null) {
                        currentAvdelning.addKapitel(currentKapitel);
                    } else {
                        lag.addKapitel(currentKapitel);
                    }
                    currentParagraf = null;
                    currentStycke = null;
                    pendingNewStycke = false;
                    continue;
                }

                Matcher parMatcher = PARAGRAF_RE.matcher(line);
                if (parMatcher.find()) {
                    if (currentKapitel == null) {
                        if (currentAvdelning == null) {
                            currentAvdelning = new Avdelning("A", "AUTO");
                            lag.add(currentAvdelning);
                            lag.setAktuellAvdelning(currentAvdelning);
                        }
                        currentKapitel = new Kapitel("1", "Auto-generated chapter", true);
                        currentAvdelning.addKapitel(currentKapitel);
                    }

                    currentParagraf = new Paragraf(normalizeNumberToken(parMatcher.group(1)));
                    currentKapitel.addParagraf(currentParagraf);

                    currentStycke = new Stycke();
                    currentParagraf.add(currentStycke);
                    pendingNewStycke = false;

                    String remainder = parMatcher.group(2).strip();
                    if (!remainder.isEmpty()) {
                        PeriodiseringSplit split = splitPeriodiseringPrefix(remainder);
                        if (split.periodisering != null && !split.periodisering.isBlank()) {
                            currentParagraf.setPeriodisering(split.periodisering);
                        }
                        if (!split.remainder.isEmpty()) {
                            currentStycke.add(split.remainder);
                            warnIfInlinePeriodiseringRemains(currentKapitel, currentParagraf, split.remainder);
                        }
                    }
                    continue;
                }

                if (currentParagraf != null) {
                    if (pendingNewStycke) {
                        currentStycke = new Stycke();
                        currentParagraf.add(currentStycke);
                        pendingNewStycke = false;
                    }

                    if (currentStycke == null) {
                        currentStycke = new Stycke();
                        currentParagraf.add(currentStycke);
                    }

                    PeriodiseringSplit split = splitPeriodiseringPrefix(line);
                    if (split.periodisering != null && !split.periodisering.isBlank()
                            && currentParagraf.getPeriodisering().isEmpty()) {
                        currentParagraf.setPeriodisering(split.periodisering);
                    }
                    if (!split.remainder.isEmpty()) {
                        currentStycke.add(split.remainder);
                        warnIfInlinePeriodiseringRemains(currentKapitel, currentParagraf, split.remainder);
                    }
                }
            }
        }

        lag.prune();
        return Optional.of(lag);
    }

    private static String normalizeNumberToken(String token) {
        return token
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static PeriodiseringSplit splitPeriodiseringPrefix(String text) {
        Matcher matcher = PERIODISERING_PREFIX_RE.matcher(text.strip());
        if (!matcher.matches()) {
            return new PeriodiseringSplit(null, text);
        }
        String periodisering = matcher.group(1).strip();
        String remainder = matcher.group(2).strip();
        return new PeriodiseringSplit(periodisering, remainder);
    }

    private void warnIfInlinePeriodiseringRemains(Kapitel kapitel, Paragraf paragraf, String text) {
        if (PeriodiseringMarker.containsInlineMarker(text)) {
            String chapter = kapitel == null ? "?" : kapitel.id();
            String paragraph = paragraf == null ? "?" : paragraf.nummer();
            log.warn("Inline periodisering marker left in paragraph body at K{} P{}: {}",
                    chapter, paragraph, snippet(text));
        }
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String s = text.strip();
        if (s.length() <= 120) {
            return s;
        }
        return s.substring(0, 120) + "...";
    }

    private record PeriodiseringSplit(String periodisering, String remainder) {}
}
