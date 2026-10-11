package ir.iau.library.controller;

import ir.iau.library.dto.PersonDto;
import ir.iau.library.dto.PersonFilterDto;
import ir.iau.library.entity.Person;
import ir.iau.library.service.EntityMapper;
import ir.iau.library.service.ImageContentType;
import ir.iau.library.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/person")
public class PersonController {

    /**
     * Sortable properties: only the member's own persistent fields. A blank or unknown
     * value is rejected with a 400 rather than failing deep inside Spring Data as a 500.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id", "firstName", "lastName", "email", "nationalId", "phone", "birthDate",
            "membershipDate", "membershipType", "address", "notes", "active");

    @Autowired
    private PersonService personService;

    @Autowired
    private EntityMapper entityMapper;

    @GetMapping
    public Page<PersonDto> listPersons(
            PersonFilterDto filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        Pageable pageable = PageableFactory.of(page, size, sort, SORTABLE_PROPERTIES, Sort.Direction.DESC);

        // Map to DTOs so the LONGBLOB profile picture is never serialized into list responses.
        return entityMapper.toPersonDtoPage(personService.findAllFiltered(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonDto> getPerson(@PathVariable Long id) {
        return personService.getPersonById(id)
                .map(entityMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Serves the stored profile picture.
     *
     * <p>The read model omits the blob, so this endpoint is the only way for an authenticated
     * client to display a member photo. The media type is sniffed from the payload rather than
     * trusted from the upload header.
     *
     * @return 200 with the image bytes and the detected {@code Content-Type}, or 404 when the
     * member does not exist or has no picture
     */
    @GetMapping("/{id}/picture")
    public ResponseEntity<byte[]> getPersonPicture(@PathVariable Long id) {
        return personService.getProfilePicture(id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(ImageContentType.detect(bytes))
                        .cacheControl(CacheControl.noCache())
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<PersonDto> createPerson(
            @Valid @RequestPart("person") Person person,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture) throws IOException {
        Person createdPerson = personService.createPerson(person, profilePicture);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityMapper.toDto(createdPerson));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<PersonDto> updatePerson(
            @PathVariable Long id,
            @Valid @RequestPart("person") Person personDetails,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture) throws IOException {
        Person updatedPerson = personService.updatePerson(id, personDetails, profilePicture);
        return ResponseEntity.ok(entityMapper.toDto(updatedPerson));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id) {
        personService.deactivatePerson(id); // Soft delete
        return ResponseEntity.noContent().build();
    }
}
