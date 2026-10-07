package ir.iau.library.service;

import ir.iau.library.entity.Person;
import ir.iau.library.repository.PersonRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private PersonService personService;

    @Test
    void deactivatePerson_softDeletesInsteadOfRemovingRow() {
        Person person = new Person();
        person.setId(1L);
        person.setActive(true);
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));

        personService.deactivatePerson(1L);

        // A hard delete would cascade-remove this member's loan history.
        verify(personRepository, never()).deleteById(any());

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void deactivatePerson_throwsWhenPersonMissing() {
        when(personRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.deactivatePerson(404L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(personRepository, never()).save(any());
    }
}
