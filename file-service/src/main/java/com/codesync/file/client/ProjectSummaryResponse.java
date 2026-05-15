package com.codesync.file.client;

import lombok.Data;

@Data
public class ProjectSummaryResponse {

    private Long projectId;
    private Long ownerId;
    private String visibility;
    private Boolean isArchived;
}
