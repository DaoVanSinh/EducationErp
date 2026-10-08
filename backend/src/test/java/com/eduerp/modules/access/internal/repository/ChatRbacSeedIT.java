package com.eduerp.modules.access.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mirror {@code AcademicRbacSeedIT}. Đây là lần ĐẦU TIÊN role STUDENT được cấp quyền cho một resource
 * ngoài {@code ACCOUNT} tự phục vụ, nên test này chốt ba điều cùng lúc: admin có CHAT ở
 * ORGANIZATION, STUDENT có CHAT:CREATE ở PERSONAL, và TEACHER KHÔNG có gì.
 */
@Testcontainers
@DataJpaTest
class ChatRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    private List<String> permissionKeysOf(String roleCode) {
        return roles.findByCode(roleCode).orElseThrow()
                .getPermissionGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(item -> item.getPermission().getResource() + ":" + item.getPermission().getAction() + ":"
                        + item.getScope().name())
                .toList();
    }

    private static String chatKey(String scope) {
        return AccessConstants.Resources.CHAT + ":" + AccessConstants.Actions.CREATE + ":" + scope;
    }

    @Test
    void adminHasOrganizationScopedChatPermission() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.ADMIN))
                .contains(chatKey(AccessConstants.ScopeNames.ORGANIZATION));
    }

    /**
     * Spec mục 1: học viên là đối tượng chính. Scope PERSONAL là đủ vì
     * {@code AccessRules.CREATE_CHAT} cố ý KHÔNG mang marker scope tối thiểu - nếu ai đó thêm marker
     * đó vào thì test này vẫn xanh nhưng {@code ChatAdminControllerIT} sẽ đỏ, nên hai test phải đọc
     * cùng nhau.
     */
    @Test
    void studentHasPersonalScopedChatCreate() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT))
                .contains(chatKey(AccessConstants.PermissionScope.PERSONAL.name()));
    }

    /**
     * Review Focus #4: V5 gắn group "Tự phục vụ cơ bản" ('...0002') cho CẢ TEACHER và STUDENT, nên
     * nếu V27 nhét CHAT vào group đó thì giáo viên cũng được cấp - trái spec mục 1 ("Giáo viên KHÔNG
     * được cấp quyền"). Test này biến sai sót đó thành build đỏ.
     */
    @Test
    void teacherGetsNoChatPermissionAtAll() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.CHAT + ":"));
    }

    /** Học viên chỉ được CREATE, không được một action nào khác trên CHAT - không có endpoint nào dùng
     * chúng, và một quyền không ai dùng là một quyền sẽ bị dùng sai về sau. */
    @Test
    void studentGetsOnlyTheCreateActionOnChat() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT))
                .filteredOn(key -> key.startsWith(AccessConstants.Resources.CHAT + ":"))
                .containsExactly(chatKey(AccessConstants.PermissionScope.PERSONAL.name()));
    }

    /** Quyền GRADE của Phân hệ 4 và quyền tự phục vụ sẵn có không được mất đi khi thêm group mới. */
    @Test
    void keepsTheExistingPermissionsOfEveryRole() {
        var personalAccountUpdate = AccessConstants.Resources.ACCOUNT + ":"
                + AccessConstants.Actions.UPDATE + ":" + AccessConstants.PermissionScope.PERSONAL.name();
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).contains(personalAccountUpdate);
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(personalAccountUpdate);
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(
                AccessConstants.Resources.GRADE + ":" + AccessConstants.Actions.UPDATE + ":"
                        + AccessConstants.PermissionScope.PERSONAL.name());
    }

    /** Học viên vẫn KHÔNG được xem điểm (Global Constraint của Phân hệ 4) - thêm quyền chat không
     * được kéo theo một quyền nào khác. */
    @Test
    void addingChatDidNotAccidentallyGiveTheStudentAnyGradePermission() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":"));
    }
}
