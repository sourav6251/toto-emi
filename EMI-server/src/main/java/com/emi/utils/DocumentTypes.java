package com.emi.utils;

import java.util.List;
import java.util.Optional;

// The document types a finance can carry. Identity documents are masked when their number is shown.
public final class DocumentTypes {

    public record DocType(String key, String label, String group, boolean mandatory, boolean numbered) {
    }

    public static final List<DocType> ALL = List.of(
            new DocType("voter", "Voter Card", "Customer", true, true),
            new DocType("aadhaar", "Aadhaar Card", "Customer", true, true),
            new DocType("pan", "PAN Card", "Customer", true, true),
            new DocType("gold", "Gold-related document", "Security", false, false),
            new DocType("land", "Land Record", "Security", false, true),
            new DocType("gst", "GST Number / GST Bill", "Security", false, true),
            new DocType("do", "DO Document", "Asset", true, true),
            new DocType("bill", "Purchase Bill", "Asset", true, true),
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
