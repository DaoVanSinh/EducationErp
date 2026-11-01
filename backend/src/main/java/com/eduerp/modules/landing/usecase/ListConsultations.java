package com.eduerp.modules.landing.usecase;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.CoursesManagement.ActiveCourseSnapshot;
import com.eduerp.modules.landing.dto.ConsultationItemResponse;
import com.eduerp.modules.landing.internal.repository.LandingConsultationRepository;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.organization.OrganizationManagement.BranchPublicSummary;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListConsultations {

    private final LandingConsultationRepository consultations;
    private final CoursesManagement courses;
    private final OrganizationManagement organization;

    ListConsultations(
            LandingConsultationRepository consultations,
            CoursesManagement courses,
            OrganizationManagement organization) {
        this.consultations = consultations;
        this.courses = courses;
        this.organization = organization;
    }

    @Transactional(readOnly = true)
    public List<ConsultationItemResponse> execute() {
        var courseNames = courses.listActiveCourses().stream()
                .collect(Collectors.toMap(ActiveCourseSnapshot::id, ActiveCourseSnapshot::name));
        var branchNames = organization.listActiveBranches().stream()
                .collect(Collectors.toMap(BranchPublicSummary::id, BranchPublicSummary::name));

        return consultations.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> new ConsultationItemResponse(
                        c.getId(),
                        c.getFullName(),
                        c.getPhone(),
                        c.getEmail(),
                        c.getTargetCourseId(),
                        c.getTargetCourseId() == null ? null : courseNames.get(c.getTargetCourseId()),
                        c.getTargetBranchId(),
                        c.getTargetBranchId() == null ? null : branchNames.get(c.getTargetBranchId()),
                        c.getNotes(),
                        c.getStatus(),
                        c.getCreatedAt()))
                .toList();
    }
}
