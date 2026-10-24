package com.emi.utils;

import java.util.regex.Pattern;

// DO (delivery order) numbers are typed in from the dealer's paper. They are saved cleaned and compared
// ignoring case and spaces, so "do-2026-125" and "DO-2026 -125" are the same number.
public final class DoNumbers {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 \\-/.]{0,39}$");

    private DoNumbers() {
    }

    public static String clean(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    public static boolean isValid(String cleaned) {
        return VALID.matcher(cleaned).matches();
    }

    public static String key(String cleaned) {
        return cleaned.replace(" ", "");
    }
}
