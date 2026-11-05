package com.frauddetect;

import java.util.*;

/**
 * Synthetic payments world: ordinary customers (some are families sharing a phone), merchants,
 * and planted fraud (mule rings and device farms) hidden in the traffic. All data is synthetic.
 */
public final class World {
    static final String[] FIRST = {"Aarav","Priya","Rahul","Sneha","Vikram","Ananya","Karthik","Divya","Rohit","Meena","Suresh","Lakshmi","Arjun","Pooja","Imran","Fatima","Harsha","Swathi","Naveen","Kavya","Manoj","Deepika","Sai","Ramya","Ajay","Nisha","Varun","Bhavana","Ravi","Sunitha"};
    static final String[] LAST = {"Sharma","Reddy","Naidu","Verma","Iyer","Khan","Patel","Das","Rao","Singh","Kumar","Nair","Gupta","Mehta","Chowdary","Varma"};
    static final String[] CITIES = {"Hyderabad","Vijayawada","Bengaluru","Chennai","Mumbai","Visakhapatnam","Pune","Delhi","Kolkata","Rajahmundry"};
    static final String[] PHONES = {"Redmi Note 12","Samsung Galaxy M34","Realme 11","iPhone 13","OnePlus Nord CE3","Vivo Y56","Oppo A78","Moto G54"};
    static final String[] MERCHANTS = {"Flipkart","Myntra","Meesho","Amazon Pay","Swiggy","BigBasket"};
    public static final String ATM = "ATM-EXT";

    private record Pending(String from, String to, long amount, String mode, double due) { }

    public final Random rng;
    public final Map<String, Entity> ent = new LinkedHashMap<>();
    public final Map<String, String> deviceModel = new LinkedHashMap<>();
    public final List<Tx> tx = new ArrayList<>();
    /** device id -> accounts that have sent money from it */
    public final Map<String, Set<String>> deviceUse = new LinkedHashMap<>();
    public final List<Ring> rings = new ArrayList<>();
    public final List<String> customers = new ArrayList<>();

    public int t;
    public int txCount;
    public long valueProcessed;
    private int nextRingAt = 8;
    private int nextFarmAt = 10;
    private int uid = 1000;
    private int ringCount, farmCount;
    private final List<Pending> pending = new ArrayList<>();

    /** Empty world for hand-built test scenarios. */
    public World(long seed) { this.rng = new Random(seed); }

    /** Standard population: 100 phones, 20 of them shared by a two-person family. */
    public static World standard(long seed) {
        World w = new World(seed);
        for (int g = 0; g < 100; g++) {
            String d = "DEV-" + (3000 + g);
            w.deviceModel.put(d, w.pick(PHONES));
            if (g < 20) {
                String ln = w.pick(LAST), city = w.pick(CITIES);
                w.customers.add(w.addPerson(Entity.Kind.CUSTOMER, d, ln, city));
                w.customers.add(w.addPerson(Entity.Kind.CUSTOMER, d, ln, city));
            } else {
                w.customers.add(w.addPerson(Entity.Kind.CUSTOMER, d, w.pick(LAST), w.pick(CITIES)));
            }
        }
        for (String m : MERCHANTS) w.ent.put("MER-" + m, new Entity("MER-" + m, Entity.Kind.MERCHANT, m, m, "", null, 0));
        w.ent.put(ATM, new Entity(ATM, Entity.Kind.EXTERNAL, "Cash withdrawal (ATM or exchange)", "", "", null, 0));
        return w;
    }

    <T> T pick(T[] a) { return a[rng.nextInt(a.length)]; }
    double rnd(double a, double b) { return a + rng.nextDouble() * (b - a); }

    public String addPerson(Entity.Kind kind, String device, String lastName, String city) {
        String fn = pick(FIRST), id = "ACC-" + (uid++);
        int age = kind == Entity.Kind.MULE ? 2 + rng.nextInt(6) : 300 + rng.nextInt(2700);
        ent.put(id, new Entity(id, kind, fn + " " + lastName, lastName, city, device, age));
        return id;
    }

    public String addPerson(Entity.Kind kind, String device) { return addPerson(kind, device, pick(LAST), pick(CITIES)); }

    public String name(String id) {
        Entity e = ent.get(id);
        if (e != null) return e.name;
        return deviceModel.getOrDefault(id, id);
    }

    public boolean isPerson(String id) { Entity e = ent.get(id); return e != null && e.isPerson(); }

    /** Queue a payment to execute {@code delay} simulated seconds from now. */
    public void post(String from, String to, double amount, String mode, double delay) {
        pending.add(new Pending(from, to, Math.round(amount), mode, t + delay));
    }

