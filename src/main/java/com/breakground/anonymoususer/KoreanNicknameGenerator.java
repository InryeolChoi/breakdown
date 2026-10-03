package com.breakground.anonymoususer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class KoreanNicknameGenerator {
    private static final List<String> MODIFIERS = List.of(
            "졸린", "느긋한", "나른한", "바쁜", "여유로운",
            "배고픈", "심심한", "조용한", "활기찬", "몽롱한",
            "커피마시는", "차마시는", "퇴근기다리는", "점심기다리는", "회의끝난",
            "잠깐쉬는", "집중하는", "딴생각하는", "창밖보는", "구름보는",
            "메모하는", "산책하고싶은", "간식찾는", "음악듣는", "웃고있는",
            "차분한", "용감한", "둥글둥글한", "반짝이는", "포근한",
            "씩씩한", "깜빡졸던", "키보드치는", "의자에기댄", "야근중인",
            "퇴근한", "점심먹은", "저녁고민하는", "월급기다리는", "금요일기다리는",
            "월요병걸린", "햇살좋은", "비를기다리는", "바람따라가는", "배터리충전중인",
            "알람끄고싶은", "카페가고싶은", "할일미루는", "조용히숨은", "소소한"
    );

    private static final List<String> NOUNS = List.of(
            "고양이", "강아지", "참새", "수달", "햄스터",
            "펭귄", "너구리", "부엉이", "판다", "코알라",
            "고슴도치", "라쿤", "여우", "토끼", "다람쥐",
            "오리", "거북이", "문어", "고래", "돌고래",
            "바다표범", "미어캣", "사막여우", "카피바라", "나무늘보",
            "병아리", "청설모", "두더지", "까치", "제비",
            "물개", "사자", "호랑이", "곰", "원숭이",
            "코끼리", "기린", "얼룩말", "알파카", "파랑새"
    );

    public String generate() {
        String modifier = MODIFIERS.get(ThreadLocalRandom.current().nextInt(MODIFIERS.size()));
        String noun = NOUNS.get(ThreadLocalRandom.current().nextInt(NOUNS.size()));
        int number = ThreadLocalRandom.current().nextInt(1_000);
        String numberSuffix = Integer.toString(number);
        numberSuffix = "0".repeat(3 - numberSuffix.length()) + numberSuffix;
        return modifier + noun + numberSuffix;
    }
}
