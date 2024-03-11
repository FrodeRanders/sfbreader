package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;

public class Avdelning implements Layer {
    private final String id;
    private final String namn;

    private final Collection<Kapitel> kapitel = new ArrayList<>();

    public Avdelning(String id, String namn) {
        this.id = id;
        this.namn = namn;
    }

    public String id() {
        return id;
    }

    public String namn() {
        return namn;
    }

    public void add(Kapitel k) {
        kapitel.add(k);
    }

    public Collection<Kapitel> get() {
        return kapitel;
    }

    public void prune() {
        kapitel.forEach(Kapitel::prune);
    }

    public String toString() {
        return "Avdelning{id=" + id + " namn=\"" + namn + "\"}";
    }
}
