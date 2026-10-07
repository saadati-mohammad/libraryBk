package ir.iau.library.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ir.iau.library.dto.PersonFilterDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Audited
@Table(name = "person", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "nationalId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "نام الزامی است")
    @Size(max = 100, message = "نام نمی‌تواند بیش از ۱۰۰ کاراکتر باشد")
    private String firstName;

    @NotBlank(message = "نام خانوادگی الزامی است")
    @Size(max = 100, message = "نام خانوادگی نمی‌تواند بیش از ۱۰۰ کاراکتر باشد")
    private String lastName;

    // These mirror the DB NOT NULL / unique constraints so an invalid payload fails
    // fast with a 400 (via GlobalExceptionHandler) instead of surfacing as an opaque
    // 500 from the H2 constraint violation.
    @NotBlank(message = "ایمیل الزامی است")
    @Email(message = "قالب ایمیل نامعتبر است")
    @Column(nullable = false)
    private String email;

    @NotBlank(message = "کد ملی الزامی است")
    @Column(nullable = false)
    private String nationalId; // کد ملی، یک فیلد مهم و منحصر به فرد

    private String phone;
    private LocalDate birthDate;
    private LocalDate membershipDate;
    private String membershipType; // STANDARD, PREMIUM, etc.
    private String address;

    @Column(length = 2000)
    private String notes; // یادداشت های اضافی درباره عضو

    // @Lob byte[] already maps to a large-object column; see the note on
    // Book.bookCoverFile for why columnDefinition="LONGBLOB" was removed.
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "profile_picture")
    private byte[] profilePicture; // عکس پروفایل

    private Boolean active = true; // فلگ برای غیرفعال سازی

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<BookLoan> loans = new ArrayList<>();

    // سازنده برای استفاده در فیلترینگ
    public Person(PersonFilterDto filterDto) {
        this.firstName = filterDto.getFirstName();
        this.lastName = filterDto.getLastName();
        this.email = filterDto.getEmail();
        this.phone = filterDto.getPhone();
        this.nationalId = filterDto.getNationalId();
        this.membershipType = filterDto.getMembershipType();
        this.address = filterDto.getAddress();
        this.active = filterDto.getActive();
    }
}