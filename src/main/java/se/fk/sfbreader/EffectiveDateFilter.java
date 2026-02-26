package se.fk.sfbreader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.sfbreader.model.Avdelning;
import se.fk.sfbreader.model.Kapitel;
import se.fk.sfbreader.model.Lag;
import se.fk.sfbreader.model.Paragraf;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EffectiveDateFilter {
    private static final Logger log = LoggerFactory.getLogger(EffectiveDateFilter.class);

    private EffectiveDateFilter() {
    }

    static Report apply(Lag lag, LocalDate effectiveDate) {
        Stats before = stats(lag);
        int removed = 0;
        int ambiguousGroups = 0;
        int selected = 0;
        int unresolvedCount = 0;
        int invalidCount = 0;
        Set<String> unresolvedKeys = java.util.Collections.newSetFromMap(new LinkedHashMap<>());
        Set<String> invalidKeys = java.util.Collections.newSetFromMap(new LinkedHashMap<>());

        for (Avdelning avdelning : lag.get()) {
            for (Kapitel kapitel : avdelning.get()) {
                Map<String, List<Paragraf>> byParagraphId = new LinkedHashMap<>();
                for (Paragraf p : kapitel.get()) {
                    byParagraphId.computeIfAbsent(p.nummer(), ignored -> new ArrayList<>()).add(p);
                }

                Set<Paragraf> keep = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                for (Map.Entry<String, List<Paragraf>> entry : byParagraphId.entrySet()) {
                    List<Paragraf> variants = entry.getValue();
                    if (variants.size() == 1) {
                        keep.add(variants.getFirst());
                        continue;
                    }

                    List<Paragraf> active = new ArrayList<>();
                    List<Paragraf> unresolved = new ArrayList<>();
                    List<Paragraf> invalid = new ArrayList<>();
                    for (Paragraf variant : variants) {
                        String location = "K" + kapitel.id() + " P" + entry.getKey();
                        PeriodiseringMarker.Parsed parsed = PeriodiseringMarker.parse(variant.getPeriodisering().orElse(null));
                        if (parsed.isInvalid()) {
                            invalid.add(variant);
                            invalidKeys.add(location);
                            invalidCount++;
                            continue;
                        }
                        if (parsed.isUnresolved()) {
                            unresolved.add(variant);
                            unresolvedKeys.add(location);
                            unresolvedCount++;
                            continue;
                        }
                        if (parsed.isActiveOn(effectiveDate).orElse(true)) {
                            active.add(variant);
                        }
                    }

                    if (!active.isEmpty()) {
                        keep.addAll(active);
                        selected += active.size();
                        continue;
                    }
                    if (!unresolved.isEmpty() || !invalid.isEmpty()) {
                        keep.addAll(unresolved);
                        keep.addAll(invalid);
                        ambiguousGroups++;
                        log.warn("Effective-date filtering unresolved for K{} P{} (date={}): keeping {} unresolved variants",
                                kapitel.id(), entry.getKey(), effectiveDate, unresolved.size() + invalid.size());
                        selected += unresolved.size() + invalid.size();
                        continue;
                    }

                    keep.addAll(variants);
                    ambiguousGroups++;
                    log.warn("Effective-date filtering selected no variants for K{} P{} (date={}): keeping all {} variants",
                            kapitel.id(), entry.getKey(), effectiveDate, variants.size());
                    selected += variants.size();
                }

                int beforeVariants = kapitel.get().size();
                kapitel.get().removeIf(p -> !keep.contains(p));
                removed += Math.max(0, beforeVariants - kapitel.get().size());
            }
        }

        log.info("Effective-date filtering complete for {}: removed {} paragraph variants, unresolved groups={}",
                effectiveDate, removed, ambiguousGroups);
        Stats after = stats(lag);
        return new Report(
                effectiveDate.toString(),
                selected,
                removed,
                unresolvedCount,
                invalidCount,
                new ArrayList<>(unresolvedKeys),
                new ArrayList<>(invalidKeys),
                before.chapterCount(),
                after.chapterCount(),
                before.paragraphGroupCount(),
                after.paragraphGroupCount(),
                before.paragraphVariantCount(),
                after.paragraphVariantCount()
        );
    }

    private static Stats stats(Lag lag) {
        int chapterCount = 0;
        int paragraphGroupCount = 0;
        int paragraphVariantCount = 0;
        for (Avdelning avdelning : lag.get()) {
            for (Kapitel kapitel : avdelning.get()) {
                chapterCount++;
                Map<String, Integer> groups = new LinkedHashMap<>();
                for (Paragraf paragraf : kapitel.get()) {
                    groups.merge(paragraf.nummer(), 1, Integer::sum);
                    paragraphVariantCount++;
                }
                paragraphGroupCount += groups.size();
            }
        }
        return new Stats(chapterCount, paragraphGroupCount, paragraphVariantCount);
    }

    record Report(
            String effectiveDate,
            int selectedVariants,
            int droppedVariants,
            int unresolvedMarkers,
            int invalidMarkers,
            List<String> unresolvedParagraphs,
            List<String> invalidParagraphs,
            int chapterCountBefore,
            int chapterCountAfter,
            int paragraphGroupsBefore,
            int paragraphGroupsAfter,
            int paragraphVariantsBefore,
            int paragraphVariantsAfter
    ) {}

    private record Stats(
            int chapterCount,
            int paragraphGroupCount,
            int paragraphVariantCount
    ) {}
}
