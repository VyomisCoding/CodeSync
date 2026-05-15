package com.codesync.file.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PathUtil {

    private PathUtil() {
    }

    public static String build(String parent, String name) {
        String cleanName = cleanSegment(name);
        String cleanParent = normalizeParent(parent);

        if (cleanParent.isBlank()) {
            return cleanName;
        }

        return cleanParent + "/" + cleanName;
    }

    public static String normalizeParent(String parent) {
        return normalize(parent);
    }

    public static String parentPath(String fullPath) {
        int last = fullPath.lastIndexOf("/");

        if (last == -1) {
            return "";
        }

        return fullPath.substring(0, last);
    }

    public static String replacePrefix(String value, String oldPrefix, String newPrefix) {
        if (!value.equals(oldPrefix) && !value.startsWith(oldPrefix + "/")) {
            return value;
        }

        return newPrefix + value.substring(oldPrefix.length());
    }

    private static String cleanSegment(String segment) {
        String normalized = normalize(segment);

        if (normalized.isBlank() || normalized.contains("/")) {
            throw new IllegalArgumentException("Path segment must be a single non-empty name");
        }

        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\\", "/").trim();

        return Arrays.stream(normalized.split("/+"))
                .filter(part -> !part.isBlank())
                .peek(PathUtil::rejectUnsafeSegment)
                .collect(Collectors.joining("/"));
    }

    private static void rejectUnsafeSegment(String segment) {
        if (segment.equals(".") || segment.equals("..")) {
            throw new IllegalArgumentException("Path cannot contain relative segments");
        }
    }
}
