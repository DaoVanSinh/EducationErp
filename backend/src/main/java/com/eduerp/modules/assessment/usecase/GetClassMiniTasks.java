package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.dto.MiniTaskResponses;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.model.MiniTask;
import com.eduerp.modules.assessment.internal.model.MiniTaskAttempt;
import com.eduerp.modules.assessment.internal.repository.MiniTaskAttemptRepository;
import com.eduerp.modules.assessment.internal.repository.MiniTaskRepository;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import com.eduerp.modules.assessment.internal.util.MiniTaskViews;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mini-task của một lớp cho giáo viên: đề kèm đáp án, thống kê lượt làm, và mỗi học viên một dòng với
 * điểm CAO NHẤT + số lượt ở từng mini-task (theo dõi tiến độ tự ôn luyện).
 */
@Service
public class GetClassMiniTasks {

    private final MiniTaskRepository miniTasks;
    private final MiniTaskAttemptRepository attempts;
    private final EnrollmentManagement enrollments;
    private final StudentsManagement students;
    private final ClassAssessmentAccessGuard guard;

    GetClassMiniTasks(MiniTaskRepository miniTasks, MiniTaskAttemptRepository attempts,
            EnrollmentManagement enrollments, StudentsManagement students, ClassAssessmentAccessGuard guard) {
        this.miniTasks = miniTasks;
        this.attempts = attempts;
        this.enrollments = enrollments;
        this.students = students;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public MiniTaskResponses.ClassMiniTasksResponse execute(UUID actorAccountId, UUID classId) {
        guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listEnrollmentsByClass(classId);
        var tasks = miniTasks.findAllByClassIdOrderByCreatedAtAsc(classId);
        var attemptsByTask = attempts.findAllByMiniTaskIdIn(tasks.stream().map(MiniTask::getId).toList()).stream()
                .collect(Collectors.groupingBy(MiniTaskAttempt::getMiniTaskId));
        var names = students.displayNamesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());

        var responses = tasks.stream()
                .map(task -> toResponse(task, attemptsByTask.getOrDefault(task.getId(), List.of())))
                .toList();
        var rows = roster.stream()
                .map(entry -> toRow(entry, names.get(entry.studentProfileId()), attemptsByTask))
                .toList();
        return new MiniTaskResponses.ClassMiniTasksResponse(classId, responses, rows);
    }

    private static MiniTaskResponses.MiniTaskResponse toResponse(MiniTask task, List<MiniTaskAttempt> taskAttempts) {
        var bestByEnrollment = bestScoreByEnrollment(taskAttempts);
        var passed = (int) bestByEnrollment.values().stream()
                .filter(best -> AssessmentRules.isPassed(best, task.getPassPercent()))
                .count();
        return new MiniTaskResponses.MiniTaskResponse(task.getId(), task.getClassId(), task.getTitle(),
                task.getDescription(), task.isActive(), task.getPassPercent(), task.getCreatedAt(),
                MiniTaskViews.questionsWithAnswers(task), taskAttempts.size(), bestByEnrollment.size(), passed,
                average(bestByEnrollment.values()));
    }

    private static MiniTaskResponses.StudentMiniTaskRow toRow(EnrollmentManagement.ClassRosterEntry entry,
            String studentName, Map<UUID, List<MiniTaskAttempt>> attemptsByTask) {
        var best = new HashMap<UUID, Integer>();
        var counts = new HashMap<UUID, Integer>();
        attemptsByTask.forEach((taskId, taskAttempts) -> taskAttempts.stream()
                .filter(attempt -> attempt.getEnrollmentId().equals(entry.enrollmentId()))
                .forEach(attempt -> {
                    best.merge(taskId, attempt.getScorePercent(), Math::max);
                    counts.merge(taskId, 1, Integer::sum);
                }));
        return new MiniTaskResponses.StudentMiniTaskRow(entry.enrollmentId(), entry.studentProfileId(), studentName,
                entry.status(), best, counts);
    }

    private static Map<UUID, Integer> bestScoreByEnrollment(List<MiniTaskAttempt> taskAttempts) {
        return taskAttempts.stream().collect(Collectors.toMap(MiniTaskAttempt::getEnrollmentId,
                MiniTaskAttempt::getScorePercent, Math::max));
    }

    private static Integer average(Collection<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        var sum = values.stream().mapToLong(Integer::longValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 0, RoundingMode.HALF_UP).intValue();
    }
}
