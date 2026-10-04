package com.breakground.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// 신고 대상과 증거 내용은 입력받지 않고 서버 원본에서 결정한다.
public record ReportCreateRequest(
        @NotNull(message = "신고할 메시지 ID가 필요합니다.") UUID messageId,
        @NotBlank(message = "신고 사유를 입력해주세요.")
        @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.") String reason) {
}
