package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.Report;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.event.EventPublisher;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.ReportService;
import sg.edu.nus.iss.c2csectrade.service.UserAdministrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProductService productService;

    @Autowired
    private EventPublisher eventPublisher;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        // Return users together with their roles
        return ResponseEntity.ok(userMapper.selectAllWithRoles());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userAdministrationService.delete(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    /** Blocks login, and rejects the user's existing tokens on their next request. */
    @PutMapping("/users/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> suspendUser(@PathVariable Long id) {
        return ResponseEntity.ok(userAdministrationService.suspend(id));
    }

    @PutMapping("/users/{id}/reinstate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> reinstateUser(@PathVariable Long id) {
        return ResponseEntity.ok(userAdministrationService.reinstate(id));
    }

    @PutMapping("/users/{id}/display-name")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> renameUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userAdministrationService.rename(id, body.get("displayName")));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<sg.edu.nus.iss.c2csectrade.dto.ReportDTO>> getAllReports() {
        List<Report> reports = reportService.getAllReports();
        List<sg.edu.nus.iss.c2csectrade.dto.ReportDTO> reportDTOs = new java.util.ArrayList<>();

        for (Report report : reports) {
            // Load the product
            Product product = productService.getProductById(report.getProductId());
            String productName = product != null ? product.getName() : "Unknown product";

            // Load the reporter
            User reporter = userMapper.selectById(report.getReporterId());
            String reporterUsername = reporter != null ? reporter.getUsername() : "Unknown user";
            String reporterDisplayName = reporter != null ? reporter.getDisplayName() : reporterUsername;

            sg.edu.nus.iss.c2csectrade.dto.ReportDTO dto = sg.edu.nus.iss.c2csectrade.dto.ReportDTO.builder()
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

            // Chat persists and pushes the seller notification; Core holds no
            // privilege on chat_message, so it publishes an event instead.
            try {
                User owner = userMapper.selectById(product.getUserId());
                if (owner != null) {
                    eventPublisher.publishProductDelisted(new Events.ProductDelistedEvent(
                            productId, product.getName(), owner.getUsername()));
                    System.out.println("[DELIST] Delist event published for seller: " + owner.getUsername());
                }
            } catch (Exception e) {
                System.err.println("[DELIST] Failed to publish delist event: " + e.getMessage());
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
