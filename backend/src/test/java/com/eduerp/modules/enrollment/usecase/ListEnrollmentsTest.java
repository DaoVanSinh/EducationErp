package com.eduerp.modules.enrollment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ListEnrollmentsTest {

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final ListEnrollments useCase = new ListEnrollments(enrollments);

    @Test
    void passesAllThreeOptionalFiltersStraightToTheRepositoryAndMapsThePage() {
        var studentProfileId = UUID.randomUUID();
        var classId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        var enrollment = new Enrollment(studentProfileId, classId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        when(enrollments.search(studentProfileId, classId, EnrollmentConstants.EnrollmentStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(enrollment), pageable, 1));

        var page = useCase.execute(pageable, studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.studentProfileId()).isEqualTo(studentProfileId));
    }

    @Test
    void acceptsNullFiltersForAnUnfilteredList() {
        var pageable = PageRequest.of(0, 20);
        when(enrollments.search(null, null, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(useCase.execute(pageable, null, null, null).items()).isEmpty();
    }

    /** Spec mục 7: thêm filter status KHÔNG đổi hành vi khi để trống - status null vẫn là "mọi
     * trạng thái", đúng như trước khi có tham số này. */
    @Test
    void passesOnlyTheStatusFilterWhenTheOtherTwoAreNull() {
        var pageable = PageRequest.of(0, 20);
        when(enrollments.search(null, null, EnrollmentConstants.EnrollmentStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(useCase.execute(pageable, null, null, EnrollmentConstants.EnrollmentStatus.ACTIVE).items())
                .isEmpty();
    }
}
