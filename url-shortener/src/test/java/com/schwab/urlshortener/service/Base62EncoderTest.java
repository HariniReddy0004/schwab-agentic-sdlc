package com.schwab.urlshortener.service;

import com.schwab.urlshortener.testing.MicroTest;

import java.util.HashSet;
import java.util.Set;

public class Base62EncoderTest {
    private final Base62Encoder encoder = new Base62Encoder();

    @MicroTest.Test
    public void generatesRequestedLength() {
        String code = encoder.generate(7);
        MicroTest.assertEquals(7, code.length(), "generated code should have requested length");
    }

    @MicroTest.Test
    public void generatesDistinctCodesAcrossManyCalls() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(encoder.generate(7));
        }
        MicroTest.assertTrue(seen.size() > 490, "expected near-unique codes across 500 generations, got " + seen.size());
    }

    @MicroTest.Test
    public void validatesAliasCharset() {
        MicroTest.assertTrue(encoder.isValidAlias("MyAlias1"), "alphanumeric alias should be valid");
        MicroTest.assertFalse(encoder.isValidAlias("bad alias!"), "alias with spaces/punctuation should be invalid");
        MicroTest.assertFalse(encoder.isValidAlias(""), "empty alias should be invalid");
        MicroTest.assertFalse(encoder.isValidAlias(null), "null alias should be invalid");
    }
}
