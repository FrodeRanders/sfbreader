package se.fk.sfsreader;

import org.junit.Test;
import se.fk.sfsreader.model.Avdelning;
import se.fk.sfsreader.model.Kapitel;
import se.fk.sfsreader.model.Lag;
import se.fk.sfsreader.model.Paragraf;
import se.fk.sfsreader.model.Stycke;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class PeriodiseringScheduleTest {

    @Test
    public void computesNextTransitionDateFromReferenceDate() {
        Lag lag = new Lag("Testlag", "2000:1");
        Avdelning avdelning = new Avdelning("A", "TEST");
        lag.add(avdelning);
        Kapitel kapitel = new Kapitel("1", "Rubrik");
        avdelning.addKapitel(kapitel);
        kapitel.addParagraf(paragraph("1", "x", "Upphör att gälla U:2028-07-01"));
        kapitel.addParagraf(paragraph("1", "y", "Träder i kraft I:2028-07-01"));
        kapitel.addParagraf(paragraph("2", "z", "Träder i kraft I:2030-01-01"));

        PeriodiseringSchedule.Report report = PeriodiseringSchedule.build(lag, LocalDate.parse("2028-06-30"));
        assertEquals("2028-07-01", report.nextTransitionDate());
        assertEquals(3, report.totalDatedTransitions());
        assertEquals(3, report.upcomingTransitions());

        PeriodiseringSchedule.Report report2 = PeriodiseringSchedule.build(lag, LocalDate.parse("2028-07-02"));
        assertEquals("2030-01-01", report2.nextTransitionDate());
        assertEquals(1, report2.upcomingTransitions());
    }

    private static Paragraf paragraph(String number, String body, String periodisering) {
        Paragraf p = new Paragraf(number);
        p.setPeriodisering(periodisering);
        Stycke s = new Stycke();
        s.add(body);
        p.add(s);
        return p;
    }
}
