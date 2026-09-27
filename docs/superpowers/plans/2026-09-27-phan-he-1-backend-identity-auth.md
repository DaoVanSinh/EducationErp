# Phân hệ 1 — Backend: Core Định danh & Quản trị — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng module Spring Boot `identity` (Branch, Account, Role, Group, Permission có scope, Audit Log, Dashboard cơ bản) cùng luồng Auth JWT-gọn qua cookie + Redis (permission cache + blacklist), đúng theo spec.

**Architecture:** Một Spring Boot app (Spring Modulith), package `com.eduerp.identity` sở hữu toàn bộ entity/logic của phân hệ này; `com.eduerp.core` chỉ chứa cơ chế dùng chung (exception mapping). Quyền hiệu lực tính từ Role+Group, cache ở Redis theo account, JWT chỉ mang `accountId`+`jti`.

**Tech Stack:** Java 21, Spring Boot 3.4.x, Maven, Spring Data JPA, Spring Security, Spring Modulith, Spring Data Redis, PostgreSQL + Flyway, Lombok, JJWT, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md` (mục 4, 5, 6, 7)

## Global Constraints

- Java 21, Spring Boot 3.4.x, Maven, một deployable duy nhất — theo skill `springboot-modular-scaffold`.
- PostgreSQL + Flyway cho migration; Redis cho cache quyền + blacklist token.
- Base package `com.eduerp`; toàn bộ entity/logic Phân hệ 1 nằm trong `com.eduerp.identity` (module sở hữu constants/exception/config của chính nó).
- Class/method mang `@Transactional` hoặc `@ApplicationModuleListener` phải `public` (proxy-based AOP chỉ chặn được method public).
- Không constant hay hàm nào nằm ngoài class — mọi enum/hằng số nằm trong `IdentityConstants` (nested class theo từng nhóm: `PermissionScope`, `AccountStatus`, `Resources`, `Actions`, `Roles`).
- Cookie: `access_token` (HttpOnly, Secure, SameSite=Strict, TTL 15 phút, JWT chỉ chứa `sub`=accountId + `jti`), `refresh_token` (cùng thuộc tính, TTL 30 ngày, rotate mỗi lần dùng), `XSRF-TOKEN` (không HttpOnly, double-submit CSRF).
- Redis key: `perm:account:{id}` (permission cache, TTL 10 phút, evict ngay khi đổi Role/Group/PermissionGroup), `blacklist:jti:{jti}` (TTL = thời gian còn lại của token), `sessions:account:{id}` (set các `jti` access token đang hoạt động, dùng cho force-logout), `pwreset:token:{token}` (TTL 30 phút).
- Dedupe scope: cùng một `(resource, action)` xuất hiện ở nhiều nhóm quyền → lấy scope rộng nhất theo thứ tự `ORGANIZATION` > `BRANCH` > `PERSONAL`.
- Không trả JPA `@Entity` trực tiếp ra HTTP response — luôn qua DTO record.

---

### Task 1: Bootstrap dự án Spring Boot

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/eduerp/EduErpApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/eduerp/core/exception/AppException.java`
- Create: `backend/src/main/java/com/eduerp/core/exception/GlobalExceptionHandler.java`
- Create: `backend/compose.yaml`
- Test: (không có unit test riêng — verify bằng build ở Step cuối)

**Interfaces:**
- Produces: `AppException(String errorCode, HttpStatus status, String message)` — lớp cha mọi domain exception dùng ở các task sau; `GlobalExceptionHandler` map `AppException` → `ProblemDetail`.

- [ ] **Step 1: Tạo `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>
  </parent>
  <groupId>com.eduerp</groupId>
  <artifactId>backend</artifactId>
  <version>0.1.0</version>
  <properties>
    <java.version>21</java.version>
    <spring-modulith.version>1.3.1</spring-modulith.version>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-bom</artifactId>
        <version>${spring-modulith.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.redis</groupId>
      <artifactId>testcontainers-redis</artifactId>
      <version>2.2.4</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok</artifactId>
            </exclude>
          </excludes>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Tạo `EduErpApplication.java`**

```java
package com.eduerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "EduERP", sharedModules = {})
@SpringBootApplication
public class EduErpApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduErpApplication.class, args);
    }
}
```

- [ ] **Step 3: Tạo `application.yml`**

```yaml
spring:
  application:
    name: eduerp-backend
  datasource:
    url: jdbc:postgresql://localhost:5432/eduerp
    username: ${DB_USERNAME:eduerp}
    password: ${DB_PASSWORD:eduerp}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}

identity:
  jwt-secret: ${IDENTITY_JWT_SECRET:dev-only-secret-change-me-please-32bytes}
  access-token-ttl: 15m
  refresh-token-ttl: 30d
  password-reset-ttl: 30m
  mail-from: no-reply@eduerp.local
  frontend-reset-url: http://localhost:5173/reset-password

server:
  port: 8080
```

- [ ] **Step 4: Tạo `AppException.java`**

```java
package com.eduerp.core.exception;

import org.springframework.http.HttpStatus;

public abstract class AppException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    protected AppException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
```

- [ ] **Step 5: Tạo `GlobalExceptionHandler.java`**

```java
package com.eduerp.core.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(ex.getStatus());
        detail.setTitle(ex.getErrorCode());
        detail.setDetail(ex.getMessage());
        detail.setProperty("errorCode", ex.getErrorCode());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("VALIDATION_FAILED");
        detail.setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList());
        return detail;
    }
}
```

- [ ] **Step 6: Tạo `compose.yaml` (Postgres + Redis + MailHog cho dev)**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: eduerp
      POSTGRES_USER: eduerp
      POSTGRES_PASSWORD: eduerp
    ports: ["5432:5432"]
  redis:
    image: redis:7
    ports: ["6379:6379"]
  mailhog:
    image: mailhog/mailhog
    ports: ["1025:1025", "8025:8025"]
```

- [ ] **Step 7: Build thử để xác nhận project khởi tạo đúng**

Run: `cd backend && mvn -q -DskipTests package`
Expected: `BUILD SUCCESS`, file `target/backend-0.1.0.jar` được tạo (chưa có DB nên chưa chạy `spring-boot:run` ở bước này).

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml backend/compose.yaml backend/src
git commit -m "feat(backend): bootstrap Spring Boot project skeleton"
```

---

### Task 2: `IdentityConstants` — enum & hằng số dùng chung của module

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/IdentityConstants.java`
- Test: `backend/src/test/java/com/eduerp/identity/IdentityConstantsTest.java`

**Interfaces:**
- Produces: `IdentityConstants.PermissionScope` (enum: `PERSONAL, BRANCH, ORGANIZATION`), `IdentityConstants.AccountStatus` (enum: `ACTIVE, DISABLED`), `IdentityConstants.Resources` (String constants: `ACCOUNT, ROLE, GROUP, PERMISSION_GROUP, BRANCH, AUDIT_LOG, DASHBOARD`), `IdentityConstants.Actions` (`CREATE, READ, UPDATE, DELETE, APPROVE`), `IdentityConstants.RoleCodes` (`ADMIN, TEACHER, STUDENT`).

