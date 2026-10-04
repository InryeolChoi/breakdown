package com.breakground.report;

import com.breakground.anonymoususer.AnonymousUserCookies;
import com.breakground.report.dto.ReportCreateRequest;
import com.breakground.report.dto.ReportResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rooms/{roomId}/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ReportResponse> create(
            @PathVariable Integer roomId, @Valid @RequestBody ReportCreateRequest request,
            @CookieValue(value = AnonymousUserCookies.NAME, required = false) String rawToken,
            HttpServletRequest servletRequest) {
        Report report = service.create(roomId, rawToken, request.messageId(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, AnonymousUserCookies.create(rawToken, servletRequest.isSecure()).toString())
                .body(ReportResponse.from(report));
    }
}
