package ir.iau.library.controller;

import ir.iau.library.dto.PersonDto;
import ir.iau.library.dto.PersonFilterDto;
import ir.iau.library.entity.Person;
import ir.iau.library.service.EntityMapper;
import ir.iau.library.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/person")
public class PersonController {

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

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 ? Sort.Direction.fromString(sortParts[1]) : Sort.Direction.DESC;
        Sort sortOrder = Sort.by(direction, sortParts[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

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
