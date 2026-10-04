package com.breakground.anonymoususer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, Integer> {
    Optional<AnonymousUser> findByTokenHash(String tokenHash);
}
