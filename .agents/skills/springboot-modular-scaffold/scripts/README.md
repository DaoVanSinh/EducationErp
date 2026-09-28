# Spring Boot Modular Scaffolder

Generate modular Spring Boot 3.4+ / Java 21 domain and integration modules following Spring Modulith conventions.

## Usage

### 1. Add a Domain Module
```bash
python scripts/scaffold.py --add-module billing --package com.eduerp --output ./backend/src/main/java
```

This generates an advanced module under `com.eduerp.modules.billing`:
- `BillingConstants.java` (Class-grouped Limits, Resources, Actions, Authorities, CacheNamespaces, ErrorCodes)
- `BillingProperties.java` (`@ConfigurationProperties(prefix = "billing")`)
- `BillingException.java` (Sealed domain exception hierarchy)
- `BillingEvents.java` (Domain event records)
- `BillingManagement.java` (Public Facade for cross-module calls)
- `dto/` (`package-info.java` with `@NamedInterface("dto")`, `CreateBillingRequest`, `BillingResponse`)
- `internal/model/Billing.java` (`@Entity`)
- `internal/repository/BillingRepository.java` (`JpaRepository`)
- `internal/rules/BillingRules.java` (Pure business decision rules)
- `usecase/CreateBilling.java` (One class = one use case, `@Transactional` on `execute()`)
- `web/BillingController.java` (`@RestController`, thin HTTP mapping)

### 2. Add an Integration
```bash
python scripts/scaffold.py --add-integration cache --package com.eduerp --output ./backend/src/main/java
```
