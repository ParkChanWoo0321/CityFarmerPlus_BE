package chungbuk.cityfarmerplus.farm.ownership.dto;

import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipDocument;

public record FarmOwnershipDocumentResponse(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes
) {

    public static FarmOwnershipDocumentResponse from(FarmOwnershipDocument document) {
        return new FarmOwnershipDocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes()
        );
    }
}
