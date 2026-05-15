package com.codesync.collab.repository;

import com.codesync.collab.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    @Query("""
            select participant
            from Participant participant
            where participant.sessionId = :sessionId
            order by participant.joinedAt asc
            """)
    List<Participant> findParticipantsBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
            select count(participant)
            from Participant participant
            where participant.sessionId = :sessionId
              and participant.leftAt is null
            """)
    long countParticipants(@Param("sessionId") UUID sessionId);

    Optional<Participant> findBySessionIdAndUserIdAndLeftAtIsNull(UUID sessionId, Long userId);

    Optional<Participant> findByParticipantIdAndSessionId(Long participantId, UUID sessionId);
}
