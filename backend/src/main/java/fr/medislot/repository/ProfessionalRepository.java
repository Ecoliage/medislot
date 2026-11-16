package fr.medislot.repository;

import fr.medislot.domain.Professional;
import org.springframework.data.jpa.repository.*;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {
  @Query(
      "select p from Professional p where (:specialty = '' or p.specialty = :specialty) and (:city = '' or lower(p.city) like lower(concat('%',:city,'%'))) and (:q = '' or lower(p.name) like lower(concat('%',:q,'%')))")
  org.springframework.data.domain.Page<Professional> search(
      String specialty, String city, String q, org.springframework.data.domain.Pageable pageable);

  @Query(
      """
  select p from Professional p
  where p.latitude between :minLat and :maxLat
  and ((:crossesAntimeridian = false and p.longitude between :minLon and :maxLon)
   or (:crossesAntimeridian = true and (p.longitude >= :minLon or p.longitude <= :maxLon)))
  and (:specialty = '' or p.specialty = :specialty)
  """)
  java.util.List<Professional> findInBox(
      double minLat,
      double maxLat,
      double minLon,
      double maxLon,
      boolean crossesAntimeridian,
      String specialty);
}
