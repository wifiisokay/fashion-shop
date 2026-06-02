package com.fashionshop.backend.module.ai;

import java.text.Normalizer;
import java.util.Locale;

public final class VietnameseTextNormalizer {

    private VietnameseTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT)
            .replace('-', ' ')
            .replaceAll("[^\\p{Alnum}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static String padded(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "" : " " + normalized + " ";
    }
}
