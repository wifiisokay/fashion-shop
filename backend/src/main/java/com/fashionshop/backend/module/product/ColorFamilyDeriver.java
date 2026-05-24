package com.fashionshop.backend.module.product;

public final class ColorFamilyDeriver {

    private ColorFamilyDeriver() {
    }

    public static String derive(String hexCode) {
        String hex = normalizeHex(hexCode);
        if (hex == null) {
            return null;
        }

        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        float[] hsl = rgbToHsl(r, g, b);
        float h = hsl[0];
        float s = hsl[1];
        float l = hsl[2];

        if (s < 0.15f || l < 0.20f || (l > 0.80f && (s < 0.40f || (h >= 45 && h <= 75)))) {
            return "neutral";
        }
        if (l > 0.75f && h >= 240 && h < 300) {
            return "warm";
        }
        if (h >= 0 && h <= 50 && l < 0.55f && s < 0.75f) {
            return "earth";
        }
        if (h >= 15 && h <= 50 && s < 0.55f) {
            return "earth";
        }
        if (h >= 50 && h <= 90 && s < 0.45f) {
            return "earth";
        }
        if (h < 30 || h >= 330) {
            return "warm";
        }
        if (h >= 30 && h < 65) {
            return "warm";
        }
        if (h >= 270 && h < 330) {
            return "warm";
        }
        if (h >= 65 && h < 270) {
            return "cool";
        }
        return "mixed";
    }

    private static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0)
                + hex.charAt(1) + hex.charAt(1)
                + hex.charAt(2) + hex.charAt(2);
        }
        return hex.matches("[0-9a-fA-F]{6}") ? hex.toLowerCase() : null;
    }

    private static float[] rgbToHsl(int red, int green, int blue) {
        float r = red / 255f;
        float g = green / 255f;
        float b = blue / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h;
        float s;
        float l = (max + min) / 2f;

        if (max == min) {
            h = 0f;
            s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h *= 60f;
        }
        return new float[]{h, s, l};
    }
}
