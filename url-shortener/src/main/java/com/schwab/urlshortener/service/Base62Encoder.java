package com.schwab.urlshortener.service;

import java.security.SecureRandom;

/** Generates random Base62 short codes. Collisions are handled by the repository's insertIfAbsent + retry loop. */
public final class Base62Encoder {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public boolean isValidAlias(String alias) {
        if (alias == null || alias.isBlank() || alias.length() > 32) return false;
        for (int i = 0; i < alias.length(); i++) {
            if (ALPHABET.indexOf(alias.charAt(i)) < 0) return false;
        }
        return true;
    }
}
