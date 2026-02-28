package se.fk.sfsreader;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class PeriodiseringMarkerTest {

    @Test
    public void parsesValidDatedIkraft() {
        PeriodiseringMarker.Parsed parsed = PeriodiseringMarker.parse("Träder i kraft I:2028-07-01");
        assertEquals(PeriodiseringMarker.Status.VALID_DATED, parsed.status());
        assertEquals(PeriodiseringMarker.Kind.IKRAFT, parsed.kind());
        assertTrue(parsed.isActiveOn(LocalDate.parse("2028-07-01")).orElse(false));
        assertFalse(parsed.isActiveOn(LocalDate.parse("2028-06-30")).orElse(true));
    }

    @Test
    public void detectsInvalidVerbCodeCombination() {
        PeriodiseringMarker.Parsed parsed = PeriodiseringMarker.parse("Träder i kraft U:2028-07-01");
        assertTrue(parsed.isInvalid());
    }

    @Test
    public void classifiesRelativeMarkersAsUnresolved() {
        PeriodiseringMarker.Parsed parsed = PeriodiseringMarker.parse("Upphör att gälla U:den dag regeringen bestämmer");
        assertTrue(parsed.isUnresolved());
        assertTrue(parsed.isActiveOn(LocalDate.parse("2030-01-01")).isEmpty());
    }
}
