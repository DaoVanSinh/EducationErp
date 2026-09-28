import { useSession } from "@/entities/account";
import { ChangePasswordForm } from "@/modules/profile/ui/change-password-form";
import { GrantedPermissionList } from "@/modules/profile/ui/granted-permission-list";
import { ProfileForm } from "@/modules/profile/ui/profile-form";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";

export function ProfilePage() {
  const session = useSession();

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Hồ sơ của tôi" description="Thông tin hiển thị, mật khẩu và quyền hiện có." />

      {session.data ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="flex flex-col gap-4">
            <ProfileForm session={session.data} />
            <GrantedPermissionList />
          </div>
          <ChangePasswordForm />
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-80" />
          <Skeleton className="h-80" />
        </div>
      )}
    </div>
  );
}
