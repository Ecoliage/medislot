package fr.medislot.repository;

import fr.medislot.domain.PlatformEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface PlatformEventRepository extends JpaRepository<PlatformEvent, Long> {
  java.util.List<PlatformEvent> findTop100ByOrderByCreatedAtDescIdDesc();
}
