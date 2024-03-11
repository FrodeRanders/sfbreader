package se.fk.sfbreader.model;

import java.util.ArrayList;
import java.util.Collection;

public class Referens implements Layer {
    private final String referens;

    public Referens(String referens) {
        this.referens = referens;
    }

    public String referens() {
        return referens;
    }

    public String toString() {
        return "Referens{" + referens + "}";
    }
}
