package service;

import com.ceremonyconnect.bookingservice.model.AuditLog;
import com.ceremonyconnect.bookingservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String details, String performedBy,
                    String entityType, Long entityId) {
        auditLogRepository.save(new AuditLog(action, details, performedBy, entityType, entityId));
    }
}
