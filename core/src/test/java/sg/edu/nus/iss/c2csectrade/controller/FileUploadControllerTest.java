package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Only non-empty images up to 10 MB are stored.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private FileUploadController fileUploadController;

    @Test
    void anImageIsStoredAndItsUrlReturned() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2});
        when(fileStorageService.uploadFile(image)).thenReturn("https://media.example/a.png");

        ResponseEntity<?> response = fileUploadController.uploadFile(image);

        assertThat((Map<Object, Object>) response.getBody()).containsEntry("url", "https://media.example/a.png");
    }

    @Test
    void emptyOversizedAndNonImageFilesAreRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        MockMultipartFile huge = new MockMultipartFile("file", "a.png", "image/png", new byte[10 * 1024 * 1024 + 1]);
        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});
        MockMultipartFile untyped = new MockMultipartFile("file", "a", null, new byte[]{1});

        assertThat(message(fileUploadController.uploadFile(empty))).contains("must not be empty");
        assertThat(message(fileUploadController.uploadFile(huge))).contains("10 MB");
        assertThat(message(fileUploadController.uploadFile(text))).contains("Only image");
        assertThat(message(fileUploadController.uploadFile(untyped))).contains("Only image");
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void aStorageFailureIsA500() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        when(fileStorageService.uploadFile(image)).thenThrow(new RuntimeException("bucket down"));

        ResponseEntity<?> response = fileUploadController.uploadFile(image);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat((Map<Object, Object>) response.getBody()).containsEntry("success", false);
    }

    private static String message(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        return String.valueOf(((Map<Object, Object>) response.getBody()).get("message"));
    }
}
