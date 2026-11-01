import { useSubmitConsultation } from "@/modules/landing/api/use-landing-overview";
import type { ConsultationFormData } from "@/modules/landing/model/landing-schema";
import { useState, type ChangeEvent, type FormEvent } from "react";

/** Điền sẵn form tư vấn từ nơi khách bấm: một lớp, một chi nhánh, hoặc gói đang xem ở công cụ dự toán. */
export interface ConsultPrefill {
  readonly courseId?: string;
  readonly branchId?: string;
  readonly notes?: string;
}

export interface ConsultationFormErrors {
  readonly fullName?: string;
  readonly phone?: string;
  readonly submit?: string;
}

const INITIAL_FORM: ConsultationFormData = {
  fullName: "",
  phone: "",
  email: "",
  targetCourseId: "",
  targetBranchId: "",
  notes: "",
};

function resolvePhoneError(phone: string): string | undefined {
  if (phone.length === 0) {
    return "Vui lòng nhập số điện thoại liên hệ";
  }
  if (!/^[0-9+ ]{9,13}$/.test(phone)) {
    return "Số điện thoại gồm 9–13 chữ số";
  }
  return undefined;
}

function validateConsultation(data: ConsultationFormData): ConsultationFormErrors {
  const fullName = data.fullName.trim();
  const phone = data.phone.trim();

  return {
    fullName: fullName.length === 0 ? "Vui lòng nhập họ và tên của bạn" : undefined,
    phone: resolvePhoneError(phone),
  };
}

function toNullableTrimmed(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function useConsultationForm() {
  const submitMutation = useSubmitConsultation();
  const [formData, setFormData] = useState<ConsultationFormData>(INITIAL_FORM);
  const [errors, setErrors] = useState<ConsultationFormErrors>({});
  const [isSuccessModalOpen, setIsSuccessModalOpen] = useState(false);

  const handleFieldChange = (
    event: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>,
  ) => {
    const { name, value } = event.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (name === "fullName" || name === "phone") {
      setErrors((prev) => ({ ...prev, [name]: undefined, submit: undefined }));
    }
  };

  const prefillCourseOrBranch = (options: ConsultPrefill) => {
    setFormData((prev) => ({
      ...prev,
      targetCourseId: options.courseId ?? prev.targetCourseId,
      targetBranchId: options.branchId ?? prev.targetBranchId,
      notes: options.notes ?? prev.notes,
    }));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validation = validateConsultation(formData);
    if (validation.fullName !== undefined || validation.phone !== undefined) {
      setErrors(validation);
      return;
    }

    submitMutation.mutate(
      {
        fullName: formData.fullName.trim(),
        phone: formData.phone.trim(),
        email: toNullableTrimmed(formData.email),
        targetCourseId: toNullableTrimmed(formData.targetCourseId),
        targetBranchId: toNullableTrimmed(formData.targetBranchId),
        notes: toNullableTrimmed(formData.notes),
      },
      {
        onSuccess: () => {
          setIsSuccessModalOpen(true);
        },
        onError: () => {
          setErrors({
            submit: "Không thể gửi đăng ký tư vấn lúc này. Vui lòng thử lại sau.",
          });
        },
      },
    );
  };

  const handleCloseModal = () => {
    setIsSuccessModalOpen(false);
    setFormData(INITIAL_FORM);
  };

  return {
    formData,
    errors,
    isSubmitting: submitMutation.isPending,
    isSuccessModalOpen,
    onChange: handleFieldChange,
    onPrefill: prefillCourseOrBranch,
    onSubmit: handleSubmit,
    onCloseSuccessModal: handleCloseModal,
  };
}
