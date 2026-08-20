package chungbuk.cityfarmerplus.farm.ownership.service;

record StoredOwnershipDocument(
        String originalFilename,
        String storageKey,
        String contentType,
        long sizeBytes,
        String sha256
) {
}
