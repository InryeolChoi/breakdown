package com.breakground.anonymoususer;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnonymousUserService {
    private static final int MAX_NICKNAME_LENGTH = 30;
    private final AnonymousUserRepository anonymousUserRepository;
    private final KoreanNicknameGenerator koreanNicknameGenerator;

    public AnonymousUserService(
            AnonymousUserRepository anonymousUserRepository,
            KoreanNicknameGenerator koreanNicknameGenerator
    ) {
        this.anonymousUserRepository = anonymousUserRepository;
        this.koreanNicknameGenerator = koreanNicknameGenerator;
    }

    public AnonymousUser addNewAnonymousUser(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            nickname = koreanNicknameGenerator.generate();
        }
        else {
            nickname = nickname.strip();
            if (nickname.length() > MAX_NICKNAME_LENGTH) {
                throw new IllegalArgumentException("닉네임은 30자 이하여야 합니다.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        AnonymousUser anonymousUser = new AnonymousUser(nickname, now.toLocalDate(), now);
        return anonymousUserRepository.save(anonymousUser);
    }

}
