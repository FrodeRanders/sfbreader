package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Paragraf implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Paragraf.class);

    private final String nummer;

    // Such as /Träder i kraft I:den dag som regeringen bestämmer/
    private String periodisering = null;

    @SerializedName(value = "stycke")
    private final Collection<Stycke> stycken = new ArrayList<>();

    private String rubrik = null;

    public Paragraf(String nummer) {
        this.nummer = nummer.trim();
    }

    public String nummer() {
        return nummer;
    }

    public void add(Stycke s) {
        stycken.add(s);
    }

    public void setPeriodisering(String periodisering) {
        this.periodisering = periodisering;
    }

    public Optional<String> getPeriodisering() {
        return Optional.ofNullable(periodisering);
    }

    public void setAktuellParagrafrubrik(Paragrafrubrik aktuellParagrafRubrik) {
        Objects.requireNonNull(aktuellParagrafRubrik, "aktuellParagrafRubrik");

        log.trace("Paragraf {}#rubrik <-- {}", nummer, aktuellParagrafRubrik);

        rubrik = aktuellParagrafRubrik.namn();
    }

    public boolean isEmpty() {
        if (stycken.isEmpty())
            return true;
        else {
            Optional<Stycke> s = stycken.stream().findFirst();
            return s.get().isEmpty();
        }
    }

    public Collection<Stycke> get() {
        return stycken;
    }

    public void prune() {
        Iterator<Stycke> it = stycken.iterator();
        while (it.hasNext()) {
            Stycke s = it.next();
            s.prune();
            if (s.get().isEmpty()) {
                log.trace("PRUNED: {}", s);
                it.remove();
            }
        }
    }

    public String toString() {
        StringBuilder buf = new StringBuilder("Paragraf{");
        buf.append("nummer=").append(nummer);
        if (null != rubrik && !rubrik.isEmpty()) {
            buf.append(" rubrik=\"").append(rubrik).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
