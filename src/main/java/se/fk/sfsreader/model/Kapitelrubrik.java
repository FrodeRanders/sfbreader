package se.fk.sfsreader.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kapitelrubrik extends Sektion implements Layer  {
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");

    public Kapitelrubrik(String namn) {
        super(namn);

        strukturLog.info("Kapitelrubrik: " + namn);
    }

    @Override
    public String toString() {
        return "Kapitelrubrik{\"" + namn() + "\"}";
    }
}