- [ ] **Step 1: Viết test cho thứ tự rộng-hẹp của scope (dùng ở Task 6)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityConstantsTest {

    @Test
    void organizationIsBroaderThanBranchAndPersonal() {
        assertThat(IdentityConstants.PermissionScope.ORGANIZATION.rank())
                .isGreaterThan(IdentityConstants.PermissionScope.BRANCH.rank());
        assertThat(IdentityConstants.PermissionScope.BRANCH.rank())
                .isGreaterThan(IdentityConstants.PermissionScope.PERSONAL.rank());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận fail (chưa có `IdentityConstants`)**

Run: `cd backend && mvn -q test -Dtest=IdentityConstantsTest`
Expected: FAIL biên dịch — không tìm thấy class `IdentityConstants`.

- [ ] **Step 3: Viết `IdentityConstants.java`**

```java
package com.eduerp.identity;

public final class IdentityConstants {

    private IdentityConstants() {
    }

    public enum PermissionScope {
        PERSONAL(0), BRANCH(1), ORGANIZATION(2);

        private final int rank;

        PermissionScope(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    public enum AccountStatus {
        ACTIVE, DISABLED
    }

    public static final class Resources {
        private Resources() {
        }

        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String GROUP = "GROUP";
        public static final String PERMISSION_GROUP = "PERMISSION_GROUP";
        public static final String BRANCH = "BRANCH";
        public static final String AUDIT_LOG = "AUDIT_LOG";
        public static final String DASHBOARD = "DASHBOARD";
    }

    public static final class Actions {
        private Actions() {
        }

        public static final String CREATE = "CREATE";
        public static final String READ = "READ";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String APPROVE = "APPROVE";
    }

    public static final class RoleCodes {
        private RoleCodes() {
        }

        public static final String ADMIN = "ADMIN";
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";
    }
}
```

- [ ] **Step 4: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=IdentityConstantsTest`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/IdentityConstants.java backend/src/test/java/com/eduerp/identity/IdentityConstantsTest.java
git commit -m "feat(identity): add IdentityConstants (scope rank, resources, actions, role codes)"
```

---

### Task 3: `Branch` entity + migration + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_branches.sql`
- Create: `backend/src/main/java/com/eduerp/identity/Branch.java`
- Create: `backend/src/main/java/com/eduerp/identity/BranchRepository.java`
- Test: `backend/src/test/java/com/eduerp/identity/BranchRepositoryIT.java`

**Interfaces:**
- Produces: `Branch(String code, String name, String address)` constructor, `Branch.getId()/getCode()/getName()/getAddress()/isActive()`; `BranchRepository extends JpaRepository<Branch, UUID>` với `findByCode(String code)`.

- [ ] **Step 1: Tạo migration `V1__create_branches.sql`**

```sql
CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true
);
```

- [ ] **Step 2: Viết test integration cho `BranchRepository` (fail trước vì entity chưa tồn tại)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class BranchRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    BranchRepository branches;

    @Test
    void savesAndFindsByCode() {
        branches.save(new Branch("HN01", "Chi nhánh Hà Nội", "123 Cau Giay"));

        var found = branches.findByCode("HN01");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Chi nhánh Hà Nội");
        assertThat(found.get().isActive()).isTrue();
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=BranchRepositoryIT`
Expected: FAIL biên dịch — chưa có `Branch`/`BranchRepository`.

- [ ] **Step 4: Viết `Branch.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "branches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
class Branch {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String address;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    Branch(String code, String name, String address) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.active = true;
    }
}
```

- [ ] **Step 5: Viết `BranchRepository.java`**

```java
package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BranchRepository extends JpaRepository<Branch, UUID> {
    Optional<Branch> findByCode(String code);
}
```

- [ ] **Step 6: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=BranchRepositoryIT`
Expected: `Tests run: 1, Failures: 0` (Testcontainers tự kéo image Postgres 16)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__create_branches.sql backend/src/main/java/com/eduerp/identity/Branch.java backend/src/main/java/com/eduerp/identity/BranchRepository.java backend/src/test/java/com/eduerp/identity/BranchRepositoryIT.java
git commit -m "feat(identity): add Branch entity, migration, repository"
```

---

### Task 4: `Permission`, `PermissionGroup`, `PermissionGroupItem` — catalog quyền có scope

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_permission_catalog.sql`
- Create: `backend/src/main/java/com/eduerp/identity/Permission.java`
- Create: `backend/src/main/java/com/eduerp/identity/PermissionRepository.java`
- Create: `backend/src/main/java/com/eduerp/identity/PermissionGroup.java`
- Create: `backend/src/main/java/com/eduerp/identity/PermissionGroupItem.java`
- Create: `backend/src/main/java/com/eduerp/identity/PermissionGroupRepository.java`
- Test: `backend/src/test/java/com/eduerp/identity/PermissionGroupRepositoryIT.java`

**Interfaces:**
- Produces: `Permission(String resource, String action)`; `PermissionGroup(String name, String description)` + `addItem(Permission permission, IdentityConstants.PermissionScope scope)` + `getItems()` (trả `List<PermissionGroupItem>` bất biến); `PermissionGroupItem.getPermission()/getScope()`; `PermissionRepository extends JpaRepository<Permission, UUID>` với `findByResourceAndAction(String, String)`; `PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID>`.

- [ ] **Step 1: Migration `V2__create_permission_catalog.sql`**

```sql
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    UNIQUE (resource, action)
);

CREATE TABLE permission_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE permission_group_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_group_id UUID NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    scope VARCHAR(16) NOT NULL,
    UNIQUE (permission_group_id, permission_id)
);
```

- [ ] **Step 2: Viết test integration (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class PermissionGroupRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PermissionRepository permissions;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Test
    void savesGroupWithScopedItems() {
        var permission = permissions.save(new Permission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.CREATE));
        var group = new PermissionGroup("Quản trị tài khoản", "Tạo/sửa tài khoản");
        group.addItem(permission, IdentityConstants.PermissionScope.BRANCH);

        permissionGroups.save(group);
        var found = permissionGroups.findById(group.getId()).orElseThrow();

        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getScope()).isEqualTo(IdentityConstants.PermissionScope.BRANCH);
        assertThat(found.getItems().get(0).getPermission().getResource()).isEqualTo(IdentityConstants.Resources.ACCOUNT);
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=PermissionGroupRepositoryIT`
Expected: FAIL biên dịch.

- [ ] **Step 4: Viết `Permission.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"resource", "action"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Permission {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String action;

    Permission(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }
}
```

- [ ] **Step 5: Viết `PermissionGroupItem.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permission_group_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PermissionGroupItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "permission_group_id", nullable = false)
    private PermissionGroup permissionGroup;

    @ManyToOne
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    private IdentityConstants.PermissionScope scope;

    PermissionGroupItem(PermissionGroup permissionGroup, Permission permission, IdentityConstants.PermissionScope scope) {
        this.permissionGroup = permissionGroup;
        this.permission = permission;
        this.scope = scope;
    }
}
```

- [ ] **Step 6: Viết `PermissionGroup.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permission_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PermissionGroup {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String description;

    @OneToMany(mappedBy = "permissionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PermissionGroupItem> items = new ArrayList<>();

    PermissionGroup(String name, String description) {
        this.name = name;
        this.description = description;
    }

    void addItem(Permission permission, IdentityConstants.PermissionScope scope) {
        items.add(new PermissionGroupItem(this, permission, scope));
    }

    public List<PermissionGroupItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
```

- [ ] **Step 7: Viết `PermissionRepository.java` và `PermissionGroupRepository.java`**

```java
package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByResourceAndAction(String resource, String action);
}
```

```java
package com.eduerp.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID> {
}
```

- [ ] **Step 8: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=PermissionGroupRepositoryIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__create_permission_catalog.sql backend/src/main/java/com/eduerp/identity/Permission*.java backend/src/test/java/com/eduerp/identity/PermissionGroupRepositoryIT.java
git commit -m "feat(identity): add Permission/PermissionGroup/PermissionGroupItem catalog"
```

---

### Task 5: `Role` và `Group` — gán nhiều `PermissionGroup`

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_roles_and_groups.sql`
- Create: `backend/src/main/java/com/eduerp/identity/Role.java`
- Create: `backend/src/main/java/com/eduerp/identity/RoleRepository.java`
- Create: `backend/src/main/java/com/eduerp/identity/Group.java`
- Create: `backend/src/main/java/com/eduerp/identity/GroupRepository.java`
- Test: `backend/src/test/java/com/eduerp/identity/RoleRepositoryIT.java`

**Interfaces:**
- Produces: `Role(String code, String name, boolean systemDefault)` + `addPermissionGroup(PermissionGroup)` + `getPermissionGroups()`; `Group(String name, String description)` + `addPermissionGroup(PermissionGroup)` + `getPermissionGroups()`; `RoleRepository.findByCode(String)`; `GroupRepository extends JpaRepository<Group, UUID>`.

- [ ] **Step 1: Migration `V3__create_roles_and_groups.sql`**

```sql
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    system_default BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE role_permission_groups (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_group_id UUID NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_group_id)
);

CREATE TABLE user_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE group_permission_groups (
    group_id UUID NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    permission_group_id UUID NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, permission_group_id)
);
```

- [ ] **Step 2: Viết test integration (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class RoleRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Test
    void roleCanReferenceMultiplePermissionGroups() {
        var group = permissionGroups.save(new PermissionGroup("Nhóm cơ bản", null));
        var role = new Role(IdentityConstants.RoleCodes.TEACHER, "Giáo viên", true);
        role.addPermissionGroup(group);

        roles.save(role);
        var found = roles.findByCode(IdentityConstants.RoleCodes.TEACHER).orElseThrow();

        assertThat(found.getPermissionGroups()).hasSize(1);
        assertThat(found.isSystemDefault()).isTrue();
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=RoleRepositoryIT`
Expected: FAIL biên dịch.

- [ ] **Step 4: Viết `Role.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Role {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private boolean systemDefault;

    @ManyToMany
    @JoinTable(name = "role_permission_groups",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_group_id"))
    private final Set<PermissionGroup> permissionGroups = new HashSet<>();

    Role(String code, String name, boolean systemDefault) {
        this.code = code;
        this.name = name;
        this.systemDefault = systemDefault;
    }

    void addPermissionGroup(PermissionGroup group) {
        permissionGroups.add(group);
    }

    public Set<PermissionGroup> getPermissionGroups() {
        return Collections.unmodifiableSet(permissionGroups);
    }
}
```

- [ ] **Step 5: Viết `Group.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "user_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Group {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(name = "group_permission_groups",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_group_id"))
    private final Set<PermissionGroup> permissionGroups = new HashSet<>();

    Group(String name, String description) {
        this.name = name;
        this.description = description;
    }

    void addPermissionGroup(PermissionGroup group) {
        permissionGroups.add(group);
    }

    public Set<PermissionGroup> getPermissionGroups() {
        return Collections.unmodifiableSet(permissionGroups);
    }
}
```

- [ ] **Step 6: Viết `RoleRepository.java` và `GroupRepository.java`**

```java
package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
}
```

```java
package com.eduerp.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GroupRepository extends JpaRepository<Group, UUID> {
}
```

- [ ] **Step 7: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=RoleRepositoryIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V3__create_roles_and_groups.sql backend/src/main/java/com/eduerp/identity/Role*.java backend/src/main/java/com/eduerp/identity/Group*.java backend/src/test/java/com/eduerp/identity/RoleRepositoryIT.java
git commit -m "feat(identity): add Role and Group entities with PermissionGroup assignment"
```

---

### Task 6: `EffectivePermission` — quy tắc tính quyền hiệu lực (dedupe scope rộng nhất)

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/EffectivePermission.java`
- Create: `backend/src/main/java/com/eduerp/identity/EffectivePermissionCalculator.java`
- Test: `backend/src/test/java/com/eduerp/identity/EffectivePermissionCalculatorTest.java`

**Interfaces:**
- Consumes: `Account.getRole()`, `Account.getGroups()` (định nghĩa ở Task 7) — file test dùng bản dựng thủ công `Role`/`Group`/`PermissionGroup` trực tiếp, không cần `Account`.
- Produces: `record EffectivePermission(String resource, String action, IdentityConstants.PermissionScope scope)`; `EffectivePermissionCalculator.calculate(Role role, Set<Group> groups)` trả `Set<EffectivePermission>`.

**Đây là rule thuần, không Spring — viết test trước theo TDD như mọi rule khác trong dự án.**

