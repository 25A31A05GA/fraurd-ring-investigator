package com.frauddetect;

/** One money movement. {@code t} is simulated seconds since start. */
public record Tx(String id, String from, String to, long amount, String mode, int t) { }
