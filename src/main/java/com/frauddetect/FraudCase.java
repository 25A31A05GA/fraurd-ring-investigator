package com.frauddetect;

import java.util.*;

/** An investigation case. Detections that touch the same entities are merged into one case. */
public final class FraudCase {
    public enum Status { NEW, UNDER_INVESTIGATION, NEEDS_REVIEW, RESOLVED, FALSE_POSITIVE }

    public final String id;
    public String title = "";
    public final Set<String> entities = new LinkedHashSet<>();
    public final Set<String> victims = new LinkedHashSet<>();
    public final Map<PatternType, String> patterns = new EnumMap<>(PatternType.class);
    public final List<String> notes = new ArrayList<>();
    public final List<String> audit = new ArrayList<>();
    public Status status = Status.NEW;
    public int score;

    FraudCase(String id) { this.id = id; }

    /** Risk score = sum of the weights of the distinct patterns seen, capped at 100. */
    void rescore() {
        int s = 0;
        for (PatternType p : patterns.keySet()) s += p.weight;
        score = Math.min(100, s);
    }

    public String level() { return score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW"; }

    public void setStatus(Status s) {
        if (s != status) {
            audit.add("Investigator changed status " + status + " -> " + s);
            status = s;
        }
    }

    public void addNote(String note) {
        notes.add(note);
        audit.add("Investigator added a note");
    }
}
