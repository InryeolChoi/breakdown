package com.breakground.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    @Transactional(readOnly = true)
    Optional<Message> findByIdAndExpiresAtGreaterThan(UUID id, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("delete from Message m where m.deleteAfter <= :now")
    int deleteReadyForCleanup(@Param("now") LocalDateTime now);
}
