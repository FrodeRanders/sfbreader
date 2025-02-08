package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Stycke implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Stycke.class);
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");

    //String ROMAN_NUMERAL_RE_LOWER = "^m{0,3}(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$";

    // Cf. se.fk.sfbreader.LatexProcessor NEEDS_EXTRA_SPACING_RE
    private static final String IS_ITEMIZED_RE = "^(?<id>(-(?=\\s)|\\d+(\\s?[a-z])?(?=\\.\\s)|[a-z](?=\\.\\s))).+";
    private static final Pattern itemIdPattern = Pattern.compile(IS_ITEMIZED_RE);

    private final int nummer;

    // Such as "/Träder i kraft I:den dag som regeringen bestämmer/"
    private String periodisering = null;

    private final Collection<String> referens = new ArrayList<>();

    private final Collection<String> text = new ArrayList<>();

    //@Expose(serialize = false, deserialize = false)
    private transient boolean isItemized;

    //@Expose(serialize = false, deserialize = false)
    private transient final List<String> textOnHold = new ArrayList<>();

    @SerializedName(value = "punkt")
    private final List<Punkt> punkter = new ArrayList<>();

    private final static String[] T = {};

    private Stycke(int nummer) {
        this.nummer = nummer;
        this.isItemized = false;

        strukturLog.debug("Stycke: {}", nummer);
    }

    public Stycke() {
        this(1);
    }

    public Stycke(Stycke previous) {
        this(previous.nummer + 1);

        // merge
        if (!previous.textOnHold.isEmpty()) {
            log.info("Merging " + previous.textOnHold.size() + " text on hold from part " + previous.nummer + " into part " + this.nummer);
            text.addAll(previous.textOnHold);
            previous.textOnHold.clear();
        }
    }

    public Stycke(int nummer, String t) {
        this(nummer);
        text.add(t);
    }

    public void setPeriodisering(String periodisering) {
        this.periodisering = periodisering;
    }

    public Optional<String> getPeriodisering() {
        return Optional.ofNullable(periodisering);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public int nummer() {
        return nummer;
    }

    public Collection<String> referens() {
        return referens;
    }

    public void add(String s) {
        if (null == s)
            return;

        // Treat a single '.' specifically
        if (".".equals(s) && !text.isEmpty()) {
            int lastIndex = text.size() - 1;
            String updated = ((List<String>)text).get(lastIndex) + s;
            ((List<String>)text).set(lastIndex, updated);
        } else {
            if (!s.isEmpty()) {
                final boolean alreadyItemized = isItemized; // since we mutate isItemized next

                Matcher matcher = itemIdPattern.matcher(s);
                final boolean adding_itemized = matcher.matches();
                isItemized |= adding_itemized;

                String id = "";
                if (adding_itemized) {
                    id = matcher.group("id");
                }

                /* Explanation of example from K5P9 SFB.

                   [
                       "Avdelning C Förmåner vid sjukdom eller arbetsskada",
                       "6. sjukpenning i särskilda fall, (28 a kap.)",
                       "7. rehabilitering, bidrag till arbetshjälpmedel, särskilt bidrag och rehabiliteringspenning i",
                       "särskilda fall, (29-31 a kap.)",
                       "8. sjukersättning och aktivitetsersättning i form av",
                       "garantiersättning, (33 och 35-37 kap.)"
                   ]

                   In this example you see that punkt 7. and 8. is broken up, so we want to re-assemble them
                   into correct items (Punkter).

                   Also, we may actually run into text from the next part (Stycke), such
                   as (building on this example) "Avdelning D Särskilda förmåner vid funktionshinder".
                   In that case we can't add the text to this Stycke, so we will keep it "on hold"
                   for the next Stycke.
                */

                // An item (Punkt) in this part (Stycke), such as "6. sjukpenning i särskilda fall, (28 a kap.)",
                // or even "7. rehabilitering, bidrag till arbetshjälpmedel, särskilt bidrag och rehabiliteringspenning i".
                if (adding_itemized) {
                    punkter.add(new Punkt(punkter.size() + 1, id.trim(), s));
                }

                // In case we encounter (building on the example) "Avdelning D Särskilda förmåner vid funktionshinder"
                // then we will keep the text on hold -- it may actually be part of next Stycke
                Character c = s.charAt(0);
                if (/* already contains itemized entries? */ isItemized && !adding_itemized
                        && /* is letter? */ Character.isLetter(c)
                        && /* is uppercase (letter)? */ 0 == c.compareTo(Character.toUpperCase(c))) {
                    textOnHold.add(s);
                    return;
                }

                // Reassemble item (Punkt) if it was broken up item
                if (alreadyItemized && !adding_itemized) {
                    if (!punkter.isEmpty()) {
                        Punkt lastPunkt = punkter.getLast();
                        lastPunkt.add(s);
                    }
                }
            }
            text.add(s);
        }
    }

    public void add(Referens r) {
        referens.add(r.referens());
        text.add(r.referens());
    }

    public Collection<String> get() {
        return text;
    }

    public void prune() {
        if (!textOnHold.isEmpty()) {
            log.warn("Text on hold in part " + this.nummer);
            text.addAll(textOnHold);
            textOnHold.clear();
        }
        text.removeIf(String::isEmpty);
    }

    @Override
    public String toString() {
        return referens.stream()
                .map(r -> " referens=\"" + r + "\"")
                .collect(Collectors.joining("", "Stycke{nummer=" + nummer, "}"));
    }
}
