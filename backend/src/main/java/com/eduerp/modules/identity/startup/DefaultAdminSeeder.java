package com.eduerp.modules.identity.startup;

import com.eduerp.modules.identity.usecase.SeedDefaultAdmin;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Chạy mỗi lần khởi động chứ không chỉ lần đầu: nếu tài khoản Admin cuối cùng bị vô hiệu hoá hoặc
 * xoá mất thì hệ thống tự phục hồi một đường vào, thay vì phải sửa database bằng tay.
 */
@Component
class DefaultAdminSeeder implements ApplicationRunner {

    private final SeedDefaultAdmin seedDefaultAdmin;

    DefaultAdminSeeder(SeedDefaultAdmin seedDefaultAdmin) {
        this.seedDefaultAdmin = seedDefaultAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDefaultAdmin.execute();
    }
}
