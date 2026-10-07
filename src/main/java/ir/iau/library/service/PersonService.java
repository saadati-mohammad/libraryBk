package ir.iau.library.service;

import ir.iau.library.dto.PersonFilterDto;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.specification.PersonSpecification;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Service
@Transactional
public class PersonService {

    @Autowired
    private PersonRepository personRepository;

    public Page<Person> findAllFiltered(PersonFilterDto filter, Pageable pageable) {
        return personRepository.findAll(PersonSpecification.filter(filter), pageable);
    }

    public Optional<Person> getPersonById(Long id) {
        return personRepository.findById(id);
    }

    public Person createPerson(Person person, MultipartFile profilePicture) throws IOException {
        // Server-controlled fields: a client-supplied id would make save() a merge that
        // can overwrite an existing member (mass assignment).
        person.setId(null);
        person.setLoans(null);
        person.setActive(true);
        person.setMembershipDate(java.time.LocalDate.now());

        if (profilePicture != null && !profilePicture.isEmpty()) {
            person.setProfilePicture(profilePicture.getBytes());
        }

        return personRepository.save(person);
    }

    public Person updatePerson(Long id, Person personDetails, MultipartFile profilePicture) throws IOException {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Person not found with id " + id));

        person.setFirstName(personDetails.getFirstName());
        person.setLastName(personDetails.getLastName());
        person.setEmail(personDetails.getEmail());
        person.setNationalId(personDetails.getNationalId());
        person.setPhone(personDetails.getPhone());
        person.setBirthDate(personDetails.getBirthDate());
        person.setMembershipType(personDetails.getMembershipType());
        person.setAddress(personDetails.getAddress());
        person.setNotes(personDetails.getNotes());
        person.setActive(personDetails.getActive());

        if (profilePicture != null && !profilePicture.isEmpty()) {
            person.setProfilePicture(profilePicture.getBytes());
        } else if (personDetails.getProfilePicture() == null) {
            // اگر عکس جدیدی آپلود نشده و در دیتای ارسالی هم عکس نال بود، یعنی حذف عکس
            person.setProfilePicture(null);
        }
        // در غیر این صورت، عکس قبلی باقی می ماند

        return personRepository.save(person);
    }

    public void deactivatePerson(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Person not found with id " + id));
        person.setActive(false);
        personRepository.save(person);
    }
}