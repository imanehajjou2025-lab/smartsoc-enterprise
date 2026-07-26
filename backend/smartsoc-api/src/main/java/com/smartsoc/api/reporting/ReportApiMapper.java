package com.smartsoc.api.reporting;

import com.smartsoc.api.reporting.dto.ReportDtos.AlertMetricsResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.IncidentMetricsResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.MitreTechniqueCountResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.ReportMetricsResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.ReportResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.ReportSummaryResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.SoarMetricsResponse;
import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportMetrics;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportApiMapper {

    ReportResponse toResponse(Report report);

    ReportSummaryResponse toSummary(Report report);

    ReportMetricsResponse toResponse(ReportMetrics metrics);

    AlertMetricsResponse toResponse(AlertPeriodMetrics metrics);

    IncidentMetricsResponse toResponse(IncidentPeriodMetrics metrics);

    SoarMetricsResponse toResponse(ExecutionPeriodMetrics metrics);

    MitreTechniqueCountResponse toResponse(MitreCoverageCount count);
}
