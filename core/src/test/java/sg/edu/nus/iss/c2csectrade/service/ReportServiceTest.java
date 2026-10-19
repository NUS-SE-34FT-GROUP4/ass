package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.Report;
import sg.edu.nus.iss.c2csectrade.mapper.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A buyer's report starts the delisting flow: it waits as PENDING until an
 * admin delists the product (APPROVED) or rejects it. Buyer 5 reports product 42.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ReportService reportService;

    @Test
    void aNewReportWaitsForAnAdminAsPending() {
        Report report = report();
        when(reportMapper.selectPendingReportByProductAndReporter(42L, 5L)).thenReturn(null);

        reportService.createReport(report);

        assertThat(report.getStatus()).isEqualTo("PENDING");
        assertThat(report.getCreatedAt()).isNotNull();
        verify(reportMapper).insert(report);
    }

    @Test
    void aBuyerCannotReportTheSameProductAgainWhileTheFirstIsPending() {
        when(reportMapper.selectPendingReportByProductAndReporter(42L, 5L)).thenReturn(report());

        assertThatThrownBy(() -> reportService.createReport(report()))
                .hasMessageContaining("already reported");
        verify(reportMapper, never()).insert(any());
    }

    @Test
    void handlingAReportChangesItsStatus() {
        Report report = report();
        when(reportMapper.selectById(1L)).thenReturn(report);

        reportService.updateReportStatus(1L, "APPROVED");

        assertThat(report.getStatus()).isEqualTo("APPROVED");
        verify(reportMapper).updateStatus(report);
    }

    @Test
    void handlingAnUnknownReportDoesNothing() {
        when(reportMapper.selectById(1L)).thenReturn(null);

        reportService.updateReportStatus(1L, "APPROVED");

        verify(reportMapper, never()).updateStatus(any());
    }

    @Test
    void reportsAreReadFromTheMapper() {
        Report report = report();
        when(reportMapper.selectAll()).thenReturn(List.of(report));
        when(reportMapper.selectById(1L)).thenReturn(report);

        assertThat(reportService.getAllReports()).containsExactly(report);
        assertThat(reportService.getReportById(1L)).isSameAs(report);
    }

    private static Report report() {
        Report report = new Report();
        report.setId(1L);
        report.setProductId(42L);
        report.setReporterId(5L);
        report.setReason("Counterfeit");
        return report;
    }
}
