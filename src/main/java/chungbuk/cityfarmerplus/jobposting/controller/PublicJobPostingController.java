package chungbuk.cityfarmerplus.jobposting.controller;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.jobposting.dto.PublicJobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.PublicRecruitmentStatus;
import chungbuk.cityfarmerplus.jobposting.service.PublicJobPostingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
@Validated
public class PublicJobPostingController {

    private final PublicJobPostingService service;

    @GetMapping
    public PageResponse<PublicJobPostingResponse> getOpenPostings(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) ChungbukCityCounty region,
            @RequestParam(required = false) @Size(max = 50) String crop,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @RequestParam(required = false) @Size(max = 100) String workType,
            @RequestParam(defaultValue = "OPEN")
            PublicRecruitmentStatus recruitmentStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.getPostings(
                AuthenticatedUser.optionalId(authentication),
                keyword,
                region,
                crop,
                dateFrom,
                dateTo,
                workType,
                recruitmentStatus,
                page,
                size
        );
    }

    @GetMapping("/{postingId}")
    public PublicJobPostingResponse getOpenPosting(
            Authentication authentication,
            @PathVariable Long postingId,
            @RequestParam(defaultValue = "false") boolean includeClosed
    ) {
        return service.getPosting(
                AuthenticatedUser.optionalId(authentication),
                postingId,
                includeClosed
        );
    }
}
