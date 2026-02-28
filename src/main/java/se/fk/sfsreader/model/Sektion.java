package se.fk.sfsreader.model;

public abstract class Sektion implements Layer {
    private final String namn;

    public Sektion(String namn) {
        this.namn = namn;
    }

    public String namn() {
        return namn;
    }

    @Override
    public String toString() {
        return "Sektion{\"" + namn + "\"}";
    }
}
