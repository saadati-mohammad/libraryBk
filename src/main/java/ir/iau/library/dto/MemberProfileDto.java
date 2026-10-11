package ir.iau.library.dto;

import ir.iau.library.entity.Person;

import java.time.LocalDate;

/** Self-service profile view; omits national identifier, address, notes, and credentials. */
public record MemberProfileDto(Long id, String firstName, String lastName, String email,
                               String phone, LocalDate membershipDate, String membershipType,
                               boolean active) {
    public static MemberProfileDto from(Person person) {
        return new MemberProfileDto(person.getId(), person.getFirstName(), person.getLastName(),
                person.getEmail(), person.getPhone(), person.getMembershipDate(),
                person.getMembershipType(), Boolean.TRUE.equals(person.getActive()));
    }
}