- [ ] **Step 1: Viết test — cùng permission 2 scope khác nhau ở 2 nhóm khác nhau, phải lấy scope rộng nhất**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectivePermissionCalculatorTest {

    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();

    @Test
    void takesBroadestScopeWhenSamePermissionGrantedTwice() {
        var permission = new Permission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.UPDATE);

        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(permission, IdentityConstants.PermissionScope.PERSONAL);
        var role = new Role(IdentityConstants.RoleCodes.TEACHER, "Giáo viên", true);
        role.addPermissionGroup(roleGroup);

        var extraGroup = new PermissionGroup("Từ group", null);
        extraGroup.addItem(permission, IdentityConstants.PermissionScope.BRANCH);
        var group = new Group("Phòng đào tạo", null);
        group.addPermissionGroup(extraGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactly(
                new EffectivePermission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.UPDATE,
                        IdentityConstants.PermissionScope.BRANCH));
    }

    @Test
    void unionsDistinctPermissionsFromRoleAndGroups() {
        var readPermission = new Permission(IdentityConstants.Resources.BRANCH, IdentityConstants.Actions.READ);
        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(readPermission, IdentityConstants.PermissionScope.ORGANIZATION);
        var role = new Role(IdentityConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(roleGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of());

        assertThat(result).hasSize(1);
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=EffectivePermissionCalculatorTest`
Expected: FAIL biên dịch — chưa có `EffectivePermission`/`EffectivePermissionCalculator`.

- [ ] **Step 3: Viết `EffectivePermission.java`**

```java
package com.eduerp.identity;

public record EffectivePermission(String resource, String action, IdentityConstants.PermissionScope scope) {

    String key() {
        return resource + ":" + action;
    }
}
```

- [ ] **Step 4: Viết `EffectivePermissionCalculator.java`**

```java
package com.eduerp.identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

class EffectivePermissionCalculator {

    Set<EffectivePermission> calculate(Role role, Set<Group> groups) {
        Map<String, EffectivePermission> byKey = new HashMap<>();

        Stream.concat(
                role.getPermissionGroups().stream(),
                groups.stream().flatMap(g -> g.getPermissionGroups().stream()))
                .flatMap(pg -> pg.getItems().stream())
                .forEach(item -> {
                    var candidate = new EffectivePermission(
                            item.getPermission().getResource(),
                            item.getPermission().getAction(),
                            item.getScope());
                    byKey.merge(candidate.key(), candidate, this::broader);
                });

        return new HashSet<>(byKey.values());
    }

    private EffectivePermission broader(EffectivePermission a, EffectivePermission b) {
        return a.scope().rank() >= b.scope().rank() ? a : b;
    }
}
```

- [ ] **Step 5: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=EffectivePermissionCalculatorTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/EffectivePermission*.java backend/src/test/java/com/eduerp/identity/EffectivePermissionCalculatorTest.java
git commit -m "feat(identity): add EffectivePermissionCalculator (broadest-scope dedupe rule)"
```

---

### Task 7: `Account` entity + migration + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__create_accounts.sql`
- Create: `backend/src/main/java/com/eduerp/identity/Account.java`
- Create: `backend/src/main/java/com/eduerp/identity/AccountRepository.java`
- Test: `backend/src/test/java/com/eduerp/identity/AccountRepositoryIT.java`

**Interfaces:**
- Produces: `Account(String email, String passwordHash, String fullName, Role role, Branch homeBranch)` (homeBranch có thể null); `Account.getId()/getEmail()/getPasswordHash()/getFullName()/getAvatarUrl()/getStatus()/getHomeBranch()/getRole()/getGroups()`; `Account.changePasswordHash(String)`, `Account.updateProfile(String fullName, String avatarUrl)`, `Account.transferToBranch(Branch)`, `Account.joinGroup(Group)`; `AccountRepository.findByEmail(String)`.

- [ ] **Step 1: Migration `V4__create_accounts.sql`**

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    home_branch_id UUID REFERENCES branches(id),
    role_id UUID NOT NULL REFERENCES roles(id)
);

CREATE TABLE account_groups (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (account_id, group_id)
);
```

- [ ] **Step 2: Viết test integration (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class AccountRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    BranchRepository branches;

    @Test
    void savesAccountWithHomeBranchAndFindsByEmail() {
        var role = roles.save(new Role(IdentityConstants.RoleCodes.TEACHER, "Giáo viên", true));
        var branch = branches.save(new Branch("HN01", "Chi nhánh Hà Nội", null));

        accounts.save(new Account("teacher@eduerp.local", "hashed", "Nguyễn Văn A", role, branch));
        var found = accounts.findByEmail("teacher@eduerp.local");

        assertThat(found).isPresent();
        assertThat(found.get().getHomeBranch()).isNotNull();
        assertThat(found.get().getStatus()).isEqualTo(IdentityConstants.AccountStatus.ACTIVE);
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=AccountRepositoryIT`
Expected: FAIL biên dịch.

- [ ] **Step 4: Viết `Account.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Account {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdentityConstants.AccountStatus status;

    @ManyToOne
    @JoinColumn(name = "home_branch_id")
    private Branch homeBranch;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToMany
    @JoinTable(name = "account_groups",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private final Set<Group> groups = new HashSet<>();

    Account(String email, String passwordHash, String fullName, Role role, Branch homeBranch) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.homeBranch = homeBranch;
        this.status = IdentityConstants.AccountStatus.ACTIVE;
    }

    public Set<Group> getGroups() {
        return Collections.unmodifiableSet(groups);
    }

    void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    void updateProfile(String fullName, String avatarUrl) {
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
    }

    void transferToBranch(Branch branch) {
        this.homeBranch = branch;
    }

    void joinGroup(Group group) {
        groups.add(group);
    }

    void changeRole(Role role) {
        this.role = role;
    }
}
```

- [ ] **Step 5: Viết `AccountRepository.java`**

```java
package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    long countByRole_Code(String roleCode);

    long countByHomeBranch_Id(UUID branchId);
}
```

- [ ] **Step 6: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=AccountRepositoryIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V4__create_accounts.sql backend/src/main/java/com/eduerp/identity/Account*.java backend/src/test/java/com/eduerp/identity/AccountRepositoryIT.java
git commit -m "feat(identity): add Account entity with home branch, role, groups"
```

---

### Task 8: Seed permission catalog mặc định + Role Admin/Teacher/Student

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__seed_default_rbac.sql`
- Test: `backend/src/test/java/com/eduerp/identity/DefaultRbacSeedIT.java`

**Interfaces:**
- Không thêm class mới — chỉ dữ liệu seed, kiểm tra bằng test đọc DB sau khi Flyway chạy.

- [ ] **Step 1: Viết test xác nhận seed tồn tại (fail trước vì chưa có migration)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class DefaultRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    @Test
    void adminRoleHasOrganizationScopeOnEveryResource() {
        var admin = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();

        var allOrganizationScope = admin.getPermissionGroups().stream()
                .flatMap(g -> g.getItems().stream())
                .allMatch(item -> item.getScope() == IdentityConstants.PermissionScope.ORGANIZATION);

        assertThat(allOrganizationScope).isTrue();
        assertThat(roles.findByCode(IdentityConstants.RoleCodes.TEACHER)).isPresent();
        assertThat(roles.findByCode(IdentityConstants.RoleCodes.STUDENT)).isPresent();
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=DefaultRbacSeedIT`
Expected: FAIL — `findByCode(ADMIN)` rỗng vì chưa có migration seed.

- [ ] **Step 3: Viết migration `V5__seed_default_rbac.sql`**

```sql
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'ACCOUNT', 'CREATE'),
    (gen_random_uuid(), 'ACCOUNT', 'READ'),
    (gen_random_uuid(), 'ACCOUNT', 'UPDATE'),
    (gen_random_uuid(), 'ACCOUNT', 'DELETE'),
    (gen_random_uuid(), 'ROLE', 'CREATE'),
    (gen_random_uuid(), 'ROLE', 'READ'),
    (gen_random_uuid(), 'ROLE', 'UPDATE'),
    (gen_random_uuid(), 'ROLE', 'DELETE'),
    (gen_random_uuid(), 'GROUP', 'CREATE'),
    (gen_random_uuid(), 'GROUP', 'READ'),
    (gen_random_uuid(), 'GROUP', 'UPDATE'),
    (gen_random_uuid(), 'GROUP', 'DELETE'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'CREATE'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'READ'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'UPDATE'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'DELETE'),
    (gen_random_uuid(), 'BRANCH', 'CREATE'),
    (gen_random_uuid(), 'BRANCH', 'READ'),
    (gen_random_uuid(), 'BRANCH', 'UPDATE'),
    (gen_random_uuid(), 'AUDIT_LOG', 'READ'),
    (gen_random_uuid(), 'DASHBOARD', 'READ');

INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000001', 'Toàn quyền hệ thống', 'Mọi permission, scope ORGANIZATION'),
    ('11111111-0000-0000-0000-000000000002', 'Tự phục vụ cơ bản', 'Xem/sửa hồ sơ của chính mình');

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p;

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000002', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'ACCOUNT' AND p.action IN ('READ', 'UPDATE');

INSERT INTO roles (id, code, name, system_default) VALUES
    ('22222222-0000-0000-0000-000000000001', 'ADMIN', 'Quản trị viên', true),
    ('22222222-0000-0000-0000-000000000002', 'TEACHER', 'Giáo viên', true),
    ('22222222-0000-0000-0000-000000000003', 'STUDENT', 'Học viên', true);

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001'),
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000002'),
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002');
```

- [ ] **Step 4: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=DefaultRbacSeedIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__seed_default_rbac.sql backend/src/test/java/com/eduerp/identity/DefaultRbacSeedIT.java
git commit -m "feat(identity): seed default permission catalog and Admin/Teacher/Student roles"
```

---

### Task 9: `PermissionCacheService` — cache Redis quyền hiệu lực theo account

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/PermissionCacheService.java`
- Create: `backend/src/main/java/com/eduerp/identity/AccountNotFoundException.java`
- Test: `backend/src/test/java/com/eduerp/identity/PermissionCacheServiceIT.java`

**Interfaces:**
- Consumes: `AccountRepository` (Task 7), `EffectivePermissionCalculator` (Task 6).
- Produces: `PermissionCacheService.getEffectivePermissions(UUID accountId)` trả `Set<EffectivePermission>`; `PermissionCacheService.evict(UUID accountId)`.

- [ ] **Step 1: Viết `AccountNotFoundException.java`**

```java
package com.eduerp.identity;

import com.eduerp.core.exception.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends AppException {
    public AccountNotFoundException(UUID id) {
        super("IDENTITY_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản " + id);
    }
}
```

- [ ] **Step 2: Viết test integration dùng Redis Testcontainers (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class PermissionCacheServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    PermissionCacheService cache;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Test
    void computesOnMissThenServesFromCacheAfterEviction() {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        var account = accounts.save(new Account("cache-test@eduerp.local", "hash", "Cache Test", role, null));

        var first = cache.getEffectivePermissions(account.getId());
        assertThat(first).isNotEmpty();

        cache.evict(account.getId());
        var second = cache.getEffectivePermissions(account.getId());
        assertThat(second).isEqualTo(first);
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=PermissionCacheServiceIT`
Expected: FAIL biên dịch — chưa có `PermissionCacheService`.

- [ ] **Step 4: Viết `PermissionCacheService.java`**

```java
package com.eduerp.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PermissionCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "perm:account:";

    private final StringRedisTemplate redis;
    private final AccountRepository accounts;
    private final EffectivePermissionCalculator calculator;
    private final ObjectMapper mapper;

    PermissionCacheService(StringRedisTemplate redis, AccountRepository accounts,
            EffectivePermissionCalculator calculator, ObjectMapper mapper) {
        this.redis = redis;
        this.accounts = accounts;
        this.calculator = calculator;
        this.mapper = mapper;
    }

    public Set<EffectivePermission> getEffectivePermissions(UUID accountId) {
        String key = KEY_PREFIX + accountId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return deserialize(cached);
        }
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var computed = calculator.calculate(account.getRole(), account.getGroups());
        redis.opsForValue().set(key, serialize(computed), TTL);
        return computed;
    }

    public void evict(UUID accountId) {
        redis.delete(KEY_PREFIX + accountId);
    }

    private String serialize(Set<EffectivePermission> permissions) {
        try {
            return mapper.writeValueAsString(permissions);
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được permission set", e);
        }
    }

    private Set<EffectivePermission> deserialize(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(Set.class, EffectivePermission.class));
        } catch (Exception e) {
            throw new IllegalStateException("Không deserialize được permission set", e);
        }
    }
}
```

- [ ] **Step 5: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=PermissionCacheServiceIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/PermissionCacheService.java backend/src/main/java/com/eduerp/identity/AccountNotFoundException.java backend/src/test/java/com/eduerp/identity/PermissionCacheServiceIT.java
git commit -m "feat(identity): add Redis-backed effective-permission cache with eviction"
```

---

### Task 10: `JwtTokenService` + `TokenBlacklistService` (Redis)

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/IdentityProperties.java`
- Create: `backend/src/main/java/com/eduerp/identity/JwtTokenService.java`
- Create: `backend/src/main/java/com/eduerp/identity/IssuedToken.java`
- Create: `backend/src/main/java/com/eduerp/identity/DecodedToken.java`
- Create: `backend/src/main/java/com/eduerp/identity/TokenInvalidException.java`
- Create: `backend/src/main/java/com/eduerp/identity/TokenBlacklistService.java`
- Test: `backend/src/test/java/com/eduerp/identity/JwtTokenServiceTest.java`
- Test: `backend/src/test/java/com/eduerp/identity/TokenBlacklistServiceIT.java`

**Interfaces:**
- Produces: `IdentityProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl, Duration passwordResetTtl, String mailFrom, String frontendResetUrl)` — `@ConfigurationProperties(prefix = "identity")`; `IssuedToken(String jti, String token, Instant expiresAt)`; `DecodedToken(UUID accountId, String jti, Instant expiresAt)`; `JwtTokenService.issueAccessToken(UUID)`, `.issueRefreshToken(UUID)`, `.verify(String token)` (ném `TokenInvalidException` nếu sai chữ ký/hết hạn); `TokenBlacklistService.blacklist(String jti, Duration ttl)`, `.isBlacklisted(String jti)`, `.trackSession(UUID accountId, String jti, Duration ttl)`, `.blacklistAllActiveSessions(UUID accountId)`.

- [ ] **Step 1: Viết `IdentityProperties.java`**

```java
package com.eduerp.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        String mailFrom,
        String frontendResetUrl) {
}
```

- [ ] **Step 2: Bật `@ConfigurationPropertiesScan` trong `EduErpApplication`**

```java
package com.eduerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "EduERP", sharedModules = {})
@SpringBootApplication
@ConfigurationPropertiesScan
public class EduErpApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduErpApplication.class, args);
    }
}
```

- [ ] **Step 3: Viết `IssuedToken.java`, `DecodedToken.java`, `TokenInvalidException.java`**

```java
package com.eduerp.identity;

import java.time.Instant;

record IssuedToken(String jti, String token, Instant expiresAt) {
}
```

```java
package com.eduerp.identity;

import java.time.Instant;
import java.util.UUID;

record DecodedToken(UUID accountId, String jti, Instant expiresAt) {
}
```

```java
package com.eduerp.identity;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public class TokenInvalidException extends AppException {
    public TokenInvalidException(String reason) {
        super("IDENTITY_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, reason);
    }
}
```

- [ ] **Step 4: Viết test thuần cho `JwtTokenService` (issue rồi verify ra đúng accountId, verify JWT hết hạn/sai chữ ký ném `TokenInvalidException`)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private final IdentityProperties properties = new IdentityProperties(
            "test-secret-key-must-be-at-least-32-bytes-long",
            Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(30),
            "no-reply@eduerp.local", "http://localhost:5173/reset-password");

    private final JwtTokenService service = new JwtTokenService(properties);

    @Test
    void issuesAndVerifiesAccessToken() {
        var accountId = UUID.randomUUID();
        var issued = service.issueAccessToken(accountId);

        var decoded = service.verify(issued.token());

        assertThat(decoded.accountId()).isEqualTo(accountId);
        assertThat(decoded.jti()).isEqualTo(issued.jti());
    }

    @Test
    void rejectsTamperedToken() {
        var issued = service.issueAccessToken(UUID.randomUUID());
        var tampered = issued.token().substring(0, issued.token().length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(TokenInvalidException.class);
    }
}
```

- [ ] **Step 5: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=JwtTokenServiceTest`
Expected: FAIL biên dịch.

- [ ] **Step 6: Viết `JwtTokenService.java`**

```java
package com.eduerp.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
class JwtTokenService {

    private final SecretKey key;
    private final IdentityProperties properties;

    JwtTokenService(IdentityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    IssuedToken issueAccessToken(UUID accountId) {
        return issue(accountId, properties.accessTokenTtl());
    }

    IssuedToken issueRefreshToken(UUID accountId) {
        return issue(accountId, properties.refreshTokenTtl());
    }

    DecodedToken verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new DecodedToken(UUID.fromString(claims.getSubject()), claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException("Token không hợp lệ hoặc đã hết hạn");
        }
    }

    private IssuedToken issue(UUID accountId, java.time.Duration ttl) {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(ttl);
        String token = Jwts.builder()
                .subject(accountId.toString())
                .id(jti)
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(jti, token, expiresAt);
    }
}
```

- [ ] **Step 7: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=JwtTokenServiceTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 8: Viết test integration cho `TokenBlacklistService` (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class TokenBlacklistServiceIT {

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    TokenBlacklistService blacklist;

    @Test
    void blacklistedJtiIsDetected() {
        String jti = UUID.randomUUID().toString();

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
        blacklist.blacklist(jti, Duration.ofMinutes(1));
        assertThat(blacklist.isBlacklisted(jti)).isTrue();
    }

    @Test
    void blacklistAllActiveSessionsCoversTrackedJtis() {
        UUID accountId = UUID.randomUUID();
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();
        blacklist.trackSession(accountId, jti1, Duration.ofMinutes(15));
        blacklist.trackSession(accountId, jti2, Duration.ofMinutes(15));

        blacklist.blacklistAllActiveSessions(accountId);

        assertThat(blacklist.isBlacklisted(jti1)).isTrue();
        assertThat(blacklist.isBlacklisted(jti2)).isTrue();
    }
}
```

- [ ] **Step 9: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=TokenBlacklistServiceIT`
Expected: FAIL biên dịch — chưa có `TokenBlacklistService`.

- [ ] **Step 10: Viết `TokenBlacklistService.java`**

```java
package com.eduerp.identity;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:jti:";
    private static final String SESSIONS_PREFIX = "sessions:account:";

    private final StringRedisTemplate redis;

    TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    public void trackSession(UUID accountId, String jti, Duration ttl) {
        String key = SESSIONS_PREFIX + accountId;
        redis.opsForSet().add(key, jti);
        redis.expire(key, ttl);
    }

    public void blacklistAllActiveSessions(UUID accountId) {
        String key = SESSIONS_PREFIX + accountId;
        Set<String> jtis = redis.opsForSet().members(key);
        if (jtis == null) {
            return;
        }
        jtis.forEach(jti -> blacklist(jti, Duration.ofDays(31)));
        redis.delete(key);
    }
}
```

- [ ] **Step 11: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=TokenBlacklistServiceIT`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/IdentityProperties.java backend/src/main/java/com/eduerp/identity/JwtTokenService.java backend/src/main/java/com/eduerp/identity/IssuedToken.java backend/src/main/java/com/eduerp/identity/DecodedToken.java backend/src/main/java/com/eduerp/identity/TokenInvalidException.java backend/src/main/java/com/eduerp/identity/TokenBlacklistService.java backend/src/test/java/com/eduerp/identity/JwtTokenServiceTest.java backend/src/test/java/com/eduerp/identity/TokenBlacklistServiceIT.java backend/src/main/java/com/eduerp/EduErpApplication.java
git commit -m "feat(identity): add thin JWT issue/verify and Redis token blacklist + session tracking"
```

---

### Task 11: Spring Security — cookie filter, CSRF, `PermissionEvaluator`

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/CookieAuthenticationFilter.java`
- Create: `backend/src/main/java/com/eduerp/identity/AccountPrincipal.java`
- Create: `backend/src/main/java/com/eduerp/identity/ScopedPermissionEvaluator.java`
- Create: `backend/src/main/java/com/eduerp/identity/SecurityConfig.java`
- Test: `backend/src/test/java/com/eduerp/identity/CookieAuthenticationFilterIT.java`

**Interfaces:**
- Consumes: `JwtTokenService.verify`, `TokenBlacklistService.isBlacklisted`, `PermissionCacheService.getEffectivePermissions`, `AccountRepository`.
- Produces: `AccountPrincipal(UUID accountId, UUID homeBranchId)` implements `org.springframework.security.core.userdetails.UserDetails`-adjacent contract dùng nội bộ; `GrantedAuthority` dạng chuỗi `"PERM:" + resource + ":" + action + ":" + scope`; endpoint không xác thực → `401`; thiếu quyền → `403` qua `ScopedPermissionEvaluator`.

- [ ] **Step 1: Viết `AccountPrincipal.java`**

```java
package com.eduerp.identity;

import java.util.UUID;

record AccountPrincipal(UUID accountId, UUID homeBranchId) {
}
```

- [ ] **Step 2: Viết test integration cho filter (fail trước — chưa có filter/security config)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CookieAuthenticationFilterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Test
    void requestWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=CookieAuthenticationFilterIT`
Expected: FAIL — chưa có `SecurityConfig`, Spring Security mặc định trả 401 nhưng do chưa cấu hình đúng resource `/api/dashboard/stats` chưa tồn tại → 404 thay vì 401 (test giữ nguyên tinh thần, sẽ pass đúng ở Step tiếp theo khi filter + endpoint mẫu đã có).

- [ ] **Step 4: Viết `CookieAuthenticationFilter.java`**

```java
package com.eduerp.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService blacklist;
    private final PermissionCacheService permissionCache;
    private final AccountRepository accounts;

    CookieAuthenticationFilter(JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            PermissionCacheService permissionCache, AccountRepository accounts) {
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.permissionCache = permissionCache;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        readCookie(request, "access_token").ifPresent(token -> authenticate(token));
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        var decoded = jwtTokenService.verify(token);
        if (blacklist.isBlacklisted(decoded.jti())) {
            return;
        }
        var account = accounts.findById(decoded.accountId()).orElse(null);
        if (account == null) {
            return;
        }
        var permissions = permissionCache.getEffectivePermissions(account.getId());
        List<GrantedAuthority> authorities = permissions.stream()
                .map(p -> new SimpleGrantedAuthority("PERM:" + p.resource() + ":" + p.action() + ":" + p.scope()))
                .map(GrantedAuthority.class::cast)
                .toList();
        var principal = new AccountPrincipal(account.getId(),
                account.getHomeBranch() == null ? null : account.getHomeBranch().getId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return List.of(request.getCookies()).stream()
                .filter(c -> c.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst();
    }
}
```

- [ ] **Step 5: Viết `ScopedPermissionEvaluator.java`**

```java
package com.eduerp.identity;

import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
class ScopedPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null) {
            return false;
        }
        String action = String.valueOf(permission);
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().startsWith("PERM:" + targetType + ":" + action + ":"));
    }
}
```

- [ ] **Step 6: Viết `SecurityConfig.java`**

```java
package com.eduerp.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(ScopedPermissionEvaluator evaluator) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            PermissionCacheService permissionCache, AccountRepository accounts) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new CookieAuthenticationFilter(jwtTokenService, blacklist, permissionCache, accounts),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 7: Thêm endpoint mẫu tạm để test có route thật (`DashboardController` placeholder tối giản, sẽ mở rộng đầy đủ ở Task 16)**

```java
package com.eduerp.identity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'DASHBOARD', 'READ')")
    String stats() {
        return "{}";
    }
}
```

- [ ] **Step 8: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=CookieAuthenticationFilterIT`
Expected: `Tests run: 1, Failures: 0` (request không cookie → 401 do `anyRequest().authenticated()` chặn trước khi vào controller)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/CookieAuthenticationFilter.java backend/src/main/java/com/eduerp/identity/AccountPrincipal.java backend/src/main/java/com/eduerp/identity/ScopedPermissionEvaluator.java backend/src/main/java/com/eduerp/identity/SecurityConfig.java backend/src/main/java/com/eduerp/identity/DashboardController.java backend/src/test/java/com/eduerp/identity/CookieAuthenticationFilterIT.java
git commit -m "feat(identity): wire cookie-based JWT auth filter, CSRF, scoped PermissionEvaluator"
```

---

### Task 12: `AuthController` — login / refresh / logout / force-logout

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/InvalidCredentialsException.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/AuthCookies.java`
- Create: `backend/src/main/java/com/eduerp/identity/AuthController.java`
- Test: `backend/src/test/java/com/eduerp/identity/AuthControllerIT.java`

