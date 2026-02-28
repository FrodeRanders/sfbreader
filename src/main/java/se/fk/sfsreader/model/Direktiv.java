package se.fk.sfsreader.model;

public class Direktiv implements Layer {
    private final String direktiv;

    public Direktiv(String direktiv) {
        this.direktiv = direktiv;
    }

    public String direktiv() {
        return direktiv;
    }

    @Override
    public String toString() {
        return "Direktiv{" + direktiv + "}";
    }
}
