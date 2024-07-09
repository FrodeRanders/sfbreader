package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class Kapitel implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Kapitel.class);

    private final String nummer;
    private final String namn;

    // Such as /Träder i kraft I:den dag som regeringen bestämmer/
    private String periodisering = null;

    @SerializedName(value = "paragraf")
    private final Collection<Paragraf> paragrafer = new ArrayList<>();

    @SerializedName(value = "avdelning")
    private Avdelning aktuellAvdelning = null;

    @SerializedName(value = "underavdelning")
    private Underavdelning aktuellUnderavdelning = null;

    @SerializedName(value = "rubrik")
    private String aktuellKapitelrubrik = null;

    private transient Paragrafrubrik aktuellParagrafrubrik = null;

    public Kapitel(String nummer, String namn) {
        this.nummer = nummer;
        this.namn = namn;
    }

    public String id() {
        return nummer;
    }

    public String namn() {
        return namn;
    }

    public void setPeriodisering(String periodisering) {
        this.periodisering = periodisering;
    }

    public Optional<String> getPeriodisering() {
        return Optional.ofNullable(periodisering);
    }

    public void addParagraf(Paragraf p) {
        Objects.requireNonNull(p, "p");

        if (null != aktuellParagrafrubrik) {
            p.setAktuellParagrafrubrik(aktuellParagrafrubrik);
        }

        paragrafer.add(p);
    }

    public void setAktuellAvdelning(Avdelning aktuellAvdelning) {
        Objects.requireNonNull(aktuellAvdelning, "aktuellAvdelning");

        log.trace("Kapitel {}#avdelning <-- {}", nummer, aktuellAvdelning);
        this.aktuellAvdelning = aktuellAvdelning;
    }

    public void setAktuellUnderavdelning(Underavdelning aktuellUnderavdelning) {
        Objects.requireNonNull(aktuellUnderavdelning, "aktuellUnderavdelning");

        log.trace("Kapitel {}#underavdelning <-- {}", nummer, aktuellUnderavdelning);
        this.aktuellUnderavdelning = aktuellUnderavdelning;
    }

    public void setAktuellKapitelrubrik(Kapitelrubrik aktuellKapitelrubrik) {
        Objects.requireNonNull(aktuellKapitelrubrik, "aktuellKapitelrubrik");

        log.trace("Kapitel {}#rubrik <-- {}", nummer, aktuellKapitelrubrik);
        this.aktuellKapitelrubrik = aktuellKapitelrubrik.namn();
    }

    public void setAktuellParagrafrubrik(Paragrafrubrik aktuellParagrafrubrik) {
        Objects.requireNonNull(aktuellParagrafrubrik, "aktuellParagrafrubrik");

        log.trace("Kapitel {}#paragrafrubrik <-- {}", nummer, aktuellParagrafrubrik);
        this.aktuellParagrafrubrik = aktuellParagrafrubrik;
    }

    public Collection<Paragraf> get() {
        return paragrafer;
    }

    public void prune() {
        paragrafer.forEach(Paragraf::prune);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Kapitel{");
        buf.append("nummer=").append(nummer);
        buf.append(" namn=\"").append(namn).append("\"");
        if (null != aktuellAvdelning) {
            buf.append(" ").append(aktuellAvdelning);
        }
        if (null != aktuellUnderavdelning) {
            buf.append(" ").append(aktuellUnderavdelning);
        }
        if (null != aktuellKapitelrubrik && !aktuellKapitelrubrik.isEmpty()) {
            buf.append(" rubrik=\"").append(aktuellKapitelrubrik).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
