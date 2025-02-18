package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class Paragraf implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Paragraf.class);

    private final String nummer;

    // Such as /Träder i kraft I:den dag som regeringen bestämmer/
    private String periodisering = null;

    @SerializedName(value = "stycke")
    private final Collection<Stycke> stycken = new ArrayList<>();

    private String rubrik = null;
    private String underrubrik = null;

    private final Collection<String> referens = new ArrayList<>();

    public Paragraf(String nummer) {
        this.nummer = nummer.trim();
    }

    public String nummer() {
        return nummer;
    }

    public String rubriker() {
        /*
        if (null == underrubrik || underrubrik.isEmpty()) {
            return "";
        }
        */

        StringBuilder buf = new StringBuilder();
        if (null != rubrik && !rubrik.isEmpty()) {
            buf.append("rubrik=\"").append(rubrik).append("\"");
        }
        if (null != underrubrik && !underrubrik.isEmpty()) {
            buf.append(" underrubrik=\"").append(underrubrik).append("\"");
        }
        return buf.toString();
    }

    public void add(Stycke s) {
        stycken.add(s);
    }

    public void add(Referens r) {
        referens.add(r.referens());
    }

    public void setPeriodisering(String periodisering) {
        this.periodisering = periodisering;
    }

    public Optional<String> getPeriodisering() {
        return Optional.ofNullable(periodisering);
    }

    public void setParagrafrubriker(List<Paragrafrubrik> paragrafrubriker) {
        Objects.requireNonNull(paragrafrubriker, "paragrafrubriker");
        assert paragrafrubriker.size() <= 2;

        if (!paragrafrubriker.isEmpty()) {
            if (paragrafrubriker.size() == 1) {
                log.trace("Paragraf {} # rubrik <-- {}", nummer, paragrafrubriker.getFirst().namn());
            }
            else {
                log.trace("Paragraf {} # rubrik <-- {} / {}", nummer, paragrafrubriker.getFirst().namn(), paragrafrubriker.getLast().namn());
            }
        }

        // TODO Rework
        int antalRubriker = paragrafrubriker.size();
        if (antalRubriker-- > 0) {
            rubrik = paragrafrubriker.getFirst().namn();
        }
        if (antalRubriker > 0) {
            underrubrik = paragrafrubriker.getLast().namn();
        }
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

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Paragraf{");
        buf.append("nummer=").append(nummer);
        if (null != rubrik && !rubrik.isEmpty()) {
            buf.append(" rubrik=\"").append(rubrik).append("\"");
        }
        if (null != underrubrik && !underrubrik.isEmpty()) {
            buf.append(" underrubrik=\"").append(underrubrik).append("\"");
        }
        for (String ref : referens) {
            buf.append(" referens=\"").append(ref).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
