package com.frauddetect;

import java.util.*;

/**
 * Usage:
 *   java -jar fraud-ring-investigator.jar demo  [--seconds 300] [--seed 42] [--rate 5]
 *   java -jar fraud-ring-investigator.jar bench [--runs 20] [--seconds 300] [--rate 5]
 *   java -jar fraud-ring-investigator.jar farm  [--seconds 150] [--seed 5]   (prints a device-farm evidence report)
 *   java -jar fraud-ring-investigator.jar serve [--port 8080] [--seed 42] [--rate 5]
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "demo";
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) o.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        int seconds = Integer.parseInt(o.getOrDefault("seconds", "300")), rate = Integer.parseInt(o.getOrDefault("rate", "5"));
        long seed = Long.parseLong(o.getOrDefault("seed", "42"));
        switch (cmd) {
            case "demo" -> demo(seed, seconds, rate);
            case "farm" -> farm(seed, seconds, rate);
            case "bench" -> bench(Integer.parseInt(o.getOrDefault("runs", "20")), seconds, rate);
            case "serve" -> serve(Integer.parseInt(o.getOrDefault("port", System.getenv().getOrDefault("PORT", "8080"))), seed, rate);
            default -> System.out.println("Commands: demo | bench | serve   (see README)");
        }
    }

    static void demo(long seed, int seconds, int rate) {
        Engine e = new Engine(seed);
        for (int i = 0; i < seconds; i++) e.tick(rate);
        System.out.printf("Fraud Ring Investigator | seed=%d | %d simulated seconds | %d events/s%n%n", seed, seconds, rate);
        System.out.printf("Transactions: %,d   Value: INR %,d   Alerts: %d   Cases: %d%n%n",
                e.world.txCount, e.world.valueProcessed, e.book.alerts.size(), e.book.cases.size());
        System.out.println("TOP CASES");
        e.book.cases.stream().sorted(Comparator.comparingInt((FraudCase c) -> c.score).reversed()).limit(8).forEach(c -> {
            System.out.printf("  %-9s risk %3d %-6s %s%n", c.id, c.score, c.level(), c.title);
            c.patterns.forEach((p, why) -> System.out.printf("      +%d %s: %s%n", p.weight, p.label, why));
        });
        Performance p = e.performance();
        System.out.printf("%nPERFORMANCE vs planted ground truth%n  planted %d, caught %d  | recall %.0f%%  precision %.0f%%  false alarms %d  avg detection %.1fs%n",
                p.planted(), p.caught(), p.recall() * 100, p.precision() * 100, p.falseAlarms(), p.avgLagSeconds());
        List<DeviceFarmLab.Farm> farms = e.lab.findFarms(4);
        System.out.printf("%nDEVICE FARMS (>=4 accounts on one phone): %d%n", farms.size());
        farms.stream().limit(3).forEach(f -> System.out.printf("  %s %-18s %d accounts, %d swapped returns, exposure INR %,d%n",
                f.device(), f.model(), f.accounts().size(), f.swaps(), f.exposure()));
    }

    static void farm(long seed, int seconds, int rate) {
        Engine e = new Engine(seed);
        for (int i = 0; i < seconds; i++) e.tick(rate);
        List<DeviceFarmLab.Farm> farms = e.lab.findFarms(4);
        if (farms.isEmpty()) { System.out.println("No device farms yet. Try --seconds 150."); return; }
        System.out.println(e.lab.evidenceReport(farms.get(0).device()));
    }

    static void bench(int runs, int seconds, int rate) {
        double rec = 0, prec = 0, lag = 0, fa = 0, planted = 0, caught = 0;
        long events = 0, t0 = System.nanoTime();
        for (int s = 1; s <= runs; s++) {
            Engine e = new Engine(s);
            for (int i = 0; i < seconds; i++) e.tick(rate);
            Performance p = e.performance();
            rec += p.recall(); prec += p.precision(); lag += p.avgLagSeconds(); fa += p.falseAlarms();
            planted += p.planted(); caught += p.caught(); events += e.world.txCount;
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("BENCHMARK: %d runs x %d s x %d events/s (seeds 1..%d)%n", runs, seconds, rate, runs);
        System.out.printf("  planted fraud groups : %.0f (caught %.0f)%n", planted, caught);
        System.out.printf("  mean recall          : %.1f%%%n", rec / runs * 100);
        System.out.printf("  mean precision       : %.1f%%%n", prec / runs * 100);
        System.out.printf("  mean false alarms    : %.2f per run%n", fa / runs);
        System.out.printf("  mean time-to-detect  : %.2f simulated seconds%n", lag / runs);
        System.out.printf("  throughput           : %,d events in %.0f ms (%.0f events/s wall-clock)%n", events, ms, events / (ms / 1000));
    }

    static void serve(int port, long seed, int rate) throws Exception {
        ApiServer s = new ApiServer(new Engine(seed), port, rate);
        s.start();
        System.out.println("Fraud Ring Investigator API on http://localhost:" + s.port() + "/api/summary  (Ctrl+C to stop)");
        Thread.currentThread().join();
    }
}
