package com.frauddetect;

import java.util.Set;

public record Alert(String id, PatternType type, Set<String> entities, String text, int t) { }
