package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Avdelning implements Layer {
    private String id;
    private String namn;

    private final Collection<Kapitel> kapitel = new ArrayList<>();

    private transient Rubrik subRubrik = null;

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

    public void addKapitel(Kapitel k) {
        if (null != subRubrik) {
            k.setRubrik(subRubrik);
        }
        kapitel.add(k);
    }

    public void addSubRubrik(Rubrik r) {
        subRubrik = r;
    }

    public Collection<Kapitel> get() {
        return kapitel;
    }

    public void prune() {
        kapitel.forEach(Kapitel::prune);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder("Avdelning{");
        buf.append("id=").append(id);
        buf.append(" namn=\"").append(namn).append("\"");
        buf.append("}");
        return buf.toString();
    }
}