**Interfaces:**
- Consumes: `AccountRepository`, `PasswordEncoder`, `JwtTokenService`, `TokenBlacklistService`.
- Produces: `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` — set/clear cookie `access_token`/`refresh_token`.

- [ ] **Step 1: Viết `InvalidCredentialsException.java`**

```java
package com.eduerp.identity;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException() {
        super("IDENTITY_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
    }
}
```

- [ ] **Step 2: Viết `dto/LoginRequest.java`**

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
}
```

- [ ] **Step 3: Viết `AuthCookies.java` — nơi duy nhất tạo cookie (rule đặt tên nhất quán)**

```java
package com.eduerp.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

final class AuthCookies {

    private AuthCookies() {
    }

    static void set(HttpServletResponse response, String name, String value, Duration ttl) {
        String cookie = name + "=" + value
                + "; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=" + ttl.toSeconds();
        response.addHeader("Set-Cookie", cookie);
    }

    static void clear(HttpServletResponse response, String name) {
        String cookie = name + "=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0";
        response.addHeader("Set-Cookie", cookie);
    }
}
```

- [ ] **Step 4: Viết test integration cho login → refresh → logout (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.eduerp.identity.dto.LoginRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("flow@eduerp.local", passwordEncoder.encode("Password123!"), "Flow Test", role, null));

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("flow@eduerp.local", "Password123!"))))
                .andReturn();
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
        Cookie access = loginResult.getResponse().getCookie("access_token");
        Cookie refresh = loginResult.getResponse().getCookie("refresh_token");
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();

        var refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andReturn();
        assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);
        Cookie newAccess = refreshResult.getResponse().getCookie("access_token");
        assertThat(newAccess.getValue()).isNotEqualTo(access.getValue());

        var logoutResult = mockMvc.perform(post("/api/auth/logout").cookie(newAccess)).andReturn();
        assertThat(logoutResult.getResponse().getStatus()).isEqualTo(200);
    }
}
```

