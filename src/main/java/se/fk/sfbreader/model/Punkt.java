package se.fk.sfbreader.model;

public class Punkt /* implements Layer  */ {
    private final int nummer;
    private final String id;
    private String text = "";

    public Punkt(int nummer, String id, String text) {
        this.nummer = nummer;
        this.id = id;
        this.text = text;
    }

    public void add(String s) {
        this.text += " " + s;
    }

    @Override
    public String toString() {
        return "Punkt{\"" + text + "\"}";
    }
}
