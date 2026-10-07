package lut.cn.c2cplatform.controller;

import lut.cn.c2cplatform.dto.ChatMessageDTO;
import lut.cn.c2cplatform.entity.ChatMessage;
import lut.cn.c2cplatform.entity.Product;
import lut.cn.c2cplatform.entity.Report;
import lut.cn.c2cplatform.entity.User;
import lut.cn.c2cplatform.mapper.UserMapper;
import lut.cn.c2cplatform.service.ChatMessageService;
import lut.cn.c2cplatform.service.ProductService;
import lut.cn.c2cplatform.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        // Return users together with their roles
        return ResponseEntity.ok(userMapper.selectAllWithRoles());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userMapper.deleteById(id);
            return ResponseEntity.ok("User deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting user: " + e.getMessage());
        }
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<lut.cn.c2cplatform.dto.ReportDTO>> getAllReports() {
        List<Report> reports = reportService.getAllReports();
        List<lut.cn.c2cplatform.dto.ReportDTO> reportDTOs = new java.util.ArrayList<>();

        for (Report report : reports) {
            // Load the product
            Product product = productService.getProductById(report.getProductId());
            String productName = product != null ? product.getName() : "Unknown product";

            // Load the reporter
            User reporter = userMapper.selectById(report.getReporterId());
            String reporterUsername = reporter != null ? reporter.getUsername() : "Unknown user";
            String reporterDisplayName = reporter != null ? reporter.getDisplayName() : reporterUsername;

            lut.cn.c2cplatform.dto.ReportDTO dto = lut.cn.c2cplatform.dto.ReportDTO.builder()
                .id(report.getId())
                .productId(report.getProductId())
                .productName(productName)
                .reporterId(report.getReporterId())
                .reporterUsername(reporterUsername)
                .reporterDisplayName(reporterDisplayName)
                .reason(report.getReason())
                .description(report.getDescription())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();

            reportDTOs.add(dto);
        }

        return ResponseEntity.ok(reportDTOs);
    }

    @PutMapping("/reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateReportStatus(@PathVariable Long id, @RequestBody Report report) {
        try {
            reportService.updateReportStatus(id, report.getStatus());
            return ResponseEntity.ok("Report status updated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error updating report status: " + e.getMessage());
        }
    }

    @PutMapping("/products/{productId}/delist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delistProduct(@PathVariable Long productId, @RequestParam Long reportId) {
        try {
            System.out.println("[DELIST] Delisting product, product ID: " + productId + ", report ID: " + reportId);

            Product product = productService.getProductById(productId);
            if (product == null) {
                System.err.println("[DELIST] Product not found: " + productId);
                return ResponseEntity.notFound().build();
            }

            // Delist the product
            productService.delistProduct(productId);
            System.out.println("[DELIST] Product delisted: " + product.getName());

            // Notify the product owner
            try {
                System.out.println("[DELIST] ========== Notifying product owner ==========");
                System.out.println("[DELIST] Looking up product owner, user ID: " + product.getUserId());
                User owner = userMapper.selectById(product.getUserId());

                if (owner == null) {
                    System.err.println("[DELIST] ❌ Error: no user with ID " + product.getUserId());
                } else if (owner.getUsername() == null || owner.getUsername().isEmpty()) {
                    System.err.println("[DELIST] ❌ Error: user exists but username is empty, user ID: " + owner.getId());
                } else {
                    System.out.println("[DELIST] ✅ Found owner: " + owner.getUsername() + " (ID: " + owner.getId() + ")");
                    String ownerMessage = "Your product '" + product.getName() + "' has been delisted by an administrator for violating platform rules. Please contact support if you have questions.";

                    // Save the message to the database
                    ChatMessage savedOwnerMessage = chatMessageService.saveMessage("System", owner.getUsername(), ownerMessage, true);
                    System.out.println("[DELIST] ✅ Owner message saved, message ID: " + savedOwnerMessage.getId());

                    // Build the WebSocket message
                    ChatMessageDTO ownerResponseDTO = ChatMessageDTO.builder()
                        .sender("System")
                        .recipient(owner.getUsername())
                        .content(savedOwnerMessage.getContent())
                        .timestamp(savedOwnerMessage.getTimestamp())
                        .isSystemMessage(true)
                        .build();

                    System.out.println("[DELIST] 📤 Sending WebSocket message to owner: " + owner.getUsername());
                    System.out.println("[DELIST] Message: " + ownerMessage);

                    try {
                        messagingTemplate.convertAndSendToUser(
                            owner.getUsername(),
                            "/queue/private",
                            ownerResponseDTO
                        );
                        System.out.println("[DELIST] ✅✅ WebSocket message sent to product owner: " + owner.getUsername());
                    } catch (Exception wsError) {
                        System.err.println("[DELIST] ⚠️ WebSocket send failed (message is saved in the database): " + wsError.getMessage());
                        wsError.printStackTrace();
                    }
                }
                System.out.println("[DELIST] ========== Product owner notified ==========");
            } catch (Exception e) {
                System.err.println("[DELIST] ❌ Failed to notify product owner: " + e.getMessage());
                e.printStackTrace();
            }

            // Notify the reporter
            try {
                System.out.println("[DELIST] ========== Notifying reporter ==========");
                System.out.println("[DELIST] Looking up report, report ID: " + reportId);
                Report report = reportService.getReportById(reportId);

                if (report == null) {
                    System.err.println("[DELIST] ❌ Error: no report with ID " + reportId);
                } else if (report.getReporterId() == null) {
                    System.err.println("[DELIST] ❌ Error: report exists but reporter ID is empty");
                } else {
                    System.out.println("[DELIST] ✅ Found report, reporter ID: " + report.getReporterId());
                    User reporter = userMapper.selectById(report.getReporterId());

                    if (reporter == null) {
                        System.err.println("[DELIST] ❌ Error: no reporter with user ID " + report.getReporterId());
                    } else if (reporter.getUsername() == null || reporter.getUsername().isEmpty()) {
                        System.err.println("[DELIST] ❌ Error: reporter exists but username is empty, user ID: " + reporter.getId());
                    } else {
                        System.out.println("[DELIST] ✅ Found reporter: " + reporter.getUsername() + " (ID: " + reporter.getId() + ")");
                        String reporterMessage = "The product you reported, '" + product.getName() + "', has been reviewed and delisted by an administrator. Thank you for helping keep the platform safe!";

                        // Save the message to the database
                        ChatMessage savedReporterMessage = chatMessageService.saveMessage("System", reporter.getUsername(), reporterMessage, true);
                        System.out.println("[DELIST] ✅ Reporter message saved, message ID: " + savedReporterMessage.getId());

                        // Build the WebSocket message
                        ChatMessageDTO reporterResponseDTO = ChatMessageDTO.builder()
                            .sender("System")
                            .recipient(reporter.getUsername())
                            .content(savedReporterMessage.getContent())
                            .timestamp(savedReporterMessage.getTimestamp())
                            .isSystemMessage(true)
                            .build();

                        System.out.println("[DELIST] 📤 Sending WebSocket message to reporter: " + reporter.getUsername());
                        System.out.println("[DELIST] Message: " + reporterMessage);

                        try {
                            messagingTemplate.convertAndSendToUser(
                                reporter.getUsername(),
                                "/queue/private",
                                reporterResponseDTO
                            );
                            System.out.println("[DELIST] ✅✅ WebSocket message sent to reporter: " + reporter.getUsername());
                        } catch (Exception wsError) {
                            System.err.println("[DELIST] ⚠️ WebSocket send failed (message is saved in the database): " + wsError.getMessage());
                            wsError.printStackTrace();
                        }
                    }
                }
                System.out.println("[DELIST] ========== Reporter notified ==========");
            } catch (Exception e) {
                System.err.println("[DELIST] ❌ Failed to notify reporter: " + e.getMessage());
                e.printStackTrace();
            }

            // Mark the report as handled
            try {
                System.out.println("[DELIST] Updating report status, report ID: " + reportId);
                reportService.updateReportStatus(reportId, "APPROVED");
                System.out.println("[DELIST] ✅ Report status updated to APPROVED");
            } catch (Exception e) {
                System.err.println("[DELIST] ⚠️ Failed to update report status: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("[DELIST] Delisting complete");
            return ResponseEntity.ok("Product delisted successfully and notifications sent");
        } catch (Exception e) {
            System.err.println("[DELIST] Failed to delist product: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error delisting product: " + e.getMessage());
        }
    }
}