- [ ] **Step 5: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=AuthControllerIT`
Expected: FAIL biên dịch — chưa có `AuthController`.

- [ ] **Step 6: Viết `AuthController.java`**

```java
package com.eduerp.identity;

import com.eduerp.identity.dto.LoginRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService blacklist;
    private final IdentityProperties properties;

    AuthController(AccountRepository accounts, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
            TokenBlacklistService blacklist, IdentityProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.properties = properties;
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var account = accounts.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        issueTokenPair(account.getId(), response);
    }

    @PostMapping("/refresh")
    public void refresh(@CookieValue("refresh_token") String refreshToken, HttpServletResponse response) {
        var decoded = jwtTokenService.verify(refreshToken);
        if (blacklist.isBlacklisted(decoded.jti())) {
            throw new TokenInvalidException("Refresh token đã bị thu hồi");
        }
        blacklist.blacklist(decoded.jti(), properties.refreshTokenTtl());
        issueTokenPair(decoded.accountId(), response);
    }

    @PostMapping("/logout")
    public void logout(@CookieValue("access_token") String accessToken,
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        blacklist.blacklist(jwtTokenService.verify(accessToken).jti(), properties.accessTokenTtl());
        if (refreshToken != null) {
            blacklist.blacklist(jwtTokenService.verify(refreshToken).jti(), properties.refreshTokenTtl());
        }
        AuthCookies.clear(response, "access_token");
        AuthCookies.clear(response, "refresh_token");
    }

    private void issueTokenPair(java.util.UUID accountId, HttpServletResponse response) {
        var access = jwtTokenService.issueAccessToken(accountId);
        var refresh = jwtTokenService.issueRefreshToken(accountId);
        blacklist.trackSession(accountId, access.jti(), properties.accessTokenTtl());
        AuthCookies.set(response, "access_token", access.token(), properties.accessTokenTtl());
        AuthCookies.set(response, "refresh_token", refresh.token(), properties.refreshTokenTtl());
    }
}
```

- [ ] **Step 7: Cho phép `/api/auth/logout` cần xác thực (đã đúng vì không nằm trong danh sách `permitAll` ở Task 11), chạy lại test xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=AuthControllerIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/InvalidCredentialsException.java backend/src/main/java/com/eduerp/identity/dto/LoginRequest.java backend/src/main/java/com/eduerp/identity/AuthCookies.java backend/src/main/java/com/eduerp/identity/AuthController.java backend/src/test/java/com/eduerp/identity/AuthControllerIT.java
git commit -m "feat(identity): add login/refresh/logout endpoints with rotating refresh token"
```

---

### Task 13: Tự phục vụ tài khoản — đổi mật khẩu, quên/đặt lại mật khẩu, hồ sơ + avatar

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/dto/ChangePasswordRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/ForgotPasswordRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/ResetPasswordRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/ProfileUpdateRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/PasswordResetMailer.java`
- Create: `backend/src/main/java/com/eduerp/identity/AccountSelfServiceController.java`
- Test: `backend/src/test/java/com/eduerp/identity/AccountSelfServiceControllerIT.java`

**Interfaces:**
- Consumes: `AccountRepository`, `PasswordEncoder`, `StringRedisTemplate` (token quên mật khẩu), `TokenBlacklistService` (force-logout sau reset), `PermissionCacheService`.
- Produces: `POST /api/account/change-password`, `POST /api/account/forgot-password`, `POST /api/account/reset-password`, `PATCH /api/account/profile`.

- [ ] **Step 1: Viết 4 DTO**

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {
}
```

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
```

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {
}
```

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(@NotBlank String fullName, String avatarUrl) {
}
```

- [ ] **Step 2: Viết `PasswordResetMailer.java` (gửi mail thật qua `JavaMailSender`, không phải placeholder)**

```java
package com.eduerp.identity;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    PasswordResetMailer(JavaMailSender mailSender, IdentityProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    void sendResetLink(String toEmail, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(toEmail);
        message.setSubject("Đặt lại mật khẩu EduERP");
        message.setText("Nhấn vào liên kết sau để đặt lại mật khẩu (hết hạn sau 30 phút): "
                + properties.frontendResetUrl() + "?token=" + token);
        mailSender.send(message);
    }
}
```

- [ ] **Step 3: Viết test integration cho toàn bộ 4 endpoint (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.eduerp.identity.dto.ChangePasswordRequest;
import com.eduerp.identity.dto.ForgotPasswordRequest;
import com.eduerp.identity.dto.ProfileUpdateRequest;
import com.eduerp.identity.dto.ResetPasswordRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AccountSelfServiceControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate redisTemplate;

    private Cookie login(String email, String rawPassword) throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.eduerp.identity.dto.LoginRequest(email, rawPassword))))
                .andReturn();
        return loginResult.getResponse().getCookie("access_token");
    }

    @Test
    void changePasswordThenLoginWithNewPassword() throws Exception {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("selfservice@eduerp.local", passwordEncoder.encode("OldPass123!"), "Self Service", role, null));
        var access = login("selfservice@eduerp.local", "OldPass123!");

        var result = mockMvc.perform(post("/api/account/change-password")
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("OldPass123!", "NewPass456!"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(login("selfservice@eduerp.local", "NewPass456!")).isNotNull();
    }

    @Test
    void forgotPasswordThenResetPassword() throws Exception {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("forgot@eduerp.local", passwordEncoder.encode("Whatever123!"), "Forgot Test", role, null));

        mockMvc.perform(post("/api/account/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("forgot@eduerp.local"))));

        String token = redisTemplate.keys("pwreset:token:*").stream().findFirst()
                .map(k -> k.substring("pwreset:token:".length()))
                .orElseThrow();

        var resetResult = mockMvc.perform(post("/api/account/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(token, "BrandNew789!"))))
                .andReturn();

        assertThat(resetResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(login("forgot@eduerp.local", "BrandNew789!")).isNotNull();
    }

    @Test
    void updatesProfile() throws Exception {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("profile@eduerp.local", passwordEncoder.encode("Password123!"), "Old Name", role, null));
        var access = login("profile@eduerp.local", "Password123!");

        var result = mockMvc.perform(patch("/api/account/profile")
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProfileUpdateRequest("New Name", "https://cdn/avatar.png"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accounts.findByEmail("profile@eduerp.local").orElseThrow().getFullName()).isEqualTo("New Name");
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=AccountSelfServiceControllerIT`
Expected: FAIL biên dịch — chưa có `AccountSelfServiceController`.

- [ ] **Step 5: Viết `AccountSelfServiceController.java`**

