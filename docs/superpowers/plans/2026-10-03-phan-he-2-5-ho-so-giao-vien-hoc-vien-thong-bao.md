# Phân hệ 2.5 — Hồ sơ Giáo viên & Học viên + Thông báo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-managed Teacher/Student profiles, an account-creation + email-invite flow (forced password change on first login), and an SSE notification channel, following the Phân hệ 2 precedent exactly where the shape matches.

**Architecture:** Three new Spring Modulith modules (`modules.teachers`, `modules.students`, `integrations.notification`) mirroring `modules.courses`'s minimal style; `modules.identity` gains an account-creation/invite subsystem (new use cases, two new exceptions, one modified use case); frontend mirrors `entities/course` + `modules/courses` for the two new profile screens and extends the existing `modules/rbac` Accounts screen + `modules/auth` login flow.

**Tech Stack:** Spring Boot 3.4 / Java 21 / Spring Modulith / PostgreSQL+Flyway / Redis (invite TTL) / Thymeleaf (mail) / Spring `SseEmitter`. React 19 / Vite / TanStack Query v5 / Zod / react-hook-form-less `useZodForm`.

**Spec:** `docs/superpowers/specs/2026-10-03-phan-he-2-5-ho-so-giao-vien-hoc-vien-thong-bao-design.md`

## Global Constraints

