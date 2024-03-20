package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;

public class Kapitel implements Layer {

    private final String id;
    private final String namn;

    private final Collection<Paragraf> paragraf = new ArrayList<>();

    private String rubrik = null;

    private transient Rubrik subRubrik = null;

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

    public void addParagraf(Paragraf p) {
        if (null != subRubrik) {
            p.setRubrik(subRubrik);
        }
        paragraf.add(p);
    }

    public void setRubrik(Rubrik r) {
        rubrik = r.rubrik();
    }

    public void addSubRubrik(Rubrik r) {
        subRubrik = r;
    }

    public Collection<Paragraf> get() {
        return paragraf;
    }

    public void prune() {
        paragraf.forEach(Paragraf::prune);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder("Kapitel{");
        buf.append("id=").append(id);
        buf.append(" namn=\"").append(namn).append("\"");
        if (null != rubrik && !rubrik.isEmpty()) {
            buf.append(" rubrik=\"").append(rubrik).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
