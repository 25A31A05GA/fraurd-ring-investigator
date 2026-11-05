package com.frauddetect;

import java.util.*;

/**
 * Device-farm tooling: find phones with many accounts, check returned serial numbers,
 * "hang" (freeze) a whole farm in one call, and gate new signups against the hang list.
 */
public final class DeviceFarmLab {
    public enum Decision { ALLOWED, REVIEW, BLOCKED }

    public record Farm(String device, String model, List<String> accounts, int orders, int returns, int swaps, long exposure) { }
    public record GateResult(String device, Decision decision, String reason) { }
    public record SerialCheck(String serial, String verdict, String detail) { }

    private final World w;
    private final Set<String> blockedDevices = new LinkedHashSet<>();
    private final Set<String> frozenAccounts = new LinkedHashSet<>();

    public DeviceFarmLab(World w) { this.w = w; }

    public Set<String> blockedDevices() { return Collections.unmodifiableSet(blockedDevices); }
    public Set<String> frozenAccounts() { return Collections.unmodifiableSet(frozenAccounts); }

    /** Devices with at least {@code threshold} accounts, worst first. */
    public List<Farm> findFarms(int threshold) {
        List<Farm> farms = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : w.deviceMap().entrySet()) {
            if (e.getValue().size() < threshold) continue;
            farms.add(stats(e.getKey(), e.getValue()));
        }
        farms.sort(Comparator.comparingInt((Farm f) -> f.accounts().size()).reversed().thenComparing(Comparator.comparingLong(Farm::exposure).reversed()));
        return farms;
    }

    private Farm stats(String device, List<String> accounts) {
        int orders = 0, returns = 0, swaps = 0;
        long exposure = 0;
        for (String a : accounts) {
            for (Ledger.Order o : Ledger.forAccount(w.ent.get(a))) {
                orders++;
                if (!o.status().equals("Delivered")) {
                    returns++;
                    if (o.swapped()) { swaps++; exposure += o.amount(); }
                }
            }
        }
        return new Farm(device, w.name(device), accounts, orders, returns, swaps, exposure);
    }

    /** Freeze every account on the device and add the device to the hang list. */
    public int hang(String device) {
        List<String> accts = w.accountsOnDevice(device);
        if (accts.isEmpty()) return 0;
        blockedDevices.add(device);
        frozenAccounts.addAll(accts);
        return accts.size();
    }

    public void release(String device) {
        blockedDevices.remove(device);
        for (String a : w.accountsOnDevice(device)) frozenAccounts.remove(a);
    }

    /** Hang every device at or above the threshold. @return devices newly hung. */
    public List<String> autoHang(int threshold) {
        List<String> hung = new ArrayList<>();
        for (Farm f : findFarms(threshold)) if (!blockedDevices.contains(f.device())) { hang(f.device()); hung.add(f.device()); }
        return hung;
    }

    /** Decide what to do with a new signup coming from {@code device}. */
    public GateResult gate(String device, int threshold) {
        if (blockedDevices.contains(device)) return new GateResult(device, Decision.BLOCKED, "Device is on the hang list");
        int n = w.accountsOnDevice(device).size();
        if (n >= threshold) return new GateResult(device, Decision.REVIEW, n + " accounts already use this device");
        return new GateResult(device, Decision.ALLOWED, "New device, normal signup");
    }

    /** Check a returned serial number against every order in the world. */
    public SerialCheck verifySerial(String serial) {
        String q = serial.trim().toUpperCase();
        if (q.isEmpty()) return new SerialCheck(q, "EMPTY", "Type or scan a serial number first.");
        for (Entity e : w.ent.values()) {
            if (!e.isPerson()) continue;
            for (Ledger.Order o : Ledger.forAccount(e)) {
                if (o.sentSerial().equals(q))
                    return o.swapped()
                            ? new SerialCheck(q, "SENT_BUT_SWAPPED", "Dispatched on " + o.id() + " to " + e.name + ", but the unit that came back had serial " + o.returnedSerial() + ". Refuse the refund.")
                            : new SerialCheck(q, "GENUINE", "Matches the unit dispatched for " + o.id() + ".");
                if (o.swapped() && o.returnedSerial().equals(q))
                    return new SerialCheck(q, "SWAP", "Returned on " + o.id() + " but " + o.sentSerial() + " was dispatched (" + o.swappedPart() + " swapped).");
            }
        }
        return new SerialCheck(q, "UNKNOWN", "Serial not found in any order.");
    }

    /** Plain-text evidence report for police / platform trust-and-safety teams. */
    public String evidenceReport(String device) {
        List<String> accts = w.accountsOnDevice(device);
        Farm f = stats(device, accts);
        StringBuilder sb = new StringBuilder();
        sb.append("DEVICE FARM EVIDENCE REPORT (demo data, all people and addresses are synthetic)\n");
        sb.append("Device: ").append(device).append(" (").append(f.model()).append(")\n");
        sb.append("Accounts on device: ").append(accts.size()).append('\n');
        sb.append("Orders: ").append(f.orders()).append(", returns: ").append(f.returns()).append(", swapped returns: ").append(f.swaps()).append('\n');
        sb.append(String.format("Refund exposure: INR %,d%n%n", f.exposure()));
        for (String a : accts) {
            Entity e = w.ent.get(a);
            sb.append(a).append("  ").append(e.name).append("  (opened ").append(e.accountAgeDays).append(" days ago)\n");
            for (Ledger.Order o : Ledger.forAccount(e)) {
                if (o.swapped()) sb.append("   ").append(o.id()).append(' ').append(o.item()).append(": sent ").append(o.sentSerial())
                        .append(", returned ").append(o.returnedSerial()).append(" (").append(o.swappedPart()).append(" swapped)\n");
            }
        }
        return sb.toString();
    }
}
