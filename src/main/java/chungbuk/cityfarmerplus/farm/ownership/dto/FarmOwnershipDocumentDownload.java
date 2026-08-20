package chungbuk.cityfarmerplus.farm.ownership.dto;

import org.springframework.core.io.Resource;

public record FarmOwnershipDocumentDownload(
        Resource resource,
        String originalFilename,
        String contentType,
        long sizeBytes
) {
}
