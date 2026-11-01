import {
  MCP_COPY_FIELD,
  MCP_IDE_TAB_OPTIONS,
  type McpCopyField,
  type McpIdeTab,
} from "@/modules/mcp-connector/constants/mcp-connector-constants";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { SegmentedTabs, type SegmentedTab } from "@/shared/ui/segmented-tabs";
import { Check, Code2, Copy, Terminal } from "lucide-react";

export interface McpIdeConfigCardProps {
  readonly activeIdeTab: McpIdeTab;
  readonly ideSnippet: string;
  readonly copiedField: McpCopyField | null;
  readonly onSelectIdeTab: (tab: McpIdeTab) => void;
  readonly onCopyText: (field: McpCopyField, value: string) => void;
}

const IDE_SEGMENTED_TABS: readonly SegmentedTab<McpIdeTab>[] = MCP_IDE_TAB_OPTIONS.map((opt) => ({
  key: opt.id,
  label: opt.label,
  icon: <Terminal size={15} aria-hidden />,
}));

export function McpIdeConfigCard({
  activeIdeTab,
  ideSnippet,
  copiedField,
  onSelectIdeTab,
  onCopyText,
}: McpIdeConfigCardProps) {
  const isCopied = copiedField === MCP_COPY_FIELD.IDE_SNIPPET;
  const activeOption =
    MCP_IDE_TAB_OPTIONS.find((item) => item.id === activeIdeTab) ?? MCP_IDE_TAB_OPTIONS[0];

  return (
    <GlassPanel className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-orange-50 text-orange-600">
            <Code2 size={20} aria-hidden />
          </span>
          <div>
            <h2 className="text-base font-bold text-slate-900">
              Cấu hình nhanh cho AI IDE &amp; CLI (Bearer Token)
            </h2>
            <p className="text-sm text-slate-500">
              Dán đoạn cấu hình dưới đây vào tệp cấu hình MCP của IDE ({activeOption?.fileHint}).
            </p>
          </div>
        </div>

        <GlassButton
          variant="secondary"
          size="sm"
          icon={isCopied ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />}
          onClick={() => onCopyText(MCP_COPY_FIELD.IDE_SNIPPET, ideSnippet)}
        >
          {isCopied ? "Đã chép cấu hình" : "Sao chép cấu hình"}
        </GlassButton>
      </div>

      <SegmentedTabs
        tabs={IDE_SEGMENTED_TABS}
        activeTab={activeIdeTab}
        onTabChange={onSelectIdeTab}
      />

      <pre className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-950 p-4 font-mono text-xs leading-relaxed text-slate-100">
        <code>{ideSnippet}</code>
      </pre>
    </GlassPanel>
  );
}
