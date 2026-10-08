package com.eduerp.modules.organization.internal.access;

import com.eduerp.modules.access.BranchCatalog;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.shared.NamedReference;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class OrganizationBranchCatalog implements BranchCatalog {

    private final OrganizationManagement organization;

    OrganizationBranchCatalog(OrganizationManagement organization) {
        this.organization = organization;
    }

    @Override
    public List<NamedReference> listAll() {
        return organization.listAll();
    }
}
