package ir.iau.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.NotAudited;

import java.time.Instant;

@Entity
@Table(name = "input_device", indexes = {
        @Index(name = "idx_input_device_type_enabled", columnList = "type_key,enabled"),
        @Index(name = "idx_input_device_owner", columnList = "owner_username")})
@Getter
@Setter
@NoArgsConstructor
public class InputDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "type_key", nullable = false, length = 64)
    private String typeKey;

    @Column(name = "owner_username", length = 80)
    private String ownerUsername;

    @Column(length = 120)
    private String station;

    @NotAudited
    @jakarta.persistence.Lob
    @Column(name = "config_json", nullable = false)
    private String configJson = "{}";

    @NotAudited
    @jakarta.persistence.Lob
    @Column(name = "capabilities_json", nullable = false)
    private String capabilitiesJson = "[]";

    @NotAudited
    @Column(name = "token_hash", unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Version
    @Column(nullable = false)
    private long version;

    @jakarta.persistence.PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
