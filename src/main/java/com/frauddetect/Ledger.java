package com.frauddetect;

import java.util.*;

/**
 * Deterministic synthetic order/return history per account. Mule accounts that return electronics
 * often send back a unit with a different serial number (an inside part was swapped, or the whole unit).
 */
public final class Ledger {
    public record Order(String id, String item, String platform, long amount, String status,
                        String sentSerial, String returnedSerial, boolean swapped, String swappedPart, String account) { }

    private static final Object[][] ELECTRONICS = {
            {"Laptop 15\"", 0, 52000L}, {"Smartphone 5G", 1, 24000L}, {"Gaming laptop", 2, 89000L},
            {"Smartwatch", 3, 14000L}, {"Tablet 11\"", 4, 31000L}, {"Mirrorless camera", 5, 64000L}};
    private static final String[] PARTS = {"motherboard", "SSD", "RAM", "battery", "display", "whole unit (dummy)"};

    private Ledger() { }

    public static List<Order> forAccount(Entity e) {
        Random r = new Random(e.id.hashCode() * 2654435761L);
        boolean mule = e.kind == Entity.Kind.MULE;
        int n = mule ? 3 + r.nextInt(3) : r.nextInt(3);
        List<Order> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Object[] it = ELECTRONICS[r.nextInt(ELECTRONICS.length)];
            double u = r.nextDouble();
            String status = mule ? (u < .5 ? "Returned" : u < .75 ? "Replaced" : "Delivered") : (u < .08 ? "Returned" : "Delivered");
            String sent = "SN" + (100000 + r.nextInt(899999));
            boolean bad = mule && !status.equals("Delivered") && r.nextDouble() < .8;
            String back = status.equals("Delivered") ? "" : bad ? "SN" + (100000 + r.nextInt(899999)) : sent;
            long amount = Math.round((long) it[2] * (.9 + r.nextDouble() * .2) / 10) * 10;
            out.add(new Order("ORD-" + (20000 + r.nextInt(79999)), (String) it[0], r.nextDouble() < .55 ? "Amazon" : "Flipkart",
                    amount, status, sent, back, bad, bad ? PARTS[r.nextInt(PARTS.length)] : "", e.id));
        }
        return out;
    }
}
