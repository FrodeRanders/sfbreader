package se.fk.sfbreader.model;

public interface Layer {

    default String type() {
        return getClass().getSimpleName();
    }

    default void prune() {
    }
}
