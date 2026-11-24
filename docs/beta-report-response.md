# 비공개 베타 신고 대응

베타 운영자는 신고가 접수되면 서버에 저장된 증거를 확인하고 베타 안내에 적힌 이용 규칙을 기준으로 조치를 판단한다. 신고 횟수만으로 차단하지 않는다. 운영 데이터베이스에서 조치가 필요하면 별도 운영 승인을 받는다.

## 접수 상태 확인

- `201`이면 서버가 원본에서 복사한 증거를 저장했다.
- `409`이면 같은 신고자의 같은 메시지 신고가 이미 있다. 신고 사유를 다시 보내도 새 신고가 생기지 않는다.
- `410`이면 열려 있는 방에서 확인한 원본이 누락됐거나 만료됐다. 닫힌 방에서 제출하면 `403`을 받을 수 있다. 스크린샷이나 브라우저 본문으로 서버 증거를 대체하지 않는다.
- `429`이면 입력을 유지하고 안내 후 재시도할 수 있다. 채팅 전송에 대한 `429`는 WebSocket 오류 메시지이며 연결을 종료하지 않는다.

원본을 신고할 수 있는 시간은 작성 후 15분과 방 종료 중 빠른 쪽까지다. 접수된 증거는 신고 시각부터 30일간 보관한다.

## 읽기 전용 열람과 인계

승인된 운영자 세션에서 최근 접수의 식별자부터 확인한다. 아래 SQL은 서울 시간의 DB 저장 형식을 기준으로 하며, psql에 숫자 신고 ID를 지정했을 때 한 건의 내용을 열람한다.

```sql
BEGIN READ ONLY;
SELECT id, room_id, created_at
FROM report
WHERE evidence_expires_at > (now() AT TIME ZONE 'Asia/Seoul')
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- psql에서 먼저 \set report_id <확인할 신고 ID>를 실행한다.
SELECT id, room_id, reported_user_id, message_event_id, message_sent_at,
       reason, evidence_content, evidence_expires_at
FROM report
WHERE id = :'report_id'::integer
  AND evidence_expires_at > (now() AT TIME ZONE 'Asia/Seoul');
COMMIT;
```

1. 신고 사유와 서버 원본을 비교한다. 증거가 부족하면 보류하고 불명확한 점을 남긴다.
2. 조치가 필요하면 담당 운영자와 승인 경로로 인계한다. 이용 제한 기간은 기존 7일 정책을 따른다.
3. 담당자, 신고 ID, 확인 시각, 판단과 승인 상태를 접근이 제한된 운영 기록에 남긴다. 원본 본문, 신고 사유, 세션 토큰은 GitHub나 공개 채널에 복사하지 않는다.
4. 리허설에서는 테스트 신고 한 건을 접수·열람한 뒤 `테스트 메시지 확인, 조치 없음`으로 종료한다.

[서비스 운영 정책](decisions.md)
