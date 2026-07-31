package com.smartsoc.application.audit;

import com.smartsoc.domain.audit.AuditLogEntry;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public PageResult<AuditLogEntry> search(AuditLogQuery query) {
        return auditLogRepository.search(query);
    }
}