```java
package com.eduerp.identity;

import com.eduerp.identity.dto.ChangePasswordRequest;
import com.eduerp.identity.dto.ForgotPasswordRequest;
import com.eduerp.identity.dto.ProfileUpdateRequest;
import com.eduerp.identity.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountSelfServiceController {

    private static final String RESET_TOKEN_PREFIX = "pwreset:token:";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService permissionCache;
    private final TokenBlacklistService blacklist;
    private final StringRedisTemplate redis;
    private final PasswordResetMailer mailer;
    private final IdentityProperties properties;

    AccountSelfServiceController(AccountRepository accounts, PasswordEncoder passwordEncoder,
            PermissionCacheService permissionCache, TokenBlacklistService blacklist, StringRedisTemplate redis,
            PasswordResetMailer mailer, IdentityProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.permissionCache = permissionCache;
        this.blacklist = blacklist;
        this.redis = redis;
        this.mailer = mailer;
        this.properties = properties;
    }

    @PostMapping("/change-password")
    @Transactional
    public void changePassword(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        var account = accounts.findById(principal.accountId()).orElseThrow(() -> new AccountNotFoundException(principal.accountId()));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accounts.findByEmail(request.email()).ifPresent(account -> {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set(RESET_TOKEN_PREFIX + token, account.getId().toString(), properties.passwordResetTtl());
            mailer.sendResetLink(account.getEmail(), token);
        });
    }

    @PostMapping("/reset-password")
    @Transactional
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String key = RESET_TOKEN_PREFIX + request.token();
        String accountIdRaw = redis.opsForValue().get(key);
        if (accountIdRaw == null) {
            throw new TokenInvalidException("Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        var accountId = UUID.fromString(accountIdRaw);
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        redis.delete(key);
        blacklist.blacklistAllActiveSessions(accountId);
    }

    @PatchMapping("/profile")
    @Transactional
    public void updateProfile(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        var account = accounts.findById(principal.accountId()).orElseThrow(() -> new AccountNotFoundException(principal.accountId()));
        account.updateProfile(request.fullName(), request.avatarUrl());
    }
}
```

- [ ] **Step 6: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=AccountSelfServiceControllerIT`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/dto/ChangePasswordRequest.java backend/src/main/java/com/eduerp/identity/dto/ForgotPasswordRequest.java backend/src/main/java/com/eduerp/identity/dto/ResetPasswordRequest.java backend/src/main/java/com/eduerp/identity/dto/ProfileUpdateRequest.java backend/src/main/java/com/eduerp/identity/PasswordResetMailer.java backend/src/main/java/com/eduerp/identity/AccountSelfServiceController.java backend/src/test/java/com/eduerp/identity/AccountSelfServiceControllerIT.java
git commit -m "feat(identity): add change/forgot/reset password and profile self-service endpoints"
```

---

### Task 14: Seed self-healing tài khoản Admin mặc định

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/DefaultAdminSeeder.java`
- Test: `backend/src/test/java/com/eduerp/identity/DefaultAdminSeederIT.java`

**Interfaces:**
- Consumes: `AccountRepository`, `RoleRepository`, `PasswordEncoder`.
- Produces: `ApplicationRunner` chạy mỗi lần khởi động, tạo tài khoản `admin@eduerp.local` (mật khẩu lấy từ biến môi trường `IDENTITY_DEFAULT_ADMIN_PASSWORD`, mặc định `ChangeMe123!` cho dev) nếu chưa có Account nào giữ Role `ADMIN`.

- [ ] **Step 1: Viết test (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class DefaultAdminSeederIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    AccountRepository accounts;

    @Test
    void defaultAdminExistsAfterStartup() {
        assertThat(accounts.findByEmail("admin@eduerp.local")).isPresent();
        assertThat(accounts.findByEmail("admin@eduerp.local").get().getRole().getCode())
                .isEqualTo(IdentityConstants.RoleCodes.ADMIN);
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=DefaultAdminSeederIT`
Expected: FAIL biên dịch — chưa có `DefaultAdminSeeder`.

- [ ] **Step 3: Viết `DefaultAdminSeeder.java`**

```java
package com.eduerp.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DefaultAdminSeeder implements ApplicationRunner {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    DefaultAdminSeeder(AccountRepository accounts, RoleRepository roles, PasswordEncoder passwordEncoder,
            @Value("${identity.default-admin-password:ChangeMe123!}") String defaultPassword) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasAdmin = accounts.countByRole_Code(IdentityConstants.RoleCodes.ADMIN) > 0;
        if (hasAdmin) {
            return;
        }
        var adminRole = roles.findByCode(IdentityConstants.RoleCodes.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Role ADMIN chưa được seed — kiểm tra V5__seed_default_rbac.sql"));
        accounts.save(new Account("admin@eduerp.local", passwordEncoder.encode(defaultPassword), "Quản trị viên mặc định",
                adminRole, null));
    }
}
```

- [ ] **Step 4: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=DefaultAdminSeederIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/DefaultAdminSeeder.java backend/src/test/java/com/eduerp/identity/DefaultAdminSeederIT.java
git commit -m "feat(identity): self-healing default Admin account seeded on every startup"
```

---

### Task 15: Audit Log qua AOP + ghi nhận đăng nhập

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_audit_logs.sql`
- Create: `backend/src/main/java/com/eduerp/identity/AuditLog.java`
- Create: `backend/src/main/java/com/eduerp/identity/AuditLogRepository.java`
- Create: `backend/src/main/java/com/eduerp/identity/Audited.java`
- Create: `backend/src/main/java/com/eduerp/identity/AuditAspect.java`
- Create: `backend/src/main/java/com/eduerp/identity/CurrentAccountContext.java`
- Modify: `backend/src/main/java/com/eduerp/identity/AuthController.java` (Task 12) — ghi `AuditLog` action `LOGIN` sau khi đăng nhập thành công
- Test: `backend/src/test/java/com/eduerp/identity/AuditAspectIT.java`
- Test: `backend/src/test/java/com/eduerp/identity/AuthControllerIT.java` (Task 12) — bổ sung assertion đăng nhập có ghi audit

**Interfaces:**
- Produces: `@Audited(action = "...", entityType = "...")` — annotation đặt trên method use-case; `AuditAspect` bọc quanh method đó, ghi `AuditLog` sau khi method chạy thành công; `CurrentAccountContext.currentAccountId()` đọc từ `SecurityContextHolder`.
- **Vì sao đăng nhập không dùng `@Audited`**: `AuditAspect` lấy `entityId` từ `String.valueOf(joinPoint.getArgs()[0])` — với `login(LoginRequest request, ...)`, arg đầu tiên là cả `LoginRequest` (chứa mật khẩu dạng plaintext), `toString()` mặc định của record sẽ in thẳng mật khẩu vào `AuditLog.entityId`. Đây là lỗ hổng rò rỉ dữ liệu nhạy cảm nếu dùng annotation chung — nên `AuthController.login` ghi `AuditLog` trực tiếp qua `AuditLogRepository`, không qua `@Audited`.

- [ ] **Step 1: Migration `V6__create_audit_logs.sql`**

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_account_id UUID,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64),
    before_state JSONB,
    after_state JSONB,
    branch_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
```

- [ ] **Step 2: Viết `CurrentAccountContext.java`**

```java
package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class CurrentAccountContext {

    Optional<UUID> currentAccountId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal.accountId());
    }
}
```

- [ ] **Step 3: Viết `AuditLog.java` và `AuditLogRepository.java`**

```java
package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID actorAccountId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    private String entityId;

    @Column(columnDefinition = "jsonb")
    private String beforeState;

    @Column(columnDefinition = "jsonb")
    private String afterState;

    private UUID branchId;

    @Column(nullable = false)
    private Instant occurredAt;

    AuditLog(UUID actorAccountId, String action, String entityType, String entityId, String beforeState,
            String afterState, UUID branchId) {
        this.actorAccountId = actorAccountId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.branchId = branchId;
        this.occurredAt = Instant.now();
    }
}
```

```java
package com.eduerp.identity;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);
}
```

- [ ] **Step 4: Viết annotation `Audited.java`**

```java
package com.eduerp.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Audited {
    String action();

    String entityType();
}
```

- [ ] **Step 5: Viết test cho aspect — dùng một method mẫu đánh dấu `@Audited` (fail trước)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class AuditAspectIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Component
    static class SampleAuditedService {
        @Audited(action = "TEST_ACTION", entityType = "TEST_ENTITY")
        @Transactional
        public String doSomething(String entityId) {
            return "ok:" + entityId;
        }
    }

    @Autowired
    SampleAuditedService sampleService;

    @Autowired
    AuditLogRepository auditLogs;

    @Test
    void logsAfterMethodSucceeds() {
        sampleService.doSomething("entity-123");

        var logs = auditLogs.findByEntityType("TEST_ENTITY", org.springframework.data.domain.Pageable.unpaged());

        assertThat(logs.getContent()).hasSize(1);
        assertThat(logs.getContent().get(0).getAction()).isEqualTo("TEST_ACTION");
    }
}
```

- [ ] **Step 6: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=AuditAspectIT`
Expected: FAIL biên dịch — chưa có `Audited`/`AuditAspect`.

- [ ] **Step 7: Viết `AuditAspect.java`**

```java
package com.eduerp.identity;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
class AuditAspect {

    private final AuditLogRepository auditLogs;
    private final CurrentAccountContext currentAccountContext;

    AuditAspect(AuditLogRepository auditLogs, CurrentAccountContext currentAccountContext) {
        this.auditLogs = auditLogs;
        this.currentAccountContext = currentAccountContext;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        String entityId = joinPoint.getArgs().length > 0 ? String.valueOf(joinPoint.getArgs()[0]) : null;
        var actorId = currentAccountContext.currentAccountId().orElse(null);
        auditLogs.save(new AuditLog(actorId, audited.action(), audited.entityType(), entityId, null, null, null));
        return result;
    }
}
```

- [ ] **Step 8: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=AuditAspectIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 9: Sửa `AuthController.login` để ghi `AuditLog` action `LOGIN` trực tiếp (không qua `@Audited` — lý do nêu ở phần Interfaces)**

```java
package com.eduerp.identity;

