package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

public class Paragraf implements Layer {
    private final String nummer;

    private final Collection<Stycke> stycke = new ArrayList<>();

    public Paragraf(String nummer) {
        this.nummer = nummer.trim();
    }

    public String nummer() {
        return nummer;
    }

    public void add(Stycke s) {
        stycke.add(s);
    }

    public boolean isEmpty() {
        if (stycke.isEmpty())
            return true;
        else {
            Optional<Stycke> s = stycke.stream().findFirst();
            return s.get().isEmpty();
        }
    }

    public Collection<Stycke> get() {
        return stycke;
    }

    public void prune() {
        Iterator<Stycke> it = stycke.iterator();
        while (it.hasNext()) {
            Stycke s = it.next();
            s.prune();
            if (s.get().isEmpty()) {
                System.out.println("PRUNED: " + s);
                it.remove();
            }
        }
    }

    public String toString() {
        return "Paragraf{nummer=" + nummer + "}";
    }
}
