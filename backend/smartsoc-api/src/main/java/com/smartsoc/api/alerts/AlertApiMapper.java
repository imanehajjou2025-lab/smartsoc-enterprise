package com.smartsoc.api.alerts;

import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.domain.alerts.Alert;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AlertApiMapper {

    AlertResponse toResponse(Alert alert);

    List<AlertResponse> toResponses(List<Alert> alerts);
}
