package se.fk.sfsreader;

import org.jsoup.Jsoup;
import org.junit.Test;
import se.fk.sfsreader.model.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.io.StringReader;
import java.util.*;
import static org.junit.Assert.*;

public class HybridSourceRepairTest {
    @Test public void restoresSgiSectionsAndHeadingsWithoutChangingSourceWording() throws Exception {
        DocumentSources sources = DocumentSources.from(Path.of("data/sfs-2010-110.txt.xml"), StandardCharsets.UTF_8);
        String text;
        try (var in = sources.openTextStream().orElseThrow()) { text = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        org.jsoup.nodes.Document doc;
        try (var in = sources.openHtmlStream().orElseThrow()) { doc = Jsoup.parse(in, "UTF-8", ""); }
        String before = TextStructure.normalize(doc.text());
        var report = new HybridSourceRepair().repair(doc, text);
        assertEquals("Repair must conserve all source wording", before, TextStructure.normalize(doc.text()));
        assertTrue(report.findings().stream().anyMatch(f -> f.status().equals("repaired") && "25".equals(f.chapter()) && "16".equals(f.paragraph())));
        assertTrue(report.findings().stream().anyMatch(f -> f.status().equals("repaired") && "25".equals(f.chapter()) && "26".equals(f.paragraph())));
        Lag html = new HtmlProcessor("SFB", "2010:110").process(doc).orElseThrow();
        for (var finding : report.findings()) {
            if (finding.status().equals("repaired") && finding.kind().equals("missing_section_anchor")) {
                var restored = chapter(html, finding.chapter()).get().stream()
                        .filter(p -> p.nummer().equals(finding.paragraph())).toList();
                assertEquals("Each recovered anchor must produce one section", 1, restored.size());
                assertFalse(body(restored.getFirst()).isBlank());
            }
        }
        Lag plain = new TextProcessor("SFB", "2010:110").process(new StringReader(text)).orElseThrow();
        for (Lag lag : List.of(html, plain)) {
            var sections = chapter(lag, "25").get();
            assertEquals(1, sections.stream().filter(p -> p.nummer().equals("2")).count());
            assertEquals(1, sections.stream().filter(p -> p.nummer().equals("16")).count());
            assertEquals(1, sections.stream().filter(p -> p.nummer().equals("26")).count());
            assertEquals("Om ersättning för arbete för någon annans räkning inte kan antas uppgå till minst 1 000 kronor under året, räknas ersättningen från denne som sjukpenninggrundande inkomst endast om ersättningen utgör inkomst av näringsverksamhet.", body(section(lag,"25","16")));
            assertEquals("Årsarbetstiden är det antal timmar eller dagar per år som en försäkrad tills vidare kan antas komma att ha som ordinarie arbetstid eller motsvarande normal arbetstid i sitt förvärvsarbete.", body(section(lag,"25","26")));
            assertFalse(body(section(lag,"25","25")).contains("26 §"));
            assertTrue(body(section(lag,"25","10")).contains("9 kap. 2 § skatteförfarandelagen"));
        }
        assertTrue(section(html,"25","16").rubriker().contains("Ersättning understigande 1 000 kronor"));
        assertTrue(section(html,"25","17").rubriker().contains("Semesterlön och semesterersättning"));
        var again = new HybridSourceRepair().repair(doc, text);
        assertTrue("Repairs must be idempotent", again.findings().isEmpty());
    }

    @Test public void ambiguousMatchesAreReportedAndLeftUntouched() {
        var doc = Jsoup.parse("<div><h3><a>25 kap. Test</a></h3>16 § Om något gäller.<p></p>16 § Om något gäller.</div>");
        String before = doc.outerHtml();
        var result = new HybridSourceRepair().repair(doc, "25 kap. Test\n16 § Om något gäller.");
        assertEquals(before, doc.outerHtml());
        assertEquals("unresolved", result.findings().getFirst().status());
    }

    @Test public void repairsMultipleSectionsInOneNodeAndKeepsWrappedReferences() {
        var doc = Jsoup.parse("<div><h3><a>25 kap. Test</a></h3><a class='paragraf' name='K25P25'><b>25 §</b></a> Första.\n26 § Andra.\n27 § Tredje.</div>");
        var report = new HybridSourceRepair().repair(doc, "25 kap. Test\n25 § Första.\n26 § Andra.\n27 § Tredje.");
        assertEquals(2, report.findings().size());
        Lag lag = new HtmlProcessor("Test", "2000:1").process(doc).orElseThrow();
        assertEquals("Första.", body(section(lag,"25","25")));
        assertEquals("Andra.", body(section(lag,"25","26")));
        assertEquals("Tredje.", body(section(lag,"25","27")));
    }

    @Test public void absentWordingIsNeverInvented() {
        var doc = Jsoup.parse("<div><h3><a>25 kap. Test</a></h3></div>");
        String before = doc.outerHtml();
        var report = new HybridSourceRepair().repair(doc, "25 kap. Test\n16 § Om något gäller.");
        assertEquals(before, doc.outerHtml());
        assertEquals("unresolved", report.findings().getFirst().status());
    }

    @Test public void chapterlessRepairUsesSimpleAnchors() {
        var doc = Jsoup.parse("<div><a class='paragraf' name='P1'><b>1 §</b></a> Första.\n2 § Andra.</div>");
        var report = new HybridSourceRepair().repair(doc, "1 § Första.\n2 § Andra.");
        assertEquals(1, report.findings().size());
        assertEquals(1, doc.select("a[name=P2]").size());
        Lag lag = new HtmlProcessor("Test", "2000:1").process(doc).orElseThrow();
        lag.prepareForSerialization();
        assertEquals(2, lag.getParagrafer().size());
    }

    @Test public void differentVersionsWithoutAnchorsNeedReview() {
        var doc = Jsoup.parse("<div><h3><a>25 kap. Test</a></h3>16 § Gammal.\n16 § Ny.</div>");
        var report = new HybridSourceRepair().repair(doc, "25 kap. Test\n16 § Gammal.\n16 § Ny.");
        assertEquals(2, report.findings().size());
        assertTrue(report.findings().stream().allMatch(f -> f.status().equals("unresolved")));
        assertTrue(doc.select("a.paragraf").isEmpty());
    }

    static Kapitel chapter(Lag lag, String id) {
        return lag.getKapitel().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
    static Paragraf section(Lag lag, String chapter, String number) {
        return chapter(lag,chapter).get().stream().filter(p -> p.nummer().equals(number)).findFirst().orElseThrow();
    }
    static String body(Paragraf p) {
        return TextStructure.normalize(p.get().stream().flatMap(s -> s.get().stream()).collect(java.util.stream.Collectors.joining(" ")));
    }
}
