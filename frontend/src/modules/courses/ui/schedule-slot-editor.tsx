import { DAY_OF_WEEK, DAY_OF_WEEK_LABEL, type WeeklyScheduleSlot } from "@/entities/class";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassSelect } from "@/shared/ui/glass-select";
import { Plus, X } from "lucide-react";

export interface ScheduleSlotEditorProps {
  readonly slots: readonly WeeklyScheduleSlot[];
  // Mutable, không readonly: nơi gọi truyền thẳng vào form.setValue("schedule", ...), mà kiểu trường
  // đó do Zod suy ra từ schema (z.array(...) luôn là mảng mutable, không phải readonly).
  readonly onChange: (slots: WeeklyScheduleSlot[]) => void;
}

const DAY_OPTIONS = Object.entries(DAY_OF_WEEK_LABEL) as [WeeklyScheduleSlot["dayOfWeek"], string][];

export function ScheduleSlotEditor({ slots, onChange }: ScheduleSlotEditorProps) {
  const addSlot = () => {
    onChange([...slots, { dayOfWeek: DAY_OF_WEEK.mon, startTime: "18:00", endTime: "20:00" }]);
  };

  const updateSlot = (index: number, patch: Partial<WeeklyScheduleSlot>) => {
    onChange(slots.map((slot, i) => (i === index ? { ...slot, ...patch } : slot)));
  };

  const removeSlot = (index: number) => {
    onChange(slots.filter((_, i) => i !== index));
  };

  return (
    <div className="flex flex-col gap-2">
      {slots.map((slot, index) => (
        <div key={index} className="flex items-center gap-2">
          {/* Ô nhập luôn w-full, nên độ rộng đặt ở khung bọc ngoài. */}
          <div className="min-w-0 flex-1">
            <GlassSelect
              aria-label="Thứ"
              value={slot.dayOfWeek}
              onChange={(event) => updateSlot(index, { dayOfWeek: event.target.value as WeeklyScheduleSlot["dayOfWeek"] })}
            >
              {DAY_OPTIONS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </GlassSelect>
          </div>
          <div className="w-32 shrink-0">
            <GlassInput
              type="time"
              aria-label="Giờ bắt đầu"
              value={slot.startTime}
              onChange={(event) => updateSlot(index, { startTime: event.target.value })}
            />
          </div>
          <span className="text-slate-400">–</span>
          <div className="w-32 shrink-0">
            <GlassInput
              type="time"
              aria-label="Giờ kết thúc"
              value={slot.endTime}
              onChange={(event) => updateSlot(index, { endTime: event.target.value })}
            />
          </div>
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => removeSlot(index)}
            aria-label="Xoá buổi học"
          >
            <X size={14} aria-hidden />
          </GlassButton>
        </div>
      ))}
      <GlassButton type="button" variant="secondary" size="sm" onClick={addSlot} icon={<Plus size={14} aria-hidden />}>
        Thêm buổi học
      </GlassButton>
    </div>
  );
}
