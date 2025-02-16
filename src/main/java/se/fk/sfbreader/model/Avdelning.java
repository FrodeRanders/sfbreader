package se.fk.sfbreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class Avdelning extends Sektion implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Avdelning.class);
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");

    private String id;
    // name as part of Sektion

    private transient Collection<Kapitel> kapitlen = null;
    private transient Underavdelning aktuellUnderavdelning = null;

    public Avdelning(String id, String namn) {
        super(namn);
        this.id = id;

        strukturLog.info(indent(2, "Avdelning: " + id + " " + namn));
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

        // ... and Underavdelning (possibly several within one Avdelning)
        if (null != aktuellUnderavdelning) {
            k.setAktuellUnderavdelning(aktuellUnderavdelning);
        }

        // Accumulate to collection of Kapitel
        kapitlen.add(k);
    }

    public void setAktuellUnderavdelning(Underavdelning aktuellUnderavdelning) {
        log.trace("Avdelning {} <-- {}", id, aktuellUnderavdelning);
        this.aktuellUnderavdelning = aktuellUnderavdelning;
    }

    public Collection<Kapitel> get() {
        return kapitlen;
    }

    public void prune() {
        kapitlen.forEach(Kapitel::prune);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Avdelning{");
        buf.append("id=").append(id);
        buf.append(" namn=\"").append(namn()).append("\"");
        // if (null != aktuellUnderavdelning) {
        //    buf.append(" ").append(aktuellUnderavdelning);
        // }
        buf.append("}");
        return buf.toString();
    }
}
