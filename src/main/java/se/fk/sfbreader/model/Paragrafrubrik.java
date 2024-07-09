package se.fk.sfbreader.model;

public class Paragrafrubrik extends Sektion implements Layer  {
    public Paragrafrubrik(String namn) {
        super(namn);
    }

    @Override
    public String toString() {
        return "Paragrafrubrik{\"" + namn() + "\"}";
    }
}
