package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

// Exempel: Socialförsäkringsbalk (2010:110)
public class Lag implements Layer {
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");
    private final String namn;

    private final String id;

    @SerializedName(value = "kapitel")
    private final Collection<Kapitel> kapitlen = new ArrayList<>();

    // Don't serialize
    private transient Collection<Avdelning> avdelningar = new ArrayList<>();
    private transient Avdelning aktuellAvdelning = null;

    public Lag(String namn, String id) {
        this.namn = namn;
        this.id = id;

        strukturLog.info("<html><meta charset=\"UTF-8\"><body>");
        strukturLog.info("<h1>Lag: " + namn + " (" + id + ")</h1>");
    }

    public String namn() {
        return namn;
    }

    public String id() {
        return id;
    }

    public void setAktuellAvdelning(Avdelning aa) {
        this.aktuellAvdelning = aa;
    }

    public void add(Avdelning a) {
        Objects.requireNonNull(a, "a");

        //
        a.assignAccessTo(kapitlen);
        avdelningar.add(a);
    }

    public Collection<Avdelning> get() {
        return avdelningar;
    }

    public void prune() {
        kapitlen.forEach(Kapitel::prune);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Lag{");
        buf.append("namn=\"").append(namn).append("\"");
        buf.append(" id=\"").append(id).append("\"");
        buf.append("}");
        return buf.toString();
    }
}
