package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
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
        var course = courses.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        course.setName(request.name());
        course.setDescription(request.description());
        course.setStandardSessionCount(request.standardSessionCount());
        course.setTuitionFee(request.tuitionFee());
        course.setActive(request.active());
        events.publishEvent(new CoursesEvents.CourseUpdated(courseId, actorAccountId, actorBranchId));
    }
}
