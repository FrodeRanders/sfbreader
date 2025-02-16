package se.fk.sfbreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Underavdelning extends Sektion implements Layer  {
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");

    private String id;
    // name as part of Sektion

    public Underavdelning(String id, String namn) {
        super(namn);
        this.id = id;

        strukturLog.info(indent(3, "Underavdelning: " + id + " " + namn));
    }

    @Override
    public String toString() {
        return "Underavdelning{id=" + id + " namn=\"" + namn() + "\"}";
    }
}
