package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;

// Exempel: Socialförsäkringsbalk (2010:110)
public class Lag implements Layer {
    private final String namn;

    private final String id;

    private final Collection<Avdelning> avdelning = new ArrayList<>();

    public Lag(String namn, String id) {
        this.namn = namn;
        this.id = id;
    }

    public String namn() {
        return namn;
    }

    public String id() {
        return id;
    }

    public void add(Avdelning a) {
        avdelning.add(a);
    }

    public Collection<Avdelning> get() {
        return avdelning;
    }

    public void prune() {
        avdelning.forEach(Avdelning::prune);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder("Lag{");
        buf.append("namn=\"").append(namn).append("\"");
        buf.append(" id=\"").append(id).append("\"");
        buf.append("}");
        return buf.toString();

    }
}
