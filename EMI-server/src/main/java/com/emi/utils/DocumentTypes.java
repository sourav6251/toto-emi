package com.emi.utils;

import java.util.List;
import java.util.Optional;

public final class DocumentTypes {

    public record DocType(String key, String label, String group, boolean mandatory, boolean numbered) {
    }

    public static final List<DocType> ALL = List.of(
            new DocType("voter", "Voter Card", "Customer", false, true),
            new DocType("aadhaar", "Aadhaar Card", "Customer", false, true),
            new DocType("pan", "PAN Card", "Customer", false, true),
            new DocType("gold", "Gold-related document", "Security", false, false),
            new DocType("land", "Land Record", "Security", false, true),
            new DocType("gst", "GST Number / GST Bill", "Security", false, true),
            new DocType("do", "DO Document", "Asset", false, true),
            new DocType("bill", "Purchase Bill", "Asset", false, true),
            new DocType("asset_other", "Other vehicle/product document", "Asset", false, false));

    private DocumentTypes() {
    }

    public static Optional<DocType> find(String key) {
        return key == null ? Optional.empty() : ALL.stream().filter(t -> t.key().equals(key.trim())).findFirst();
    }

    public static boolean isIdentity(String key) {
        return "aadhaar".equals(key) || "pan".equals(key) || "voter".equals(key);
    }
}
