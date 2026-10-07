package lut.cn.c2cplatform.service;

import lut.cn.c2cplatform.entity.Report;
import lut.cn.c2cplatform.mapper.ReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {

    @Autowired
    private ReportMapper reportMapper;

    public void createReport(Report report) {
        // Check for an existing pending report
        Report existingReport = reportMapper.selectPendingReportByProductAndReporter(
            report.getProductId(),
            report.getReporterId()
        );

        if (existingReport != null) {
            throw new RuntimeException("You have already reported this product. Please wait for an administrator to review it");
        }

        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }

    public List<Report> getAllReports() {
        return reportMapper.selectAll();
    }

    public Report getReportById(Long id) {
        return reportMapper.selectById(id);
    }

    public void updateReportStatus(Long reportId, String status) {
        Report report = reportMapper.selectById(reportId);
        if (report != null) {
            report.setStatus(status);
            reportMapper.updateStatus(report);
        }
    }
}
