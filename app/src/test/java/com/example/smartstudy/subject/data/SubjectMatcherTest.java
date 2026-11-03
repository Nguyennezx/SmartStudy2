package com.example.smartstudy.subject.data;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;

public class SubjectMatcherTest {
    private Subject subject(long id, String name, boolean archived) {
        return new Subject(id, name, null, "#6C7EE1", archived);
    }
    @Test public void uniqueExactIgnoresCaseWhitespaceAndUnicodeComposition() {
        assertEquals(1, SubjectMatcher.find("  MẠNG\u00a0MÁY TÍNH ",
                Arrays.asList(subject(1, "Mạng máy tính", false)), Collections.emptyList()).uniqueId());
        assertEquals(1, SubjectMatcher.find(java.text.Normalizer.normalize("Toán", java.text.Normalizer.Form.NFD),
                Arrays.asList(subject(1, "Toán", false)), Collections.emptyList()).uniqueId());
    }
    @Test public void sameNameAcrossSemestersIsAmbiguous() {
        SubjectMatcher.Result result = SubjectMatcher.find("Toán", Arrays.asList(subject(1, "Toán", false),
                subject(2, "Toán", false)), Collections.emptyList());
        assertEquals(0, result.uniqueId());
        assertEquals(2, result.exact.size());
    }
    @Test public void accentlessOrPracticalVariantIsSuggestionOnly() {
        SubjectMatcher.Result result = SubjectMatcher.find("Mang may tinh",
                Arrays.asList(subject(1, "Mạng máy tính", false), subject(2, "Mạng máy tính (Thực hành)", false)),
                Collections.emptyList());
        assertEquals(0, result.uniqueId());
        assertEquals(2, result.suggestions.size());
    }
    @Test public void archivedSubjectIsNeverSelected() {
        Subject archived = subject(1, "Toán", true);
        SubjectMatcher.Result result = SubjectMatcher.find("Toán", Arrays.asList(archived), Arrays.asList(archived));
        assertEquals(0, result.uniqueId());
        assertEquals(1, result.archived.size());
    }
    @Test public void blankUnrelatedAndCodeDoNotGuess() {
        for (String name : new String[]{"", " ", "INT1306", "Lập trình mạng"}) {
            SubjectMatcher.Result result = SubjectMatcher.find(name, Arrays.asList(subject(1, "Mạng máy tính", false)), Collections.emptyList());
            assertEquals(0, result.uniqueId());
            assertTrue(result.suggestions.isEmpty());
        }
    }
}
