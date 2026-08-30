import { Check, ChevronDown } from "lucide-react";
import { useEffect, useRef, useState } from "react";

export interface StyledSelectOption<Value extends string> {
  value: Value;
  label: string;
}

interface StyledSelectProps<Value extends string> {
  label: string;
  value: Value;
  options: readonly StyledSelectOption<Value>[];
  onChange: (value: Value) => void;
  className?: string;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export function StyledSelect<Value extends string>({ label, value, options, onChange, className = "", open: controlledOpen, onOpenChange }: StyledSelectProps<Value>) {
  const [internalOpen, setInternalOpen] = useState(false);
  const open = controlledOpen ?? internalOpen;
  const setOpen = (next: boolean | ((current: boolean) => boolean)) => {
    const resolved = typeof next === "function" ? next(open) : next;
    if (controlledOpen === undefined) setInternalOpen(resolved);
    onOpenChange?.(resolved);
  };
  const rootRef = useRef<HTMLDivElement>(null);
  const selected = options.find((option) => option.value === value) ?? options[0];

  useEffect(() => {
    const close = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("keydown", escape);
    };
  }, []);

  return (
    <div className={`styled-select ${className}${open ? " open" : ""}`} ref={rootRef}>
      <button type="button" className="styled-select-trigger" aria-label={label} aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen((current) => !current)}>
        <span>{selected.label}</span><ChevronDown size={16} />
      </button>
      {open && <div className="styled-select-menu" role="listbox" aria-label={label}>
        {options.map((option) => <button key={option.value || "all"} type="button" role="option" aria-selected={option.value === value} onClick={() => { onChange(option.value); setOpen(false); }}>
          <span>{option.label}</span>{option.value === value && <Check size={15} />}
        </button>)}
      </div>}
    </div>
  );
}
