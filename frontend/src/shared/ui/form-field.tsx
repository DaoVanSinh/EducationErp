import { m } from "framer-motion";
import type { ReactNode } from "react";

export interface FormFieldProps {
  readonly label: string;
  readonly htmlFor: string;
  readonly hint?: string;
  readonly error?: string;
  readonly children: ReactNode;
}

export function FormField({ label, htmlFor, hint, error, children }: FormFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-xs font-medium tracking-wide text-mist-400 uppercase">
        {label}
      </label>
      {children}
      {error ? (
        <m.p
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          className="text-xs text-rose-400"
          role="alert"
        >
          {error}
        </m.p>
      ) : hint ? (
        <p className="text-xs text-mist-500">{hint}</p>
      ) : null}
    </div>
  );
}
