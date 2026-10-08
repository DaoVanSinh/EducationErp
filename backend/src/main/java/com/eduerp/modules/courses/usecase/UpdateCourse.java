package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CourseGradeColumnWeightsInvalidException;
import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.courses.internal.rules.GradeColumnRules;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
        course.clearGradeColumns();
        for (var column : request.gradeColumns()) {
            course.addGradeColumn(column.name(), column.weightPercent(), column.maxScore(), column.displayOrder());
        }
        events.publishEvent(new CoursesEvents.CourseUpdated(courseId, actorAccountId, actorBranchId));
    }
}
