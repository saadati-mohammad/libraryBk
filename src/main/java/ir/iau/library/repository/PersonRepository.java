package ir.iau.library.repository;

import ir.iau.library.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByNationalId(String nationalId);
    List<Person> findByEmailInIgnoreCase(Collection<String> emails);
    List<Person> findByNationalIdIn(Collection<String> nationalIds);

    /** Members that are not deactivated. Used by the dashboard. */
    long countByActiveTrue();
}
