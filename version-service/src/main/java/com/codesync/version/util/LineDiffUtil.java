package com.codesync.version.util;

import java.util.Arrays;
import java.util.List;

public final class LineDiffUtil {

    private LineDiffUtil() {
    }

    public static String diff(Long leftSnapshotId, String leftContent, Long rightSnapshotId, String rightContent) {
        List<String> leftLines = toLines(leftContent);
        List<String> rightLines = toLines(rightContent);

        int[][] lcs = buildLcs(leftLines, rightLines);
        StringBuilder diff = new StringBuilder()
                .append("--- Snapshot ").append(leftSnapshotId).append(System.lineSeparator())
                .append("+++ Snapshot ").append(rightSnapshotId).append(System.lineSeparator());

        int i = 0;
        int j = 0;
        boolean changed = false;

        while (i < leftLines.size() && j < rightLines.size()) {
            String left = leftLines.get(i);
            String right = rightLines.get(j);
            if (left.equals(right)) {
                i++;
                j++;
                continue;
            }

            changed = true;
            if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                diff.append("- ").append(left).append(System.lineSeparator());
                i++;
            } else {
                diff.append("+ ").append(right).append(System.lineSeparator());
                j++;
            }
        }

        while (i < leftLines.size()) {
            changed = true;
            diff.append("- ").append(leftLines.get(i++)).append(System.lineSeparator());
        }

        while (j < rightLines.size()) {
            changed = true;
            diff.append("+ ").append(rightLines.get(j++)).append(System.lineSeparator());
        }

        if (!changed) {
            diff.append("No differences").append(System.lineSeparator());
        }

        return diff.toString();
    }

    private static List<String> toLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.split("\\R", -1));
    }

    private static int[][] buildLcs(List<String> leftLines, List<String> rightLines) {
        int[][] lcs = new int[leftLines.size() + 1][rightLines.size() + 1];
        for (int i = leftLines.size() - 1; i >= 0; i--) {
            for (int j = rightLines.size() - 1; j >= 0; j--) {
                if (leftLines.get(i).equals(rightLines.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        return lcs;
    }
}
