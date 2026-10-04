package com.breakground.anonymoususer;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AnonymousUserService {
    private static final int MAX_NICKNAME_LENGTH = 30;
    private static final Duration IDLE_TIMEOUT = Duration.ofDays(7);
    private final SecureRandom secureRandom = new SecureRandom();
    private final AnonymousUserRepository anonymousUserRepository;
    private final KoreanNicknameGenerator koreanNicknameGenerator;

    public AnonymousUserService(
            AnonymousUserRepository anonymousUserRepository,
            KoreanNicknameGenerator koreanNicknameGenerator
    ) {
        this.anonymousUserRepository = anonymousUserRepository;
        this.koreanNicknameGenerator = koreanNicknameGenerator;
    }

    @Transactional
    public AnonymousUserSession getOrCreateAnonymousUser(String nickname, String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        Optional<AnonymousUser> user = findByToken(rawToken);
        if (user.isEmpty()) {
            return createSession(nickname, now);
        }

        AnonymousUser existingUser = user.orElseThrow();
        existingUser.liftBanIfExpired(now);
        if (existingUser.isBanActiveAt(now)) {
            return new AnonymousUserSession(existingUser, rawToken);
        }
        if (isExpired(existingUser, now)) {
            return createSession(nickname, now);
        }

        existingUser.recordVisit(now);
        return new AnonymousUserSession(existingUser, rawToken);
    }

    // 메시지/신고 작성에서는 새 사용자를 만들지 않고 기존 쿠키를 검증한다.
    @Transactional
    public AnonymousUser requireActiveUser(String rawToken, LocalDateTime now) {
        AnonymousUser user = findByToken(rawToken).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "익명 사용자를 먼저 준비해주세요."));
        user.liftBanIfExpired(now);
        if (user.isBanActiveAt(now)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "현재 차단된 사용자입니다.");
        }
        if (isExpired(user, now)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "익명 사용자 식별이 만료됐습니다.");
        }
        user.recordVisit(now);
        return user;
    }

    private Optional<AnonymousUser> findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return anonymousUserRepository.findByTokenHash(hashToken(rawToken));
    }

    private boolean isExpired(AnonymousUser user, LocalDateTime now) {
        Duration idle = Duration.between(user.getLastSeenAt(), now);
        return idle.compareTo(IDLE_TIMEOUT) >= 0;
    }

    private AnonymousUserSession createSession(String nickname, LocalDateTime now) {
        String rawToken = generateToken();
        AnonymousUser user = new AnonymousUser(
                resolveNickname(nickname), now.toLocalDate(), now, hashToken(rawToken));
        return new AnonymousUserSession(anonymousUserRepository.save(user), rawToken);
    }

    private String resolveNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return koreanNicknameGenerator.generate();
        }
        String strippedNickname = nickname.strip();
        if (strippedNickname.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("닉네임은 30자 이하여야 합니다.");
        }
        return strippedNickname;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
