package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Avdelning implements Layer {
    private String id;
    private String namn;

    private final Collection<Kapitel> kapitel = new ArrayList<>();

    public Avdelning() {
    }

    public Avdelning(String id, String namn) {
        set(id, namn);
    }

    public void set(String id, String namn) {
        this.id = id;
        this.namn = namn;
    }
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    public Optional<String> namn() {
        return Optional.ofNullable(namn);
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
