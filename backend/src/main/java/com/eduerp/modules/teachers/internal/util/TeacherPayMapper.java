package com.eduerp.modules.teachers.internal.util;

import com.eduerp.modules.teachers.dto.AllowanceItem;
import com.eduerp.modules.teachers.dto.TeacherPayResponse;
import com.eduerp.modules.teachers.dto.TeacherPayTermsRequest;
import com.eduerp.modules.teachers.internal.model.TeacherAllowance;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;

/** Chuyển giữa DTO và entity: {@code dto} và {@code internal.model} cùng tier, không phụ thuộc lẫn nhau. */
public final class TeacherPayMapper {

    private TeacherPayMapper() {
    }

    public static void apply(TeacherProfile profile, TeacherPayTermsRequest terms) {
        var allowances = terms.allowances().stream()
                .map(item -> new TeacherAllowance(item.name().strip(), item.amount()))
                .toList();
        profile.applyPayTerms(terms.payType(), terms.monthlySalary(), terms.hourlyRate(), terms.workStartDate(),
                terms.probationEndDate(), terms.dependentCount(), allowances);
    }

    public static TeacherPayResponse toResponse(TeacherProfile profile) {
        var allowances = profile.getAllowances().stream()
                .map(allowance -> new AllowanceItem(allowance.getName(), allowance.getAmount()))
                .toList();
        return new TeacherPayResponse(profile.getId(), profile.getPayType(), profile.getMonthlySalary(),
                profile.getHourlyRate(), profile.getWorkStartDate(), profile.getProbationEndDate(),
                profile.getDependentCount(), allowances);
    }
}
