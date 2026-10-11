import { useSession } from "@/entities/account";
import { useProfilePageController, type ProfileTab } from "@/modules/profile/hooks/use-profile-page-controller";
import { ChangePasswordForm } from "@/modules/profile/ui/change-password-form";
import { GrantedPermissionList } from "@/modules/profile/ui/granted-permission-list";
import { MyContractsList } from "@/modules/profile/ui/my-contracts-list";
import { MyPayslipsList } from "@/modules/profile/ui/my-payslips-list";
import { ProfileForm } from "@/modules/profile/ui/profile-form";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { SegmentedTabs, type SegmentedTab } from "@/shared/ui/segmented-tabs";
import { Skeleton } from "@/shared/ui/skeleton";
import { FileSignature, KeyRound, ShieldCheck, UserCircle, Wallet } from "lucide-react";

const TABS: readonly SegmentedTab<ProfileTab>[] = [
  { key: "profile", label: "Hồ sơ", icon: <UserCircle size={16} aria-hidden /> },
  { key: "security", label: "Bảo mật", icon: <KeyRound size={16} aria-hidden /> },
  { key: "payslips", label: "Lương", icon: <Wallet size={16} aria-hidden /> },
  { key: "contracts", label: "Hợp đồng", icon: <FileSignature size={16} aria-hidden /> },
  { key: "permissions", label: "Quyền của tôi", icon: <ShieldCheck size={16} aria-hidden /> },
];

export function ProfilePage() {
  const session = useSession();
  const controller = useProfilePageController();

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Hồ sơ của tôi" description="Thông tin hiển thị, mật khẩu, lương, hợp đồng và quyền hiện có." />

      {session.data ? (
        <>
          <SegmentedTabs tabs={TABS} activeTab={controller.activeTab} onTabChange={controller.setActiveTab} />
          <ProfileTabContent activeTab={controller.activeTab} session={session.data} />
        </>
      ) : (
        <Skeleton className="h-80" />
      )}
    </div>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ProfileTabContent({
  activeTab,
  session,
}: {
  readonly activeTab: ProfileTab;
  readonly session: NonNullable<ReturnType<typeof useSession>["data"]>;
}) {
  if (activeTab === "security") {
    return <ChangePasswordForm />;
  }
  if (activeTab === "payslips") {
    return (
      <GlassPanel>
        <MyPayslipsList />
      </GlassPanel>
    );
  }
  if (activeTab === "contracts") {
    return (
      <GlassPanel>
        <MyContractsList />
      </GlassPanel>
    );
  }
  if (activeTab === "permissions") {
    return <GrantedPermissionList />;
  }
  return <ProfileForm session={session} />;
}
