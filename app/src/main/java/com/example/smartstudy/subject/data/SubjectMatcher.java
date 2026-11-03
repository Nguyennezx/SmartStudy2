package com.example.smartstudy.subject.data;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chỉ tên khớp đầy đủ mới được chọn tự động; gợi ý gần giống phải do người dùng chọn. */
public final class SubjectMatcher {
    private SubjectMatcher() { }
    public static final class Result {
        public final List<Subject> exact = new ArrayList<>();
        public final List<Subject> suggestions = new ArrayList<>();
        public final List<Subject> archived = new ArrayList<>();
        public long uniqueId() { return exact.size() == 1 ? exact.get(0).getId() : 0; }
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+", " ").trim();
    }
    private static String folded(String value) {
        return Normalizer.normalize(normalize(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("[^a-z0-9]+", " ").trim();
    }
    public static Result find(String name, List<Subject> active, List<Subject> archived) {
        Result result = new Result();
        String wanted = normalize(name), near = folded(name);
        if (wanted.isEmpty()) return result;
        for (Subject subject : active) {
            if (subject.isArchived() || subject.getId() <= 0) continue;
            if (wanted.equals(normalize(subject.getName()))) result.exact.add(subject);
            else {
                String candidate = folded(subject.getName());
                if (!near.isEmpty() && (near.equals(candidate)
                        || near.length() >= 8 && candidate.length() >= 8
                        && (candidate.startsWith(near + " ") || near.startsWith(candidate + " ")))) {
                    result.suggestions.add(subject);
                }
            }
        }
        for (Subject subject : archived) {
            if (subject.isArchived() && wanted.equals(normalize(subject.getName()))) result.archived.add(subject);
        }
        return result;
    }
}
