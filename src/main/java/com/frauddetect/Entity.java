package com.frauddetect;

/** An account holder, mule account, merchant or the external cash-out sink. */
public final class Entity {
    public enum Kind { CUSTOMER, MULE, MERCHANT, EXTERNAL }

    public final String id;
    public final Kind kind;
    public final String name;
    public final String lastName;
    public final String city;
    public final String deviceId;   // null for merchants / external
    public final int accountAgeDays;

    public Entity(String id, Kind kind, String name, String lastName, String city, String deviceId, int accountAgeDays) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.lastName = lastName;
        this.city = city;
        this.deviceId = deviceId;
        this.accountAgeDays = accountAgeDays;
    }

    public boolean isPerson() {
        return kind == Kind.CUSTOMER || kind == Kind.MULE;
    }
}