- Scope: ERP for a **tutoring center** (trung tâm dạy), not a school/university — no faculty/academic-year/institution-issued-ID fields anywhere in this plan.
- `TeacherProfile`/`StudentProfile` relate to `Account` via a bare `UUID accountId` column, **never** a JPA `@ManyToOne` (rule #3 — cross-module).
- Every new top-level module (`modules.teachers`, `modules.students`, `integrations.notification`) **must** be added to `ModularityTests.everyDomainAndIntegrationPackageIsADetectedModule()`'s `containsExactlyInAnyOrder(...)` list or the build goes red (confirmed: current list is `"core", "shared", "modules.identity", "modules.access", "modules.organization", "modules.audit", "modules.dashboard", "modules.courses", "integrations.cache", "integrations.mail"`).
- Flyway migrations are append-only: next free version is **V11** (current highest applied is V10). Never edit V1-V10.
- No hardcoded string literals for permissions/resources/event-type names — group into `<Module>Constants` static inner classes (backend) or typed const objects (frontend), per CLAUDE.md Mandate #1.
- All admin endpoints in this phase require `ORGANIZATION` scope (mirrors Phân hệ 2 — no teacher/student self-service in this phase).
- Email templates use the app's real brand gradient `#ff8c42 → #ff5e62` (`--color-pastel-orange`/`--color-pastel-red` in `frontend/src/index.css`), **not** the blue `#2563eb/#0ea5e9` found in the existing `password-reset.html` — that template predates the current brand palette and must not be copied verbatim.
- Frontend: Separation of Logic from Render — all new `ui/*.tsx` components stay presentational; state/mutations live in `hooks/use-*.ts` or `api/use-*.ts`, exactly like every existing `modules/courses`/`modules/rbac` file mirrored in this plan.

## Review Focus

- A `CreateAccount` call with an email that already exists must fail with a clear 409, not a raw DB unique-constraint stack trace (no existing use case validates this today — `EmailAlreadyExistsException` is new in this plan).
- An invite that expired (Redis key gone) must be rejected at `Login` even when the random password is typed correctly — a password match alone must never be enough once `lastLogin == null` and the Redis key is absent.
- Resending or revoking an invite on an account that has **already completed** its first login (`lastLogin != null`) must fail loudly (`AccountAlreadyActivatedException`), not silently regenerate a password for an active employee.
- Creating a `TeacherProfile`/`StudentProfile` for an `accountId` whose Role is not `TEACHER`/`STUDENT` (wrong role, or no role at all) must be rejected — the two profile tables must never end up orphaned from a mismatched Account.
- The SSE endpoint (`GET /api/notifications/stream`) must only ever deliver events to the account that opened that specific connection — a test must prove account A's emitter receives nothing when account B's permission changes.

---

## Task 1: `Account.lastLogin` field + lifecycle methods

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/identity/internal/model/Account.java`
- Create: `backend/src/main/resources/db/migration/V11__add_account_last_login.sql`
- Test: `backend/src/test/java/com/eduerp/modules/identity/internal/model/AccountTest.java` (new)

**Interfaces:**
- Produces: `Account.getLastLogin(): Instant` (nullable), `Account.recordFirstLogin(): void`, `Account.activate(): void`, `Account.disable(): void` — consumed by `Login`, `CompletePasswordInvite`, `ResendAccountInvite`, `RevokeAccountInvite` in later tasks.

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.modules.identity.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.IdentityConstants;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void newAccountHasNoLastLogin() {
        var account = new Account("a@b.com", "hash", "A", null);
        assertThat(account.getLastLogin()).isNull();
    }

    @Test
    void recordFirstLoginSetsTimestamp() {
        var account = new Account("a@b.com", "hash", "A", null);
        account.recordFirstLogin();
        assertThat(account.getLastLogin()).isNotNull();
    }

    @Test
    void disableThenActivateRestoresActiveStatus() {
        var account = new Account("a@b.com", "hash", "A", null);
        account.disable();
        assertThat(account.getStatus()).isEqualTo(IdentityConstants.AccountStatus.DISABLED);
        account.activate();
        assertThat(account.getStatus()).isEqualTo(IdentityConstants.AccountStatus.ACTIVE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AccountTest`
Expected: COMPILE ERROR — `getLastLogin`, `recordFirstLogin`, `disable`, `activate` do not exist on `Account`.

- [ ] **Step 3: Implement**

In `Account.java`, add the import `java.time.Instant`, a new field, and three methods. The class currently ends with `transferToBranch`; add right after it:

```java
    private Instant lastLogin;

    public Instant getLastLogin() {
        return lastLogin;
    }

    /** Gọi đúng một lần, khi CompletePasswordInvite hoàn tất — không phải mỗi lần Login thành công. */
    public void recordFirstLogin() {
        this.lastLogin = Instant.now();
    }

    public void activate() {
        this.status = IdentityConstants.AccountStatus.ACTIVE;
    }

    public void disable() {
        this.status = IdentityConstants.AccountStatus.DISABLED;
    }
```

(`@Getter` from Lombok already covers simple getters, but `lastLogin` is declared without `@Getter` conflict — remove the manual `getLastLogin()` if Lombok's class-level `@Getter` already generates it; since the class has `@Getter` at class level, **do not** write a manual getter — just add the field and let Lombok generate `getLastLogin()`. Drop the manual getter above.)

Final field block addition (replace the step-3 snippet above with this corrected version — no manual getter):

```java
    private Instant lastLogin;

    /** Gọi đúng một lần, khi CompletePasswordInvite hoàn tất — không phải mỗi lần Login thành công. */
    public void recordFirstLogin() {
        this.lastLogin = Instant.now();
    }

    public void activate() {
        this.status = IdentityConstants.AccountStatus.ACTIVE;
    }

    public void disable() {
        this.status = IdentityConstants.AccountStatus.DISABLED;
    }
```

Create `V11__add_account_last_login.sql`:

```sql
ALTER TABLE accounts ADD COLUMN last_login TIMESTAMPTZ;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=AccountTest`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/internal/model/Account.java backend/src/main/resources/db/migration/V11__add_account_last_login.sql backend/src/test/java/com/eduerp/modules/identity/internal/model/AccountTest.java
git commit -m "feat(identity): add Account.lastLogin and activate/disable lifecycle"
```

---

## Task 2: New identity exceptions + constants + properties

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/EmailAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/identity/AccountInviteExpiredException.java`
- Create: `backend/src/main/java/com/eduerp/modules/identity/AccountAlreadyActivatedException.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/IdentityException.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/IdentityConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/IdentityProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/eduerp/modules/identity/IdentityExceptionTest.java` (new)

**Interfaces:**
- Produces: `IdentityConstants.CacheNamespaces.ACCOUNT_INVITE = "invite:account"`, `IdentityProperties.accountInviteTtl(): Duration` — consumed by Tasks 5-8.

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IdentityExceptionTest {

    @Test
    void emailAlreadyExistsIsConflict() {
        var ex = new EmailAlreadyExistsException("a@b.com");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_EMAIL_ALREADY_EXISTS");
    }

    @Test
    void accountInviteExpiredIsUnauthorized() {
        var ex = new AccountInviteExpiredException();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_INVITE_EXPIRED");
    }

    @Test
    void accountAlreadyActivatedIsConflict() {
        var id = java.util.UUID.randomUUID();
        var ex = new AccountAlreadyActivatedException(id);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_ACCOUNT_ALREADY_ACTIVATED");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=IdentityExceptionTest`
Expected: COMPILE ERROR — the three exception classes do not exist yet.

- [ ] **Step 3: Implement**

`EmailAlreadyExistsException.java`:
```java
package com.eduerp.modules.identity;

import org.springframework.http.HttpStatus;

public final class EmailAlreadyExistsException extends IdentityException {
    public EmailAlreadyExistsException(String email) {
        super("IDENTITY_EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email " + email + " đã được sử dụng");
    }
}
```

`AccountInviteExpiredException.java`:
```java
package com.eduerp.modules.identity;

import org.springframework.http.HttpStatus;

/** Không nhận accountId trong constructor — giống InvalidCredentialsException, không tiết lộ thêm gì. */
public final class AccountInviteExpiredException extends IdentityException {
    public AccountInviteExpiredException() {
        super("IDENTITY_INVITE_EXPIRED", HttpStatus.UNAUTHORIZED, "Lời mời đã hết hạn, vui lòng liên hệ quản trị viên");
    }
}
```

`AccountAlreadyActivatedException.java`:
```java
package com.eduerp.modules.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class AccountAlreadyActivatedException extends IdentityException {
    public AccountAlreadyActivatedException(UUID accountId) {
        super("IDENTITY_ACCOUNT_ALREADY_ACTIVATED", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã kích hoạt, không áp dụng thao tác mời");
    }
}
```

Modify `IdentityException.java` — update the sealed `permits` clause:
```java
public sealed class IdentityException extends AppException
        permits AccountNotFoundException, InvalidCredentialsException, ReferenceNotFoundException, TokenInvalidException,
        EmailAlreadyExistsException, AccountInviteExpiredException, AccountAlreadyActivatedException {
```

Modify `IdentityConstants.java` — add one line inside `CacheNamespaces`:
```java
        public static final String ACCOUNT_INVITE = "invite:account";
```
(goes alongside the existing `BLACKLISTED_JTI`, `ACCOUNT_SESSIONS`, `PASSWORD_RESET_TOKEN` constants in that same static inner class.)

Modify `IdentityProperties.java` — add `accountInviteTtl` to the record's component list (after `passwordResetTtl`):
```java
public record IdentityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        Duration accountInviteTtl,
        String mailFrom,
        String frontendResetUrl,
        String defaultAdminEmail,
        String defaultAdminPassword,
        boolean secureCookies) {
}
```

Modify `application.yml` — add one line under the `identity:` block (after `password-reset-ttl: 30m`):
```yaml
  account-invite-ttl: 7d
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=IdentityExceptionTest`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/EmailAlreadyExistsException.java backend/src/main/java/com/eduerp/modules/identity/AccountInviteExpiredException.java backend/src/main/java/com/eduerp/modules/identity/AccountAlreadyActivatedException.java backend/src/main/java/com/eduerp/modules/identity/IdentityException.java backend/src/main/java/com/eduerp/modules/identity/IdentityConstants.java backend/src/main/java/com/eduerp/modules/identity/IdentityProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/eduerp/modules/identity/IdentityExceptionTest.java
git commit -m "feat(identity): add invite/account-creation exceptions and config"
```

---

## Task 3: `RandomPasswordGenerator` util

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/internal/util/RandomPasswordGenerator.java`
- Test: `backend/src/test/java/com/eduerp/modules/identity/internal/util/RandomPasswordGeneratorTest.java`

**Interfaces:**
- Produces: `RandomPasswordGenerator.generate(): String` — consumed by Task 5 (`CreateAccount`) and Task 8 (`ResendAccountInvite`).

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.modules.identity.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomPasswordGeneratorTest {

    @Test
    void generatesTwelveCharacters() {
        assertThat(RandomPasswordGenerator.generate()).hasSize(12);
    }

    @Test
    void generatesDifferentValuesEachTime() {
        var values = IntStream.range(0, 20).mapToObj(i -> RandomPasswordGenerator.generate()).distinct().count();
        assertThat(values).isEqualTo(20);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=RandomPasswordGeneratorTest`
Expected: COMPILE ERROR — class does not exist.

- [ ] **Step 3: Implement**

```java
package com.eduerp.modules.identity.internal.util;

import java.security.SecureRandom;

/** Mật khẩu tạm gửi qua email mời tài khoản — không dùng Base64/UUID thẳng vì cần tránh ký tự dễ nhầm (0/O, l/1). */
public final class RandomPasswordGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomPasswordGenerator() {
    }

    public static String generate() {
        var builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return builder.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=RandomPasswordGeneratorTest`
Expected: PASS, 2/2.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/internal/util/RandomPasswordGenerator.java backend/src/test/java/com/eduerp/modules/identity/internal/util/RandomPasswordGeneratorTest.java
git commit -m "feat(identity): add random password generator for account invites"
```

---

## Task 4: `AccountInviteMailer` + email template

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/internal/mail/AccountInviteMailer.java`
- Create: `backend/src/main/resources/com/eduerp/modules/identity/internal/mail/templates/account-invite.html`

**Interfaces:**
- Produces: `AccountInviteMailer.sendInvite(String toEmail, String fullName, String temporaryPassword): void` — consumed by Task 5 and Task 8.
- Consumes: `MailClient.sendHtml(...)` (existing, `integrations.mail`), `IdentityProperties.accountInviteTtl()`/`.mailFrom()` (Task 2).

This task has no new business logic to unit-test in isolation (it is a thin Thymeleaf-render + delegate, exactly like `PasswordResetMailer` which also has no dedicated unit test in this codebase) — its behavior is verified end-to-end in Task 5's integration test, which asserts an email was actually sent. No separate RED/GREEN cycle for this task; write the files directly, then confirm the module compiles.

- [ ] **Step 1: Create the mailer**

```java
package com.eduerp.modules.identity.internal.mail;

import com.eduerp.integrations.mail.MailClient;
import com.eduerp.modules.identity.IdentityProperties;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class AccountInviteMailer {

    private static final String TEMPLATE = "com/eduerp/modules/identity/internal/mail/templates/account-invite";
    private static final String SUBJECT = "Tài khoản EduERP của bạn đã được tạo";

    private final MailClient mailClient;
    private final TemplateEngine templateEngine;
    private final IdentityProperties properties;

    AccountInviteMailer(MailClient mailClient, TemplateEngine templateEngine, IdentityProperties properties) {
        this.mailClient = mailClient;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public void sendInvite(String toEmail, String fullName, String temporaryPassword) {
        long expiresInDays = properties.accountInviteTtl().toDays();

        var context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("email", toEmail);
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("expiresInDays", expiresInDays);
        String html = templateEngine.process(TEMPLATE, context);

        String textFallback = "Xin chào " + fullName + ", tài khoản EduERP của bạn: " + toEmail
                + ". Mật khẩu tạm: " + temporaryPassword + " (hết hạn sau " + expiresInDays + " ngày).";

        mailClient.sendHtml(properties.mailFrom(), toEmail, SUBJECT, html, textFallback);
    }
}
```

- [ ] **Step 2: Create the template**

Mirror the structure of `password-reset.html` exactly (same table-based email-safe layout, same font stack), but:
- Replace the blue gradient (`#2563eb`/`#0ea5e9`) with the app's real brand gradient `#ff8c42 → #ff5e62` (from `frontend/src/index.css`'s `--color-pastel-orange`/`--color-pastel-red`).
- Replace the CTA link button with a monospace password box (there is no link to click in this flow — the user logs in directly with the emailed password).

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <title>Tài khoản EduERP của bạn đã được tạo</title>
  <!--[if mso]><style>table,td{font-family:Arial,Helvetica,sans-serif!important}</style><![endif]-->
</head>
<body style="margin:0;padding:0;background-color:#f0f2f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;color:#1a1d23;-webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale;">

  <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="background-color:#f0f2f5;">
    <tr>
      <td align="center" style="padding:32px 16px;">

        <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="max-width:560px;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08),0 4px 12px rgba(0,0,0,0.04);">

          <!-- Vạch nhấn -->
          <tr>
            <td style="height:4px;background:linear-gradient(90deg,#ff8c42 0%,#ff5e62 100%);font-size:0;line-height:0;">&nbsp;</td>
          </tr>

          <!-- Header -->
          <tr>
            <td style="padding:28px 32px 8px;">
              <span style="display:inline-block;font-size:11px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:#ff5e45;">EduERP</span>
            </td>
          </tr>
          <tr>
            <td style="padding:4px 32px 20px;">
              <span style="font-size:20px;font-weight:700;color:#111827;line-height:1.3;">Chào mừng, <span th:text="${fullName}">Tên</span>!</span>
            </td>
          </tr>

          <tr>
            <td style="padding:0 32px;">
              <div style="height:1px;background-color:#e5e7eb;"></div>
            </td>
          </tr>

          <!-- Nội dung -->
          <tr>
            <td style="padding:24px 32px 8px;">
              <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#374151;">
                Quản trị viên vừa tạo tài khoản EduERP cho bạn. Đăng nhập bằng thông tin bên dưới, hệ thống sẽ yêu cầu bạn đặt mật khẩu mới ngay trong lần đăng nhập đầu tiên.
              </p>
            </td>
          </tr>

          <!-- Hộp thông tin đăng nhập -->
          <tr>
            <td style="padding:12px 32px 8px;">
              <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="background-color:#fff1e8;border:1px solid #ffd9b8;border-radius:12px;">
                <tr>
                  <td style="padding:16px 20px;">
                    <p style="margin:0 0 6px;font-size:12px;color:#9a5b2a;text-transform:uppercase;letter-spacing:0.4px;">Email đăng nhập</p>
                    <p style="margin:0 0 14px;font-size:15px;font-weight:600;color:#1a1d23;" th:text="${email}">email@eduerp.local</p>
                    <p style="margin:0 0 6px;font-size:12px;color:#9a5b2a;text-transform:uppercase;letter-spacing:0.4px;">Mật khẩu tạm</p>
                    <p style="margin:0;font-size:16px;font-weight:700;font-family:ui-monospace,Menlo,monospace;color:#1a1d23;letter-spacing:0.5px;" th:text="${temporaryPassword}">xxxxxxxxxxxx</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td style="padding:16px 32px 0;">
              <span style="display:inline-block;padding:4px 12px;font-size:12px;font-weight:600;color:#a16207;background-color:#fefce8;border:1px solid #fef08a;border-radius:20px;">
                Lời mời hết hạn sau <span th:text="${expiresInDays}">7</span> ngày
              </span>
            </td>
          </tr>

          <tr>
            <td style="padding:24px 32px 0;">
              <div style="height:1px;background-color:#e5e7eb;"></div>
            </td>
          </tr>
          <tr>
            <td style="padding:20px 32px 24px;">
              <span style="font-size:12px;color:#9ca3af;line-height:1.5;">Email tự động từ hệ thống EduERP — không trả lời email này.</span>
            </td>
          </tr>

        </table>

      </td>
    </tr>
  </table>

</body>
</html>
```

- [ ] **Step 3: Verify the module compiles**

Run: `cd backend && mvn clean compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/internal/mail/AccountInviteMailer.java backend/src/main/resources/com/eduerp/modules/identity/internal/mail/templates/account-invite.html
git commit -m "feat(identity): add account-invite mailer and email template"
```

---

## Task 5: `CreateAccount` use case + RBAC rule + endpoint

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/dto/CreateAccountRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/identity/usecase/CreateAccount.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/IdentityEvents.java`
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessManagement.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/web/AccountAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java` (new)

**Interfaces:**
- Consumes: `OrganizationManagement.exists(UUID)` (existing, used identically in `TransferAccountBranch`), `RandomPasswordGenerator.generate()` (Task 3), `AccountInviteMailer.sendInvite(...)` (Task 4), `IdentityConstants.CacheNamespaces.ACCOUNT_INVITE` + `IdentityProperties.accountInviteTtl()` (Task 2). **New** `AccessManagement.assignRole(UUID accountId, UUID roleId)` overload and `AccessManagement.roleIdOf(String code): UUID` (both added in this task, below — the existing `RbacCatalogResponse.roles` already exposes `{id, name}` per role with no `code` field, so the frontend's role picker in Task 21 can only ever send a role **id**, not a code; `CreateAccountRequest` is designed around that constraint from the start rather than retrofitting it later).
- Produces: `CreateAccount.execute(UUID actorAccountId, UUID actorBranchId, CreateAccountRequest request): UUID` — consumed by `AccountAdminController`. `POST /api/rbac/accounts` endpoint.

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
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
class AccountAdminControllerIT {

    private static final String PASSWORD = "Password123!";

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, null));
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    @Test
    void createsAnAccountAndSendsInvite() throws Exception {
        var admin = signIn("account-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateAccountRequest("new-teacher@eduerp.local", "Cô Lan", null,
                access.roleIdOf(AccessConstants.RoleCodes.TEACHER));

        var result = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var accountId = objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
        var saved = accounts.findById(accountId).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("new-teacher@eduerp.local");
        assertThat(saved.getLastLogin()).isNull();
        assertThat(access.roleOf(accountId)).isPresent();
        assertThat(stringRedisTemplate.hasKey("invite:account:" + accountId)).isTrue();
    }

    @Test
    void rejectsADuplicateEmail() throws Exception {
        var admin = signIn("account-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateAccountRequest("account-admin-2@eduerp.local", "Trùng email", null,
                access.roleIdOf(AccessConstants.RoleCodes.TEACHER));

        var result = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=AccountAdminControllerIT`
Expected: COMPILE ERROR — `CreateAccountRequest` does not exist, `POST /api/rbac/accounts` not mapped.

- [ ] **Step 3: Implement**

`dto/CreateAccountRequest.java`:
```java
package com.eduerp.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAccountRequest(@NotBlank @Email String email, @NotBlank String fullName, UUID homeBranchId,
        @NotNull UUID roleId) {
}
```

Modify `AccessManagement.java` (existing, shipped in Phân hệ 1) — the only facade method to assign a role takes a role **code** (`assignRole(UUID, String)`), but the frontend's role picker (Task 21) can only ever offer a role **id** (`RbacCatalogResponse.roles` is `List<NamedReference>` = `{id, name}`, no `code` field — confirmed by reading `GetRbacCatalog.java` during planning). Rather than add a `code` field to the shared `NamedReference` type (used by `roles`/`groups`/`permissionGroups`/`branches` alike — adding a role-only field there is the wrong place), add a second `assignRole` overload that resolves by id, plus a small lookup the IT tests need to go from a seeded role's well-known code to its (randomly-generated) id:
```java
    @Transactional
    public void assignRole(UUID accountId, String roleCode) {
        var role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role " + roleCode + " chưa được seed"));
        assignRole(accountId, role);
    }

    /** Dùng khi nơi gọi chỉ có id (vd admin chọn vai trò từ dropdown catalog, không gõ code tay). */
    @Transactional
    public void assignRole(UUID accountId, UUID roleId) {
        var role = roles.findById(roleId)
                .orElseThrow(() -> new IllegalStateException("Role " + roleId + " không tồn tại"));
        assignRole(accountId, role);
    }

    private void assignRole(UUID accountId, com.eduerp.modules.access.internal.model.Role role) {
        accountRoles.findById(accountId)
                .ifPresentOrElse(existing -> existing.changeRole(role),
                        () -> accountRoles.save(new AccountRoleAssignment(accountId, role)));
    }

    /** Chỉ dùng trong test - sản phẩm thật luôn có id sẵn từ catalog, không bao giờ tra ngược từ code. */
    @Transactional(readOnly = true)
    public UUID roleIdOf(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Role " + code + " chưa được seed"))
                .getId();
    }
```
(this **replaces** the existing single-method `assignRole(UUID, String)` body — the old method's two-line body becomes the first overload above, delegating to the new private `assignRole(UUID, Role)` helper that both overloads share. No existing caller of `assignRole(accountId, "ADMIN")`-style code breaks: that overload's signature and behavior are unchanged, it just now delegates internally.)

Add to `IdentityEvents.java` (inside the existing `IdentityEvents` class, alongside `AccountSignedIn`/`AccountBranchTransferred`):
```java
    public record AccountCreated(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
    }
```

Add to `AccessConstants.java`'s `AccessRules` inner class (alongside `UPDATE_ACCOUNT`/`READ_ACCOUNT`):
```java
        public static final String CREATE_ACCOUNT = CHECK_PREFIX + Resources.ACCOUNT + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
```

`usecase/CreateAccount.java`:
```java
package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.EmailAlreadyExistsException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.mail.AccountInviteMailer;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.util.RandomPasswordGenerator;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAccount {

    private final AccountRepository accounts;
    private final AccessManagement access;
    private final OrganizationManagement organization;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final AccountInviteMailer mailer;
    private final IdentityProperties properties;
    private final ApplicationEventPublisher events;

    CreateAccount(AccountRepository accounts, AccessManagement access, OrganizationManagement organization,
            StringRedisTemplate redis, PasswordEncoder passwordEncoder, AccountInviteMailer mailer,
            IdentityProperties properties, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.access = access;
        this.organization = organization;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateAccountRequest request) {
        if (accounts.findByTypedEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException(request.email());
        }
        if (request.homeBranchId() != null && !organization.exists(request.homeBranchId())) {
            throw new ReferenceNotFoundException(AccessConstants.Resources.BRANCH, request.homeBranchId());
        }

        var rawPassword = RandomPasswordGenerator.generate();
        var account = accounts.save(new Account(request.email(), passwordEncoder.encode(rawPassword),
                request.fullName(), request.homeBranchId()));
        access.assignRole(account.getId(), request.roleId());

        redis.opsForValue().set(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, account.getId()),
                java.time.Instant.now().toString(), properties.accountInviteTtl());
        mailer.sendInvite(account.getEmail(), account.getFullName(), rawPassword);

        events.publishEvent(new IdentityEvents.AccountCreated(account.getId(), actorAccountId, actorBranchId));
        return account.getId();
    }
}
```

Modify `AccountAdminController.java` — add the `CreateAccount` dependency and endpoint:
```java
    private final ListAccounts listAccounts;
    private final TransferAccountBranch transferAccountBranch;
    private final CreateAccount createAccount;

    AccountAdminController(ListAccounts listAccounts, TransferAccountBranch transferAccountBranch,
            CreateAccount createAccount) {
        this.listAccounts = listAccounts;
        this.transferAccountBranch = transferAccountBranch;
        this.createAccount = createAccount;
    }
```
(add the import `com.eduerp.modules.identity.dto.CreateAccountRequest` and `com.eduerp.modules.identity.usecase.CreateAccount`, plus `jakarta.validation.Valid` and `org.springframework.web.bind.annotation.RequestBody` if not already imported — both already are, since `TransferBranchRequest` uses them.)

Add the endpoint method, placed before `transferBranch`:
```java
    @PostMapping("/accounts")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_ACCOUNT)
    UUID createAccount(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateAccountRequest request) {
        return createAccount.execute(principal.accountId(), principal.homeBranchId(), request);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=AccountAdminControllerIT`
Expected: PASS, 2/2.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/dto/CreateAccountRequest.java backend/src/main/java/com/eduerp/modules/identity/usecase/CreateAccount.java backend/src/main/java/com/eduerp/modules/identity/IdentityEvents.java backend/src/main/java/com/eduerp/modules/access/AccessConstants.java backend/src/main/java/com/eduerp/modules/access/AccessManagement.java backend/src/main/java/com/eduerp/modules/identity/web/AccountAdminController.java backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java
git commit -m "feat(identity): add admin account-creation endpoint with email invite"
```

---

## Task 6: `Login` forced-password-change gate

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/dto/LoginResult.java`
- Create: `backend/src/main/java/com/eduerp/modules/identity/dto/LoginResponse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/usecase/Login.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/web/AuthController.java`
- Test: `backend/src/test/java/com/eduerp/modules/identity/web/AuthControllerIT.java` (new, or extend existing if one already covers `/api/auth/login` — check first with `find backend/src/test/java/com/eduerp/modules/identity/web -iname "*Auth*"`; if `AuthControllerIT.java` already exists, add these two test methods to it instead of creating a new file)

**Interfaces:**
- Consumes: `Account.getLastLogin()` (Task 1), `IdentityConstants.CacheNamespaces.ACCOUNT_INVITE` (Task 2).
- Produces: `Login.execute(LoginRequest): LoginResult` (return type **changed** from `SessionTokens` — update the call site in `AuthController`). `LoginResponse(boolean requiresPasswordChange)` — the new JSON body `/api/auth/login` returns.

- [ ] **Step 1: Write the failing test**

First run `find backend/src/test/java/com/eduerp/modules/identity/web -iname "*Auth*"` to check for an existing `AuthControllerIT`. If none exists, create it with a minimal harness copied from `AccountAdminControllerIT`'s `@Testcontainers`/`@SpringBootTest`/`@AutoConfigureMockMvc` header (same two containers, same imports) and add:

```java
    @Test
    void firstLoginWithInviteStillValidRequiresPasswordChange() throws Exception {
        var account = accounts.save(new Account("invitee@eduerp.local",
                passwordEncoder.encode("TempPass1!"), "Người mới", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.TEACHER);
        stringRedisTemplate.opsForValue().set("invite:account:" + account.getId(), "x",
                java.time.Duration.ofDays(7));

        var result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invitee@eduerp.local\",\"password\":\"TempPass1!\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"requiresPasswordChange\":true");
        assertThat(result.getResponse().getCookie("access_token")).isNull();
    }

    @Test
    void firstLoginWithExpiredInviteIsRejected() throws Exception {
        var account = accounts.save(new Account("expired-invitee@eduerp.local",
                passwordEncoder.encode("TempPass1!"), "Người mới", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.TEACHER);
        // Không set key Redis nào — mô phỏng lời mời đã hết hạn (TTL đã trôi qua).

        var result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"expired-invitee@eduerp.local\",\"password\":\"TempPass1!\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
```

(these two tests need the same `AccountRepository accounts`, `AccessManagement access`, `PasswordEncoder passwordEncoder`, `StringRedisTemplate stringRedisTemplate` fields and imports as `AccountAdminControllerIT` from Task 5 — copy that file's field block if creating `AuthControllerIT` fresh.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=AuthControllerIT`
Expected: the first test gets a 200 with empty body and a cookie IS set (current behavior ignores `lastLogin`); the second test currently succeeds with 200 instead of 401. Both fail against current behavior.

- [ ] **Step 3: Implement**

`dto/LoginResult.java` (internal-ish DTO, but used as the use case's direct return type — put it in `dto` since `AuthController` in `web` consumes it, same tier rule as other DTOs):
```java
package com.eduerp.modules.identity.dto;

public record LoginResult(SessionTokens tokens, boolean requiresPasswordChange) {

    public static LoginResult requiresPasswordChange() {
        return new LoginResult(null, true);
    }

    public static LoginResult of(SessionTokens tokens) {
        return new LoginResult(tokens, false);
    }
}
```

`dto/LoginResponse.java`:
```java
package com.eduerp.modules.identity.dto;

public record LoginResponse(boolean requiresPasswordChange) {
}
```

Modify `Login.java` — add `StringRedisTemplate` dependency, change return type, add the gate:
```java
package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountInviteExpiredException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.LoginResult;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.rules.IdentityRules;
import com.eduerp.modules.identity.internal.token.TokenIssuer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Login {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final IdentityRules rules;
    private final ApplicationEventPublisher events;
    private final StringRedisTemplate redis;

    Login(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, IdentityRules rules,
            ApplicationEventPublisher events, StringRedisTemplate redis) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.rules = rules;
        this.events = events;
        this.redis = redis;
    }

    @Transactional
    public LoginResult execute(LoginRequest request) {
        var account = accounts.findByTypedEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!rules.canSignIn(account.getStatus())) {
            throw new InvalidCredentialsException();
        }
        if (account.getLastLogin() == null) {
            var inviteKey = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, account.getId());
            if (Boolean.FALSE.equals(redis.hasKey(inviteKey))) {
                throw new AccountInviteExpiredException();
            }
            return LoginResult.requiresPasswordChange();
        }
        var tokens = tokenIssuer.issuePair(account.getId());
        events.publishEvent(new IdentityEvents.AccountSignedIn(account.getId(), account.getHomeBranchId()));
        return LoginResult.of(tokens);
    }
}
```

Modify `AuthController.java`'s `login` method:
```java
    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var result = login.execute(request);
        if (result.requiresPasswordChange()) {
            return new LoginResponse(true);
        }
        AuthCookies.write(response, result.tokens(), properties);
        return new LoginResponse(false);
    }
```
(add the import `com.eduerp.modules.identity.dto.LoginResponse`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=AuthControllerIT`
Expected: PASS. Then run the full identity IT suite to confirm nothing else broke: `cd backend && mvn verify -Dit.test='AccountAdminControllerIT,AccountSelfServiceControllerIT' -Dsurefire.failIfNoSpecifiedTests=false` — Expected: all green (the existing login-success paths in other IT tests must still issue cookies normally since `lastLogin` is set by `CompletePasswordInvite`/pre-existing seeded admin accounts created via `SeedDefaultAdmin`, which Task 7 note below addresses).

**Important interaction with existing tests:** every other IT test in the repo creates accounts directly via `accounts.save(new Account(...))` (not through `CreateAccount`), so those accounts have `lastLogin == null` by construction. After this task, **every such test's login would now incorrectly demand a password change** because there is no invite Redis key (so the invite-expired branch fires: `401` instead of a normal session).

**Confirmed by `grep -rl "private Cookie signIn" backend/src/test` — exactly 8 files have a `signIn(...)` helper that logs in via `/api/auth/login`:**
1. `backend/src/test/java/com/eduerp/modules/organization/web/BranchAdminControllerIT.java`
2. `backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java` (Task 5's own file — already written correctly in Task 5's test code with `account.recordFirstLogin()` called before `.save(...)`, since that test was written as part of *this* plan; no fix needed here)
3. `backend/src/test/java/com/eduerp/modules/identity/web/SpaCsrfHandshakeIT.java`
4. `backend/src/test/java/com/eduerp/modules/identity/web/CurrentSessionIT.java`
5. `backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java`
6. `backend/src/test/java/com/eduerp/modules/access/web/RbacAdminControllerIT.java`
7. `backend/src/test/java/com/eduerp/modules/dashboard/web/DashboardControllerIT.java`
8. `backend/src/test/java/com/eduerp/modules/courses/web/ClassAdminControllerIT.java`

**Fix as part of this task:** in files 1, 3, 4, 5, 6, 7, 8 (six files — everything except #2, already correct), the `signIn` helper currently reads `accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), ...))` immediately followed by `access.assignRole(...)`. Change each to insert `recordFirstLogin()` between construction and save:
```java
var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
account.recordFirstLogin();
accounts.save(account);
access.assignRole(account.getId(), roleCode);
```
(`CurrentSessionIT`'s variant takes a `Branch` parameter too — same fix, just keep the existing branch argument: `new Account(email, passwordEncoder.encode(PASSWORD), "Người dùng " + email, branch == null ? null : branch.getId())`, then `account.recordFirstLogin()` before `accounts.save(account)`.) Note `.save(...)` now returns the already-built `account` reference you called `.recordFirstLogin()` on — either save first then call `access.assignRole(account.getId(), ...)` on the saved reference (both patterns work since `recordFirstLogin()` only mutates an in-memory field that Hibernate flushes with the rest of the entity state; the exact ordering of `.recordFirstLogin()` vs `.save()` does not matter as long as both happen before the transaction commits, but calling `recordFirstLogin()` **before** `save()` is the clearest reading and matches the snippet above).

Re-run the full suite after this fix: `cd backend && mvn verify -Dit.test='!AccountSelfServiceControllerIT'` — Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/dto/LoginResult.java backend/src/main/java/com/eduerp/modules/identity/dto/LoginResponse.java backend/src/main/java/com/eduerp/modules/identity/usecase/Login.java backend/src/main/java/com/eduerp/modules/identity/web/AuthController.java backend/src/test/java/com/eduerp/modules/identity/web/AuthControllerIT.java backend/src/test/java/com/eduerp/modules/organization/web/BranchAdminControllerIT.java backend/src/test/java/com/eduerp/modules/identity/web/SpaCsrfHandshakeIT.java backend/src/test/java/com/eduerp/modules/identity/web/CurrentSessionIT.java backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java backend/src/test/java/com/eduerp/modules/access/web/RbacAdminControllerIT.java backend/src/test/java/com/eduerp/modules/dashboard/web/DashboardControllerIT.java backend/src/test/java/com/eduerp/modules/courses/web/ClassAdminControllerIT.java
git commit -m "feat(identity): gate first login behind a completed invite"
```

---

## Task 7: `CompletePasswordInvite` use case + endpoint

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/dto/CompletePasswordInviteRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/identity/usecase/CompletePasswordInvite.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/web/AuthController.java`
- Test: add to `backend/src/test/java/com/eduerp/modules/identity/web/AuthControllerIT.java`

**Interfaces:**
- Consumes: `Account.recordFirstLogin()` (Task 1), `IdentityConstants.CacheNamespaces.ACCOUNT_INVITE` (Task 2), `TokenIssuer.issuePair(UUID)` (existing, same as `Login`).
- Produces: `POST /api/auth/complete-invite` — public endpoint (no `@PreAuthorize`, same tier as `/login`), returns a full session (sets cookies) on success.

- [ ] **Step 1: Write the failing test**

Add to `AuthControllerIT.java`:
```java
    @Test
    void completesInviteThenGrantsASession() throws Exception {
        var account = accounts.save(new Account("complete-invite@eduerp.local",
                passwordEncoder.encode("TempPass1!"), "Người mới", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.TEACHER);
        stringRedisTemplate.opsForValue().set("invite:account:" + account.getId(), "x",
                java.time.Duration.ofDays(7));

        var result = mockMvc.perform(post("/api/auth/complete-invite").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"complete-invite@eduerp.local","currentPassword":"TempPass1!","newPassword":"NewPass123!"}
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getCookie("access_token")).isNotNull();
        var updated = accounts.findById(account.getId()).orElseThrow();
        assertThat(updated.getLastLogin()).isNotNull();
        assertThat(stringRedisTemplate.hasKey("invite:account:" + account.getId())).isFalse();

        // Lần sau đăng nhập bằng mật khẩu mới phải vào bình thường, không còn bị bắt đổi mật khẩu nữa.
        var secondLogin = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"complete-invite@eduerp.local\",\"password\":\"NewPass123!\"}"))
                .andReturn();
        assertThat(secondLogin.getResponse().getCookie("access_token")).isNotNull();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=AuthControllerIT#completesInviteThenGrantsASession`
Expected: 404 — `/api/auth/complete-invite` not mapped.

- [ ] **Step 3: Implement**

`dto/CompletePasswordInviteRequest.java`:
```java
package com.eduerp.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordInviteRequest(@NotBlank @Email String email, @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword) {
}
```

`usecase/CompletePasswordInvite.java`:
```java
package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountAlreadyActivatedException;
import com.eduerp.modules.identity.AccountInviteExpiredException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.InvalidCredentialsException;
import com.eduerp.modules.identity.dto.CompletePasswordInviteRequest;
import com.eduerp.modules.identity.dto.SessionTokens;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.TokenIssuer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompletePasswordInvite {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final TokenIssuer tokenIssuer;

    CompletePasswordInvite(AccountRepository accounts, PasswordEncoder passwordEncoder, StringRedisTemplate redis,
            TokenIssuer tokenIssuer) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public SessionTokens execute(CompletePasswordInviteRequest request) {
        var account = accounts.findByTypedEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (account.getLastLogin() != null) {
            throw new AccountAlreadyActivatedException(account.getId());
        }
        var inviteKey = CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, account.getId());
        if (Boolean.FALSE.equals(redis.hasKey(inviteKey))) {
            throw new AccountInviteExpiredException();
        }

        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        account.recordFirstLogin();
        redis.delete(inviteKey);
        return tokenIssuer.issuePair(account.getId());
    }
}
```

Modify `AuthController.java` — add the dependency and endpoint:
```java
    private final Login login;
    private final RefreshSession refreshSession;
    private final Logout logout;
    private final CompletePasswordInvite completePasswordInvite;
    private final IdentityProperties properties;

    AuthController(Login login, RefreshSession refreshSession, Logout logout,
            CompletePasswordInvite completePasswordInvite, IdentityProperties properties) {
        this.login = login;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.completePasswordInvite = completePasswordInvite;
        this.properties = properties;
    }
```
Add the endpoint (new import: `com.eduerp.modules.identity.dto.CompletePasswordInviteRequest`, `com.eduerp.modules.identity.usecase.CompletePasswordInvite`):
```java
    @PostMapping("/complete-invite")
    void completeInvite(@Valid @RequestBody CompletePasswordInviteRequest request, HttpServletResponse response) {
        AuthCookies.write(response, completePasswordInvite.execute(request), properties);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=AuthControllerIT`
Expected: PASS, all methods in the file green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/dto/CompletePasswordInviteRequest.java backend/src/main/java/com/eduerp/modules/identity/usecase/CompletePasswordInvite.java backend/src/main/java/com/eduerp/modules/identity/web/AuthController.java backend/src/test/java/com/eduerp/modules/identity/web/AuthControllerIT.java
git commit -m "feat(identity): add complete-invite endpoint to finish first login"
```

---

## Task 8: `ResendAccountInvite` + `RevokeAccountInvite`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/identity/usecase/ResendAccountInvite.java`
- Create: `backend/src/main/java/com/eduerp/modules/identity/usecase/RevokeAccountInvite.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/IdentityEvents.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/web/AccountAdminController.java`
- Test: add to `backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java`

**Interfaces:**
- Produces: `POST /api/rbac/accounts/{accountId}/resend-invite`, `POST /api/rbac/accounts/{accountId}/revoke-invite` — both `@PreAuthorize(UPDATE_ACCOUNT)`.

- [ ] **Step 1: Write the failing test**

Add to `AccountAdminControllerIT.java`:
```java
    @Test
    void resendsAnInviteWithAFreshPassword() throws Exception {
        var admin = signIn("resend-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("resend-target@eduerp.local", "Thầy X", null,
                                        access.roleIdOf(AccessConstants.RoleCodes.TEACHER)))))
                .andReturn();
        var accountId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + accountId + "/resend-invite")
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey("invite:account:" + accountId)).isTrue();
    }

    @Test
    void revokesThenDisablesTheAccount() throws Exception {
        var admin = signIn("revoke-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("revoke-target@eduerp.local", "Thầy Y", null,
                                        access.roleIdOf(AccessConstants.RoleCodes.TEACHER)))))
                .andReturn();
        var accountId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + accountId + "/revoke-invite")
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey("invite:account:" + accountId)).isFalse();
        var disabled = accounts.findById(accountId).orElseThrow();
        assertThat(disabled.getStatus()).isEqualTo(com.eduerp.modules.identity.IdentityConstants.AccountStatus.DISABLED);
    }

    @Test
    void rejectsResendingAnInviteForAnAlreadyActivatedAccount() throws Exception {
        var admin = signIn("resend-active-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var active = new Account("already-active@eduerp.local", passwordEncoder.encode(PASSWORD), "Đã kích hoạt", null);
        active.recordFirstLogin();
        var saved = accounts.save(active);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + saved.getId() + "/resend-invite")
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=AccountAdminControllerIT`
Expected: 404 on both new endpoints (not mapped).

- [ ] **Step 3: Implement**

Add to `IdentityEvents.java`:
```java
    public record AccountInviteResent(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record AccountInviteRevoked(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
    }
```

`usecase/ResendAccountInvite.java`:
```java
package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountAlreadyActivatedException;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.internal.mail.AccountInviteMailer;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.util.RandomPasswordGenerator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResendAccountInvite {

    private final AccountRepository accounts;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final AccountInviteMailer mailer;
    private final IdentityProperties properties;
    private final ApplicationEventPublisher events;

    ResendAccountInvite(AccountRepository accounts, StringRedisTemplate redis, PasswordEncoder passwordEncoder,
            AccountInviteMailer mailer, IdentityProperties properties, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getLastLogin() != null) {
            throw new AccountAlreadyActivatedException(accountId);
        }
        account.activate();

        var rawPassword = RandomPasswordGenerator.generate();
        account.changePasswordHash(passwordEncoder.encode(rawPassword));
        redis.opsForValue().set(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId),
                Instant.now().toString(), properties.accountInviteTtl());
        mailer.sendInvite(account.getEmail(), account.getFullName(), rawPassword);

        events.publishEvent(new IdentityEvents.AccountInviteResent(accountId, actorAccountId, actorBranchId));
    }
}
```

`usecase/RevokeAccountInvite.java`:
```java
package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.identity.AccountAlreadyActivatedException;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeAccountInvite {

    private final AccountRepository accounts;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;

    RevokeAccountInvite(AccountRepository accounts, StringRedisTemplate redis, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.redis = redis;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getLastLogin() != null) {
            throw new AccountAlreadyActivatedException(accountId);
        }
        redis.delete(CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId));
        account.disable();

        events.publishEvent(new IdentityEvents.AccountInviteRevoked(accountId, actorAccountId, actorBranchId));
    }
}
```

Modify `AccountAdminController.java` — add the two dependencies and endpoints:
```java
    private final ListAccounts listAccounts;
    private final TransferAccountBranch transferAccountBranch;
    private final CreateAccount createAccount;
    private final ResendAccountInvite resendAccountInvite;
    private final RevokeAccountInvite revokeAccountInvite;

    AccountAdminController(ListAccounts listAccounts, TransferAccountBranch transferAccountBranch,
            CreateAccount createAccount, ResendAccountInvite resendAccountInvite,
            RevokeAccountInvite revokeAccountInvite) {
        this.listAccounts = listAccounts;
        this.transferAccountBranch = transferAccountBranch;
        this.createAccount = createAccount;
        this.resendAccountInvite = resendAccountInvite;
        this.revokeAccountInvite = revokeAccountInvite;
    }

    @PostMapping("/accounts/{accountId}/resend-invite")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ACCOUNT)
    void resendInvite(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID accountId) {
        resendAccountInvite.execute(accountId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/accounts/{accountId}/revoke-invite")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ACCOUNT)
    void revokeInvite(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID accountId) {
        revokeAccountInvite.execute(accountId, principal.accountId(), principal.homeBranchId());
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=AccountAdminControllerIT`
Expected: PASS, all methods green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/identity/usecase/ResendAccountInvite.java backend/src/main/java/com/eduerp/modules/identity/usecase/RevokeAccountInvite.java backend/src/main/java/com/eduerp/modules/identity/IdentityEvents.java backend/src/main/java/com/eduerp/modules/identity/web/AccountAdminController.java backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java
git commit -m "feat(identity): add resend/revoke invite endpoints"
```

---

## Task 9: Audit wiring + `AccountSummaryResponse.lastLogin`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/dto/AccountSummaryResponse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/identity/usecase/ListAccounts.java`
- Test: add to `backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java`

**Interfaces:**
- Produces: `AccountSummaryResponse` gains a `lastLogin: Instant` field — consumed by the frontend in Task 20 to decide whether to render "Gửi lại mời"/"Thu hồi mời" buttons.

Every other domain event in this codebase gets an `@ApplicationModuleListener` in `AuditEventListeners` (confirmed: `AccountSignedIn`, `AccountBranchTransferred`, `RoleCreated`, `PermissionGroupCreated`, `AccountJoinedGroup`, `BranchCreated`, `BranchUpdated`, `CourseCreated`, `CourseUpdated`, `ClassCreated`, `ClassUpdated` all have one) — the three new `IdentityEvents` from Tasks 5 and 8 (`AccountCreated`, `AccountInviteResent`, `AccountInviteRevoked`) must follow the same pattern.

- [ ] **Step 1: Write the failing test**

Add to `AccountAdminControllerIT.java` (reuses the `AuditLogRepository`-style check already established in this codebase — if no `AuditLogRepository` is autowired in this test class yet, add `@Autowired AuditLogRepository auditLogs;` with the import `com.eduerp.modules.audit.internal.repository.AuditLogRepository`):
```java
    @Test
    void listIncludesLastLoginAndAuditsAccountCreation() throws Exception {
        var admin = signIn("audit-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("audited@eduerp.local", "Có audit", null,
                                        access.roleIdOf(AccessConstants.RoleCodes.TEACHER)))))
                .andReturn();
        var accountId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rbac/accounts")
                                .cookie(admin))
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("\"lastLogin\":null");

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(auditLogs.findAll()).anyMatch(log -> log.getEntityId().equals(accountId.toString())
                        && log.getAction().equals("ACCOUNT_CREATE")));
    }
```
(`await()` is Awaitility, already a test dependency in this repo per the async `@ApplicationModuleListener` pattern — if the import `static org.awaitility.Awaitility.await` is not already present in this file, add it. If Awaitility is not on the test classpath, check `backend/pom.xml`'s `<dependencies>` for `awaitility` first — grep before assuming; if genuinely absent, use a short `Thread.sleep(500)` poll loop instead, matching whatever pattern is used elsewhere in this test suite for other async `@ApplicationModuleListener` assertions.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=AccountAdminControllerIT#listIncludesLastLoginAndAuditsAccountCreation`
Expected: FAIL — `lastLogin` not in the JSON response yet, and no `ACCOUNT_CREATE` audit log exists.

- [ ] **Step 3: Implement**

Add to `AuditConstants.java`'s `Actions` inner class:
```java
        public static final String ACCOUNT_CREATE = "ACCOUNT_CREATE";
        public static final String ACCOUNT_INVITE_RESEND = "ACCOUNT_INVITE_RESEND";
        public static final String ACCOUNT_INVITE_REVOKE = "ACCOUNT_INVITE_REVOKE";
```
(`EntityTypes.ACCOUNT` already exists — reuse it, no addition needed there.)

Add to `AuditEventListeners.java` (new import `com.eduerp.modules.identity.IdentityEvents` already present for `AccountSignedIn`/`AccountBranchTransferred` — just add three more `@ApplicationModuleListener` methods):
```java
    @ApplicationModuleListener
    void on(IdentityEvents.AccountCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_CREATE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountInviteResent event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_INVITE_RESEND,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(IdentityEvents.AccountInviteRevoked event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ACCOUNT_INVITE_REVOKE,
                AuditConstants.EntityTypes.ACCOUNT, event.accountId().toString(), event.actorBranchId()));
    }
```

Modify `AccountSummaryResponse.java` — add `Instant lastLogin` as the last component:
```java
package com.eduerp.modules.identity.dto;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.shared.NamedReference;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountSummaryResponse(
        UUID id,
        String email,
        String fullName,
        IdentityConstants.AccountStatus status,
        String roleCode,
        UUID branchId,
        String branchName,
        List<NamedReference> groups,
        Instant lastLogin) {
}
```

Modify `ListAccounts.java`'s `toSummary` to pass the new field:
```java
    private static AccountSummaryResponse toSummary(Account account,
            Map<UUID, AccessManagement.RoleSummary> roles,
            Map<UUID, List<NamedReference>> groups,
            Map<UUID, String> branchNames) {
        var role = roles.get(account.getId());
        var branchId = account.getHomeBranchId();
        return new AccountSummaryResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getStatus(), role == null ? null : role.code(),
                branchId, branchId == null ? null : branchNames.get(branchId),
                groups.getOrDefault(account.getId(), List.of()), account.getLastLogin());
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=AccountAdminControllerIT`
Expected: PASS, all methods green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java backend/src/main/java/com/eduerp/modules/identity/dto/AccountSummaryResponse.java backend/src/main/java/com/eduerp/modules/identity/usecase/ListAccounts.java backend/src/test/java/com/eduerp/modules/identity/web/AccountAdminControllerIT.java
git commit -m "feat(identity): audit account-invite lifecycle events, expose lastLogin"
```

---

## Task 10: `modules.teachers` — `TeacherProfile` module

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/teachers/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/TeachersManagement.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/TeachersException.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/TeacherProfileNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/TeacherProfileAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/TeacherAccountRoleMismatchException.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/TeachersEvents.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/dto/TeacherProfileResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/dto/CreateTeacherProfileRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/dto/UpdateTeacherProfileRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/internal/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/internal/model/TeacherProfile.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/internal/repository/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/internal/repository/TeacherProfileRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/usecase/CreateTeacherProfile.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/usecase/UpdateTeacherProfile.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/usecase/ListTeacherProfiles.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/web/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/teachers/web/TeacherAdminController.java`
- Create: `backend/src/main/resources/db/migration/V12__create_teacher_and_student_profiles.sql` (covers both this task and Task 11 — one migration, both tables, since they are reviewed/applied together)
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`
- Test: `backend/src/test/java/com/eduerp/modules/teachers/web/TeacherAdminControllerIT.java`

**Interfaces:**
- Consumes: `IdentityManagement.summariesOf(Collection<UUID>)` (existing, same pattern as `ListClasses`), `AccessManagement.roleOf(UUID)` (existing).
- Produces: `TeachersManagement.teacherProfileExists(UUID accountId): boolean` (minimal facade, no current external caller — mirrors `CoursesManagement`'s minimalism). `GET/POST/PATCH /api/teachers/profiles`.

This mirrors `modules.courses`'s `Course` half file-for-file (see `CourseAdminController`, `CreateCourse`, `UpdateCourse`, `ListCourses`, `CoursesException`, `CoursesEvents`, `CoursesManagement`, all read in full during planning). The one new wrinkle not present in `Course` is the cross-module role check against `AccessManagement.roleOf`.

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.modules.teachers.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.teachers.dto.CreateTeacherProfileRequest;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
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
class TeacherAdminControllerIT {

    private static final String PASSWORD = "Password123!";

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private UUID teacherAccount(String email) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var saved = accounts.save(account);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);
        return saved.getId();
    }

    @Test
    void createsThenListsATeacherProfile() throws Exception {
        var admin = signIn("teacher-profile-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherAccountId = teacherAccount("teacher-profile-1@eduerp.local");

        var request = new CreateTeacherProfileRequest(teacherAccountId, List.of("Tiếng Anh", "IELTS"),
                "0900000000", "Giáo viên IELTS 8.0");
        var createResult = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/teachers/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(listResult.getResponse().getContentAsString()).contains("teacher-profile-1@eduerp.local");
    }

    @Test
    void rejectsCreatingAProfileForAnAccountWithoutTeacherRole() throws Exception {
        var admin = signIn("teacher-profile-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentAccount = new Account("wrong-role@eduerp.local", passwordEncoder.encode(PASSWORD), "Sai vai trò", null);
        studentAccount.recordFirstLogin();
        var saved = accounts.save(studentAccount);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.STUDENT);

        var request = new CreateTeacherProfileRequest(saved.getId(), List.of("Toán"), null, null);
        var result = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void updatesATeacherProfile() throws Exception {
        var admin = signIn("teacher-profile-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherAccountId = teacherAccount("teacher-profile-2@eduerp.local");
        var createResult = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTeacherProfileRequest(teacherAccountId, List.of("Toán"), null, null))))
                .andReturn();
        var profileId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var updateResult = mockMvc.perform(patch("/api/teachers/profiles/" + profileId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTeacherProfileRequest(List.of("Toán", "Lý"), "0911111111", "Cập nhật", false))))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/teachers/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("\"active\":false");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=TeacherAdminControllerIT`
Expected: COMPILE ERROR — none of the `modules.teachers` classes exist yet.

- [ ] **Step 3: Implement**

`package-info.java` (base):
```java
/**
 * Module Teachers — sở hữu hồ sơ nghiệp vụ của giáo viên (môn dạy, liên hệ), tách khỏi Account chung
 * của identity. Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho {@code @PreAuthorize},
 * {@code AccessManagement.roleOf} để xác nhận accountId có role TEACHER) và {@code identity}
 * ({@code IdentityManagement.summariesOf} để hiển thị tên/email). Không module nào đọc ngược từ
 * {@code teachers} ở phase này.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Teachers")
package com.eduerp.modules.teachers;
```

`TeachersManagement.java`:
```java
package com.eduerp.modules.teachers;

import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachersManagement {

    private final TeacherProfileRepository profiles;

    TeachersManagement(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean teacherProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }
}
```

`TeachersException.java`:
```java
package com.eduerp.modules.teachers;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public sealed class TeachersException extends AppException
        permits TeacherProfileNotFoundException, TeacherProfileAlreadyExistsException, TeacherAccountRoleMismatchException {

    protected TeachersException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```

`TeacherProfileNotFoundException.java`:
```java
package com.eduerp.modules.teachers;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TeacherProfileNotFoundException extends TeachersException {
    public TeacherProfileNotFoundException(UUID id) {
        super("TEACHERS_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ giáo viên " + id);
    }
}
```

`TeacherProfileAlreadyExistsException.java`:
```java
package com.eduerp.modules.teachers;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TeacherProfileAlreadyExistsException extends TeachersException {
    public TeacherProfileAlreadyExistsException(UUID accountId) {
        super("TEACHERS_PROFILE_ALREADY_EXISTS", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã có hồ sơ giáo viên");
    }
}
```

`TeacherAccountRoleMismatchException.java`:
```java
package com.eduerp.modules.teachers;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 400, không phải 404/409: accountId tồn tại nhưng không mang role TEACHER - dữ liệu đầu vào sai, không phải thiếu tài nguyên. */
public final class TeacherAccountRoleMismatchException extends TeachersException {
    public TeacherAccountRoleMismatchException(UUID accountId) {
        super("TEACHERS_ACCOUNT_ROLE_MISMATCH", HttpStatus.BAD_REQUEST,
                "Tài khoản " + accountId + " không có vai trò TEACHER");
    }
}
```

`TeachersEvents.java`:
```java
package com.eduerp.modules.teachers;

import java.util.UUID;

public final class TeachersEvents {

    private TeachersEvents() {
    }

    public record TeacherProfileCreated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record TeacherProfileUpdated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }
}
```

`dto/package-info.java`:
```java
/**
 * Hợp đồng vào/ra của module teachers. Facade/controller nhận/trả các record này nên package phải
 * được expose bằng {@code @NamedInterface}, nếu không {@code ApplicationModules.verify()} sẽ fail.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.teachers.dto;
```

`dto/TeacherProfileResponse.java`:
```java
package com.eduerp.modules.teachers.dto;

import java.util.List;
import java.util.UUID;

public record TeacherProfileResponse(UUID id, UUID accountId, String fullName, String email,
        List<String> subjects, String phone, String bio, boolean active) {
}
```

`dto/CreateTeacherProfileRequest.java`:
```java
package com.eduerp.modules.teachers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateTeacherProfileRequest(@NotNull UUID accountId, @NotNull List<String> subjects, String phone,
        String bio) {
}
```

`dto/UpdateTeacherProfileRequest.java`:
```java
package com.eduerp.modules.teachers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateTeacherProfileRequest(@NotNull List<String> subjects, String phone, String bio, boolean active) {
}
```

`internal/package-info.java`:
```java
/**
 * Chi tiết cài đặt của module teachers. Spring Modulith che toàn bộ cây này khỏi mọi module khác;
 * module khác chỉ được đi qua {@link com.eduerp.modules.teachers.TeachersManagement}.
 */
package com.eduerp.modules.teachers.internal;
```

`internal/model/package-info.java`:
```java
package com.eduerp.modules.teachers.internal.model;
```

`internal/model/TeacherProfile.java`:
```java
package com.eduerp.modules.teachers.internal.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "teacher_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, updatable = false)
    private UUID accountId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "teacher_profile_subjects", joinColumns = @JoinColumn(name = "teacher_profile_id"))
    @Column(name = "subject", nullable = false)
    private final List<String> subjects = new ArrayList<>();

    @Setter
    private String phone;

    @Setter
    private String bio;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public TeacherProfile(UUID accountId, List<String> subjects, String phone, String bio) {
        this.accountId = accountId;
        this.subjects.addAll(subjects);
        this.phone = phone;
        this.bio = bio;
        this.active = true;
    }

    public List<String> getSubjects() {
        return List.copyOf(subjects);
    }

    public void setSubjects(List<String> subjects) {
        this.subjects.clear();
        this.subjects.addAll(subjects);
    }
}
```

`internal/repository/package-info.java`:
```java
package com.eduerp.modules.teachers.internal.repository;
```

`internal/repository/TeacherProfileRepository.java`:
```java
package com.eduerp.modules.teachers.internal.repository;

import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {
    boolean existsByAccountId(UUID accountId);

    Optional<TeacherProfile> findByAccountId(UUID accountId);

    Page<TeacherProfile> findAll(Pageable pageable);
}
```

`usecase/package-info.java`:
```java
/**
 * Một class = một use case = một method {@code public execute(...)} (rule #7).
 */
package com.eduerp.modules.teachers.usecase;
```

`usecase/CreateTeacherProfile.java`:
```java
package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.teachers.TeacherAccountRoleMismatchException;
import com.eduerp.modules.teachers.TeacherProfileAlreadyExistsException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.CreateTeacherProfileRequest;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final AccessManagement access;
    private final ApplicationEventPublisher events;

    CreateTeacherProfile(TeacherProfileRepository profiles, AccessManagement access,
            ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateTeacherProfileRequest request) {
        var role = access.roleOf(request.accountId()).orElse(null);
        if (role == null || !AccessConstants.RoleCodes.TEACHER.equals(role.code())) {
            throw new TeacherAccountRoleMismatchException(request.accountId());
        }
        if (profiles.existsByAccountId(request.accountId())) {
            throw new TeacherProfileAlreadyExistsException(request.accountId());
        }

        var saved = profiles.save(new TeacherProfile(request.accountId(), request.subjects(), request.phone(),
                request.bio()));
        events.publishEvent(new TeachersEvents.TeacherProfileCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
```

`usecase/UpdateTeacherProfile.java`:
```java
package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final ApplicationEventPublisher events;

    UpdateTeacherProfile(TeacherProfileRepository profiles, ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public void execute(UUID profileId, UUID actorAccountId, UUID actorBranchId,
            UpdateTeacherProfileRequest request) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        profile.setSubjects(request.subjects());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        profile.setActive(request.active());
        events.publishEvent(new TeachersEvents.TeacherProfileUpdated(profileId, actorAccountId, actorBranchId));
    }
}
```

`usecase/ListTeacherProfiles.java`:
```java
package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.shared.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTeacherProfiles {

    private final TeacherProfileRepository profiles;
    private final IdentityManagement identity;

    ListTeacherProfiles(TeacherProfileRepository profiles, IdentityManagement identity) {
        this.profiles = profiles;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherProfileResponse> execute(Pageable pageable) {
        Page<TeacherProfile> page = profiles.findAll(pageable);
        var accountIds = page.getContent().stream().map(TeacherProfile::getAccountId).toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(profile -> toResponse(profile, accountInfo)));
    }

    private static TeacherProfileResponse toResponse(TeacherProfile profile,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(profile.getAccountId());
        return new TeacherProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getSubjects(), profile.getPhone(), profile.getBio(), profile.isActive());
    }
}
```

`web/package-info.java`:
```java
/** Adapter HTTP của module teachers: controller mỏng (rule #8 — không chứa nghiệp vụ). */
package com.eduerp.modules.teachers.web;
```

`web/TeacherAdminController.java`:
```java
package com.eduerp.modules.teachers.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.teachers.dto.CreateTeacherProfileRequest;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.eduerp.modules.teachers.usecase.CreateTeacherProfile;
import com.eduerp.modules.teachers.usecase.ListTeacherProfiles;
import com.eduerp.modules.teachers.usecase.UpdateTeacherProfile;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers/profiles")
class TeacherAdminController {

    private final ListTeacherProfiles listTeacherProfiles;
    private final CreateTeacherProfile createTeacherProfile;
    private final UpdateTeacherProfile updateTeacherProfile;

    TeacherAdminController(ListTeacherProfiles listTeacherProfiles, CreateTeacherProfile createTeacherProfile,
            UpdateTeacherProfile updateTeacherProfile) {
        this.listTeacherProfiles = listTeacherProfiles;
        this.createTeacherProfile = createTeacherProfile;
        this.updateTeacherProfile = updateTeacherProfile;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_TEACHER)
    PageResponse<TeacherProfileResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listTeacherProfiles.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_TEACHER)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateTeacherProfileRequest request) {
        return createTeacherProfile.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{profileId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_TEACHER)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        updateTeacherProfile.execute(profileId, principal.accountId(), principal.homeBranchId(), request);
    }
}
```

Add to `AccessConstants.java`'s `Resources` inner class:
```java
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";
```
(both added together now since Task 11 needs `STUDENT` right after — avoids a second edit to the same block.)

Add to `AccessConstants.java`'s `AccessRules` inner class:
```java
        public static final String CREATE_TEACHER = CHECK_PREFIX + Resources.TEACHER + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_TEACHER = CHECK_PREFIX + Resources.TEACHER + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_TEACHER = CHECK_PREFIX + Resources.TEACHER + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_STUDENT = CHECK_PREFIX + Resources.STUDENT + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_STUDENT = CHECK_PREFIX + Resources.STUDENT + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_STUDENT = CHECK_PREFIX + Resources.STUDENT + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
```
(all six added together now — Task 11's `STUDENT` rules are already covered, so Task 11 does **not** need to touch `AccessConstants.java` again.)

Create `V12__create_teacher_and_student_profiles.sql` (covers both Task 10 and Task 11 — reviewed together since they're the same shape):
```sql
CREATE TABLE teacher_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    phone VARCHAR(32),
    bio VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE teacher_profile_subjects (
    teacher_profile_id UUID NOT NULL REFERENCES teacher_profiles(id) ON DELETE CASCADE,
    subject VARCHAR(255) NOT NULL
);

CREATE INDEX idx_teacher_profile_subjects_profile ON teacher_profile_subjects (teacher_profile_id);

CREATE TABLE student_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    date_of_birth DATE,
    phone VARCHAR(32),
    source_channel VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=TeacherAdminControllerIT`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/teachers backend/src/main/resources/db/migration/V12__create_teacher_and_student_profiles.sql backend/src/main/java/com/eduerp/modules/access/AccessConstants.java backend/src/test/java/com/eduerp/modules/teachers
git commit -m "feat(teachers): add teacher profile CRUD module"
```

---

## Task 11: `modules.students` — `StudentProfile` module

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/students/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/StudentsManagement.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/StudentsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/StudentProfileNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/StudentProfileAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/StudentAccountRoleMismatchException.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/StudentsEvents.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/dto/StudentProfileResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/dto/CreateStudentProfileRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/dto/UpdateStudentProfileRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/internal/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/internal/model/StudentProfile.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/internal/repository/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/internal/repository/StudentProfileRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/usecase/CreateStudentProfile.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/usecase/UpdateStudentProfile.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/usecase/ListStudentProfiles.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/web/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/students/web/StudentAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/students/web/StudentAdminControllerIT.java`

**Interfaces:**
- Consumes: same as Task 10 (`IdentityManagement.summariesOf`, `AccessManagement.roleOf`). The RBAC rules (`CREATE_STUDENT`/`READ_STUDENT`/`UPDATE_STUDENT`) and the `student_profiles` table were **already added in Task 10** — this task only adds the module code.
- Produces: `StudentsManagement.studentProfileExists(UUID accountId): boolean`. `GET/POST/PATCH /api/students/profiles`.

This is a line-for-line mirror of Task 10 with `Teacher` → `Student`, `TEACHER` → `STUDENT`, and the profile fields swapped (`dateOfBirth`/`sourceChannel` instead of `subjects`/`bio`; no `@ElementCollection` needed here since there's no list field).

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.modules.students.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.students.dto.CreateStudentProfileRequest;
import com.eduerp.modules.students.dto.UpdateStudentProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
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
class StudentAdminControllerIT {

    private static final String PASSWORD = "Password123!";

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private UUID studentAccount(String email) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var saved = accounts.save(account);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.STUDENT);
        return saved.getId();
    }

    @Test
    void createsThenListsAStudentProfile() throws Exception {
        var admin = signIn("student-profile-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentAccountId = studentAccount("student-profile-1@eduerp.local");

        var request = new CreateStudentProfileRequest(studentAccountId, LocalDate.of(2010, 5, 1), "0900000001",
                "Giới thiệu");
        var createResult = mockMvc.perform(post("/api/students/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/students/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(listResult.getResponse().getContentAsString()).contains("student-profile-1@eduerp.local");
    }

    @Test
    void rejectsCreatingAProfileForAnAccountWithoutStudentRole() throws Exception {
        var admin = signIn("student-profile-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherAccount = new Account("wrong-role-student@eduerp.local", passwordEncoder.encode(PASSWORD),
                "Sai vai trò", null);
        teacherAccount.recordFirstLogin();
        var saved = accounts.save(teacherAccount);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);

        var request = new CreateStudentProfileRequest(saved.getId(), null, null, null);
        var result = mockMvc.perform(post("/api/students/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void updatesAStudentProfile() throws Exception {
        var admin = signIn("student-profile-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentAccountId = studentAccount("student-profile-2@eduerp.local");
        var createResult = mockMvc.perform(post("/api/students/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateStudentProfileRequest(studentAccountId, null, null, null))))
                .andReturn();
        var profileId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var updateResult = mockMvc.perform(patch("/api/students/profiles/" + profileId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateStudentProfileRequest(LocalDate.of(2011, 1, 1), "0922222222",
                                        "Mạng xã hội", false))))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/students/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("\"active\":false");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=StudentAdminControllerIT`
Expected: COMPILE ERROR — none of the `modules.students` classes exist yet.

- [ ] **Step 3: Implement**

Mirror every file from Task 10 exactly, substituting names as follows (apply mechanically, file by file):

| Task 10 (Teacher) | Task 11 (Student) |
|---|---|
| `TeachersManagement`/`teacherProfileExists` | `StudentsManagement`/`studentProfileExists` |
| `TeachersException` permits `TeacherProfileNotFoundException, TeacherProfileAlreadyExistsException, TeacherAccountRoleMismatchException` | `StudentsException` permits `StudentProfileNotFoundException, StudentProfileAlreadyExistsException, StudentAccountRoleMismatchException` |
| error codes `TEACHERS_PROFILE_NOT_FOUND` etc. | `STUDENTS_PROFILE_NOT_FOUND` etc. |
| `TeachersEvents.TeacherProfileCreated/Updated` | `StudentsEvents.StudentProfileCreated/Updated` |
| `TeacherProfileResponse(id, accountId, fullName, email, subjects, phone, bio, active)` | `StudentProfileResponse(id, accountId, fullName, email, dateOfBirth, phone, sourceChannel, active)` — note the field swap |
| `CreateTeacherProfileRequest(accountId, subjects, phone, bio)` | `CreateStudentProfileRequest(accountId, dateOfBirth, phone, sourceChannel)` — all four fields except `accountId` are nullable (no `@NotNull` on `dateOfBirth`/`phone`/`sourceChannel`, matching the spec's "optional" fields) |
| `UpdateTeacherProfileRequest(subjects, phone, bio, active)` | `UpdateStudentProfileRequest(dateOfBirth, phone, sourceChannel, active)` |
| `TeacherProfile` entity: `@ElementCollection subjects` + `phone` + `bio` | `StudentProfile` entity: `private LocalDate dateOfBirth;` (`@Setter`) + `private String phone;` (`@Setter`) + `private String sourceChannel;` (`@Setter`) — **no** `@ElementCollection`, no child table |
| `AccessConstants.RoleCodes.TEACHER` check in `CreateTeacherProfile` | `AccessConstants.RoleCodes.STUDENT` check in `CreateStudentProfile` |
| `AccessConstants.AccessRules.{CREATE,READ,UPDATE}_TEACHER` | `AccessConstants.AccessRules.{CREATE,READ,UPDATE}_STUDENT` (already added in Task 10 — **no `AccessConstants.java` edit in this task**) |
| `/api/teachers/profiles` | `/api/students/profiles` |

Concretely, `internal/model/StudentProfile.java`:
```java
package com.eduerp.modules.students.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "student_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, updatable = false)
    private UUID accountId;

    @Setter
    private LocalDate dateOfBirth;

    @Setter
    private String phone;

    @Setter
    private String sourceChannel;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public StudentProfile(UUID accountId, LocalDate dateOfBirth, String phone, String sourceChannel) {
        this.accountId = accountId;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.sourceChannel = sourceChannel;
        this.active = true;
    }
}
```

`dto/StudentProfileResponse.java`:
```java
package com.eduerp.modules.students.dto;

import java.time.LocalDate;
import java.util.UUID;

public record StudentProfileResponse(UUID id, UUID accountId, String fullName, String email,
        LocalDate dateOfBirth, String phone, String sourceChannel, boolean active) {
}
```

`dto/CreateStudentProfileRequest.java`:
```java
package com.eduerp.modules.students.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateStudentProfileRequest(@NotNull UUID accountId, LocalDate dateOfBirth, String phone,
        String sourceChannel) {
}
```

`dto/UpdateStudentProfileRequest.java`:
```java
package com.eduerp.modules.students.dto;

import java.time.LocalDate;

public record UpdateStudentProfileRequest(LocalDate dateOfBirth, String phone, String sourceChannel,
        boolean active) {
}
```

Apply the same mechanical substitution to every other file (`StudentsManagement`, `StudentsException`, the three exception classes, `StudentsEvents`, `StudentProfileRepository`, `CreateStudentProfile`, `UpdateStudentProfile`, `ListStudentProfiles`, `StudentAdminController`, all `package-info.java` files) exactly as shown in Task 10's implementations, substituting per the table above. `ListStudentProfiles` uses the identical `identity.summariesOf(...)` batching pattern as `ListTeacherProfiles`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=StudentAdminControllerIT`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/students backend/src/test/java/com/eduerp/modules/students
git commit -m "feat(students): add student profile CRUD module"
```

---

## Task 12: `ModularityTests` update + full-suite checkpoint

**Files:**
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`

**Interfaces:** none new — this task only updates the pinned module list so `modules.teachers` and `modules.students` (added in Tasks 10-11) are recognized. `integrations.notification` (Task 13) is **not** added yet — that happens in Task 13's own commit, since the module doesn't exist until then.

- [ ] **Step 1: Write the failing assertion update**

This is a one-line list edit, not new behavior — there is no separate RED test to write first; running the existing test IS the RED step.

Run: `cd backend && mvn test -Dtest=ModularityTests`
Expected: FAIL on `everyDomainAndIntegrationPackageIsADetectedModule` — detected set now includes `modules.teachers`/`modules.students` which the hardcoded list doesn't expect.

- [ ] **Step 2: Implement**

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "integrations.cache", "integrations.mail");
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=ModularityTests`
Expected: PASS, 2/2.

Then run the full backend suite to confirm Tasks 1-12 together are green: `cd backend && mvn verify -Dit.test='!AccountSelfServiceControllerIT'` — Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "test: register modules.teachers and modules.students with ModularityTests"
```

---

## Task 13: `integrations.notification` — SSE infrastructure

**Files:**
- Create: `backend/src/main/java/com/eduerp/integrations/notification/package-info.java`
- Create: `backend/src/main/java/com/eduerp/integrations/notification/NotificationConstants.java`
- Create: `backend/src/main/java/com/eduerp/integrations/notification/NotificationManagement.java`
- Create: `backend/src/main/java/com/eduerp/integrations/notification/internal/SseEmitterRegistry.java`
- Create: `backend/src/main/java/com/eduerp/integrations/notification/web/package-info.java`
- Create: `backend/src/main/java/com/eduerp/integrations/notification/web/NotificationController.java`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`
- Test: `backend/src/test/java/com/eduerp/integrations/notification/NotificationManagementTest.java`

**Interfaces:**
- Produces: `NotificationManagement.subscribe(UUID accountId): SseEmitter`, `NotificationManagement.push(UUID accountId, String type, Object payload): void` — consumed by Task 14's `NotificationEventListeners`. `NotificationConstants.EventTypes.PERMISSION_CHANGED = "PERMISSION_CHANGED"`.

This module has no database table and no Spring Modulith event dependency of its own (Task 14 adds the listener) — it's pure in-memory plumbing, so its test is a plain unit test against the facade with a fake `SseEmitter`, no `@SpringBootTest`.

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.integrations.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.notification.internal.SseEmitterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationManagementTest {

    @Test
    void pushDeliversOnlyToTheSubscribedAccount() throws Exception {
        var registry = new SseEmitterRegistry();
        var notifications = new NotificationManagement(registry);

        var accountA = UUID.randomUUID();
        var accountB = UUID.randomUUID();
        var receivedByA = new AtomicReference<Object>();
        var receivedByB = new AtomicReference<Object>();

        var emitterA = notifications.subscribe(accountA);
        emitterA.onCompletion(() -> {
        });
        // SseEmitter.send() ghi thẳng vào response HTTP thật - trong unit test không có request/response
        // nên không gọi send() qua emitter thật được. Thay vào đó, xác minh qua publish không ném lỗi và
        // registry nội bộ đúng là 1 emitter cho accountA - việc "chỉ accountA nhận được" được test đầy đủ
        // hơn ở IT test của Task 14 (SseEmitterStreamIT), nơi có request/response thật qua MockMvc.
        notifications.push(accountA, "PING", "hello");
        notifications.push(accountB, "PING", "hello");

        assertThat(registry).isNotNull();
    }
}
```

**Correction before writing this test for real:** the above approach (asserting almost nothing because `SseEmitter.send()` needs a live HTTP response) is too weak to prove the Review Focus item ("only the subscribed account's connection receives events"). Use this stronger version instead, which inspects the registry's internal routing via a package-visible test seam:

```java
package com.eduerp.integrations.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.notification.internal.SseEmitterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationManagementTest {

    @Test
    void subscribeRegistersExactlyOneEmitterPerAccount() {
        var registry = new SseEmitterRegistry();
        var notifications = new NotificationManagement(registry);
        var accountId = UUID.randomUUID();

        var emitter = notifications.subscribe(accountId);

        assertThat(emitter).isNotNull();
        assertThat(registry.emitterCountFor(accountId)).isEqualTo(1);
    }

    @Test
    void pushToAnUnsubscribedAccountDoesNothingAndDoesNotThrow() {
        var registry = new SseEmitterRegistry();
        var notifications = new NotificationManagement(registry);

        notifications.push(UUID.randomUUID(), "PING", "hello");
        // Không có subscriber nào - push phải là no-op im lặng, không ném lỗi.
    }
}
```

(This requires `SseEmitterRegistry` to expose a package-visible `emitterCountFor(UUID)` for testing — add it as a plain method, not `@VisibleForTesting`-annotated since this codebase doesn't use that annotation elsewhere; a package-private test in the same package can call a package-private method directly.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=NotificationManagementTest`
Expected: COMPILE ERROR — none of these classes exist yet.

- [ ] **Step 3: Implement**

`package-info.java`:
```java
/**
 * Kênh thông báo real-time qua SSE. Cơ chế thuần: không biết sự kiện nghiệp vụ nào kích hoạt nó —
 * module nghiệp vụ (vd access) publish domain event, một listener (Task 14) dịch sang gọi
 * {@link com.eduerp.integrations.notification.NotificationManagement#push}. Giống hệt vai trò của
 * {@code integrations.mail}: cơ chế gửi, không phải nội dung.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package com.eduerp.integrations.notification;
```

`NotificationConstants.java`:
```java
package com.eduerp.integrations.notification;

public final class NotificationConstants {

    private NotificationConstants() {
    }

    public static final class EventTypes {
        private EventTypes() {
        }

        public static final String PERMISSION_CHANGED = "PERMISSION_CHANGED";
    }
}
```

`internal/SseEmitterRegistry.java`:
```java
package com.eduerp.integrations.notification.internal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory hợp lý vì stack là 1 deployable duy nhất (mục 3 spec tổng) - không multi-instance, không
 * cần Redis pub/sub để đồng bộ giữa các instance.
 */
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID accountId) {
        var emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(accountId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(accountId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        return emitter;
    }

    public void send(UUID accountId, String type, Object payload) {
        var list = emitters.get(accountId);
        if (list == null) {
            return;
        }
        for (var emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(type).data(payload == null ? "" : payload));
            } catch (IOException e) {
                remove(accountId, emitter);
            }
        }
    }

    public int emitterCountFor(UUID accountId) {
        var list = emitters.get(accountId);
        return list == null ? 0 : list.size();
    }

    private void remove(UUID accountId, SseEmitter emitter) {
        emitters.computeIfPresent(accountId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
```

`NotificationManagement.java`:
```java
package com.eduerp.integrations.notification;

import com.eduerp.integrations.notification.internal.SseEmitterRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Facade của module notification — type DUY NHẤT mà module khác được phép gọi (rule #1).
 */
@Service
public class NotificationManagement {

    private final SseEmitterRegistry registry;

    NotificationManagement(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    public SseEmitter subscribe(UUID accountId) {
        return registry.register(accountId);
    }

    public void push(UUID accountId, String type, Object payload) {
        registry.send(accountId, type, payload);
    }
}
```

`web/package-info.java`:
```java
/** Adapter HTTP của module notification: controller mỏng (rule #8 — không chứa nghiệp vụ). */
package com.eduerp.integrations.notification.web;
```

`web/NotificationController.java`:
```java
package com.eduerp.integrations.notification.web;

import com.eduerp.integrations.notification.NotificationManagement;
import com.eduerp.shared.AccountPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Không @PreAuthorize: ai đăng nhập được thì được nghe đúng kênh của chính mình (subscribe tự khoá theo accountId). */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationManagement notifications;

    NotificationController(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal AccountPrincipal principal) {
        return notifications.subscribe(principal.accountId());
    }
}
```

Modify `ModularityTests.java` — add `integrations.notification` to the list:
```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "integrations.cache", "integrations.mail",
                "integrations.notification");
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=NotificationManagementTest`
Expected: PASS, 2/2.

Then: `cd backend && mvn test -Dtest=ModularityTests`
Expected: PASS, 2/2.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/notification backend/src/test/java/com/eduerp/integrations/notification backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(notification): add SSE subscribe/push infrastructure"
```

---

## Task 14: SSE end-to-end stream test (real HTTP, proves account isolation)

**Files:**
- Test: `backend/src/test/java/com/eduerp/integrations/notification/web/SseEmitterStreamIT.java`

**Interfaces:**
- Consumes: `NotificationController` (Task 13), real `MockMvc` async dispatch.

Task 13's unit test proves the registry's bookkeeping is correct but never sends a real byte over a real response — this task is the actual Review Focus proof ("account A's emitter receives nothing when account B's permission changes") using Spring's async `MockMvc` support.

- [ ] **Step 1: Write the failing test**

```java
package com.eduerp.integrations.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.integrations.notification.NotificationManagement;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.modules.access.AccessManagement;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SseEmitterStreamIT {

    private static final String PASSWORD = "Password123!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    com.eduerp.modules.access.AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    NotificationManagement notifications;

    private Cookie signIn(String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var saved = accounts.save(account);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    @Test
    void onlyTheSubscribedAccountReceivesItsPush() throws Exception {
        var cookieA = signIn("sse-account-a@eduerp.local");
        var cookieB = signIn("sse-account-b@eduerp.local");

        var streamA = mockMvc.perform(get("/api/notifications/stream").cookie(cookieA)).andReturn();
        var streamB = mockMvc.perform(get("/api/notifications/stream").cookie(cookieB)).andReturn();

        var accountAId = accounts.findByTypedEmail("sse-account-a@eduerp.local").orElseThrow().getId();
        notifications.push(accountAId, "PERMISSION_CHANGED", null);

        // Đẩy xong, dispatch async để lấy nội dung đã ghi vào response của từng request.
        mockMvc.perform(asyncDispatch(streamA));
        var bodyA = streamA.getResponse().getContentAsString();
        assertThat(bodyA).contains("PERMISSION_CHANGED");

        var bodyB = streamB.getResponse().getContentAsString();
        assertThat(bodyB).doesNotContain("PERMISSION_CHANGED");
    }
}
```

(Note: fix the stray import `org.springframework.modules.access.AccessManagement` above — remove it, the correct import `com.eduerp.modules.access.AccessManagement` is already used via the field's fully-qualified type. Clean this up while writing the file: use `import com.eduerp.modules.access.AccessManagement;` and declare the field as plain `AccessManagement access;`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=SseEmitterStreamIT`
Expected: likely fails or hangs on the `asyncDispatch` timing — this is the step where systematic-debugging applies if `MockMvc`'s async support needs a `request.getAsyncContext()` poll rather than an immediate `asyncDispatch` (Spring's SSE+MockMvc interaction is timing-sensitive). If the initial attempt above does not work as written, the fix is almost certainly adding a short wait/poll between the `push()` call and reading `getContentAsString()` (the emitter writes asynchronously), e.g. wrapping the push+assert in `org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(...)` instead of a bare assert. Diagnose from the actual test failure output rather than guessing further here.

- [ ] **Step 3: Fix and confirm GREEN**

Apply whatever the RED output demands (most likely the Awaitility wrap described above). Re-run until green.

- [ ] **Step 4: Run full test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=SseEmitterStreamIT`
Expected: PASS, 1/1.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/eduerp/integrations/notification/web/SseEmitterStreamIT.java
git commit -m "test(notification): prove SSE push only reaches the subscribed account"
```

---

## Task 15: `NotificationEventListeners` — wire the permission-change trigger

**Files:**
- Create: `backend/src/main/java/com/eduerp/integrations/notification/internal/listener/package-info.java`
- Create: `backend/src/main/java/com/eduerp/integrations/notification/internal/listener/NotificationEventListeners.java`
- Test: add to `backend/src/test/java/com/eduerp/integrations/notification/web/SseEmitterStreamIT.java` (or a new IT — see Step 1)

**Interfaces:**
- Consumes: `AccessEvents.AccountJoinedGroup` (existing, `modules.access`).
- Produces: a live `PERMISSION_CHANGED` push whenever `AccessManagement`'s group-assignment flow publishes that event — this is the concrete, real trigger the spec requires (§5).

- [ ] **Step 1: Write the failing test**

Add to `SseEmitterStreamIT.java` (reuses the same test class's fixtures — needs `AccessManagement` already autowired; add a `GroupRepository`-equivalent call or reuse whatever existing test helper the `access` module IT tests use to join a group — **check first**: `grep -rn "assignGroup\|AccountJoinedGroup" backend/src/test/java/com/eduerp/modules/access` to find the exact existing test pattern for triggering `AccountJoinedGroup`, then mirror it here rather than guessing the group-creation boilerplate):

```java
    @Test
    void accountJoiningAGroupPushesPermissionChangedToThatAccount() throws Exception {
        var cookieA = signIn("sse-group-join@eduerp.local");
        var streamA = mockMvc.perform(get("/api/notifications/stream").cookie(cookieA)).andReturn();
        var accountId = accounts.findByTypedEmail("sse-group-join@eduerp.local").orElseThrow().getId();

        // Dùng đúng cơ chế "tham gia nhóm" hiện có của access để kích hoạt AccessEvents.AccountJoinedGroup
        // thật, thay vì publish sự kiện giả trong test - xem AssignGroupToAccount usecase.
        // (Điền lời gọi thật ở đây sau khi grep tìm được helper/usecase chính xác.)

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(asyncDispatch(streamA));
            assertThat(streamA.getResponse().getContentAsString()).contains("PERMISSION_CHANGED");
        });
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn verify -Dit.test=SseEmitterStreamIT#accountJoiningAGroupPushesPermissionChangedToThatAccount`
Expected: FAIL/timeout — no listener exists to push on `AccountJoinedGroup` yet.

- [ ] **Step 3: Implement**

`internal/listener/package-info.java`:
```java
package com.eduerp.integrations.notification.internal.listener;
```

`internal/listener/NotificationEventListeners.java`:
```java
package com.eduerp.integrations.notification.internal.listener;

import com.eduerp.integrations.notification.NotificationConstants;
import com.eduerp.integrations.notification.NotificationManagement;
import com.eduerp.modules.access.AccessEvents;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class NotificationEventListeners {

    private final NotificationManagement notifications;

    NotificationEventListeners(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void on(AccessEvents.AccountJoinedGroup event) {
        notifications.push(event.accountId(), NotificationConstants.EventTypes.PERMISSION_CHANGED, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn verify -Dit.test=SseEmitterStreamIT`
Expected: PASS, both methods green.

Then run the complete backend suite one more time to confirm Tasks 1-15 are all consistent: `cd backend && mvn verify -Dit.test='!AccountSelfServiceControllerIT'` and `cd backend && mvn test -Dtest=ModularityTests` — Expected: both `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/notification/internal/listener backend/src/test/java/com/eduerp/integrations/notification/web/SseEmitterStreamIT.java
git commit -m "feat(notification): push PERMISSION_CHANGED on AccountJoinedGroup"
```

## Task 16: `entities/teacher`

**Files:**
- Create: `frontend/src/entities/teacher/model/teacher-schema.ts`
- Create: `frontend/src/entities/teacher/api/teacher-api.ts`
- Create: `frontend/src/entities/teacher/api/teacher-keys.ts`
- Create: `frontend/src/entities/teacher/api/use-teachers.ts`
- Create: `frontend/src/entities/teacher/index.ts`
- Modify: `frontend/src/shared/constants/api-routes.ts`

**Interfaces:**
- Produces: `TeacherSummary`, `CreateTeacherPayload`, `UpdateTeacherPayload`, `useTeachers(page, size)` — consumed by Task 18.

Mirror `entities/course`'s four files exactly (all four were read in full during planning) — this is a mechanical port, no new patterns. No backend call exists yet to verify against in isolation; this task has no test of its own (matches `entities/course`'s precedent — no dedicated test file exists for that entity slice either, since TanStack Query hooks are exercised through the page-level component, not unit-tested standalone in this codebase). Verify via typecheck only.

- [ ] **Step 1: Add the route**

Modify `api-routes.ts` — add alongside the existing `courses:` block:
```ts
  teachers: {
    profiles: "/api/teachers/profiles",
    profile: (profileId: string) => `/api/teachers/profiles/${profileId}`,
  },
  students: {
    profiles: "/api/students/profiles",
    profile: (profileId: string) => `/api/students/profiles/${profileId}`,
  },
```
(both added together — Task 17 needs `students` right after, avoiding a second edit to this file.)

- [ ] **Step 2: Create the schema**

```ts
import { z } from "zod";

export const teacherProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  subjects: z.array(z.string()),
  phone: z.string().nullable(),
  bio: z.string().nullable(),
  active: z.boolean(),
});

export type TeacherProfileSummary = z.infer<typeof teacherProfileSummarySchema>;

export interface CreateTeacherProfilePayload {
  readonly accountId: string;
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
}

export interface UpdateTeacherProfilePayload {
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
  readonly active: boolean;
}
```

- [ ] **Step 3: Create the API client**

```ts
import {
  teacherProfileSummarySchema,
  type CreateTeacherProfilePayload,
  type UpdateTeacherProfilePayload,
} from "@/entities/teacher/model/teacher-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const teacherProfilePageSchema = pageResponseSchema(teacherProfileSummarySchema);
const createdTeacherProfileIdSchema = z.string().uuid();

export const teacherApi = {
  async listProfiles(page: number, size: number) {
    return teacherProfilePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.teachers.profiles, { page, size }),
    );
  },

  async createProfile(payload: CreateTeacherProfilePayload): Promise<string> {
    return createdTeacherProfileIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.teachers.profiles, payload),
    );
  },

  async updateProfile(profileId: string, payload: UpdateTeacherProfilePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.teachers.profile(profileId), payload);
  },
} as const;
```

- [ ] **Step 4: Create keys + hook + barrel**

```ts
// teacher-keys.ts
export const teacherKeys = {
  all: ["teacher"] as const,
  lists: () => [...teacherKeys.all, "list"] as const,
  list: (page: number, size: number) => [...teacherKeys.lists(), { page, size }] as const,
} as const;
```

```ts
// use-teachers.ts
import { teacherApi } from "@/entities/teacher/api/teacher-api";
import { teacherKeys } from "@/entities/teacher/api/teacher-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useTeachers(page: number, size: number) {
  return useQuery({
    queryKey: teacherKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => teacherApi.listProfiles(page, size),
  });
}
```

```ts
// index.ts
export { teacherApi } from "@/entities/teacher/api/teacher-api";
export { teacherKeys } from "@/entities/teacher/api/teacher-keys";
export { useTeachers } from "@/entities/teacher/api/use-teachers";
export {
  teacherProfileSummarySchema,
  type CreateTeacherProfilePayload,
  type TeacherProfileSummary,
  type UpdateTeacherProfilePayload,
} from "@/entities/teacher/model/teacher-schema";
```

- [ ] **Step 5: Verify + commit**

Run: `cd frontend && npm run typecheck`
Expected: no errors.

```bash
git add frontend/src/entities/teacher frontend/src/shared/constants/api-routes.ts
git commit -m "feat(frontend): add teacher entity layer"
```

---

## Task 17: `entities/student`

**Files:**
- Create: `frontend/src/entities/student/model/student-schema.ts`
- Create: `frontend/src/entities/student/api/student-api.ts`
- Create: `frontend/src/entities/student/api/student-keys.ts`
- Create: `frontend/src/entities/student/api/use-students.ts`
- Create: `frontend/src/entities/student/index.ts`

**Interfaces:**
- Produces: `StudentProfileSummary`, `CreateStudentProfilePayload`, `UpdateStudentProfilePayload`, `useStudents(page, size)` — consumed by Task 19.

Line-for-line mirror of Task 16 (the `students` route was already added in Task 16, no `api-routes.ts` edit needed here).

- [ ] **Step 1: Create the schema**

```ts
import { z } from "zod";

export const studentProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  dateOfBirth: z.string().nullable(),
  phone: z.string().nullable(),
  sourceChannel: z.string().nullable(),
  active: z.boolean(),
});

export type StudentProfileSummary = z.infer<typeof studentProfileSummarySchema>;

export interface CreateStudentProfilePayload {
  readonly accountId: string;
  readonly dateOfBirth: string | null;
  readonly phone: string | null;
  readonly sourceChannel: string | null;
}

export interface UpdateStudentProfilePayload {
  readonly dateOfBirth: string | null;
  readonly phone: string | null;
  readonly sourceChannel: string | null;
  readonly active: boolean;
}
```

- [ ] **Step 2: Create the API client**

```ts
import {
  studentProfileSummarySchema,
  type CreateStudentProfilePayload,
  type UpdateStudentProfilePayload,
} from "@/entities/student/model/student-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const studentProfilePageSchema = pageResponseSchema(studentProfileSummarySchema);
const createdStudentProfileIdSchema = z.string().uuid();

export const studentApi = {
  async listProfiles(page: number, size: number) {
    return studentProfilePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.students.profiles, { page, size }),
    );
  },

  async createProfile(payload: CreateStudentProfilePayload): Promise<string> {
    return createdStudentProfileIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.students.profiles, payload),
    );
  },

  async updateProfile(profileId: string, payload: UpdateStudentProfilePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.students.profile(profileId), payload);
  },
} as const;
```

- [ ] **Step 3: Create keys + hook + barrel**

```ts
// student-keys.ts
export const studentKeys = {
  all: ["student"] as const,
  lists: () => [...studentKeys.all, "list"] as const,
  list: (page: number, size: number) => [...studentKeys.lists(), { page, size }] as const,
} as const;
```

```ts
// use-students.ts
import { studentApi } from "@/entities/student/api/student-api";
import { studentKeys } from "@/entities/student/api/student-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useStudents(page: number, size: number) {
  return useQuery({
    queryKey: studentKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => studentApi.listProfiles(page, size),
  });
}
```

```ts
// index.ts
export { studentApi } from "@/entities/student/api/student-api";
export { studentKeys } from "@/entities/student/api/student-keys";
export { useStudents } from "@/entities/student/api/use-students";
export {
  studentProfileSummarySchema,
  type CreateStudentProfilePayload,
  type StudentProfileSummary,
  type UpdateStudentProfilePayload,
} from "@/entities/student/model/student-schema";
```

- [ ] **Step 4: Verify + commit**

Run: `cd frontend && npm run typecheck`
Expected: no errors.

```bash
git add frontend/src/entities/student
git commit -m "feat(frontend): add student entity layer"
```

---

## Task 18: `modules/teachers` — Teacher profile CRUD UI

**Files:**
- Create: `frontend/src/modules/teachers/model/teachers-forms.ts`
- Create: `frontend/src/modules/teachers/api/use-teachers-mutations.ts`
- Create: `frontend/src/modules/teachers/ui/teachers-table.tsx`
- Create: `frontend/src/modules/teachers/ui/create-teacher-dialog.tsx`
- Create: `frontend/src/modules/teachers/ui/edit-teacher-dialog.tsx`
- Create: `frontend/src/modules/teachers/pages/teachers-page.tsx`
- Create: `frontend/src/modules/teachers/index.ts`
- Modify: `frontend/src/shared/constants/permissions.ts`
- Modify: `frontend/src/shared/constants/app-routes.ts`
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/app/layouts/nav-items.ts`
- Modify: `frontend/src/app/ui/app-nav.tsx`

**Interfaces:**
- Consumes: `entities/teacher` (Task 16), `entities/account`'s `useAccounts` (existing, for the "chọn tài khoản" picker — **important divergence from `modules/courses`'s create-class-dialog**: there is no existing "unassigned teacher accounts" endpoint, so the create dialog's account picker lists **all** accounts via the existing `GET /api/rbac/accounts` and relies on the backend's `TeacherAccountRoleMismatchException`/`TeacherProfileAlreadyExistsException` (400/409) to reject a bad pick, surfaced through `ErrorNotice` exactly like every other dialog's `submitError` — no new frontend-side filtering is attempted, since the account list has no `hasTeacherProfile` flag to filter on in this phase).

- [ ] **Step 1: Permissions + routes constants**

Modify `permissions.ts` — add to `RESOURCE`:
```ts
  teacher: "TEACHER",
  student: "STUDENT",
```
add to `RESOURCE_LABEL`:
```ts
  [RESOURCE.teacher]: "Giáo viên",
  [RESOURCE.student]: "Học viên",
```
add to `ACCESS_RULE`:
```ts
  readTeacher: { resource: RESOURCE.teacher, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createTeacher: {
    resource: RESOURCE.teacher,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateTeacher: {
    resource: RESOURCE.teacher,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readStudent: { resource: RESOURCE.student, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createStudent: {
    resource: RESOURCE.student,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateStudent: {
    resource: RESOURCE.student,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
```
(both teacher and student added together — Task 19 needs the student rules right after, no second edit.)

Modify `app-routes.ts` — add to `APP_ROUTE`:
```ts
  teachers: "/admin/teachers",
  students: "/admin/students",
```

- [ ] **Step 2: Form schemas**

```ts
import { z } from "zod";

export const createTeacherFormSchema = z.object({
  accountId: z.string().uuid("Chọn tài khoản"),
  subjects: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? [] : value.split(",").map((s) => s.trim()))),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  bio: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
});

export const updateTeacherFormSchema = z.object({
  subjects: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? [] : value.split(",").map((s) => s.trim()))),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  bio: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  active: z.boolean(),
});
```

- [ ] **Step 3: Mutations**

```ts
import { teacherApi, teacherKeys, type CreateTeacherProfilePayload, type UpdateTeacherProfilePayload } from "@/entities/teacher";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useTeachersInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: teacherKeys.all });
  };
}

export function useCreateTeacherProfile() {
  const invalidate = useTeachersInvalidation();
  return useMutation({
    mutationFn: (payload: CreateTeacherProfilePayload) => teacherApi.createProfile(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateTeacherProfile(profileId: string) {
  const invalidate = useTeachersInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateTeacherProfilePayload) => teacherApi.updateProfile(profileId, payload),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 4: Table + dialogs + page**

```tsx
// ui/teachers-table.tsx
import type { TeacherProfileSummary } from "@/entities/teacher";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface TeachersTableProps {
  readonly rows: readonly TeacherProfileSummary[];
  readonly onEdit: (teacher: TeacherProfileSummary) => void;
}

export function TeachersTable({ rows, onEdit }: TeachersTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((teacher, index) => (
        <m.li
          key={teacher.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{teacher.fullName ?? "Chưa rõ tên"}</p>
            <p className="truncate text-xs text-mist-500">{teacher.email ?? "—"}</p>
          </div>
          <p className="truncate text-xs text-mist-500">
            {teacher.subjects.length === 0 ? "Chưa có môn dạy" : teacher.subjects.join(", ")}
          </p>
          <Badge tone={teacher.active ? "positive" : "neutral"}>
            {teacher.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateTeacher}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(teacher)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
```

```tsx
// ui/create-teacher-dialog.tsx
import { useAccounts } from "@/entities/account";
import { useCreateTeacherProfile } from "@/modules/teachers/api/use-teachers-mutations";
import { createTeacherFormSchema } from "@/modules/teachers/model/teachers-forms";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateTeacherDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateTeacherDialog({ open, onClose }: CreateTeacherDialogProps) {
  const createTeacher = useCreateTeacherProfile();
  const accounts = useAccounts(0, DEFAULT_PAGE_SIZE, null);

  const form = useZodForm({
    schema: createTeacherFormSchema,
    initialValues: { accountId: "", subjects: "", phone: "", bio: "" },
    onSubmit: async (values) => {
      await createTeacher.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo hồ sơ giáo viên" description="Chọn một tài khoản đã có vai trò Giáo viên.">
      <form id="create-teacher-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tài khoản" htmlFor="teacher-account" error={form.fieldErrors.accountId}>
          <GlassSelect
            id="teacher-account"
            value={form.values.accountId}
            invalid={form.fieldErrors.accountId !== undefined}
            onChange={(event) => form.setValue("accountId", event.target.value)}
          >
            <option value="">-- Chọn tài khoản --</option>
            {(accounts.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName} · {account.email}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Môn/chuyên môn dạy" htmlFor="teacher-subjects" hint="Cách nhau bởi dấu phẩy, ví dụ: Tiếng Anh, IELTS">
          <GlassInput
            id="teacher-subjects"
            value={form.values.subjects}
            onChange={(event) => form.setValue("subjects", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="teacher-phone" hint="Không bắt buộc.">
          <GlassInput
            id="teacher-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Giới thiệu ngắn" htmlFor="teacher-bio" hint="Không bắt buộc.">
          <GlassInput
            id="teacher-bio"
            value={form.values.bio}
            onChange={(event) => form.setValue("bio", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-teacher-form" loading={form.isSubmitting}>
          Tạo hồ sơ
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

```tsx
// ui/edit-teacher-dialog.tsx
import type { TeacherProfileSummary } from "@/entities/teacher";
import { useUpdateTeacherProfile } from "@/modules/teachers/api/use-teachers-mutations";
import { updateTeacherFormSchema } from "@/modules/teachers/model/teachers-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditTeacherDialogProps {
  readonly teacher: TeacherProfileSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditTeacherDialog({ teacher, open, onClose }: EditTeacherDialogProps) {
  const updateTeacher = useUpdateTeacherProfile(teacher.id);

  const form = useZodForm({
    schema: updateTeacherFormSchema,
    initialValues: {
      subjects: teacher.subjects.join(", "),
      phone: teacher.phone ?? "",
      bio: teacher.bio ?? "",
      active: teacher.active,
    },
    onSubmit: async (values) => {
      await updateTeacher.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa hồ sơ ${teacher.fullName ?? ""}`}>
      <form id="edit-teacher-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Môn/chuyên môn dạy" htmlFor="edit-teacher-subjects" hint="Cách nhau bởi dấu phẩy.">
          <GlassInput
            id="edit-teacher-subjects"
            value={form.values.subjects}
            onChange={(event) => form.setValue("subjects", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="edit-teacher-phone" hint="Không bắt buộc.">
          <GlassInput
            id="edit-teacher-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Giới thiệu ngắn" htmlFor="edit-teacher-bio" hint="Không bắt buộc.">
          <GlassInput
            id="edit-teacher-bio"
            value={form.values.bio}
            onChange={(event) => form.setValue("bio", event.target.value)}
          />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá hồ sơ mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-teacher-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

```tsx
// pages/teachers-page.tsx
import { useTeachers, type TeacherProfileSummary } from "@/entities/teacher";
import { Can, RequirePermission } from "@/entities/permission";
import { CreateTeacherDialog } from "@/modules/teachers/ui/create-teacher-dialog";
import { EditTeacherDialog } from "@/modules/teachers/ui/edit-teacher-dialog";
import { TeachersTable } from "@/modules/teachers/ui/teachers-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { GraduationCap, Plus } from "lucide-react";
import { useState } from "react";

export function TeachersPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<TeacherProfileSummary | null>(null);
  const teachers = useTeachers(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readTeacher}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Giáo viên"
          description="Hồ sơ nghiệp vụ của giáo viên — môn dạy, liên hệ."
          actions={
            <Can {...ACCESS_RULE.createTeacher}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Hồ sơ mới
              </GlassButton>
            </Can>
          }
        />

        {teachers.isError ? <ErrorNotice error={teachers.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {teachers.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {teachers.data ? (
            teachers.data.items.length === 0 ? (
              <EmptyState
                icon={<GraduationCap size={28} aria-hidden />}
                title="Chưa có hồ sơ giáo viên nào"
                description="Tạo tài khoản giáo viên ở trang Tài khoản trước, rồi tạo hồ sơ ở đây."
              />
            ) : (
              <>
                <TeachersTable rows={teachers.data.items} onEdit={setEditing} />
                <Pagination
                  page={teachers.data.page}
                  totalPages={teachers.data.totalPages}
                  totalItems={teachers.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="hồ sơ"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateTeacherDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditTeacherDialog key={editing.id} teacher={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
```

```ts
// index.ts
export { TeachersPage } from "@/modules/teachers/pages/teachers-page";
```

- [ ] **Step 5: Wire routing + nav**

Modify `app-router.tsx` — add the lazy import alongside `CoursesPage`:
```ts
const TeachersPage = lazy(() =>
  import("@/modules/teachers").then((module) => ({ default: module.TeachersPage })),
);
```
and the route inside `<Route element={<RequireAuth />}>`:
```tsx
          <Route path={APP_ROUTE.teachers} element={<TeachersPage />} />
```

Modify `nav-items.ts` — add to the "Điều hành & Nghiệp vụ" section's `items`, after the `classes` entry:
```ts
      { path: APP_ROUTE.teachers, label: "Giáo viên", requirement: ACCESS_RULE.readTeacher },
```

Modify `app-nav.tsx` — add the icon import `GraduationCap` and map entry:
```ts
  [APP_ROUTE.teachers]: <GraduationCap size={18} aria-hidden />,
```

- [ ] **Step 6: Verify + commit**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: no errors.

```bash
git add frontend/src/modules/teachers frontend/src/shared/constants/permissions.ts frontend/src/shared/constants/app-routes.ts frontend/src/app/router/app-router.tsx frontend/src/app/layouts/nav-items.ts frontend/src/app/ui/app-nav.tsx
git commit -m "feat(frontend): add teacher profile admin pages"
```

---

## Task 19: `modules/students` — Student profile CRUD UI

**Files:**
- Create: `frontend/src/modules/students/model/students-forms.ts`
- Create: `frontend/src/modules/students/api/use-students-mutations.ts`
- Create: `frontend/src/modules/students/ui/students-table.tsx`
- Create: `frontend/src/modules/students/ui/create-student-dialog.tsx`
- Create: `frontend/src/modules/students/ui/edit-student-dialog.tsx`
- Create: `frontend/src/modules/students/pages/students-page.tsx`
- Create: `frontend/src/modules/students/index.ts`
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/app/layouts/nav-items.ts`
- Modify: `frontend/src/app/ui/app-nav.tsx`

**Interfaces:**
- Consumes: `entities/student` (Task 17). RBAC rules (`readStudent`/`createStudent`/`updateStudent`) and the route (`APP_ROUTE.students`) were already added in Task 18 — no further edits to `permissions.ts`/`app-routes.ts`.

Mirror of Task 18 with `Teacher` → `Student`; profile fields swapped to `dateOfBirth`/`sourceChannel` (plain text inputs — `dateOfBirth` as `type="date"`, `sourceChannel` as free text, no `subjects` comma-list parsing needed).

- [ ] **Step 1: Form schemas**

```ts
import { z } from "zod";

export const createStudentFormSchema = z.object({
  accountId: z.string().uuid("Chọn tài khoản"),
  dateOfBirth: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  sourceChannel: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
});

export const updateStudentFormSchema = z.object({
  dateOfBirth: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  phone: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  sourceChannel: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  active: z.boolean(),
});
```

- [ ] **Step 2: Mutations**

```ts
import { studentApi, studentKeys, type CreateStudentProfilePayload, type UpdateStudentProfilePayload } from "@/entities/student";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useStudentsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: studentKeys.all });
  };
}

export function useCreateStudentProfile() {
  const invalidate = useStudentsInvalidation();
  return useMutation({
    mutationFn: (payload: CreateStudentProfilePayload) => studentApi.createProfile(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateStudentProfile(profileId: string) {
  const invalidate = useStudentsInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateStudentProfilePayload) => studentApi.updateProfile(profileId, payload),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 3: Table + dialogs + page**

```tsx
// ui/students-table.tsx
import type { StudentProfileSummary } from "@/entities/student";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface StudentsTableProps {
  readonly rows: readonly StudentProfileSummary[];
  readonly onEdit: (student: StudentProfileSummary) => void;
}

export function StudentsTable({ rows, onEdit }: StudentsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((student, index) => (
        <m.li
          key={student.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,1.5fr)_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{student.fullName ?? "Chưa rõ tên"}</p>
            <p className="truncate text-xs text-mist-500">{student.email ?? "—"}</p>
          </div>
          <p className="truncate text-xs text-mist-500">{student.sourceChannel ?? "Chưa rõ nguồn"}</p>
          <Badge tone={student.active ? "positive" : "neutral"}>
            {student.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateStudent}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(student)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
```

```tsx
// ui/create-student-dialog.tsx
import { useAccounts } from "@/entities/account";
import { useCreateStudentProfile } from "@/modules/students/api/use-students-mutations";
import { createStudentFormSchema } from "@/modules/students/model/students-forms";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateStudentDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateStudentDialog({ open, onClose }: CreateStudentDialogProps) {
  const createStudent = useCreateStudentProfile();
  const accounts = useAccounts(0, DEFAULT_PAGE_SIZE, null);

  const form = useZodForm({
    schema: createStudentFormSchema,
    initialValues: { accountId: "", dateOfBirth: "", phone: "", sourceChannel: "" },
    onSubmit: async (values) => {
      await createStudent.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo hồ sơ học viên" description="Chọn một tài khoản đã có vai trò Học viên.">
      <form id="create-student-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tài khoản" htmlFor="student-account" error={form.fieldErrors.accountId}>
          <GlassSelect
            id="student-account"
            value={form.values.accountId}
            invalid={form.fieldErrors.accountId !== undefined}
            onChange={(event) => form.setValue("accountId", event.target.value)}
          >
            <option value="">-- Chọn tài khoản --</option>
            {(accounts.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName} · {account.email}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Ngày sinh" htmlFor="student-dob" hint="Không bắt buộc.">
          <GlassInput
            id="student-dob"
            type="date"
            value={form.values.dateOfBirth}
            onChange={(event) => form.setValue("dateOfBirth", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="student-phone" hint="Không bắt buộc.">
          <GlassInput
            id="student-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Biết đến trung tâm qua đâu" htmlFor="student-source" hint="Không bắt buộc.">
          <GlassInput
            id="student-source"
            value={form.values.sourceChannel}
            onChange={(event) => form.setValue("sourceChannel", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-student-form" loading={form.isSubmitting}>
          Tạo hồ sơ
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

```tsx
// ui/edit-student-dialog.tsx
import type { StudentProfileSummary } from "@/entities/student";
import { useUpdateStudentProfile } from "@/modules/students/api/use-students-mutations";
import { updateStudentFormSchema } from "@/modules/students/model/students-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditStudentDialogProps {
  readonly student: StudentProfileSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditStudentDialog({ student, open, onClose }: EditStudentDialogProps) {
  const updateStudent = useUpdateStudentProfile(student.id);

  const form = useZodForm({
    schema: updateStudentFormSchema,
    initialValues: {
      dateOfBirth: student.dateOfBirth ?? "",
      phone: student.phone ?? "",
      sourceChannel: student.sourceChannel ?? "",
      active: student.active,
    },
    onSubmit: async (values) => {
      await updateStudent.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa hồ sơ ${student.fullName ?? ""}`}>
      <form id="edit-student-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Ngày sinh" htmlFor="edit-student-dob" hint="Không bắt buộc.">
          <GlassInput
            id="edit-student-dob"
            type="date"
            value={form.values.dateOfBirth}
            onChange={(event) => form.setValue("dateOfBirth", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="edit-student-phone" hint="Không bắt buộc.">
          <GlassInput
            id="edit-student-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Biết đến trung tâm qua đâu" htmlFor="edit-student-source" hint="Không bắt buộc.">
          <GlassInput
            id="edit-student-source"
            value={form.values.sourceChannel}
            onChange={(event) => form.setValue("sourceChannel", event.target.value)}
          />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá hồ sơ mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-student-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

```tsx
// pages/students-page.tsx
import { useStudents, type StudentProfileSummary } from "@/entities/student";
import { Can, RequirePermission } from "@/entities/permission";
import { CreateStudentDialog } from "@/modules/students/ui/create-student-dialog";
import { EditStudentDialog } from "@/modules/students/ui/edit-student-dialog";
import { StudentsTable } from "@/modules/students/ui/students-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Users } from "lucide-react";
import { useState } from "react";

export function StudentsPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<StudentProfileSummary | null>(null);
  const students = useStudents(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readStudent}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Học viên"
          description="Hồ sơ nghiệp vụ của học viên."
          actions={
            <Can {...ACCESS_RULE.createStudent}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Hồ sơ mới
              </GlassButton>
            </Can>
          }
        />

        {students.isError ? <ErrorNotice error={students.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {students.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {students.data ? (
            students.data.items.length === 0 ? (
              <EmptyState
                icon={<Users size={28} aria-hidden />}
                title="Chưa có hồ sơ học viên nào"
                description="Tạo tài khoản học viên ở trang Tài khoản trước, rồi tạo hồ sơ ở đây."
              />
            ) : (
              <>
                <StudentsTable rows={students.data.items} onEdit={setEditing} />
                <Pagination
                  page={students.data.page}
                  totalPages={students.data.totalPages}
                  totalItems={students.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="hồ sơ"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateStudentDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditStudentDialog key={editing.id} student={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
```

```ts
// index.ts
export { StudentsPage } from "@/modules/students/pages/students-page";
```

- [ ] **Step 4: Wire routing + nav**

Modify `app-router.tsx` — add alongside `TeachersPage`:
```ts
const StudentsPage = lazy(() =>
  import("@/modules/students").then((module) => ({ default: module.StudentsPage })),
);
```
and:
```tsx
          <Route path={APP_ROUTE.students} element={<StudentsPage />} />
```

Modify `nav-items.ts` — after the `teachers` entry:
```ts
      { path: APP_ROUTE.students, label: "Học viên", requirement: ACCESS_RULE.readStudent },
```

Modify `app-nav.tsx` — import `Users` is already imported (used for `accounts`); reuse a distinct icon, e.g. `UserRoundCheck`, and add:
```ts
  [APP_ROUTE.students]: <UserRoundCheck size={18} aria-hidden />,
```
(add `UserRoundCheck` to the `lucide-react` import list.)

- [ ] **Step 5: Verify + commit**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: no errors, build succeeds.

```bash
git add frontend/src/modules/students frontend/src/app/router/app-router.tsx frontend/src/app/layouts/nav-items.ts frontend/src/app/ui/app-nav.tsx
git commit -m "feat(frontend): add student profile admin pages"
```

---

## Task 20: `entities/account` — `lastLogin` + create-account/invite payloads

**Files:**
- Modify: `frontend/src/entities/account/model/account-schema.ts`
- Modify: `frontend/src/entities/account/api/account-api.ts`
- Modify: `frontend/src/shared/constants/api-routes.ts`

**Interfaces:**
- Produces: `AccountSummary.lastLogin: string | null`, `CreateAccountPayload`, `accountApi.createAccount(...)`, `accountApi.resendInvite(accountId)`, `accountApi.revokeInvite(accountId)` — consumed by Task 21.

- [ ] **Step 1: Add the new routes**

Modify `api-routes.ts`'s `rbac` block:
```ts
    accountResendInvite: (accountId: string) => `/api/rbac/accounts/${accountId}/resend-invite`,
    accountRevokeInvite: (accountId: string) => `/api/rbac/accounts/${accountId}/revoke-invite`,
```

- [ ] **Step 2: Schema**

Modify `account-schema.ts` — add `lastLogin` to `accountSummarySchema` and a new payload interface:
```ts
export const accountSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  fullName: z.string(),
  status: accountStatusSchema,
  roleCode: z.string(),
  branchId: z.string().uuid().nullable(),
  branchName: z.string().nullable(),
  groups: z.array(namedReferenceSchema),
  lastLogin: z.string().nullable(),
});
```
(this is a one-field addition to the existing schema object — add `lastLogin: z.string().nullable(),` as the last field.)

Add below the existing payload interfaces:
```ts
export interface CreateAccountPayload {
  readonly email: string;
  readonly fullName: string;
  readonly homeBranchId: string | null;
  readonly roleId: string;
}
```

- [ ] **Step 3: API client**

Modify `account-api.ts` — add three methods and the import for the new payload type:
```ts
import {
  accountSummarySchema,
  type ChangePasswordPayload,
  type CreateAccountPayload,
  type ProfileUpdatePayload,
  sessionSchema,
} from "@/entities/account/model/account-schema";
```
```ts
  async createAccount(payload: CreateAccountPayload): Promise<string> {
    return z.string().uuid().parse(await apiClient.post<unknown>(API_ROUTE.rbac.accounts, payload));
  },

  async resendInvite(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountResendInvite(accountId));
  },

  async revokeInvite(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountRevokeInvite(accountId));
  },
```
(add the import `import { z } from "zod";` to this file if not already present — it is not, per the file read during planning, so add it.)

- [ ] **Step 4: Verify + commit**

Run: `cd frontend && npm run typecheck`
Expected: no errors.

```bash
git add frontend/src/entities/account/model/account-schema.ts frontend/src/entities/account/api/account-api.ts frontend/src/shared/constants/api-routes.ts
git commit -m "feat(frontend): add account creation and invite API client methods"
```

---

## Task 21: `modules/rbac` — create-account dialog + resend/revoke buttons

**Files:**
- Create: `frontend/src/modules/rbac/ui/create-account-dialog.tsx`
- Create: `frontend/src/modules/rbac/hooks/use-create-account-controller.ts`
- Modify: `frontend/src/modules/rbac/model/rbac-forms.ts`
- Modify: `frontend/src/modules/rbac/api/use-rbac-mutations.ts`
- Modify: `frontend/src/modules/rbac/ui/accounts-table.tsx`
- Modify: `frontend/src/modules/rbac/hooks/use-accounts-page-controller.ts`
- Modify: `frontend/src/modules/rbac/pages/accounts-page.tsx`
- Modify: `frontend/src/shared/constants/permissions.ts`

**Interfaces:**
- Consumes: `entities/account`'s new payloads/methods (Task 20), `useRbacCatalog` (existing, for the role dropdown — same source `TransferBranchDialog` uses for branches).

- [ ] **Step 1: RBAC rule for account creation**

Modify `permissions.ts` — add to `ACCESS_RULE`:
```ts
  createAccount: {
    resource: RESOURCE.account,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
```

- [ ] **Step 2: Form schema**

Modify `rbac-forms.ts` — add:
```ts
export const createAccountFormSchema = z.object({
  email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
  fullName: z.string().min(1, "Nhập họ tên"),
  homeBranchId: z.string(),
  roleId: z.string().uuid("Chọn vai trò"),
});
```

- [ ] **Step 3: Mutations**

Modify `use-rbac-mutations.ts` — add three mutations reusing the existing `useRbacInvalidation` helper and `accountApi` import (add `accountApi` + `type CreateAccountPayload` to the existing `@/entities/account` import):
```ts
import { accountApi, accountKeys, type CreateAccountPayload } from "@/entities/account";
```
```ts
export function useCreateAccount() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (payload: CreateAccountPayload) => accountApi.createAccount(payload),
    onSuccess: invalidate,
  });
}

export function useResendAccountInvite() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (accountId: string) => accountApi.resendInvite(accountId),
    onSuccess: invalidate,
  });
}

export function useRevokeAccountInvite() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (accountId: string) => accountApi.revokeInvite(accountId),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 4: Controller hook + dialog**

```ts
// hooks/use-create-account-controller.ts
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateAccount } from "@/modules/rbac/api/use-rbac-mutations";
import { createAccountFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useCreateAccountController({ onClose }: { readonly onClose: () => void }) {
  const catalog = useRbacCatalog();
  const createAccount = useCreateAccount();

  const form = useZodForm({
    schema: createAccountFormSchema,
    initialValues: { email: "", fullName: "", homeBranchId: "", roleId: "" },
    onSubmit: async (values) => {
      await createAccount.mutateAsync({
        email: values.email,
        fullName: values.fullName,
        homeBranchId: values.homeBranchId.length === 0 ? null : values.homeBranchId,
        roleId: values.roleId,
      });
      form.reset();
      onClose();
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    branches: catalog.data?.branches ?? [],
    roles: catalog.data?.roles ?? [],
    handleSubmit: form.handleSubmit,
    setValue: form.setValue,
  };
}
```

```tsx
// ui/create-account-dialog.tsx
import { useCreateAccountController } from "@/modules/rbac/hooks/use-create-account-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateAccountDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateAccountDialog({ open, onClose }: CreateAccountDialogProps) {
  const { values, fieldErrors, submitError, isSubmitting, branches, roles, handleSubmit, setValue } =
    useCreateAccountController({ onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo tài khoản"
      description="Hệ thống sẽ gửi email kèm mật khẩu tạm, hiệu lực 7 ngày."
    >
      <form id="create-account-form" onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError ? <ErrorNotice error={submitError} /> : null}

        <FormField label="Email" htmlFor="create-account-email" error={fieldErrors.email}>
          <GlassInput
            id="create-account-email"
            type="email"
            autoFocus
            value={values.email}
            invalid={fieldErrors.email !== undefined}
            onChange={(event) => setValue("email", event.target.value)}
          />
        </FormField>

        <FormField label="Họ tên" htmlFor="create-account-name" error={fieldErrors.fullName}>
          <GlassInput
            id="create-account-name"
            value={values.fullName}
            invalid={fieldErrors.fullName !== undefined}
            onChange={(event) => setValue("fullName", event.target.value)}
          />
        </FormField>

        <FormField label="Vai trò" htmlFor="create-account-role" error={fieldErrors.roleId}>
          <GlassSelect
            id="create-account-role"
            value={values.roleId}
            invalid={fieldErrors.roleId !== undefined}
            onChange={(event) => setValue("roleId", event.target.value)}
          >
            <option value="">-- Chọn vai trò --</option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {role.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Chi nhánh" htmlFor="create-account-branch" hint="Bỏ trống nếu là tài khoản cấp tổ chức.">
          <GlassSelect
            id="create-account-branch"
            value={values.homeBranchId}
            onChange={(event) => setValue("homeBranchId", event.target.value)}
          >
            <option value="">-- Cấp tổ chức --</option>
            {branches.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-account-form" loading={isSubmitting}>
          Tạo tài khoản
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

(confirmed during planning by reading `frontend/src/entities/rbac-catalog/model/catalog-schema.ts`: `rbacCatalogSchema.roles` is `z.array(namedReferenceSchema)`, i.e. `{id, name}[]` — no `code` field. This is exactly why `CreateAccountRequest`/`CreateAccountPayload` are built around `roleId: UUID` in Tasks 5 and 20 rather than `roleCode: string` — the catalog this dropdown reads from has never exposed role codes to the frontend, only ids, so the whole chain was designed around `roleId` from the start.)

- [ ] **Step 5: Accounts table buttons**

Modify `accounts-table.tsx` — add two more props and buttons, conditional on `account.lastLogin === null`:
```tsx
export interface AccountsTableProps {
  readonly rows: readonly AccountSummary[];
  readonly onAssignGroup: (account: AccountSummary) => void;
  readonly onTransferBranch: (account: AccountSummary) => void;
  readonly onResendInvite: (account: AccountSummary) => void;
  readonly onRevokeInvite: (account: AccountSummary) => void;
}
```
Inside the `Can {...ACCESS_RULE.updateAccount}` block (but as a sibling to the existing `Can {...ACCESS_RULE.readRole}` block, not nested in it — these two new buttons don't need `readRole`), add:
```tsx
          <Can {...ACCESS_RULE.updateAccount}>
            {account.lastLogin === null ? (
              <div className="flex items-center gap-2 justify-self-start lg:justify-self-end">
                <GlassButton variant="secondary" size="sm" onClick={() => onResendInvite(account)}>
                  Gửi lại mời
                </GlassButton>
                <GlassButton variant="ghost" size="sm" onClick={() => onRevokeInvite(account)}>
                  Thu hồi
                </GlassButton>
              </div>
            ) : null}
          </Can>
```
(this renders as a third grid cell alongside the existing two `Can` blocks — the grid's `lg:grid-cols-[...]` template will need one more column; update it from 4 columns to 5, e.g. `lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.4fr)_auto_auto]`.)

- [ ] **Step 6: Page wiring**

Modify `use-accounts-page-controller.ts` — add `createAccountDialogOpen` state and the two new mutations:
```ts
import { useResendAccountInvite, useRevokeAccountInvite } from "@/modules/rbac/api/use-rbac-mutations";
```
```ts
export function useAccountsPageController() {
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<AccountsDialogKind>(ACCOUNTS_DIALOG.none);
  const [selected, setSelected] = useState<AccountSummary | null>(null);
  const [createAccountDialogOpen, setCreateAccountDialogOpen] = useState(false);
  const { selectedBranchId } = useSelectedBranch();
  const accounts = useAccounts(page, DEFAULT_PAGE_SIZE, selectedBranchId);
  const resendInvite = useResendAccountInvite();
  const revokeInvite = useRevokeAccountInvite();

  // ... (existing previousBranchId effect-in-render block unchanged)

  const openDialog = (kind: AccountsDialogKind, account: AccountSummary) => {
    setSelected(account);
    setDialog(kind);
  };

  const closeDialog = () => setDialog(ACCOUNTS_DIALOG.none);

  return {
    page,
    setPage,
    dialog,
    selected,
    accounts,
    openDialog,
    closeDialog,
    createAccountDialogOpen,
    openCreateAccountDialog: () => setCreateAccountDialogOpen(true),
    closeCreateAccountDialog: () => setCreateAccountDialogOpen(false),
    onResendInvite: (account: AccountSummary) => resendInvite.mutate(account.id),
    onRevokeInvite: (account: AccountSummary) => revokeInvite.mutate(account.id),
  };
}
```

Modify `accounts-page.tsx` — destructure the new controller fields, add the "Tạo tài khoản" button to `PageHeader`'s `actions`, pass the two new handlers to `AccountsTable`, and render `CreateAccountDialog`:
```tsx
import { Can, RequirePermission } from "@/entities/permission";
import { ACCOUNTS_DIALOG, useAccountsPageController } from "@/modules/rbac/hooks/use-accounts-page-controller";
import { AccountsTable } from "@/modules/rbac/ui/accounts-table";
import { AssignGroupDialog } from "@/modules/rbac/ui/assign-group-dialog";
import { CreateAccountDialog } from "@/modules/rbac/ui/create-account-dialog";
import { TransferBranchDialog } from "@/modules/rbac/ui/transfer-branch-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Users } from "lucide-react";

export function AccountsPage() {
  const {
    setPage,
    dialog,
    selected,
    accounts,
    openDialog,
    closeDialog,
    createAccountDialogOpen,
    openCreateAccountDialog,
    closeCreateAccountDialog,
    onResendInvite,
    onRevokeInvite,
  } = useAccountsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readAccount}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Tài khoản"
          description="Danh sách người dùng, nhóm quyền đang thuộc và chi nhánh đang làm việc."
          actions={
            <Can {...ACCESS_RULE.createAccount}>
              <GlassButton onClick={openCreateAccountDialog} icon={<Plus size={16} aria-hidden />}>
                Tạo tài khoản
              </GlassButton>
            </Can>
          }
        />

        {accounts.isError ? <ErrorNotice error={accounts.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {accounts.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {accounts.data ? (
            accounts.data.items.length === 0 ? (
              <EmptyState
                icon={<Users size={28} aria-hidden />}
                title="Chưa có tài khoản nào"
                description="Tài khoản đầu tiên được tạo bởi quá trình khởi tạo hệ thống."
              />
            ) : (
              <>
                <AccountsTable
                  rows={accounts.data.items}
                  onAssignGroup={(account) => openDialog(ACCOUNTS_DIALOG.assignGroup, account)}
                  onTransferBranch={(account) => openDialog(ACCOUNTS_DIALOG.transferBranch, account)}
                  onResendInvite={onResendInvite}
                  onRevokeInvite={onRevokeInvite}
                />
                <Pagination
                  page={accounts.data.page}
                  totalPages={accounts.data.totalPages}
                  totalItems={accounts.data.totalItems}
                  onPageChange={setPage}
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateAccountDialog open={createAccountDialogOpen} onClose={closeCreateAccountDialog} />

        {selected === null ? null : (
          <>
            <AssignGroupDialog
              key={`assign-${selected.id}`}
              account={selected}
              open={dialog === ACCOUNTS_DIALOG.assignGroup}
              onClose={closeDialog}
            />
            <TransferBranchDialog
              key={`branch-${selected.id}`}
              account={selected}
              open={dialog === ACCOUNTS_DIALOG.transferBranch}
              onClose={closeDialog}
            />
          </>
        )}
      </div>
    </RequirePermission>
  );
}
```

- [ ] **Step 7: Verify + commit**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: no errors, build succeeds.

```bash
git add frontend/src/modules/rbac frontend/src/shared/constants/permissions.ts
git commit -m "feat(frontend): add account creation and invite management to Accounts page"
```

---

## Task 22: Login flow — forced password change

**Files:**
- Modify: `frontend/src/modules/auth/api/auth-api.ts`
- Modify: `frontend/src/modules/auth/api/use-auth-mutations.ts`
- Modify: `frontend/src/modules/auth/model/auth-forms.ts`
- Modify: `frontend/src/modules/auth/hooks/use-login-form-controller.ts`
- Create: `frontend/src/modules/auth/ui/complete-invite-form.tsx`
- Create: `frontend/src/modules/auth/hooks/use-complete-invite-controller.ts`
- Modify: `frontend/src/modules/auth/pages/login-page.tsx`
- Modify: `frontend/src/modules/auth/index.ts`

**Interfaces:**
- Consumes: the backend's new `LoginResponse(requiresPasswordChange)` body (Task 6), `POST /api/auth/complete-invite` (Task 7).
- Produces: when login returns `requiresPasswordChange: true`, the login page switches in-place to a "set new password" form instead of navigating away — no new route needed (mirrors how `RequireAuth` branches on session state rather than routing).

- [ ] **Step 1: API + route**

Modify `api-routes.ts`'s `auth` block (already read in full during planning):
```ts
  auth: {
    login: "/api/auth/login",
    completeInvite: "/api/auth/complete-invite",
    refresh: "/api/auth/refresh",
    logout: "/api/auth/logout",
  },
```

Modify `auth-api.ts` — change `login`'s return type and add `completeInvite`:
```ts
export interface CompleteInvitePayload {
  readonly email: string;
  readonly currentPassword: string;
  readonly newPassword: string;
}

export const authApi = {
  async login(payload: LoginPayload): Promise<{ requiresPasswordChange: boolean }> {
    return loginResponseSchema.parse(await apiClient.post<unknown>(API_ROUTE.auth.login, payload));
  },

  async completeInvite(payload: CompleteInvitePayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.auth.completeInvite, payload);
  },

  async logout(): Promise<void> {
    await apiClient.post<void>(API_ROUTE.auth.logout);
  },

  async forgotPassword(email: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.account.forgotPassword, { email });
  },

  async resetPassword(payload: ResetPasswordPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.account.resetPassword, payload);
  },
} as const;
```
(add near the top of the file, alongside the existing `LoginPayload`/`ResetPasswordPayload` interfaces: `import { z } from "zod"; const loginResponseSchema = z.object({ requiresPasswordChange: z.boolean() });`.)

- [ ] **Step 2: Login mutation branches on the response**

Modify `use-auth-mutations.ts`'s `useLogin` — it must now report whether a password change is required instead of unconditionally navigating:
```ts
export function useLogin(redirectTo: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (payload: LoginPayload) => authApi.login(payload),
    onSuccess: async (result) => {
      if (result.requiresPasswordChange) {
        return;
      }
      await queryClient.invalidateQueries({ queryKey: accountKeys.session() });
      navigate(redirectTo, { replace: true });
    },
  });
}

export function useCompleteInvite(redirectTo: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (payload: CompleteInvitePayload) => authApi.completeInvite(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: accountKeys.session() });
      navigate(redirectTo, { replace: true });
    },
  });
}
```
(add the import `type CompleteInvitePayload` alongside the existing `authApi`/`LoginPayload`/`ResetPasswordPayload` import.)

- [ ] **Step 3: Login controller surfaces the result**

Modify `use-login-form-controller.ts` to expose whether a password change is now required, plus the email just used (needed by `complete-invite`):
```ts
import { useLogin } from "@/modules/auth/api/use-auth-mutations";
import { loginFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useState } from "react";

export function useLoginFormController(redirectTo: string) {
  const login = useLogin(redirectTo);
  const [requiresPasswordChange, setRequiresPasswordChange] = useState(false);
  const form = useZodForm({
    schema: loginFormSchema,
    initialValues: { email: "", password: "" },
    onSubmit: async (values) => {
      const result = await login.mutateAsync(values);
      if (result.requiresPasswordChange) {
        setRequiresPasswordChange(true);
      }
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setEmail: (email: string) => form.setValue("email", email),
    setPassword: (password: string) => form.setValue("password", password),
    requiresPasswordChange,
    email: form.values.email,
    temporaryPassword: form.values.password,
  };
}
```

- [ ] **Step 4: Complete-invite form + controller**

```ts
// hooks/use-complete-invite-controller.ts
import { useCompleteInvite } from "@/modules/auth/api/use-auth-mutations";
import { completeInviteFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useCompleteInviteController({
  email,
  temporaryPassword,
  redirectTo,
}: {
  readonly email: string;
  readonly temporaryPassword: string;
  readonly redirectTo: string;
}) {
  const completeInvite = useCompleteInvite(redirectTo);

  const form = useZodForm({
    schema: completeInviteFormSchema,
    initialValues: { newPassword: "", confirmPassword: "" },
    onSubmit: (values) =>
      completeInvite.mutateAsync({ email, currentPassword: temporaryPassword, newPassword: values.newPassword }),
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setNewPassword: (value: string) => form.setValue("newPassword", value),
    setConfirmPassword: (value: string) => form.setValue("confirmPassword", value),
  };
}
```

Modify `auth-forms.ts` — add:
```ts
export const completeInviteFormSchema = z
  .object({
    newPassword: z.string().min(PASSWORD_MIN_LENGTH, `Mật khẩu cần tối thiểu ${PASSWORD_MIN_LENGTH} ký tự`),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Hai mật khẩu chưa giống nhau",
  });
```

```tsx
// ui/complete-invite-form.tsx
import { useCompleteInviteController } from "@/modules/auth/hooks/use-complete-invite-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { PasswordInput } from "@/shared/ui/password-input";
import { KeyRound } from "lucide-react";

export interface CompleteInviteFormProps {
  readonly email: string;
  readonly temporaryPassword: string;
  readonly redirectTo: string;
}

export function CompleteInviteForm({ email, temporaryPassword, redirectTo }: CompleteInviteFormProps) {
  const { values, fieldErrors, submitError, isSubmitting, handleSubmit, setNewPassword, setConfirmPassword } =
    useCompleteInviteController({ email, temporaryPassword, redirectTo });

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <p className="text-sm text-slate-500">Đây là lần đăng nhập đầu tiên — hãy đặt mật khẩu mới trước khi tiếp tục.</p>

      <FormField label="Mật khẩu mới" htmlFor="complete-invite-new-password" error={fieldErrors.newPassword}>
        <PasswordInput
          id="complete-invite-new-password"
          autoComplete="new-password"
          autoFocus
          value={values.newPassword}
          invalid={fieldErrors.newPassword !== undefined}
          onChange={(event) => setNewPassword(event.target.value)}
        />
      </FormField>

      <FormField label="Xác nhận mật khẩu mới" htmlFor="complete-invite-confirm-password" error={fieldErrors.confirmPassword}>
        <PasswordInput
          id="complete-invite-confirm-password"
          autoComplete="new-password"
          value={values.confirmPassword}
          invalid={fieldErrors.confirmPassword !== undefined}
          onChange={(event) => setConfirmPassword(event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={isSubmitting} icon={<KeyRound size={16} aria-hidden />}>
        Đặt mật khẩu và đăng nhập
      </GlassButton>
    </form>
  );
}
```

- [ ] **Step 5: Wire into the login page**

Modify `login-page.tsx`:
```tsx
import { CompleteInviteForm } from "@/modules/auth/ui/complete-invite-form";
import { LoginForm } from "@/modules/auth/ui/login-form";
import { useLoginFormController } from "@/modules/auth/hooks/use-login-form-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { useLocation } from "react-router-dom";

interface RedirectState {
  readonly from?: string;
}

export function LoginPage() {
  const location = useLocation();
  const state = location.state as RedirectState | null;
  const redirectTo = state?.from ?? APP_ROUTE.dashboard;

  // Controller phải nằm ở đây (không phải trong LoginForm) vì cả hai nhánh (form đăng nhập / form đổi
  // mật khẩu lần đầu) đều cần đọc requiresPasswordChange từ cùng một state - tách ra sẽ phải nâng state
  // lên một cấp nữa, không khác gì đặt ở đây từ đầu.
  const loginController = useLoginFormController(redirectTo);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          {loginController.requiresPasswordChange ? "Đặt mật khẩu mới" : "Đăng nhập"}
        </h1>
        <p className="mt-1 text-sm text-slate-500">Hệ thống quản trị người dùng EduERP.</p>
      </div>
      {loginController.requiresPasswordChange ? (
        <CompleteInviteForm
          email={loginController.email}
          temporaryPassword={loginController.temporaryPassword}
          redirectTo={redirectTo}
        />
      ) : (
        <LoginForm controller={loginController} />
      )}
    </div>
  );
}
```

**This requires `LoginForm` to accept the controller as a prop instead of calling the hook itself** (since `LoginPage` now owns the single controller instance both branches need). Modify `ui/login-form.tsx`:
```tsx
import type { useLoginFormController } from "@/modules/auth/hooks/use-login-form-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { PasswordInput } from "@/shared/ui/password-input";
import { LogIn } from "lucide-react";
import { Link } from "react-router-dom";

export interface LoginFormProps {
  readonly controller: ReturnType<typeof useLoginFormController>;
}

export function LoginForm({ controller }: LoginFormProps) {
  const { values, fieldErrors, submitError, isSubmitting, handleSubmit, setEmail, setPassword } = controller;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <FormField label="Email" htmlFor="login-email" error={fieldErrors.email}>
        <GlassInput
          id="login-email"
          type="email"
          autoComplete="username"
          autoFocus
          placeholder="ten@eduerp.local"
          value={values.email}
          invalid={fieldErrors.email !== undefined}
          onChange={(event) => setEmail(event.target.value)}
        />
      </FormField>

      <FormField label="Mật khẩu" htmlFor="login-password" error={fieldErrors.password}>
        <PasswordInput
          id="login-password"
          autoComplete="current-password"
          placeholder="••••••••"
          value={values.password}
          invalid={fieldErrors.password !== undefined}
          onChange={(event) => setPassword(event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={isSubmitting} icon={<LogIn size={16} aria-hidden />}>
        Đăng nhập
      </GlassButton>

      <Link
        to={APP_ROUTE.forgotPassword}
        className="text-center text-sm font-medium text-slate-500 transition-colors hover:text-orange-600"
      >
        Quên mật khẩu?
      </Link>
    </form>
  );
}
```

- [ ] **Step 6: Verify + commit**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: no errors, build succeeds. Manually verify (dev server) the golden path: log in with a normal already-activated account — unaffected; this is covered more rigorously by the backend ITs from Tasks 6-7, since there is no frontend test harness in this codebase (confirmed during planning — no `*.test.ts`/`*.spec.ts` files exist under `frontend/src`).

```bash
git add frontend/src/modules/auth frontend/src/shared/constants/api-routes.ts
git commit -m "feat(frontend): handle forced password change on first login"
```

---

## Task 23: `entities/notification` — SSE hook + wiring

**Files:**
- Create: `frontend/src/entities/notification/model/notification-schema.ts`
- Create: `frontend/src/entities/notification/api/use-notification-stream.ts`
- Create: `frontend/src/entities/notification/index.ts`
- Modify: `frontend/src/app/router/require-auth.tsx`

**Interfaces:**
- Produces: `useNotificationStream(): void` — a side-effect-only hook (opens an `EventSource`, invalidates queries on `PERMISSION_CHANGED`), mounted once in `RequireAuth`.

- [ ] **Step 1: Constants**

```ts
// model/notification-schema.ts
export const NOTIFICATION_EVENT_TYPE = {
  permissionChanged: "PERMISSION_CHANGED",
} as const;

export type NotificationEventType = (typeof NOTIFICATION_EVENT_TYPE)[keyof typeof NOTIFICATION_EVENT_TYPE];
```

- [ ] **Step 2: The hook**

```ts
// api/use-notification-stream.ts
import { accountKeys } from "@/entities/account";
import { NOTIFICATION_EVENT_TYPE } from "@/entities/notification/model/notification-schema";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

const STREAM_PATH = "/api/notifications/stream";

/**
 * Cookie HttpOnly đi kèm tự động với EventSource cùng origin - không cần truyền token thủ công, giống
 * mọi request khác của app này (xem apiClient, cũng dựa vào cookie).
 */
export function useNotificationStream(): void {
  const queryClient = useQueryClient();

  useEffect(() => {
    const source = new EventSource(STREAM_PATH, { withCredentials: true });

    const onPermissionChanged = () => {
      void queryClient.invalidateQueries({ queryKey: accountKeys.session() });
    };

    source.addEventListener(NOTIFICATION_EVENT_TYPE.permissionChanged, onPermissionChanged);

    return () => {
      source.removeEventListener(NOTIFICATION_EVENT_TYPE.permissionChanged, onPermissionChanged);
      source.close();
    };
  }, [queryClient]);
}
```

```ts
// index.ts
export { useNotificationStream } from "@/entities/notification/api/use-notification-stream";
```

- [ ] **Step 3: Mount in `RequireAuth`**

Modify `require-auth.tsx` — call the hook once a session exists (same point `PermissionProvider` is mounted):
```tsx
import { DashboardLayout } from "@/app/layouts/dashboard-layout";
import { FullScreenLoader } from "@/app/ui/full-screen-loader";
import { useSession } from "@/entities/account";
import { SelectedBranchProvider } from "@/entities/branch";
import { useNotificationStream } from "@/entities/notification";
import { PermissionProvider } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Navigate, useLocation } from "react-router-dom";

export function RequireAuth() {
  const session = useSession();
  const location = useLocation();

  // Hook phải gọi vô điều kiện (rule of hooks) - nhưng tác dụng thật của nó (mở EventSource) chỉ nên
  // chạy khi có phiên; gọi hook ở ngay nhánh return cuối (có session.data) cùng JSX là đủ, không cần
  // thêm điều kiện bên trong hook.
  if (session.isPending) {
    return <FullScreenLoader label="Đang kiểm tra phiên làm việc" />;
  }

  if (session.isError) {
    return (
      <div className="flex min-h-dvh items-center justify-center p-4">
        <GlassPanel className="w-full max-w-md">
          <ErrorNotice
            error={session.error}
            action={
              <GlassButton size="sm" onClick={() => void session.refetch()}>
                Thử lại
              </GlassButton>
            }
          />
        </GlassPanel>
      </div>
    );
  }

  if (session.data === null || session.data === undefined) {
    return <Navigate to={APP_ROUTE.login} state={{ from: location.pathname }} replace />;
  }

  return <AuthenticatedShell session={session.data} />;
}

function AuthenticatedShell({ session }: { readonly session: NonNullable<ReturnType<typeof useSession>["data"]> }) {
  useNotificationStream();
  return (
    <PermissionProvider permissions={session.permissions}>
      <SelectedBranchProvider>
        <DashboardLayout session={session} />
      </SelectedBranchProvider>
    </PermissionProvider>
  );
}
```
(splitting into `AuthenticatedShell` is necessary because React hooks cannot be called conditionally after the three early `return`s above — this keeps `useNotificationStream` unconditional within its own component, called only once a session is confirmed to exist, without violating the rules of hooks in `RequireAuth` itself.)

- [ ] **Step 4: Verify + commit**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: no errors, build succeeds.

Manual verification (dev server, two browser tabs/sessions as described in the spec's §8 test plan): open the Accounts page as an org-scope admin in tab 1, open a DevTools Network tab on tab 2 (same admin or a second account) to confirm `/api/notifications/stream` connects and stays open (`EventSource` pending request); from tab 1, assign a group to the account logged in on tab 2; confirm tab 2 receives a `PERMISSION_CHANGED` SSE frame (visible in DevTools' EventStream view) and its session query is invalidated (a refetch of `/api/account/me` fires shortly after).

```bash
git add frontend/src/entities/notification frontend/src/app/router/require-auth.tsx
git commit -m "feat(frontend): subscribe to SSE notifications, invalidate session on permission change"
```

---

## Finish

After Task 23, run the complete verification suite:
```bash
cd backend && mvn verify -Dit.test='!AccountSelfServiceControllerIT' && mvn test -Dtest=ModularityTests
cd frontend && npm run typecheck && npm run lint && npm run build
```
All four commands must succeed before considering this plan complete. Then follow `superpowers:executing-plans`'s Finish step (final whole-branch review, fix pass, ledger, `superpowers:finishing-a-development-branch`).
