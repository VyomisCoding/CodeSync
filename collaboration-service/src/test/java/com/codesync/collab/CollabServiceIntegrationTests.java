package com.codesync.collab;

import com.codesync.collab.dto.CodeChangeMessage;
import com.codesync.collab.dto.CollabSessionRequest;
import com.codesync.collab.dto.CursorUpdateRequest;
import com.codesync.collab.dto.EndSessionRequest;
import com.codesync.collab.dto.JoinSessionRequest;
import com.codesync.collab.dto.LeaveSessionRequest;
import com.codesync.collab.entity.ParticipantRole;
import com.codesync.collab.entity.SessionStatus;
import com.codesync.collab.service.CollabService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "codesync.collab.remote-validation.enabled=false")
@Transactional
class CollabServiceIntegrationTests {

    @Autowired
    private CollabService collabService;

    @Test
    void collaborationFlowWorksEndToEnd() {
        var created = collabService.createSession(
                1001L,
                null,
                new CollabSessionRequest(2001L, 3001L, "java", 3, false, null)
        );

        assertThat(created.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(created.participantCount()).isEqualTo(1);

        var joined = collabService.joinSession(
                1002L,
                null,
                new JoinSessionRequest(created.sessionId(), ParticipantRole.EDITOR, null)
        );

        assertThat(joined.role()).isEqualTo(ParticipantRole.EDITOR);

        collabService.broadcastChange(
                1002L,
                created.sessionId(),
                new CodeChangeMessage(null, null, null, null, "class Demo {}", null)
        );

        var updated = collabService.updateCursor(
                1002L,
                new CursorUpdateRequest(created.sessionId(), 12, 7)
        );

        assertThat(updated.cursorLine()).isEqualTo(12);
        assertThat(updated.cursorCol()).isEqualTo(7);
        assertThat(collabService.getParticipants(created.sessionId(), null)).hasSize(2);

        collabService.leaveSession(1002L, new LeaveSessionRequest(created.sessionId()));
        collabService.endSession(1001L, new EndSessionRequest(created.sessionId()));

        var ended = collabService.getSessionById(created.sessionId(), null);
        assertThat(ended.status()).isEqualTo(SessionStatus.ENDED);
    }
}
