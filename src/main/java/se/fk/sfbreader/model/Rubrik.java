package se.fk.sfbreader.model;

public class Rubrik implements Layer {
    private final String rubrik;

    public Rubrik(String rubrik) {
        this.rubrik = rubrik;
    }

    public String rubrik() {
        return rubrik;
    }

    public String toString() {
        return "Rubrik{" + rubrik + "}";
    }
}
