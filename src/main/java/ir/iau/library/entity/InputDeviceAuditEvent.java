package ir.iau.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "input_device_audit_event", indexes = {
        @Index(name = "idx_input_device_audit_device_time", columnList = "device_id,occurred_at")})
@Getter
@Setter
@NoArgsConstructor
public class InputDeviceAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private InputDevice device;

    @Column(name = "actor_username", nullable = false, length = 80)
    private String actorUsername;

    @Column(nullable = false, length = 32)
    private String action;

    @jakarta.persistence.Lob
    @Column(name = "details_json", nullable = false)
    private String detailsJson;

    @Column(name = "remote_address", length = 64)
    private String remoteAddress;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
