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

import java.time.Instant;

@Entity
@Table(name = "input_form_policy", indexes = {
        @Index(name = "uk_input_form_policy_scope", columnList = "form_key,owner_username,station", unique = true),
        @Index(name = "idx_input_form_policy_form", columnList = "form_key")})
@Getter
@Setter
@NoArgsConstructor
public class InputFormPolicy {
    public static final String GLOBAL_OWNER = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_key", nullable = false, length = 64)
    private String formKey;

    /** Username for personal overrides, or {@link #GLOBAL_OWNER} for an admin default. */
    @Column(name = "owner_username", nullable = false, length = 80)
    private String ownerUsername;

    @Column(nullable = false, length = 120)
    private String station = "";

    @jakarta.persistence.Lob
    @Column(name = "allowed_sources_json", nullable = false)
    private String allowedSourcesJson = "[]";

    @Column(name = "default_source", nullable = false, length = 80)
    private String defaultSource = "MANUAL";

    @Column(name = "updated_by", nullable = false, length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;
}
