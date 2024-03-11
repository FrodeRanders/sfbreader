package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;

public class Kapitel implements Layer {

    private final String id;
    private final String namn;

    private final Collection<Paragraf> paragraf = new ArrayList<>();

    public Kapitel(String id, String namn) {
        this.id = id;
        this.namn = namn;
    }

    public String id() {
        return id;
    }

    public String namn() {
        return namn;
    }

    public void add(Paragraf p) {
        paragraf.add(p);
    }

    public Collection<Paragraf> get() {
        return paragraf;
    }

    public void prune() {
        paragraf.forEach(Paragraf::prune);
    }

    public String toString() {
        return "Kapitel{id=" + id + " namn=\"" + namn + "\"}";
    }
}
