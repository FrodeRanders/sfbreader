package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

public class Paragraf implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Paragraf.class);

    private final String nummer;

    private final Collection<Stycke> stycke = new ArrayList<>();

    @SerializedName(value = "rubrik", alternate = "paragrafRubrik")
    private String rubrik = null;

    public Paragraf(String nummer) {
        this.nummer = nummer.trim();
    }

    public String nummer() {
        return nummer;
    }

    public void add(Stycke s) {
        stycke.add(s);
    }

    public void setRubrik(Rubrik r) {
        rubrik = r.rubrik();
    }

    public boolean isEmpty() {
        if (stycke.isEmpty())
            return true;
        else {
            Optional<Stycke> s = stycke.stream().findFirst();
            return s.get().isEmpty();
        }
    }

    public Collection<Stycke> get() {
        return stycke;
    }

    public void prune() {
        Iterator<Stycke> it = stycke.iterator();
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
