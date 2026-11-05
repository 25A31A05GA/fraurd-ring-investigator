package com.frauddetect;

import java.util.*;

/**
 * Rule + graph detection over the live transaction stream.
 * <ul>
 *   <li>SHARED_DEVICE: >=3 accounts send money from one phone AND >=2 distinct surnames (families are ignored)</li>
 *   <li>FAN_IN: >=4 different people pay one account, total >= 50,000, average payment >= 8,000, within 60s</li>
 *   <li>RAPID_CASHOUT: >=80% of an inflow >= 50,000 leaves within 15s</li>
 *   <li>CIRCULAR_TRANSFER: money returns to its origin through 3-4 hops (DFS cycle search, transfers >= 10,000)</li>
 * </ul>
 * Payments to merchants never count as person-to-person flow, so normal shopping is ignored.
 */
public final class DetectionEngine {
    public static final int FAN_IN_MIN_SENDERS = 4;
    public static final long FAN_IN_MIN_TOTAL = 50_000;
    /** Added after benchmarking: many small ordinary payments can sum to 50k in a busy minute; mule inflows are large tickets. */
    public static final long FAN_IN_MIN_AVG_TICKET = 8_000;
    public static final long CASHOUT_MIN_INFLOW = 50_000;
    public static final double CASHOUT_RATIO = 0.8;
    public static final long CYCLE_MIN_AMOUNT = 10_000;

    private final World w;
    private final CaseBook book;

    public DetectionEngine(World w, CaseBook book) { this.w = w; this.book = book; }

    public void run() {
        sharedDevices();
        fanInAndCashout();
        cycles();
        markFoundRings();
    }

    private static String inr(long n) { return String.format("INR %,d", n); }

    private void sharedDevices() {
        for (Map.Entry<String, Set<String>> e : w.deviceUse.entrySet()) {
            List<String> accts = new ArrayList<>();
            for (String a : e.getValue()) if (w.isPerson(a)) accts.add(a);
            if (accts.size() < 3) continue;
            Set<String> surnames = new HashSet<>();
            for (String a : accts) surnames.add(w.ent.get(a).lastName);
            if (surnames.size() < 2) continue;                 // one family on one phone: normal
            Set<String> ents = new LinkedHashSet<>();
            ents.add(e.getKey());
            ents.addAll(accts);
            StringBuilder names = new StringBuilder();
            for (String a : accts) names.append(names.length() == 0 ? "" : ", ").append(w.name(a));
            book.raise(PatternType.SHARED_DEVICE, "SD:" + e.getKey(), ents,
                    accts.size() + " unrelated accounts (" + names + ") all send money from one phone, a " + w.name(e.getKey()) + ".", null);
        }
    }

    private void fanInAndCashout() {
        Map<String, List<Tx>> in = new LinkedHashMap<>(), out = new LinkedHashMap<>();
        for (int i = w.tx.size() - 1; i >= 0; i--) {
            Tx x = w.tx.get(i);
            if (x.t() < w.t - 60) break;
            in.computeIfAbsent(x.to(), k -> new ArrayList<>()).add(x);
            out.computeIfAbsent(x.from(), k -> new ArrayList<>()).add(x);
        }
        for (Map.Entry<String, List<Tx>> en : in.entrySet()) {
            String a = en.getKey();
            if (!w.isPerson(a)) continue;
            List<Tx> inflow = en.getValue();
            long total = 0;
            Set<String> senders = new LinkedHashSet<>();
            int last = Integer.MIN_VALUE;
            for (Tx x : inflow) {
                total += x.amount();
                if (w.isPerson(x.from())) senders.add(x.from());
                last = Math.max(last, x.t());
            }
            if (senders.size() >= FAN_IN_MIN_SENDERS && total >= FAN_IN_MIN_TOTAL && total / inflow.size() >= FAN_IN_MIN_AVG_TICKET) {
                book.raise(PatternType.FAN_IN, "FI:" + a, Set.of(a),
                        senders.size() + " different people paid " + w.name(a) + " a combined " + inr(total) + " within a minute.", senders);
            }
            long in30 = 0, outTotal = 0;
            for (Tx x : inflow) if (x.t() >= last - 30) in30 += x.amount();
            for (Tx x : out.getOrDefault(a, List.of())) if (x.t() >= last - 1 && x.t() <= last + 15) outTotal += x.amount();
            if (in30 >= CASHOUT_MIN_INFLOW && outTotal >= CASHOUT_RATIO * in30) {
                book.raise(PatternType.RAPID_CASHOUT, "RC:" + a, Set.of(a),
                        w.name(a) + " received " + inr(in30) + " and moved " + inr(outTotal) + " out within seconds.", null);
            }
        }
    }

    private void cycles() {
        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = w.tx.size() - 1; i >= 0; i--) {
            Tx x = w.tx.get(i);
            if (x.t() < w.t - 120) break;
            if (x.amount() >= CYCLE_MIN_AMOUNT && w.isPerson(x.from()) && w.isPerson(x.to()))
                adj.computeIfAbsent(x.from(), k -> new LinkedHashSet<>()).add(x.to());
        }
        for (String start : new ArrayList<>(adj.keySet())) dfs(start, start, new ArrayList<>(List.of(start)), adj);
    }

    private void dfs(String start, String node, List<String> path, Map<String, Set<String>> adj) {
        if (path.size() > 4) return;
        for (String next : adj.getOrDefault(node, Set.of())) {
            if (next.equals(start) && path.size() >= 3) {
                List<String> sorted = new ArrayList<>(path);
                Collections.sort(sorted);
                StringBuilder loop = new StringBuilder();
                for (String p : path) loop.append(w.name(p)).append(" -> ");
                loop.append(w.name(start));
                book.raise(PatternType.CIRCULAR_TRANSFER, "CY:" + String.join(",", sorted), new LinkedHashSet<>(sorted),
                        "Money moved in a loop: " + loop + ".", null);
            } else if (next.compareTo(start) > 0 && !path.contains(next)) {
                path.add(next);
                dfs(start, next, path, adj);
                path.remove(path.size() - 1);
            }
        }
    }

    /** Evaluation only: mark planted rings as caught once a case contains >= 2 of their mules. */
    private void markFoundRings() {
        for (Ring r : w.rings) {
            if (r.found) continue;
            for (FraudCase c : book.cases) {
                int n = 0;
                for (String m : r.mules) if (c.entities.contains(m)) n++;
                if (n >= 2) { r.found = true; r.lagSeconds = w.t - r.plantedAt; break; }
            }
        }
    }
}
