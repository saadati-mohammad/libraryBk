package ir.iau.library.repository;

import ir.iau.library.entity.InputDeviceAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InputDeviceAuditEventRepository extends JpaRepository<InputDeviceAuditEvent, Long> {
    List<InputDeviceAuditEvent> findAllByDeviceIdOrderByOccurredAtDescIdDesc(long deviceId);
}
