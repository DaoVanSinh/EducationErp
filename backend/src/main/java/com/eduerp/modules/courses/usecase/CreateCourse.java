package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CourseCodeAlreadyExistsException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCourse {

    private final CourseRepository courses;
    private final ApplicationEventPublisher events;

    CreateCourse(CourseRepository courses, ApplicationEventPublisher events) {
        this.courses = courses;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateCourseRequest request) {
        if (courses.findByCode(request.code()).isPresent()) {
            throw new CourseCodeAlreadyExistsException(request.code());
        }
        var saved = courses.save(new Course(request.code(), request.name(), request.description(),
                request.standardSessionCount()));
        events.publishEvent(new CoursesEvents.CourseCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
