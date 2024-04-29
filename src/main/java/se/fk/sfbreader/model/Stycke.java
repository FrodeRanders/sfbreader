package se.fk.sfbreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Stycke implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Stycke.class);

    private static final String IS_ITEMIZED_RE = "^(-\\s|\\d+\\.\\s|[a-z]\\.\\s).+";

    private final int nummer;
    private final Collection<String> referens = new ArrayList<>();

    private final Collection<String> text = new ArrayList<>();

    //@Expose(serialize = false, deserialize = false)
    private transient boolean itemized;

    //@Expose(serialize = false, deserialize = false)
    private transient final Collection<String> textOnHold = new ArrayList<>();


    private final static String[] T = {};

    private Stycke(int nummer) {
        this.nummer = nummer;
        this.itemized = false;
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
                boolean adding_itemized = s.matches(IS_ITEMIZED_RE);
                itemized |= adding_itemized;

                Character c = s.charAt(0);

                if (/* already containes itemized entries? */ itemized && !adding_itemized
                        && /* is letter? */ Character.isLetter(c)
                        && /* is uppercase (letter)? */ 0 == c.compareTo(Character.toUpperCase(c))) {
                    textOnHold.add(s);
                    return;
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
        text.addAll(textOnHold);
        textOnHold.clear();

        text.removeIf(String::isEmpty);
    }

    public String toString() {
        return referens.stream()
                .map(r -> " referens=\"" + r + "\"")
                .collect(Collectors.joining("", "Stycke{nummer=" + nummer, "}"));
    }
}
