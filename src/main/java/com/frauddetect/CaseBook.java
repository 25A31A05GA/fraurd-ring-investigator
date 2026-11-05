package com.frauddetect;

import java.util.*;

/** Holds alerts and cases, de-duplicates detections and merges overlapping ones into a single case. */
public final class CaseBook {
    public final List<Alert> alerts = new ArrayList<>();
    public final List<FraudCase> cases = new ArrayList<>();
    private final Set<String> seenKeys = new HashSet<>();
    private final World world;

    public CaseBook(World world) { this.world = world; }

    /** @return true if this was a new detection (not seen before under {@code key}). */
    public boolean raise(PatternType type, String key, Set<String> entities, String text, Collection<String> victims) {
        if (!seenKeys.add(key)) return false;
        Alert al = new Alert("DET-" + (alerts.size() + 1), type, entities, text, world.t);
        alerts.add(al);

        FraudCase c = null;
        for (FraudCase x : cases) {
            for (String e : entities) if (x.entities.contains(e)) { c = x; break; }
            if (c != null) break;
        }
        if (c == null) {
            c = new FraudCase(String.format("CASE-%03d", cases.size() + 1));
            cases.add(c);
            c.audit.add("System opened the case after a " + type.label.toLowerCase() + " detection");
        }
        c.entities.addAll(entities);
        if (victims != null) c.victims.addAll(victims);
        c.patterns.put(type, text);
        for (String e : c.entities) {
            Entity en = world.ent.get(e);
            if (en != null) { c.title = "Suspected mule network: " + en.name; break; }
        }
        c.rescore();
        c.audit.add("Detection " + al.id() + " added: " + type.label + " (score now " + c.score + ")");
        return true;
    }

    public int riskOf(String entityId) {
        int m = 0;
        for (FraudCase c : cases) if (c.entities.contains(entityId)) m = Math.max(m, c.score);
        return m;
    }

    public Optional<FraudCase> byId(String id) {
        return cases.stream().filter(c -> c.id.equals(id)).findFirst();
    }
}
