package se.fk.sfbreader.model;

public class Kapitelrubrik extends Sektion implements Layer  {
    public Kapitelrubrik(String namn) {
        super(namn);
    }

    public String toString() {
        return "Kapitelrubrik{" + namn() + "}";
    }
}
