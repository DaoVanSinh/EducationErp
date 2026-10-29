package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.assessment.AssessmentConstants;
import com.eduerp.modules.assessment.dto.MyAssessmentResponses;
import com.eduerp.modules.assessment.internal.model.Assignment;
import com.eduerp.modules.assessment.internal.model.MiniTask;
import com.eduerp.modules.assessment.internal.model.MiniTaskAttempt;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import com.eduerp.modules.assessment.internal.repository.MiniTaskAttemptRepository;
import com.eduerp.modules.assessment.internal.repository.MiniTaskRepository;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng học tập: bài tập/mini-test (tình trạng nộp, điểm, nhận xét) và mini-task (điểm cao nhất, số lượt)
 * của CHÍNH học viên ở mọi lớp đã ghi danh. Số lần đọc cố định theo lô, không tăng theo số lớp.
 */
@Service
public class GetMyAssessments {

    private final StudentsManagement students;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final AssignmentRepository assignments;
    private final MiniTaskRepository miniTasks;
    private final MiniTaskAttemptRepository attempts;

    GetMyAssessments(StudentsManagement students, EnrollmentManagement enrollments, CoursesManagement courses,
            AssignmentRepository assignments, MiniTaskRepository miniTasks, MiniTaskAttemptRepository attempts) {
        this.students = students;
        this.enrollments = enrollments;
        this.courses = courses;
        this.assignments = assignments;
        this.miniTasks = miniTasks;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public List<MyAssessmentResponses.MyAssessmentClassResponse> execute(UUID accountId) {
        var profile = students.findProfileByAccountId(accountId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        var entries = enrollments.listEnrollmentsByStudent(profile.studentProfileId());
        if (entries.isEmpty()) {
            return List.of();
        }
        var classIds = entries.stream().map(EnrollmentManagement.StudentEnrollmentEntry::classId).distinct().toList();
        var context = new Context(courses.classSummariesOf(classIds),
                assignments.findAllByClassIdInAndActiveTrueOrderByCreatedAtAsc(classIds).stream()
                        .collect(Collectors.groupingBy(Assignment::getClassId)),
                miniTasks.findAllByClassIdInAndActiveTrueOrderByCreatedAtAsc(classIds).stream()
                        .collect(Collectors.groupingBy(MiniTask::getClassId)),
                attempts.findAllByEnrollmentIdInOrderBySubmittedAtAsc(entries.stream()
                                .map(EnrollmentManagement.StudentEnrollmentEntry::enrollmentId).toList())
                        .stream().collect(Collectors.groupingBy(MiniTaskAttempt::getEnrollmentId)));
        return entries.stream().map(entry -> toResponse(entry, context)).toList();
    }

    private record Context(Map<UUID, CoursesManagement.ClassBasicSummary> classSummaries,
            Map<UUID, List<Assignment>> assignmentsByClass, Map<UUID, List<MiniTask>> miniTasksByClass,
            Map<UUID, List<MiniTaskAttempt>> attemptsByEnrollment) {
    }

    private static MyAssessmentResponses.MyAssessmentClassResponse toResponse(
            EnrollmentManagement.StudentEnrollmentEntry entry, Context context) {
        var items = context.assignmentsByClass().getOrDefault(entry.classId(), List.of()).stream()
                .map(assignment -> toAssignmentItem(assignment, entry.enrollmentId()))
                .toList();
        var myAttempts = context.attemptsByEnrollment().getOrDefault(entry.enrollmentId(), List.of());
        var tasks = context.miniTasksByClass().getOrDefault(entry.classId(), List.of()).stream()
                .map(task -> toMiniTaskItem(task, myAttempts))
                .toList();
        var submitted = (int) items.stream().filter(item -> AssessmentRules.isSubmitted(item.status())).count();
        var summary = context.classSummaries().get(entry.classId());
        return new MyAssessmentResponses.MyAssessmentClassResponse(entry.enrollmentId(), entry.classId(),
                summary == null ? null : summary.code(), summary == null ? null : summary.courseName(),
                entry.status(), items, tasks, submitted, AssessmentRules.percent(submitted, items.size()));
    }

    private static MyAssessmentResponses.MyAssignmentItem toAssignmentItem(Assignment assignment, UUID enrollmentId) {
        var result = assignment.getResults().stream()
                .filter(row -> row.getEnrollmentId().equals(enrollmentId))
                .findFirst()
                .orElse(null);
        if (result == null) {
            return new MyAssessmentResponses.MyAssignmentItem(assignment.getId(), assignment.getTitle(),
                    assignment.getDescription(), assignment.getType(), assignment.getDueDate(),
                    assignment.getMaxScore(), AssessmentConstants.SubmissionStatus.NOT_SUBMITTED, null, null);
        }
        return new MyAssessmentResponses.MyAssignmentItem(assignment.getId(), assignment.getTitle(),
                assignment.getDescription(), assignment.getType(), assignment.getDueDate(), assignment.getMaxScore(),
                result.getStatus(), result.getScore(), result.getFeedback());
    }

    private static MyAssessmentResponses.MyMiniTaskItem toMiniTaskItem(MiniTask task, List<MiniTaskAttempt> myAttempts) {
        var taskAttempts = myAttempts.stream().filter(attempt -> attempt.getMiniTaskId().equals(task.getId())).toList();
        var best = taskAttempts.stream().map(MiniTaskAttempt::getScorePercent).max(Integer::compareTo).orElse(null);
        var last = taskAttempts.isEmpty() ? null : taskAttempts.get(taskAttempts.size() - 1).getScorePercent();
        return new MyAssessmentResponses.MyMiniTaskItem(task.getId(), task.getTitle(), task.getDescription(),
                task.getQuestions().size(), taskAttempts.size(), best, last);
    }
}
