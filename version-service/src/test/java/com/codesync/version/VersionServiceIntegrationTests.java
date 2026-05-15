package com.codesync.version;

import com.codesync.version.dto.CreateBranchRequest;
import com.codesync.version.dto.CreateSnapshotRequest;
import com.codesync.version.dto.SnapshotDiffResponse;
import com.codesync.version.dto.SnapshotResponse;
import com.codesync.version.dto.TagSnapshotRequest;
import com.codesync.version.service.VersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VersionServiceIntegrationTests {

    @Autowired
    private VersionService versionService;

    @Test
    void snapshotWorkflowSupportsHistoryRestoreDiffBranchAndTag() {
        SnapshotResponse first = versionService.createSnapshot(
                1001L,
                null,
                new CreateSnapshotRequest(
                        2001L,
                        3001L,
                        "Initial snapshot",
                        "line one\nline two",
                        null
                )
        );

        SnapshotResponse second = versionService.createSnapshot(
                1001L,
                null,
                new CreateSnapshotRequest(
                        2001L,
                        3001L,
                        "Updated snapshot",
                        "line one\nline three",
                        "main"
                )
        );

        assertThat(first.hash()).hasSize(64);
        assertThat(first.branch()).isEqualTo("main");
        assertThat(second.parentSnapshotId()).isEqualTo(first.snapshotId());

        SnapshotResponse latest = versionService.getLatestSnapshot(3001L, null);
        assertThat(latest.snapshotId()).isEqualTo(second.snapshotId());

        SnapshotDiffResponse diff = versionService.diffSnapshots(first.snapshotId(), second.snapshotId(), null);
        assertThat(diff.diff()).contains("- line two");
        assertThat(diff.diff()).contains("+ line three");

        SnapshotResponse restored = versionService.restoreSnapshot(first.snapshotId(), 1002L, null);
        assertThat(restored.content()).isEqualTo(first.content());
        assertThat(restored.parentSnapshotId()).isEqualTo(second.snapshotId());

        SnapshotResponse branchSnapshot = versionService.createBranch(
                1003L,
                null,
                new CreateBranchRequest(first.snapshotId(), "feature-sync", null)
        );
        assertThat(branchSnapshot.branch()).isEqualTo("feature-sync");
        assertThat(branchSnapshot.parentSnapshotId()).isEqualTo(first.snapshotId());

        SnapshotResponse tagged = versionService.tagSnapshot(null, new TagSnapshotRequest(first.snapshotId(), "v1.0.0"));
        assertThat(tagged.tag()).isEqualTo("v1.0.0");

        List<SnapshotResponse> history = versionService.getFileHistory(3001L, null);
        assertThat(history).hasSize(4);
        assertThat(history.get(0).snapshotId()).isEqualTo(branchSnapshot.snapshotId());
        assertThat(history.get(1).snapshotId()).isEqualTo(restored.snapshotId());
    }
}
