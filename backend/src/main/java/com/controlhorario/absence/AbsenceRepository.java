package com.controlhorario.absence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

    Optional<Absence> findByUserIdAndDate(UUID userId, LocalDate date);

    List<Absence> findByUserIdAndDateBetweenOrderByDate(UUID userId, LocalDate from, LocalDate to);
}
