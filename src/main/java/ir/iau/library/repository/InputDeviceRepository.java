package ir.iau.library.repository;

import ir.iau.library.entity.InputDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InputDeviceRepository extends JpaRepository<InputDevice, Long> {
    List<InputDevice> findAllByOrderByNameAscIdAsc();
}
