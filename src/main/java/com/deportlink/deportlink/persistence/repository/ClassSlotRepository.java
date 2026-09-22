package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ClassSlotEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ClassSlotRepository extends JpaRepository<ClassSlotEntity, Long> {
    List<ClassSlotEntity> findByCourt_IdAndDayOfWeekAndActiveStatus(Long courtId,
            java.time.DayOfWeek day, com.deportlink.deportlink.enums.ActiveStatus status);

    @Query("SELECT s.id FROM ClassSlotEntity s WHERE s.activeStatus = :status")
    List<Long> findIdsByActiveStatus(@Param("status") com.deportlink.deportlink.enums.ActiveStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClassSlotEntity s WHERE s.id = (SELECT a.classSession.classSlot.id FROM ClassAttendanceEntity a WHERE a.id = :attendanceId)")
    Optional<ClassSlotEntity> findByAttendanceIdForUpdate(@Param("attendanceId") Long attendanceId);

    // Lock pesimista — primera (o segunda, detrás de Court) lectura de la transacción en todo
    // caso de uso que mute el ClassSlot o su grupo. Mismo patrón que CourtRepository.findByIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ClassSlotEntity s WHERE s.id = :id")
    Optional<ClassSlotEntity> findByIdForUpdate(@Param("id") Long id);

    List<ClassSlotEntity> findAllByInstructor_Id(Long instructorId);

    // Búsqueda batch — evita una consulta por sesión al enriquecer un listado de agenda.
    List<ClassSlotEntity> findAllByIdIn(Set<Long> ids);

    // Usado por CourtRepositoryAdapter.hasClassSlots / BranchRepositoryAdapter.hasClassSlots
    // (sección 5 del diseño de 1C) — mismo criterio que ReservationRepository.existsByCourt_Id.
    boolean existsByCourt_Id(Long courtId);

    boolean existsByCourt_Branch_Id(Long branchId);
}
