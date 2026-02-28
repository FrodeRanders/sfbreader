package se.fk.sfsreader;

import se.fk.sfsreader.model.Avdelning;
import se.fk.sfsreader.model.Kapitel;
import se.fk.sfsreader.model.Lag;
import se.fk.sfsreader.model.Paragraf;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class PeriodiseringSchedule {
    private PeriodiseringSchedule() {
    }

    static Report build(Lag lag, LocalDate referenceDate) {
        List<Transition> transitions = new ArrayList<>();

        for (Avdelning avdelning : lag.get()) {
            for (Kapitel kapitel : avdelning.get()) {
                for (Paragraf paragraf : kapitel.get()) {
                    String periodisering = paragraf.getPeriodisering().orElse(null);
                    PeriodiseringMarker.Parsed parsed = PeriodiseringMarker.parse(periodisering);
                    if (parsed.status() != PeriodiseringMarker.Status.VALID_DATED || parsed.date() == null || parsed.kind() == null) {
                        continue;
                    }
                    String location = "K" + kapitel.id() + " P" + paragraf.nummer();
                    String action = parsed.kind() == PeriodiseringMarker.Kind.IKRAFT
                            ? "becomes_active"
                            : "expires";
                    transitions.add(new Transition(
                            location,
                            parsed.raw(),
                            parsed.kind().name(),
                            parsed.date().toString(),
                            action
                    ));
                }
            }
        }

        transitions.sort(Comparator
                .comparing(Transition::date)
                .thenComparing(Transition::location)
                .thenComparing(Transition::kind));

        Optional<String> nextTransitionDate = transitions.stream()
                .map(Transition::date)
                .filter(d -> !LocalDate.parse(d).isBefore(referenceDate))
                .findFirst();

        int upcoming = (int) transitions.stream()
                .map(Transition::date)
                .filter(d -> !LocalDate.parse(d).isBefore(referenceDate))
                .count();

        return new Report(
                referenceDate.toString(),
                nextTransitionDate.orElse(null),
                transitions.size(),
                upcoming,
                transitions
        );
    }

    record Report(
            String referenceDate,
            String nextTransitionDate,
            int totalDatedTransitions,
            int upcomingTransitions,
            List<Transition> transitions
    ) {}

    record Transition(
            String location,
            String periodisering,
            String kind,
            String date,
            String action
    ) {}
}
