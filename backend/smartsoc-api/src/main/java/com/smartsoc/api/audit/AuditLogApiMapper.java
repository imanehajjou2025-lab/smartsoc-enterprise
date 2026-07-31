package com.smartsoc.api.audit;

import com.smartsoc.api.audit.dto.AuditLogDtos.AuditLogEntryResponse;
import com.smartsoc.domain.audit.AuditLogEntry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogApiMapper {

    AuditLogEntryResponse toResponse(AuditLogEntry entry);
}