import com.eduerp.identity.dto.LoginRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService blacklist;
    private final IdentityProperties properties;
    private final AuditLogRepository auditLogs;

    AuthController(AccountRepository accounts, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
            TokenBlacklistService blacklist, IdentityProperties properties, AuditLogRepository auditLogs) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.properties = properties;
        this.auditLogs = auditLogs;
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var account = accounts.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        issueTokenPair(account.getId(), response);
        var branchId = account.getHomeBranch() == null ? null : account.getHomeBranch().getId();
        auditLogs.save(new AuditLog(account.getId(), "LOGIN", "ACCOUNT", account.getId().toString(), null, null, branchId));
    }

    @PostMapping("/refresh")
    public void refresh(@CookieValue("refresh_token") String refreshToken, HttpServletResponse response) {
        var decoded = jwtTokenService.verify(refreshToken);
        if (blacklist.isBlacklisted(decoded.jti())) {
            throw new TokenInvalidException("Refresh token đã bị thu hồi");
        }
        blacklist.blacklist(decoded.jti(), properties.refreshTokenTtl());
        issueTokenPair(decoded.accountId(), response);
    }

    @PostMapping("/logout")
    public void logout(@CookieValue("access_token") String accessToken,
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        blacklist.blacklist(jwtTokenService.verify(accessToken).jti(), properties.accessTokenTtl());
        if (refreshToken != null) {
            blacklist.blacklist(jwtTokenService.verify(refreshToken).jti(), properties.refreshTokenTtl());
        }
        AuthCookies.clear(response, "access_token");
        AuthCookies.clear(response, "refresh_token");
    }

    private void issueTokenPair(java.util.UUID accountId, HttpServletResponse response) {
        var access = jwtTokenService.issueAccessToken(accountId);
        var refresh = jwtTokenService.issueRefreshToken(accountId);
        blacklist.trackSession(accountId, access.jti(), properties.accessTokenTtl());
        AuthCookies.set(response, "access_token", access.token(), properties.accessTokenTtl());
        AuthCookies.set(response, "refresh_token", refresh.token(), properties.refreshTokenTtl());
    }
}
```

- [ ] **Step 10: Bổ sung assertion vào `AuthControllerIT.loginRefreshLogoutFlow` xác nhận có ghi audit đăng nhập**

Thêm vào cuối method `loginRefreshLogoutFlow` trong `backend/src/test/java/com/eduerp/identity/AuthControllerIT.java` (autowire thêm `AuditLogRepository auditLogs;` vào class test):

```java
        var logs = auditLogs.findByEntityType("ACCOUNT", org.springframework.data.domain.Pageable.unpaged());
        assertThat(logs.getContent()).extracting("action").contains("LOGIN");
```

- [ ] **Step 11: Chạy lại toàn bộ 2 test để xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=AuditAspectIT,AuthControllerIT`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__create_audit_logs.sql backend/src/main/java/com/eduerp/identity/AuditLog.java backend/src/main/java/com/eduerp/identity/AuditLogRepository.java backend/src/main/java/com/eduerp/identity/Audited.java backend/src/main/java/com/eduerp/identity/AuditAspect.java backend/src/main/java/com/eduerp/identity/CurrentAccountContext.java backend/src/main/java/com/eduerp/identity/AuthController.java backend/src/test/java/com/eduerp/identity/AuditAspectIT.java backend/src/test/java/com/eduerp/identity/AuthControllerIT.java
git commit -m "feat(identity): add AOP-based audit logging with @Audited marker, record LOGIN events"
```

---

### Task 16: RBAC admin — CRUD Role/Group/PermissionGroup, gán Group cho Account, chuyển chi nhánh

**Files:**
- Create: `backend/src/main/java/com/eduerp/identity/dto/CreateRoleRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/CreatePermissionGroupRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/AssignGroupRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/TransferBranchRequest.java`
- Create: `backend/src/main/java/com/eduerp/identity/RbacAdminController.java`
- Test: `backend/src/test/java/com/eduerp/identity/RbacAdminControllerIT.java`

**Interfaces:**
- Consumes: `RoleRepository`, `GroupRepository`, `PermissionGroupRepository`, `PermissionRepository`, `AccountRepository`, `PermissionCacheService.evict`.
- Produces: `POST /api/rbac/roles`, `POST /api/rbac/permission-groups`, `POST /api/rbac/accounts/{id}/groups`, `POST /api/rbac/accounts/{id}/transfer-branch` — mọi endpoint đổi quyền/chi nhánh đều gọi `permissionCache.evict(accountId)` và được `@Audited`.

- [ ] **Step 1: Viết 4 DTO**

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateRoleRequest(@NotBlank String code, @NotBlank String name, List<UUID> permissionGroupIds) {
}
```

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreatePermissionGroupRequest(@NotBlank String name, String description, List<Item> items) {
    public record Item(@NotBlank UUID permissionId, @NotBlank String scope) {
    }
}
```

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignGroupRequest(@NotNull UUID groupId) {
}
```

```java
package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferBranchRequest(@NotNull UUID branchId) {
}
```