    /** Move due payments into the ledger. */
    public void flush() {
        List<Pending> keep = new ArrayList<>();
        for (Pending p : pending) {
            if (p.due() <= t) {
                tx.add(new Tx("TX-" + (++txCount), p.from(), p.to(), p.amount(), p.mode(), t));
                valueProcessed += p.amount();
                String d = ent.get(p.from()).deviceId;
                if (d != null) deviceUse.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(p.from());
            } else keep.add(p);
        }
        pending.clear();
        pending.addAll(keep);
        if (tx.size() > 4000) tx.subList(0, tx.size() - 4000).clear();
    }

    public int pendingCount() { return pending.size(); }

    /** Background traffic: 70% shopping at merchants, 30% person-to-person. */
    public void generateTraffic(int n) {
        String[] modes = {"UPI", "IMPS", "NEFT"};
        for (int i = 0; i < n; i++) {
            String a = customers.get(rng.nextInt(customers.size()));
            if (rng.nextDouble() < .7) {
                post(a, "MER-" + pick(MERCHANTS), rnd(150, 4500), rng.nextBoolean() ? "UPI" : "Card", 0);
            } else {
                String b = customers.get(rng.nextInt(customers.size()));
                if (b.equals(a)) b = customers.get(rng.nextInt(customers.size()));
                post(a, b, rnd(100, 6000), pick(modes), 0);
            }
        }
    }

    /**
     * Plant a mule ring: 5 victims pay mule A; A, B, C pass 20,000 round in a circle; A cashes out ~97%.
     * Six mule accounts share two phones.
     */
    public Ring spawnRing() {
        int k = ++ringCount;
        String d1 = "DEV-9" + k + "1", d2 = "DEV-9" + k + "2";
        deviceModel.put(d1, pick(PHONES));
        deviceModel.put(d2, pick(PHONES));
        List<String> mules = new ArrayList<>();
        for (int j = 0; j < 6; j++) mules.add(addPerson(Entity.Kind.MULE, j < 3 ? d1 : d2));
        List<String> pool = new ArrayList<>(customers);
        Collections.shuffle(pool, rng);
        double sum = 0;
        for (int j = 0; j < 5; j++) {
            double a = rnd(12000, 48000);
            sum += a;
            post(pool.get(j), mules.get(0), a, "UPI", j * 1.2);
        }
        post(mules.get(0), mules.get(1), 20000, "IMPS", 5);
        post(mules.get(1), mules.get(2), 20000, "IMPS", 6);
        post(mules.get(2), mules.get(0), 20000, "IMPS", 7);
        post(mules.get(0), ATM, (sum + 20000) * .97, "ATM", 9);
        Ring r = new Ring("RING-" + k, "RING", t, mules);
        rings.add(r);
        return r;
    }

    /** Plant a device farm: five throw-away accounts on one phone making big card/UPI purchases. */
    public Ring spawnFarm() {
        int k = ++farmCount;
        String d = "DEV-8" + k + "0";
        deviceModel.put(d, pick(PHONES));
        List<String> mules = new ArrayList<>();
        for (int j = 0; j < 5; j++) mules.add(addPerson(Entity.Kind.MULE, d));
        for (String m : mules) post(m, "MER-" + pick(MERCHANTS), rnd(26000, 72000), rng.nextBoolean() ? "Card" : "UPI", 0);
        Ring r = new Ring("FARM-" + k, "FARM", t, mules);
        rings.add(r);
        return r;
    }

    /** Advance one simulated second: traffic, planted fraud on a schedule, then ledger flush. */
    public void step(int eventsPerSecond, boolean plantFraud) {
        t++;
        generateTraffic(eventsPerSecond);
        if (plantFraud) {
            if (t >= nextRingAt) { spawnRing(); nextRingAt = t + 25 + rng.nextInt(20); }
            if (t >= nextFarmAt) { spawnFarm(); nextFarmAt = t + 30 + rng.nextInt(25); }
        }
        flush();
    }

    /** Accounts that sent money from a given device. */
    public List<String> accountsOnDevice(String device) {
        List<String> out = new ArrayList<>();
        for (Entity e : ent.values()) if (e.isPerson() && device.equals(e.deviceId)) out.add(e.id);
        return out;
    }

    /** device -> accounts registered on it (not just those that have sent money). */
    public Map<String, List<String>> deviceMap() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (Entity e : ent.values()) if (e.isPerson() && e.deviceId != null) m.computeIfAbsent(e.deviceId, k -> new ArrayList<>()).add(e.id);
        return m;
    }
}
