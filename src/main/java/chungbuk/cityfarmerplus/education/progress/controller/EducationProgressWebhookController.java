package chungbuk.cityfarmerplus.education.progress.controller;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.progress.dto.EducationEnrollmentResponse;
import chungbuk.cityfarmerplus.education.progress.dto.EducationProgressEventRequest;
import chungbuk.cityfarmerplus.education.progress.security.EducationProgressWebhookVerifier;
import chungbuk.cityfarmerplus.education.progress.service.EducationProgressEventService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/integrations/education")
@RequiredArgsConstructor
public class EducationProgressWebhookController {

    public static final String TIMESTAMP_HEADER = "X-Education-Event-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Education-Signature";

    private final EducationProgressWebhookVerifier verifier;
    private final EducationProgressEventService eventService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(
            path = "/progress-events",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EducationEnrollmentResponse> receive(
            @RequestHeader(TIMESTAMP_HEADER) String timestamp,
            @RequestHeader(SIGNATURE_HEADER) String signature,
            @RequestBody byte[] requestBody
    ) {
        String payloadSha256 = verifier.verify(timestamp, signature, requestBody);
        EducationProgressEventRequest request = readRequest(requestBody);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return ResponseEntity.ok(eventService.ingest(request, payloadSha256));
    }

    private EducationProgressEventRequest readRequest(byte[] requestBody) {
        try {
            return objectMapper.readValue(
                    requestBody,
                    EducationProgressEventRequest.class
            );
        } catch (JacksonException exception) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EDUCATION_PROGRESS_EVENT",
                    "교육 진도 이벤트 요청 본문이 올바르지 않습니다."
            );
        }
    }
}
