package com.frauddetect;

import java.util.*;

/** Zero-dependency test runner: `java -cp out com.frauddetect.TestRunner`. Exits non-zero on failure. */
public final class TestRunner {
    private static int passed, failed;

    interface Body { void run() throws Exception; }

    static void test(String name, Body b) {
        try { b.run(); passed++; System.out.println("  PASS  " + name); }
        catch (Throwable t) { failed++; System.out.println("  FAIL  " + name + "  -> " + t); }
    }

    static void check(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }

    /** Tiny hand-built world: no background traffic, just what each test posts. */
    static World blank() { World w = new World(1); w.ent.put(World.ATM, new Entity(World.ATM, Entity.Kind.EXTERNAL, "ATM", "", "", null, 0));
        w.ent.put("MER-Flipkart", new Entity("MER-Flipkart", Entity.Kind.MERCHANT, "Flipkart", "Flipkart", "", null, 0)); return w; }

    static String person(World w, Entity.Kind k, String dev, String ln) { return w.addPerson(k, dev, ln, "Hyderabad"); }

    static void advance(World w, CaseBook b, DetectionEngine d, int seconds) {
        for (int i = 0; i < seconds; i++) { w.t++; w.flush(); d.run(); }
    }

    public static void main(String[] args) {
        System.out.println("Fraud Ring Investigator tests");

        test("shared device with 3 unrelated surnames raises SHARED_DEVICE", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            for (String ln : List.of("Rao", "Khan", "Iyer")) { String a = person(w, Entity.Kind.MULE, "DEV-X", ln); w.post(a, "MER-Flipkart", 500, "UPI", 0); }
            advance(w, b, d, 2);
            check(b.alerts.stream().anyMatch(a -> a.type() == PatternType.SHARED_DEVICE), "expected SD alert");
        });

