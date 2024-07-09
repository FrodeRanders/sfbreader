package se.fk.sfbreader.model;

public class Underavdelning extends Sektion implements Layer  {
    private String id;
    // name as part of Sektion

    public Underavdelning(String id, String namn) {
        super(namn);
        this.id = id;
    }

    @Override
    public String toString() {
        return "Underavdelning{id=" + id + " namn=\"" + namn() + "\"}";
    }
}
