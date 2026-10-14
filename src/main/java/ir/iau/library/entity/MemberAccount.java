package ir.iau.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Login credentials for a library member. Password hashes are excluded from auditing and all APIs. */
@Entity
@Table(name = "member_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_account_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_member_account_person", columnNames = "person_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MemberAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "auth_version", nullable = false)
    private long authVersion = 0;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
