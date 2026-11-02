package com.eduerp.modules.enrollment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.students.StudentsManagement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentResponseAssemblerTest {

    private final StudentsManagement students = mock(StudentsManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final EnrollmentResponseAssembler assembler = new EnrollmentResponseAssembler(students, courses);

    private final UUID studentId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    private Enrollment enrollment() {
        return new Enrollment(studentId, classId, courseId, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void fillsStudentClassAndCourseNames() {
        when(students.displayNamesOf(any())).thenReturn(Map.of(studentId, "Nguyễn Văn A"));
        when(courses.classSummariesOf(any())).thenReturn(Map.of(classId,
                new CoursesManagement.ClassBasicSummary(classId, "TOAN-01", courseId, "Toán")));

        var response = assembler.toResponse(enrollment());

        assertThat(response.studentName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.classCode()).isEqualTo("TOAN-01");
        assertThat(response.courseName()).isEqualTo("Toán");
    }

    @Test
    void namesThatCannotBeResolvedStayNullInsteadOfFailing() {
        var response = assembler.toResponse(enrollment());

        assertThat(response.studentName()).isNull();
        assertThat(response.classCode()).isNull();
        assertThat(response.courseName()).isNull();
    }

    @Test
    void aWholeListLooksUpNamesOnlyOnce() {
        assembler.toResponses(List.of(enrollment(), enrollment()));

        verify(students, times(1)).displayNamesOf(List.of(studentId));
        verify(courses, times(1)).classSummariesOf(List.of(classId));
    }
}
