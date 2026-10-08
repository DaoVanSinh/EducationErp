import { MIN_COMBO_ENROLLMENTS } from "@/entities/billing";
import { useCreateComboDiscountTier } from "@/modules/billing/api/use-combo-discount-tier-mutations";
import { comboDiscountTierFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ComboDiscountTierDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function ComboDiscountTierDialog({ open, onClose }: ComboDiscountTierDialogProps) {
  const createTier = useCreateComboDiscountTier();

  const form = useZodForm({
    schema: comboDiscountTierFormSchema,
    initialValues: { minCourseCount: "", discountPercent: "" },
    onSubmit: async (values) => {
      await createTier.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thêm bậc giảm giá"
      description="Combo được áp bậc có mốc số khoá cao nhất mà nó đạt được. Mỗi mốc chỉ có một bậc."
    >
      <form id="combo-tier-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Số khoá tối thiểu"
          htmlFor="tier-min-course-count"
          hint={`Combo tối thiểu ${MIN_COMBO_ENROLLMENTS} khoá, nên mốc nhỏ nhất có nghĩa là ${MIN_COMBO_ENROLLMENTS}.`}
          error={form.fieldErrors.minCourseCount}
        >
          <GlassInput
            id="tier-min-course-count"
            type="number"
            min={MIN_COMBO_ENROLLMENTS}
            step={1}
            autoFocus
            value={form.values.minCourseCount}
            invalid={form.fieldErrors.minCourseCount !== undefined}
            onChange={(event) => form.setValue("minCourseCount", event.target.value)}
          />
        </FormField>

        <FormField label="% giảm" htmlFor="tier-discount-percent" error={form.fieldErrors.discountPercent}>
          <GlassInput
            id="tier-discount-percent"
            type="number"
            min={0}
            max={100}
            step={0.5}
            value={form.values.discountPercent}
            invalid={form.fieldErrors.discountPercent !== undefined}
            onChange={(event) => form.setValue("discountPercent", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="combo-tier-form" loading={form.isSubmitting}>
          Thêm bậc
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
