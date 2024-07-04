package se.fk.sfbreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class Avdelning extends Sektion implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Avdelning.class);

    private String id;

    private Lag lag = null;

    private transient Collection<Kapitel> kapitlen = null;

    private transient Underavdelning aktuellUnderavdelning = null;
    private String underavdelning; // only for serialization purposes!!!

    public Avdelning(String id, String namn) {
        super(namn);
        this.id = id;
    }

    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    public void assignAccessTo(Collection<Kapitel> kapitlen) {
        Objects.requireNonNull(kapitlen, "kapitlen");

        this.kapitlen = kapitlen;
    }

    public void addKapitel(Kapitel k) {
        Objects.requireNonNull(kapitlen, "kapitlen");

        // Kapitel will know it's position within the context of Avdelning
        k.setAktuellAvdelning(this);

        // ... and Underavdelning (possibly several within Avdelning)
        if (null != aktuellUnderavdelning) {
            k.setAktuellUnderavdelning(aktuellUnderavdelning);
        }

        // Accumulate to collection of Kapitel
        kapitlen.add(k);
    }

    public void setAktuellUnderavdelning(Underavdelning au) {
        log.trace("Avdelning {} <-- {}", id, au);
        aktuellUnderavdelning = au;
        underavdelning = aktuellUnderavdelning.namn();
    }

    public Collection<Kapitel> get() {
        return kapitlen;
    }

    public void prune() {
        kapitlen.forEach(Kapitel::prune);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder("Avdelning{");
        buf.append("id=").append(id);
        buf.append(" namn=\"").append(namn()).append("\"");
        if (null != underavdelning && !underavdelning.isEmpty()) {
            buf.append(" underavdelning=\"").append(underavdelning).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
