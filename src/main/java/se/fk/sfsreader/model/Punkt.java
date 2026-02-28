package se.fk.sfsreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Punkt /* implements Layer  */ {
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");

    private final int nummer;
    private final String id;
    private String text = "";

    public Punkt(int nummer, String id, String text) {
        this.nummer = nummer;
        this.id = id;
        this.text = text;

        strukturLog.trace("Punkt: " + nummer + " (\"" + id + "\")");
    }

    public void add(String s) {
        this.text += " " + s;
    }

    @Override
    public String toString() {
        return "Punkt{\"" + text + "\"}";
    }
}
