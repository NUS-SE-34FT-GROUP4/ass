package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.Report;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReportMapper {
    void insert(Report report);
    Report selectById(Long id);
    List<Report> selectAll();
    void updateStatus(Report report);
    Report selectPendingReportByProductAndReporter(Long productId, Long reporterId);
}
