package model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "AuditLogs")
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogID")
    private Long id;

    @Column(name = "ActionType", nullable = false, length = 80)
    private String action;

    @Column(name = "Details", length = 2000)
    private String details;

    @Column(name = "PerformedByEmail", nullable = false, length = 255)
    private String performedBy;

    @Column(name = "EntityType", nullable = false, length = 80)
    private String entityType;

    @Column(name = "EntityID")
    private Long entityId;

    @Column(name = "LoggedAt", updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public AuditLog(String action, String details, String performedBy,
                    String entityType, Long entityId) {
        this.action = action;
        this.details = details;
        this.performedBy = performedBy;
        this.entityType = entityType;
        this.entityId = entityId;
    }
}
