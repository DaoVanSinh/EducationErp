package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.ClassTeacherAssignment;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassTeacherAssignmentRepository extends JpaRepository<ClassTeacherAssignment, UUID> {

    Optional<ClassTeacherAssignment> findFirstByClassIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID classId, LocalDate date);

    Optional<ClassTeacherAssignment> findFirstByClassIdOrderByEffectiveFromAsc(UUID classId);

    Optional<ClassTeacherAssignment> findFirstByClassIdOrderByEffectiveFromDesc(UUID classId);
}
