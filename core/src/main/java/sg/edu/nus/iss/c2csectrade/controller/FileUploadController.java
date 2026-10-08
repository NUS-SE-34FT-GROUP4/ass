package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * General file upload controller
 * Used for review images, product images and similar uploads
 */
@RestController
@RequestMapping("/api")
public class FileUploadController {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * General file upload endpoint
     * Accepts review images, product images and other files
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Reject an empty file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("File must not be empty"));
            }

            // Check the file size (10 MB limit)
            long maxSize = 10 * 1024 * 1024; // 10MB
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("File size must not exceed 10 MB"));
            }

            // Check the file type (images only)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Only image files are supported"));
            }

            // Upload the file
            String fileUrl = fileStorageService.uploadFile(file);

            // Return the file URL
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", fileUrl);
            response.put("message", "File uploaded");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("File upload failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(createErrorResponse("File upload failed: " + e.getMessage()));
        }
    }

    /**
     * Build an error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}

