package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Stycke implements Layer {
    private final int nummer;
    private final Collection<String> referens = new ArrayList<>();

    private final Collection<String> text = new ArrayList<>();

    private final static String[] T = {};

    public Stycke(int nummer) {
        this.nummer = nummer;
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
        // Treat a single '.' specifically
        if (".".equals(s) && !text.isEmpty()) {
            int lastIndex = text.size() - 1;
            String updated = ((List<String>)text).get(lastIndex) + s;
            ((List<String>)text).set(lastIndex, updated);
        } else {
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
        text.removeIf(String::isEmpty);
    }

    public String toString() {
        return referens.stream()
                .map(r -> " referens=\"" + r + "\"")
                .collect(Collectors.joining("", "Stycke{nummer=" + nummer, "}"));
    }
}
