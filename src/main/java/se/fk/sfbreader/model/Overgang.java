package se.fk.sfbreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Overgang extends Kapitel {
    private static final Logger log = LoggerFactory.getLogger(Overgang.class);

    private static int serienummer = 0;

    public Overgang(String namn) {
        this(namn, false);
    }

    public Overgang(String namn, boolean synthetic) {
        super("Ö" + Integer.toString(++serienummer), namn, synthetic);
    }

    public void addParagraf(Paragraf p) {
        Objects.requireNonNull(p, "p");

        paragrafer.add(p);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Overgang{");
        buf.append("nummer=").append(nummer);
        buf.append(" namn=\"").append(namn).append("\"");
        buf.append("}");
        return buf.toString();
    }
}
