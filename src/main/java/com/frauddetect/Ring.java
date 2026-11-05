package com.frauddetect;

import java.util.List;

/** Ground truth: a planted mule ring or device farm. The detection engine is never told about these. */
public final class Ring {
    public final String id;
    public final String kind;           // "RING" or "FARM"
    public final int plantedAt;
    public final List<String> mules;
    public boolean found;
    public int lagSeconds;

    Ring(String id, String kind, int plantedAt, List<String> mules) {
        this.id = id; this.kind = kind; this.plantedAt = plantedAt; this.mules = mules;
    }
}