        test("a family (same surname) sharing one phone is NOT flagged", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            for (int i = 0; i < 4; i++) { String a = person(w, Entity.Kind.CUSTOMER, "DEV-F", "Reddy"); w.post(a, "MER-Flipkart", 500, "UPI", 0); }
            advance(w, b, d, 2);
            check(b.cases.isEmpty(), "family phone must not open a case, got " + b.cases.size());
        });

        test("two accounts on a phone is below the threshold", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            for (String ln : List.of("Rao", "Khan")) { String a = person(w, Entity.Kind.CUSTOMER, "DEV-2", ln); w.post(a, "MER-Flipkart", 500, "UPI", 0); }
            advance(w, b, d, 2);
            check(b.cases.isEmpty(), "2 accounts must not alert");
        });

        test("fan-in: 4 people paying one account >= 50,000 within a minute", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String target = person(w, Entity.Kind.CUSTOMER, "DEV-T", "Das");
            for (int i = 0; i < 4; i++) w.post(person(w, Entity.Kind.CUSTOMER, "DEV-S" + i, "Nair" + i), target, 15000, "UPI", i);
            advance(w, b, d, 6);
            check(b.alerts.stream().anyMatch(a -> a.type() == PatternType.FAN_IN), "expected FAN_IN");
            check(!b.cases.get(0).victims.isEmpty(), "fan-in should record victims");
        });

        test("fan-in needs 4 senders: 3 big payments are not enough", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String target = person(w, Entity.Kind.CUSTOMER, "DEV-T", "Das");
            for (int i = 0; i < 3; i++) w.post(person(w, Entity.Kind.CUSTOMER, "DEV-S" + i, "Nair" + i), target, 40000, "UPI", i);
            advance(w, b, d, 6);
            check(b.alerts.stream().noneMatch(a -> a.type() == PatternType.FAN_IN), "3 senders must not trigger fan-in");
        });

        test("fan-in ignores many SMALL payments that merely add up (busy-minute false positive)", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String target = person(w, Entity.Kind.CUSTOMER, "DEV-T", "Das");
            for (int i = 0; i < 16; i++) w.post(person(w, Entity.Kind.CUSTOMER, "DEV-S" + i, "Nair" + i), target, 3300, "UPI", i);
            advance(w, b, d, 20);
            check(b.alerts.isEmpty(), "16 x 3,300 is a busy minute, not a ring");
        });

        test("rapid cash-out: >=80% of 50k+ inflow leaves in seconds", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String mule = person(w, Entity.Kind.MULE, "DEV-M", "Verma"), v = person(w, Entity.Kind.CUSTOMER, "DEV-V", "Gupta");
            w.post(v, mule, 80000, "UPI", 0); w.post(mule, World.ATM, 78000, "ATM", 3);
            advance(w, b, d, 6);
            check(b.alerts.stream().anyMatch(a -> a.type() == PatternType.RAPID_CASHOUT), "expected RAPID_CASHOUT");
        });

        test("holding the money (no quick outflow) is not a cash-out", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String a = person(w, Entity.Kind.CUSTOMER, "DEV-A", "Verma"), v = person(w, Entity.Kind.CUSTOMER, "DEV-V", "Gupta");
            w.post(v, a, 80000, "UPI", 0);
            advance(w, b, d, 30);
            check(b.alerts.stream().noneMatch(x -> x.type() == PatternType.RAPID_CASHOUT), "no outflow, no alert");
        });

        test("circular transfer A->B->C->A is found", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String a = person(w, Entity.Kind.MULE, "D1", "A1"), bb = person(w, Entity.Kind.MULE, "D2", "B1"), c = person(w, Entity.Kind.MULE, "D3", "C1");
            w.post(a, bb, 20000, "IMPS", 0); w.post(bb, c, 20000, "IMPS", 1); w.post(c, a, 20000, "IMPS", 2);
            advance(w, b, d, 4);
            check(b.alerts.stream().anyMatch(x -> x.type() == PatternType.CIRCULAR_TRANSFER), "expected CYCLE");
        });

        test("small round-trip transfers under 10,000 are ignored", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String a = person(w, Entity.Kind.CUSTOMER, "D1", "A1"), bb = person(w, Entity.Kind.CUSTOMER, "D2", "B1"), c = person(w, Entity.Kind.CUSTOMER, "D3", "C1");
            w.post(a, bb, 2000, "UPI", 0); w.post(bb, c, 2000, "UPI", 1); w.post(c, a, 2000, "UPI", 2);
            advance(w, b, d, 4);
            check(b.alerts.isEmpty(), "small loops are normal (splitting a bill)");
        });

        test("a two-person ping-pong is not a 3-hop ring", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String a = person(w, Entity.Kind.CUSTOMER, "D1", "A1"), bb = person(w, Entity.Kind.CUSTOMER, "D2", "B1");
            w.post(a, bb, 20000, "UPI", 0); w.post(bb, a, 20000, "UPI", 1);
            advance(w, b, d, 3);
            check(b.alerts.isEmpty(), "A->B->A must not alert");
        });

        test("duplicate detections do not duplicate alerts", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String a = person(w, Entity.Kind.MULE, "D1", "A1"), bb = person(w, Entity.Kind.MULE, "D2", "B1"), c = person(w, Entity.Kind.MULE, "D3", "C1");
            w.post(a, bb, 20000, "IMPS", 0); w.post(bb, c, 20000, "IMPS", 1); w.post(c, a, 20000, "IMPS", 2);
            advance(w, b, d, 30);
            check(b.alerts.stream().filter(x -> x.type() == PatternType.CIRCULAR_TRANSFER).count() == 1, "one cycle alert only");
        });

        test("score = sum of distinct pattern weights, capped at 100", () -> {
            World w = World.standard(7); CaseBook b = new CaseBook(w);
            Set<String> e = Set.of(w.customers.get(0));
            b.raise(PatternType.SHARED_DEVICE, "k1", e, "x", null); b.raise(PatternType.SHARED_DEVICE, "k2", e, "again", null);
            check(b.cases.get(0).score == 35, "same pattern twice counts once, got " + b.cases.get(0).score);
            b.raise(PatternType.RAPID_CASHOUT, "k3", e, "x", null); b.raise(PatternType.CIRCULAR_TRANSFER, "k4", e, "x", null); b.raise(PatternType.FAN_IN, "k5", e, "x", null);
            check(b.cases.size() == 1 && b.cases.get(0).score == 100, "all four = 100");
            check(b.cases.get(0).level().equals("HIGH"), "100 is HIGH");
        });

        test("risk levels: 70+ HIGH, 40-69 MEDIUM, below 40 LOW", () -> {
            World w = World.standard(7); CaseBook b = new CaseBook(w);
            Set<String> e = Set.of(w.customers.get(1));
            b.raise(PatternType.FAN_IN, "a", e, "x", null);
            check(b.cases.get(0).level().equals("LOW"), "15 -> LOW");
            b.raise(PatternType.SHARED_DEVICE, "b", e, "x", null);
            check(b.cases.get(0).level().equals("MEDIUM"), "50 -> MEDIUM");
        });

        test("case audit trail records status changes and notes", () -> {
            World w = World.standard(7); CaseBook b = new CaseBook(w);
            b.raise(PatternType.FAN_IN, "a", Set.of(w.customers.get(2)), "x", null);
            FraudCase c = b.cases.get(0);
            c.setStatus(FraudCase.Status.UNDER_INVESTIGATION); c.addNote("called bank");
            check(c.audit.stream().anyMatch(s -> s.contains("UNDER_INVESTIGATION")), "status change audited");
            check(c.audit.stream().anyMatch(s -> s.contains("note")), "note audited");
        });

        test("normal traffic only (no planted fraud) -> zero cases over 20 seeds", () -> {
            for (int seed = 1; seed <= 20; seed++) {
                World w = World.standard(seed); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
                for (int i = 0; i < 200; i++) { w.step(5, false); d.run(); }
                check(b.cases.isEmpty(), "seed " + seed + " produced " + b.cases.size() + " false cases (families / merchants must be ignored)");
            }
        });

        test("KNOWN LIMITATION: a slow, split, one-phone-per-mule ring is not detected (docs/case-studies/05)", () -> {
            World w = blank(); CaseBook b = new CaseBook(w); DetectionEngine d = new DetectionEngine(w, b);
            String a = person(w, Entity.Kind.MULE, "P0", "Rao"), m1 = person(w, Entity.Kind.MULE, "P1", "Das"), m2 = person(w, Entity.Kind.MULE, "P2", "Nair");
            for (int i = 0; i < 5; i++) w.post(person(w, Entity.Kind.CUSTOMER, "V" + i, "Gupta" + i), a, 9000, "UPI", i * 100);
            w.post(a, m1, 8000, "IMPS", 120); w.post(m1, m2, 8000, "IMPS", 300); w.post(m2, a, 8000, "IMPS", 480);
            for (int i = 0; i < 10; i++) w.post(a, World.ATM, 4000, "ATM", 200 + i * 60);
            advance(w, b, d, 700);
            check(b.alerts.isEmpty(), "documented evasion: if this starts alerting, update case study 5 (good news!)");
        });

        test("full simulation: every planted group caught, no false alarms (10 seeds)", () -> {
            for (int seed = 1; seed <= 10; seed++) {
                Engine e = new Engine(seed);
                for (int i = 0; i < 300; i++) e.tick(5);
                Performance p = e.performance();
                check(p.planted() > 0, "something must be planted");
                check(p.recall() == 1.0, "seed " + seed + " recall " + p.recall());
                check(p.falseAlarms() == 0, "seed " + seed + " false alarms " + p.falseAlarms());
            }
        });

        test("same seed -> identical results (deterministic)", () -> {
            Engine a = new Engine(99), b = new Engine(99);
            for (int i = 0; i < 120; i++) { a.tick(5); b.tick(5); }
            check(a.world.txCount == b.world.txCount && a.book.cases.size() == b.book.cases.size() && a.world.valueProcessed == b.world.valueProcessed, "runs must match");
        });

        test("device lab: farms found, gate decisions, hang + release", () -> {
            Engine e = new Engine(5);
            for (int i = 0; i < 150; i++) e.tick(5);
            List<DeviceFarmLab.Farm> farms = e.lab.findFarms(4);
            check(!farms.isEmpty(), "expected at least one farm");
            String dev = farms.get(0).device();
            check(e.lab.gate(dev, 4).decision() == DeviceFarmLab.Decision.REVIEW, "farm phone -> REVIEW");
            check(e.lab.gate("DEV-NEW-1", 4).decision() == DeviceFarmLab.Decision.ALLOWED, "unknown phone -> ALLOWED");
            int frozen = e.lab.hang(dev);
            check(frozen >= 4 && e.lab.gate(dev, 4).decision() == DeviceFarmLab.Decision.BLOCKED, "hung phone -> BLOCKED");
            e.lab.release(dev);
            check(e.lab.frozenAccounts().isEmpty() && e.lab.gate(dev, 4).decision() == DeviceFarmLab.Decision.REVIEW, "release restores REVIEW");
        });

        test("device lab: auto-hang freezes every device at/above threshold", () -> {
            Engine e = new Engine(6);
            for (int i = 0; i < 120; i++) e.tick(5);
            int n = e.lab.findFarms(4).size();
            check(e.lab.autoHang(4).size() == n && n > 0, "auto-hang count must equal farms");
            check(e.lab.autoHang(4).isEmpty(), "second pass hangs nothing new");
        });

        test("serial verifier: genuine, swapped, unknown, empty", () -> {
            Engine e = new Engine(8);
            for (int i = 0; i < 100; i++) e.tick(5);
            Ledger.Order swapped = null;
            for (Entity en : e.world.ent.values()) if (en.kind == Entity.Kind.MULE) for (Ledger.Order o : Ledger.forAccount(en)) if (o.swapped()) { swapped = o; break; }
            check(swapped != null, "mule ledgers should contain a swapped return");
            check(e.lab.verifySerial(swapped.returnedSerial()).verdict().equals("SWAP"), "returned serial -> SWAP");
            check(e.lab.verifySerial(swapped.sentSerial().toLowerCase()).verdict().equals("SENT_BUT_SWAPPED"), "case-insensitive sent serial");
            check(e.lab.verifySerial("SN000000").verdict().equals("UNKNOWN"), "unknown serial");
            check(e.lab.verifySerial("  ").verdict().equals("EMPTY"), "blank input");
        });

        test("evidence report mentions device, accounts and swapped serials", () -> {
            Engine e = new Engine(5);
            for (int i = 0; i < 150; i++) e.tick(5);
            String dev = e.lab.findFarms(4).get(0).device();
            String r = e.lab.evidenceReport(dev);
            check(r.contains(dev) && r.contains("Accounts on device") && r.contains("synthetic"), "report content");
        });

        test("JSON writer escapes quotes, newlines and nests", () -> {
            String j = Json.write(Json.obj("a", "x\"y\n", "b", List.of(1, 2), "c", Json.obj("d", true)));
            check(j.equals("{\"a\":\"x\\\"y\\n\",\"b\":[1,2],\"c\":{\"d\":true}}"), j);
        });

        test("JSON writer serialises records as objects", () -> {
            check(Json.write(new Alert("A", PatternType.FAN_IN, Set.of("x"), "t", 1)).equals("{\"id\":\"A\",\"type\":\"FAN_IN\",\"entities\":[\"x\"],\"text\":\"t\",\"t\":1}"), "record json");
        });

        test("REST API: health, cases, status update, 404, bad status", () -> {
            Engine e = new Engine(3);
            for (int i = 0; i < 60; i++) e.tick(5);
            ApiServer s = new ApiServer(e, 0, 1);
            s.start();
            try {
                java.net.http.HttpClient c = java.net.http.HttpClient.newHttpClient();
                String base = "http://localhost:" + s.port();
                java.util.function.BiFunction<String, String, java.net.http.HttpResponse<String>> call = (m, p) -> {
                    try { return c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + p)).method(m, java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.ofString()); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                };
                check(call.apply("GET", "/api/health").body().contains("\"status\":\"ok\""), "health");
                check(call.apply("GET", "/api/cases").body().startsWith("["), "cases list");
                String id = e.book.cases.get(0).id;
                check(call.apply("GET", "/api/cases/" + id).body().contains("\"suspects\""), "case detail");
                check(call.apply("POST", "/api/cases/" + id + "/status?value=RESOLVED").body().contains("RESOLVED"), "status update");
                check(call.apply("POST", "/api/cases/" + id + "/status?value=NOPE").statusCode() == 400, "bad status -> 400");
                check(call.apply("GET", "/api/cases/CASE-999").statusCode() == 404, "missing case -> 404");
                check(call.apply("GET", "/api/performance").body().contains("\"recall\":"), "performance");
                check(call.apply("GET", "/api/serial?value=SN000000").body().contains("\"verdict\":\"UNKNOWN\""), "serial");
            } finally { s.stop(); }
        });

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
