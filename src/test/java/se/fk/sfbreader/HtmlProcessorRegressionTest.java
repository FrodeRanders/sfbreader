package se.fk.sfbreader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import se.fk.sfbreader.model.Avdelning;
import se.fk.sfbreader.model.Kapitel;
import se.fk.sfbreader.model.Lag;
import se.fk.sfbreader.model.Paragraf;
import se.fk.sfbreader.model.Stycke;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class HtmlProcessorRegressionTest {

    @Test
    public void inlineAvdelningInPreStartsNewStycke() {
        String html = """
                <html><body>
                <div>
                  <h2>AVD. A TESTAVDELNING</h2>
                  <h3 name="K6"><a name="K6">6 kap. Testkapitel</a></h3>
                  <a class="paragraf" name="K6P6"><b>6 §</b></a> Inledning.
                  <p><a name="K6P6S2"></a></p>
                  <pre>Avdelning E Förmåner vid ålderdom</pre>
                  <pre>8. inkomstgrundad ålderspension, (55-64 kap.)</pre>
                  <pre>8 a. inkomstpensionstillägg, (74 a kap.)</pre>
                  <pre>Avdelning F Förmåner till efterlevande</pre>
                  <pre>9. inkomstrelaterad efterlevandepension, (77 kap.)</pre>
                </div>
                </body></html>
                """;

        Document doc = Jsoup.parse(html, "http://nope.local");
        Lag lag = new HtmlProcessor("Testlag", "2000:1").process(doc).orElseThrow();

        Paragraf p6 = findParagraf(lag, "6", "6");
        assertNotNull(p6);

        List<Stycke> stycken = new ArrayList<>(p6.get());
        assertTrue("Expected at least 3 stycken", stycken.size() >= 3);

        List<String> secondText = new ArrayList<>(stycken.get(1).get());
        List<String> thirdText = new ArrayList<>(stycken.get(2).get());

        assertTrue(secondText.contains("Avdelning E Förmåner vid ålderdom"));
        assertTrue(secondText.stream().anyMatch(t -> t.startsWith("8.")));
        assertTrue(secondText.stream().anyMatch(t -> t.startsWith("8 a.")));

        assertTrue(thirdText.contains("Avdelning F Förmåner till efterlevande"));
        assertTrue(thirdText.stream().anyMatch(t -> t.startsWith("9.")));
    }

    private static Paragraf findParagraf(Lag lag, String kapitelId, String paragrafNummer) {
        for (Avdelning avdelning : lag.get()) {
            for (Kapitel kapitel : avdelning.get()) {
                if (!kapitelId.equals(kapitel.id())) {
                    continue;
                }
                for (Paragraf paragraf : kapitel.get()) {
                    if (paragrafNummer.equals(paragraf.nummer())) {
                        return paragraf;
                    }
                }
            }
        }
        return null;
    }
}
