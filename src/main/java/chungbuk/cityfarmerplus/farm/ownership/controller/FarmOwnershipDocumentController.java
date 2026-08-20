package chungbuk.cityfarmerplus.farm.ownership.controller;

import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipDocumentDownload;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/farm-ownership-documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
public class FarmOwnershipDocumentController {

    private final FarmOwnershipQueryService queryService;

    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> download(
            Authentication authentication,
            @PathVariable Long documentId
    ) {
        FarmOwnershipDocumentDownload download = queryService.download(
                getUserId(authentication),
                documentId
        );
        return ResponseEntity.ok()
                .contentType(resolveContentType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        download.originalFilename(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(download.resource());
    }

    private Long getUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw AuthException.invalidAuthentication();
        }
    }

    private MediaType resolveContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
