package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CourseGradeColumnInUseException;
import com.eduerp.modules.courses.CourseGradeColumnWeightsInvalidException;
import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.courses.internal.rules.GradeColumnRules;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCourse {

    private final CourseRepository courses;
    private final ApplicationEventPublisher events;

    UpdateCourse(CourseRepository courses, ApplicationEventPublisher events) {
        this.courses = courses;
        this.events = events;
    }

    @Transactional
    public void execute(UUID courseId, UUID actorAccountId, UUID actorBranchId, UpdateCourseRequest request) {
        if (!GradeColumnRules.weightsSumToRequiredTotal(request.gradeColumns())) {
            throw new CourseGradeColumnWeightsInvalidException(GradeColumnRules.totalWeight(request.gradeColumns()));
        }
        var course = courses.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        course.setName(request.name());
        course.setDescription(request.description());
        course.setStandardSessionCount(request.standardSessionCount());
        course.setTuitionFee(request.tuitionFee());
        course.setActive(request.active());
        course.reconcileGradeColumns(request.gradeColumns());
        saveReconciledColumns(course, courseId);
        events.publishEvent(new CoursesEvents.CourseUpdated(courseId, actorAccountId, actorBranchId));
    }

    /**
     * {@code saveAndFlush} buộc DELETE của {@code orphanRemoval} (cột bị bỏ khỏi danh sách) chạy
     * NGAY ở đây, trong khối try/catch này - mirror {@code CreateInvoice.saveInvoice}. Không có
     * {@code saveAndFlush} thì {@code DataIntegrityViolationException} trôi tới lúc transaction
     * commit, ngoài tầm với của catch, và rơi thành một 500 trống thay vì 409 có errorCode (Final
     * review Critical #1).
     */
    private void saveReconciledColumns(Course course, UUID courseId) {
        try {
            courses.saveAndFlush(course);
        } catch (DataIntegrityViolationException gradeColumnStillReferencedByAGrade) {
            throw new CourseGradeColumnInUseException(courseId);
        }
    }
}
