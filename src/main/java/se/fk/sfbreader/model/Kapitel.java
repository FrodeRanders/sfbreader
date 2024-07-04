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

    @SerializedName(value = "paragraf")
    private final Collection<Paragraf> paragrafer = new ArrayList<>();

    private String avdelning; // only for serialization purposes!!!
    private String underavdelning; // only for serialization purposes!!!
    private String rubrik; // only for serialization purposes!!!

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

        Optional<String> id = aktuellAvdelning.id();
        avdelning = "";
        id.ifPresent(s -> avdelning += s + " ");
        avdelning += aktuellAvdelning.namn();
    }

    public void setAktuellUnderavdelning(Underavdelning aktuellUnderavdelning) {
        Objects.requireNonNull(aktuellUnderavdelning, "aktuellUnderavdelning");

        log.trace("Kapitel {}#underavdelning <-- {}", nummer, aktuellUnderavdelning);
        underavdelning = aktuellUnderavdelning.namn();
    }


    public void setAktuellKapitelrubrik(Kapitelrubrik aktuellKapitelrubrik) {
        Objects.requireNonNull(aktuellKapitelrubrik, "aktuellKapitelrubrik");

        log.trace("Kapitel {}#rubrik <-- {}", nummer, aktuellKapitelrubrik);
        rubrik = aktuellKapitelrubrik.namn();
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

    public String toString() {
        StringBuilder buf = new StringBuilder("Kapitel{");
        buf.append("nummer=").append(nummer);
        buf.append(" namn=\"").append(namn).append("\"");
        if (null != avdelning && !avdelning.isEmpty()) {
            buf.append(" avdelning=\"").append(avdelning).append("\"");
        }
        if (null != underavdelning) {
            buf.append(" underavdelning=\"").append(underavdelning).append("\"");
        }
        if (null != rubrik) {
            buf.append(" rubrik=\"").append(rubrik).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
