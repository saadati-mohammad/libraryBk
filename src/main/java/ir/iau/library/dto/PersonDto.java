package ir.iau.library.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * Read-model for {@code Person}.
 *
 * <p>Mirrors every non-blob field of the entity so the existing JSON contract is
 * preserved, but deliberately omits {@code profilePicture} (a {@code LONGBLOB}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String nationalId;
    private String phone;
    private LocalDate birthDate;
    private LocalDate membershipDate;
    private String membershipType;
    private String address;
    private String notes;
    private Boolean active;
    /**
     * True when the member has a stored profile picture. The bytes are served by
     * {@code GET /api/person/{id}/picture}; the flag keeps image payloads out of list responses.
     */
    private Boolean hasPicture;
}
