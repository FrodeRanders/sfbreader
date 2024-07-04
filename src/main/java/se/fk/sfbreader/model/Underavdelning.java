package se.fk.sfbreader.model;

public class Underavdelning extends Sektion implements Layer  {
    public Underavdelning(String namn) {
        super(namn);
    }

    public String toString() {
        return "Underavdelning{" + namn() + "}";
    }
}
