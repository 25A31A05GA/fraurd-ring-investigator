package com.frauddetect;

/**
 * Recall / precision against the planted ground truth.
 * A planted group only counts as "due" once it is at least {@link #GRACE_SECONDS} old, so a ring planted
 * one second before the measurement is not (unfairly) counted as a miss.
 */
public record Performance(int planted, int caught, int cases, int falseAlarms, double recall, double precision, double avgLagSeconds) {

    public static final int GRACE_SECONDS = 30;

    public static Performance evaluate(World w, CaseBook book) {
        int planted = 0, caught = 0;
        double lag = 0;
        for (Ring r : w.rings) {
            if (r.found) { caught++; lag += r.lagSeconds; }
            if (r.found || w.t - r.plantedAt >= GRACE_SECONDS) planted++;
        }
        int bad = 0;
        for (FraudCase c : book.cases) {
            boolean real = false;
            for (Ring r : w.rings) for (String m : r.mules) if (c.entities.contains(m)) real = true;
            if (!real) bad++;
        }
        int total = book.cases.size();
        double recall = planted == 0 ? 0 : (double) caught / planted;
        double precision = total == 0 ? 0 : (double) (total - bad) / total;
        return new Performance(planted, caught, total, bad, recall, precision, caught == 0 ? 0 : lag / caught);
    }
}
