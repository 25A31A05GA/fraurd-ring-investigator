package com.frauddetect;

/** The four graph/behaviour patterns the engine looks for, with the points each adds to a case's risk score. */
public enum PatternType {
    SHARED_DEVICE("Shared device network", 35),
    RAPID_CASHOUT("Rapid cash-out", 25),
    CIRCULAR_TRANSFER("Circular transfers", 25),
    FAN_IN("Fan-in pattern", 15);

    public final String label;
    public final int weight;

    PatternType(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }
}
