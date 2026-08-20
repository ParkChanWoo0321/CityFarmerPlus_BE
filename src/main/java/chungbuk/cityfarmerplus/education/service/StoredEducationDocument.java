package chungbuk.cityfarmerplus.education.service;

record StoredEducationDocument(
        String originalFilename,
        String storageKey,
        String contentType,
        long sizeBytes,
        String sha256
) {
}
