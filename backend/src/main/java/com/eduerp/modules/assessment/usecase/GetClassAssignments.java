package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.dto.AssignmentResponses;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.model.Assignment;
import com.eduerp.modules.assessment.internal.model.AssignmentResult;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng theo dõi bài tập / mini-test của một lớp: mỗi bài kèm số đã nộp và điểm trung bình, mỗi học viên
 * kèm tỷ lệ hoàn thành. Số lần đọc cố định: guard, danh sách lớp, các bài kèm kết quả, tên theo lô.
 */
@Service
public class GetClassAssignments {

    private final AssignmentRepository assignments;
    private final EnrollmentManagement enrollments;
    private final StudentsManagement students;
    private final ClassAssessmentAccessGuard guard;

    GetClassAssignments(AssignmentRepository assignments, EnrollmentManagement enrollments,
            StudentsManagement students, ClassAssessmentAccessGuard guard) {
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.students = students;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public AssignmentResponses.ClassAssignmentsResponse execute(UUID actorAccountId, UUID classId) {
        guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listEnrollmentsByClass(classId);
        var classAssignments = assignments.findAllByClassIdOrderByCreatedAtAsc(classId);
        var names = students.displayNamesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());
        var activeRoster = (int) roster.stream()
                .filter(entry -> entry.status() == EnrollmentConstants.EnrollmentStatus.ACTIVE).count();

        var responses = classAssignments.stream().map(assignment -> toResponse(assignment, activeRoster)).toList();
        var activeAssignmentIds = classAssignments.stream().filter(Assignment::isActive).map(Assignment::getId).toList();
        var resultsByEnrollment = resultsByEnrollment(classAssignments);
        var rows = roster.stream()
                .map(entry -> toRow(entry, names.get(entry.studentProfileId()),
                        resultsByEnrollment.getOrDefault(entry.enrollmentId(), Map.of()), activeAssignmentIds))
                .toList();
        return new AssignmentResponses.ClassAssignmentsResponse(classId, responses, rows);
    }

    private static AssignmentResponses.AssignmentResponse toResponse(Assignment assignment, int activeRoster) {
        var submitted = (int) assignment.getResults().stream()
                .filter(result -> AssessmentRules.isSubmitted(result.getStatus())).count();
        var average = AssessmentRules.averageScore(assignment.getResults().stream()
                .map(AssignmentResult::getScore).toList());
        return new AssignmentResponses.AssignmentResponse(assignment.getId(), assignment.getClassId(),
                assignment.getTitle(), assignment.getDescription(), assignment.getType(), assignment.getDueDate(),
                assignment.getMaxScore(), assignment.isActive(), assignment.getCreatedAt(), submitted, activeRoster,
                average);
    }

    private static Map<UUID, Map<UUID, AssignmentResult>> resultsByEnrollment(List<Assignment> classAssignments) {
        var result = new HashMap<UUID, Map<UUID, AssignmentResult>>();
        classAssignments.forEach(assignment -> assignment.getResults().forEach(row ->
                result.computeIfAbsent(row.getEnrollmentId(), key -> new HashMap<>()).put(assignment.getId(), row)));
        return result;
    }

    private static AssignmentResponses.StudentAssignmentRow toRow(EnrollmentManagement.ClassRosterEntry entry,
            String studentName, Map<UUID, AssignmentResult> results, List<UUID> activeAssignmentIds) {
        var cells = new HashMap<UUID, AssignmentResponses.ResultCell>();
        results.forEach((assignmentId, result) -> cells.put(assignmentId,
                new AssignmentResponses.ResultCell(result.getStatus(), result.getScore(), result.getFeedback())));
        var submitted = (int) activeAssignmentIds.stream()
                .filter(id -> results.containsKey(id) && AssessmentRules.isSubmitted(results.get(id).getStatus()))
                .count();
        return new AssignmentResponses.StudentAssignmentRow(entry.enrollmentId(), entry.studentProfileId(),
                studentName, entry.status(), cells, submitted,
                AssessmentRules.percent(submitted, activeAssignmentIds.size()));
    }
}