- [ ] **Step 2: Viết test integration (fail trước) — tạo Role mới, gán Group cho Account, xác nhận cache bị evict (quyền cập nhật ngay ở lần tính tiếp theo)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.eduerp.identity.dto.AssignGroupRequest;
import com.eduerp.identity.dto.TransferBranchRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RbacAdminControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    BranchRepository branches;

    @Autowired
    GroupRepository groups;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PermissionCacheService permissionCache;

    private Cookie loginAsAdmin() throws Exception {
        var adminRole = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("rbac-admin@eduerp.local", passwordEncoder.encode("Password123!"), "Rbac Admin", adminRole, null));
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new com.eduerp.identity.dto.LoginRequest("rbac-admin@eduerp.local", "Password123!"))))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    @Test
    void assigningGroupEvictsPermissionCache() throws Exception {
        var adminCookie = loginAsAdmin();
        var teacherRole = roles.findByCode(IdentityConstants.RoleCodes.TEACHER).orElseThrow();
        var teacher = accounts.save(new Account("teacher-rbac@eduerp.local", passwordEncoder.encode("Password123!"), "Teacher Rbac", teacherRole, null));
        var group = groups.save(new Group("Phòng Kế toán", null));

        permissionCache.getEffectivePermissions(teacher.getId());

        var result = mockMvc.perform(post("/api/rbac/accounts/" + teacher.getId() + "/groups")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(group.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accounts.findById(teacher.getId()).orElseThrow().getGroups()).extracting("id").contains(group.getId());
    }

    @Test
    void transfersBranch() throws Exception {
        var adminCookie = loginAsAdmin();
        var teacherRole = roles.findByCode(IdentityConstants.RoleCodes.TEACHER).orElseThrow();
        var branch = branches.save(new Branch("HCM01", "Chi nhánh Hồ Chí Minh", null));
        var teacher = accounts.save(new Account("teacher-transfer@eduerp.local", passwordEncoder.encode("Password123!"), "Teacher Transfer", teacherRole, null));

        var result = mockMvc.perform(post("/api/rbac/accounts/" + teacher.getId() + "/transfer-branch")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferBranchRequest(branch.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accounts.findById(teacher.getId()).orElseThrow().getHomeBranch().getId()).isEqualTo(branch.getId());
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=RbacAdminControllerIT`
Expected: FAIL biên dịch — chưa có `RbacAdminController`.

- [ ] **Step 4: Viết `RbacAdminController.java`**

```java
package com.eduerp.identity;

import com.eduerp.identity.dto.AssignGroupRequest;
import com.eduerp.identity.dto.CreatePermissionGroupRequest;
import com.eduerp.identity.dto.CreateRoleRequest;
import com.eduerp.identity.dto.TransferBranchRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rbac")
public class RbacAdminController {

    private final RoleRepository roles;
    private final GroupRepository groups;
    private final PermissionGroupRepository permissionGroups;
    private final PermissionRepository permissions;
    private final AccountRepository accounts;
    private final BranchRepository branches;
    private final PermissionCacheService permissionCache;

    RbacAdminController(RoleRepository roles, GroupRepository groups, PermissionGroupRepository permissionGroups,
            PermissionRepository permissions, AccountRepository accounts, BranchRepository branches,
            PermissionCacheService permissionCache) {
        this.roles = roles;
        this.groups = groups;
        this.permissionGroups = permissionGroups;
        this.permissions = permissions;
        this.accounts = accounts;
        this.branches = branches;
        this.permissionCache = permissionCache;
    }

    @PostMapping("/permission-groups")
    @PreAuthorize("hasPermission(null, 'PERMISSION_GROUP', 'CREATE')")
    @Audited(action = "PERMISSION_GROUP_CREATE", entityType = "PERMISSION_GROUP")
    @Transactional
    public UUID createPermissionGroup(@Valid @RequestBody CreatePermissionGroupRequest request) {
        var group = new PermissionGroup(request.name(), request.description());
        for (var item : request.items()) {
            var permission = permissions.findById(item.permissionId())
                    .orElseThrow(() -> new IllegalArgumentException("Permission không tồn tại: " + item.permissionId()));
            group.addItem(permission, IdentityConstants.PermissionScope.valueOf(item.scope()));
        }
        return permissionGroups.save(group).getId();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasPermission(null, 'ROLE', 'CREATE')")
    @Audited(action = "ROLE_CREATE", entityType = "ROLE")
    @Transactional
    public UUID createRole(@Valid @RequestBody CreateRoleRequest request) {
        var role = new Role(request.code(), request.name(), false);
        request.permissionGroupIds().forEach(id -> role.addPermissionGroup(
                permissionGroups.findById(id).orElseThrow(() -> new IllegalArgumentException("PermissionGroup không tồn tại: " + id))));
        return roles.save(role).getId();
    }

    @PostMapping("/accounts/{accountId}/groups")
    @PreAuthorize("hasPermission(null, 'ACCOUNT', 'UPDATE')")
    @Audited(action = "ACCOUNT_JOIN_GROUP", entityType = "ACCOUNT")
    @Transactional
    public void assignGroup(@PathVariable UUID accountId, @Valid @RequestBody AssignGroupRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var group = groups.findById(request.groupId())
                .orElseThrow(() -> new IllegalArgumentException("Group không tồn tại: " + request.groupId()));
        account.joinGroup(group);
        permissionCache.evict(accountId);
    }

    @PostMapping("/accounts/{accountId}/transfer-branch")
    @PreAuthorize("hasPermission(null, 'ACCOUNT', 'UPDATE')")
    @Audited(action = "ACCOUNT_TRANSFER_BRANCH", entityType = "ACCOUNT")
    @Transactional
    public void transferBranch(@PathVariable UUID accountId, @Valid @RequestBody TransferBranchRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var branch = branches.findById(request.branchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch không tồn tại: " + request.branchId()));
        account.transferToBranch(branch);
        permissionCache.evict(accountId);
    }
}
```

- [ ] **Step 5: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=RbacAdminControllerIT`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/dto/CreateRoleRequest.java backend/src/main/java/com/eduerp/identity/dto/CreatePermissionGroupRequest.java backend/src/main/java/com/eduerp/identity/dto/AssignGroupRequest.java backend/src/main/java/com/eduerp/identity/dto/TransferBranchRequest.java backend/src/main/java/com/eduerp/identity/RbacAdminController.java backend/src/test/java/com/eduerp/identity/RbacAdminControllerIT.java
git commit -m "feat(identity): add RBAC admin endpoints (Role/PermissionGroup CRUD, group assignment, branch transfer) with cache eviction"
```

---

### Task 17: Dashboard stats đầy đủ (theo Role, theo chi nhánh, hoạt động đăng nhập gần đây)

**Files:**
- Modify: `backend/src/main/java/com/eduerp/identity/DashboardController.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/DashboardStatsResponse.java`
- Create: `backend/src/main/java/com/eduerp/identity/dto/RecentLoginResponse.java`
- Test: `backend/src/test/java/com/eduerp/identity/DashboardControllerIT.java`

**Interfaces:**
- Consumes: `AccountRepository` (`countByRole_Code` từ Task 7, `countByHomeBranch_Id` từ Task 7), `BranchRepository`, `AuditLogRepository` (Task 15, action `LOGIN`).
- Produces: `GET /api/dashboard/stats` → `DashboardStatsResponse(long totalAccounts, long totalBranches, Map<String, Long> accountsByRole, Map<String, Long> accountsByBranch, List<RecentLoginResponse> recentLogins)`; `RecentLoginResponse(UUID accountId, String email, Instant occurredAt)`. Đúng theo spec mục 6.6: "tổng số tài khoản (theo Role/chi nhánh), tổng số chi nhánh, hoạt động đăng nhập gần đây".

- [ ] **Step 1: Viết `DashboardStatsResponse.java` và `RecentLoginResponse.java`**

```java
package com.eduerp.identity.dto;

import java.util.List;
import java.util.Map;

public record DashboardStatsResponse(
        long totalAccounts,
        long totalBranches,
        Map<String, Long> accountsByRole,
        Map<String, Long> accountsByBranch,
        List<RecentLoginResponse> recentLogins) {
}
```

```java
package com.eduerp.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentLoginResponse(UUID accountId, String email, Instant occurredAt) {
}
```

- [ ] **Step 2: Viết test integration (fail trước — endpoint hiện tại trả `"{}"` tĩnh từ Task 11)**

```java
package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void returnsRealCounts() throws Exception {
        var adminRole = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("dashboard-admin@eduerp.local", passwordEncoder.encode("Password123!"), "Dashboard Admin", adminRole, null));
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.eduerp.identity.dto.LoginRequest("dashboard-admin@eduerp.local", "Password123!"))))
                .andReturn();
        Cookie access = loginResult.getResponse().getCookie("access_token");

        var result = mockMvc.perform(get("/api/dashboard/stats").cookie(access)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readValue(result.getResponse().getContentAsString(),
                com.eduerp.identity.dto.DashboardStatsResponse.class);
        assertThat(body.totalAccounts()).isGreaterThanOrEqualTo(1);
        assertThat(body.accountsByRole()).containsKey(IdentityConstants.RoleCodes.ADMIN);
        assertThat(body.recentLogins()).extracting("email").contains("dashboard-admin@eduerp.local");
    }
}
```

- [ ] **Step 3: Chạy test, xác nhận fail**

Run: `cd backend && mvn -q test -Dtest=DashboardControllerIT`
Expected: FAIL — endpoint hiện trả `"{}"`, không parse được thành `DashboardStatsResponse` với field đúng.

- [ ] **Step 4: Viết lại `DashboardController.java`**

```java
package com.eduerp.identity;

import com.eduerp.identity.dto.DashboardStatsResponse;
import com.eduerp.identity.dto.RecentLoginResponse;
import java.util.LinkedHashMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int RECENT_LOGINS_LIMIT = 10;

    private final AccountRepository accounts;
    private final BranchRepository branches;
    private final RoleRepository roles;
    private final AuditLogRepository auditLogs;

    DashboardController(AccountRepository accounts, BranchRepository branches, RoleRepository roles,
            AuditLogRepository auditLogs) {
        this.accounts = accounts;
        this.branches = branches;
        this.roles = roles;
        this.auditLogs = auditLogs;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'DASHBOARD', 'READ')")
    public DashboardStatsResponse stats() {
        var accountsByRole = new LinkedHashMap<String, Long>();
        roles.findAll().forEach(role -> accountsByRole.put(role.getCode(), accounts.countByRole_Code(role.getCode())));

        var accountsByBranch = new LinkedHashMap<String, Long>();
        branches.findAll().forEach(branch -> accountsByBranch.put(branch.getCode(), accounts.countByHomeBranch_Id(branch.getId())));

        var recentLogins = auditLogs
                .findByEntityType("ACCOUNT", PageRequest.of(0, RECENT_LOGINS_LIMIT, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .stream()
                .filter(log -> "LOGIN".equals(log.getAction()))
                .map(log -> new RecentLoginResponse(
                        log.getActorAccountId(),
                        accounts.findById(log.getActorAccountId()).map(Account::getEmail).orElse("(đã xoá)"),
                        log.getOccurredAt()))
                .toList();

        return new DashboardStatsResponse(accounts.count(), branches.count(), accountsByRole, accountsByBranch, recentLogins);
    }
}
```

- [ ] **Step 5: Chạy lại test, xác nhận pass**

Run: `cd backend && mvn -q test -Dtest=DashboardControllerIT`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/identity/DashboardController.java backend/src/main/java/com/eduerp/identity/dto/DashboardStatsResponse.java backend/src/main/java/com/eduerp/identity/dto/RecentLoginResponse.java backend/src/test/java/com/eduerp/identity/DashboardControllerIT.java
git commit -m "feat(identity): implement real dashboard stats (accounts by role/branch, recent logins)"
```

---

### Task 18: `ModularityTests` + kiểm tra cuối

**Files:**
- Create: `backend/src/test/java/com/eduerp/ModularityTests.java`

**Interfaces:**
- Không sản xuất API mới — đây là bước xác nhận toàn bộ module `identity` tuân thủ biên giới Spring Modulith trước khi coi Phân hệ 1 là hoàn thành.

- [ ] **Step 1: Viết `ModularityTests.java`**

```java
package com.eduerp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    ApplicationModules modules = ApplicationModules.of(EduErpApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }
}
```

- [ ] **Step 2: Chạy để xác nhận cấu trúc module hợp lệ**

Run: `cd backend && mvn -q test -Dtest=ModularityTests`
Expected: `Tests run: 1, Failures: 0`. Nếu fail, thông báo lỗi nêu chính xác class nào bị import sai từ ngoài module — sửa `dto/` (đã ở sub-package `com.eduerp.identity.dto`, cần đảm bảo `com.eduerp.identity.dto` không bị coi là "internal" khi các controller cùng module `identity` dùng nó — vì `dto` là sub-package của module `identity` (advanced module: base package `com.eduerp.identity` là API, `com.eduerp.identity.dto` là internal theo mặc định). Vì mọi consumer của `dto/*` đều nằm trong cùng module `identity`, không có module ngoài nào tham chiếu `dto/*` trực tiếp — không cần `@NamedInterface` ở đây, khác với ví dụ `identity.dto` gọi từ module khác trong `references/layer-examples.md` của skill.

- [ ] **Step 3: Chạy toàn bộ test suite + build cuối cùng**

Run: `cd backend && mvn -q verify`
Expected: `BUILD SUCCESS`, toàn bộ test (unit + Testcontainers integration) pass.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "test(identity): add ApplicationModules.verify() boundary test"
```

---

## Self-Review (đã thực hiện khi viết plan)

- **Spec coverage**: mục 6.1 (data model) → Task 3-8; mục 6.2 (tự phục vụ + seed) → Task 13-14; mục 6.3 (Auth JWT-cookie) → Task 10-12; mục 6.4 (2-layer enforcement) → Task 9, 11 (lớp coarse); *lớp fine (lọc theo scope tại tầng repository/use-case cho các entity của các phân hệ SAU) chưa có task riêng ở đây vì Phân hệ 1 chưa có entity nghiệp vụ nào thật sự cần lọc PERSONAL/BRANCH bên ngoài chính nó — cơ chế (`AccountPrincipal.homeBranchId()` đã có sẵn trong Authentication) sẽ được dùng khi Phân hệ 2-4 viết use-case của chính chúng*; mục 6.5 (Audit Log) → Task 15; mục 6.6 (Dashboard, đủ 3 phần: theo Role, theo chi nhánh, hoạt động đăng nhập gần đây) → Task 17; mục 7 (kiểm thử) → mọi Task đều có unit/integration test tương ứng, Task 18 chốt bằng `ModularityTests` + `mvn verify`.
  - **Gap tìm thấy khi tự review và đã sửa**: bản nháp đầu của Task 17 chỉ có `accountsByRole` + `totalBranches`, thiếu "theo chi nhánh" và "hoạt động đăng nhập gần đây" mà spec 6.6 yêu cầu — đã bổ sung `accountsByBranch` (dùng `AccountRepository.countByHomeBranch_Id` vốn định nghĩa từ Task 7 nhưng chưa nơi nào gọi tới) và `recentLogins` (đọc từ `AuditLogRepository`, action `LOGIN`). Vì `AuditLog`/`AuditLogRepository` chỉ tồn tại từ Task 15, phải thêm bước ghi audit đăng nhập trực tiếp trong `AuthController.login` (Task 15, Step 9) **thay vì** dùng `@Audited` — do `@Audited`/`AuditAspect` lấy `entityId` từ `toString()` của arg đầu tiên, mà arg đầu của `login()` là `LoginRequest` chứa mật khẩu plaintext, sẽ rò rỉ mật khẩu vào `AuditLog.entityId` nếu dùng chung cơ chế annotation.
- **Placeholder scan**: không còn `TBD`/"xử lý sau" nào trong code — mọi step có code thật, kể cả gửi mail (JavaMailSender thật, test dùng `@MockBean` để không gọi SMTP thật).
- **Type consistency**: `AccountPrincipal(UUID accountId, UUID homeBranchId)` dùng xuyên suốt Task 11-16; `EffectivePermission(resource, action, scope)` từ Task 6 dùng nguyên trong Task 9 (`PermissionCacheService`) và Task 11 (authorities string `"PERM:"+resource+":"+action+":"+scope`); `AccountRepository.countByRole_Code`/`countByHomeBranch_Id` (Task 7) cả hai đều được dùng thật ở Task 14/17 — không còn method khai báo nhưng không nơi nào gọi.

## Bước tiếp theo

Sau khi Phân hệ 1 Backend hoàn thành và `mvn verify` xanh, quay lại `writing-plans` để viết plan riêng cho **Phân hệ 1 Frontend** (React + Vite, module `auth`, `entities/account`, `entities/permission`, Liquid Glass theme) — plan đó phụ thuộc vào các endpoint đã có ở đây (`/api/auth/*`, `/api/account/*`, `/api/dashboard/stats`).
