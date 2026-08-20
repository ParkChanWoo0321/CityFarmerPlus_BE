package chungbuk.cityfarmerplus.farm.ownership.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileExceptionHandler;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipDocumentDownload;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmOwnershipDocumentController.class)
@Import({
        FarmProfileExceptionHandler.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class FarmOwnershipDocumentControllerWebTest {

    private static final String ENDPOINT = "/api/farm-ownership-documents/300/file";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FarmOwnershipQueryService queryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void farmOwnerDownloadsDocumentWithSafeAttachmentHeaders() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("1", "FARM"));
        when(queryService.download(1L, 300L)).thenReturn(
                new FarmOwnershipDocumentDownload(
                        new ByteArrayResource("proof".getBytes()),
                        "토지대장.pdf",
                        "application/pdf",
                        5L
                )
        );

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("proof".getBytes()))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "5"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION));

        verify(queryService).download(1L, 300L);
    }

    @Test
    void urbanFarmerCannotDownloadOwnershipDocument() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("2", "URBAN_FARMER"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(queryService);
    }

    private Jwt jwt(String subject, String role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(role + "-jwt")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
    }
}
