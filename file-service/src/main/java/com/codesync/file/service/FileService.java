package com.codesync.file.service;

import com.codesync.file.dto.*;
import com.codesync.file.entity.CodeFile;

import java.util.List;
import java.util.Map;

public interface FileService {

    CodeFile createFile(CreateFileRequest request, Long actorUserId, String authorizationHeader);

    CodeFile createFolder(CreateFolderRequest request, Long actorUserId, String authorizationHeader);

    CodeFile getById(Long fileId, String authorizationHeader);

    String getContent(Long fileId, String authorizationHeader);

    CodeFile updateContent(Long fileId,
                           UpdateContentRequest request,
                           Long actorUserId,
                           String authorizationHeader);

    CodeFile rename(Long fileId,
                    RenameRequest request,
                    Long actorUserId,
                    String authorizationHeader);

    CodeFile move(Long fileId,
                  MoveRequest request,
                  Long actorUserId,
                  String authorizationHeader);

    String delete(Long fileId, Long actorUserId, String authorizationHeader);

    String restore(Long fileId, Long actorUserId, String authorizationHeader);

    List<CodeFile> listByProject(Long projectId, String authorizationHeader);

    List<CodeFile> search(Long projectId,
                          String keyword,
                          String authorizationHeader);

    Map<String, Object> getTree(Long projectId, String authorizationHeader);

    void copyProjectFiles(Long sourceProjectId, Long targetProjectId, Long actorUserId, String authorizationHeader);
}
