package se.fk.sfsreader;

import se.fk.sfsreader.model.Avdelning;
import se.fk.sfsreader.model.Kapitel;
import se.fk.sfsreader.model.Lag;
import se.fk.sfsreader.model.Paragraf;
import se.fk.sfsreader.model.Stycke;

import java.util.ArrayList;
import java.util.List;

final class PeriodiseringValidator {
    private PeriodiseringValidator() {
    }

    static Result validate(Lag lag) {
        List<Finding> findings = new ArrayList<>();

        for (Avdelning avdelning : lag.get()) {
            for (Kapitel kapitel : avdelning.get()) {
                for (Paragraf paragraf : kapitel.get()) {
                    String location = "K" + kapitel.id() + " P" + paragraf.nummer();

                    PeriodiseringMarker.Parsed parsed = PeriodiseringMarker.parse(paragraf.getPeriodisering().orElse(null));
                    if (parsed.isInvalid()) {
                        findings.add(new Finding(
                                "paragraph_periodisering_invalid:" + location,
                                "paragraph_periodisering_invalid",
                                location,
                                "Invalid periodisering marker: " + parsed.raw()
                        ));
                    } else if (parsed.isUnresolved()) {
                        findings.add(new Finding(
                                "paragraph_periodisering_unresolved:" + location,
                                "paragraph_periodisering_unresolved",
                                location,
                                "Unresolved periodisering marker (no explicit date): " + parsed.raw()
                        ));
                    }

                    for (Stycke stycke : paragraf.get()) {
                        for (String line : stycke.get()) {
                            if (PeriodiseringMarker.containsInlineMarker(line)) {
                                findings.add(new Finding(
                                        "inline_periodisering_marker_in_text:" + location,
                                        "inline_periodisering_marker_in_text",
                                        location,
                                        "Inline periodisering marker left in paragraph body"
                                ));
                                break;
                            }
                        }
                    }
                }
            }
        }

        int invalid = (int) findings.stream().filter(f -> "paragraph_periodisering_invalid".equals(f.type())).count();
        int unresolved = (int) findings.stream().filter(f -> "paragraph_periodisering_unresolved".equals(f.type())).count();
        int inline = (int) findings.stream().filter(f -> "inline_periodisering_marker_in_text".equals(f.type())).count();
        return new Result(invalid, unresolved, inline, findings);
    }

    record Finding(
            String key,
            String type,
            String location,
            String message
    ) {}

    record Result(
            int invalidCount,
            int unresolvedCount,
            int inlineInTextCount,
            List<Finding> findings
    ) {}
}
