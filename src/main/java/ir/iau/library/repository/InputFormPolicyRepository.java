package ir.iau.library.repository;

import ir.iau.library.entity.InputFormPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InputFormPolicyRepository extends JpaRepository<InputFormPolicy, Long> {
    Optional<InputFormPolicy> findByFormKeyAndOwnerUsernameAndStation(String formKey, String ownerUsername, String station);
}
