import { useLayoutEffect, useRef, type ReactNode } from 'react';

/**
 * Table wrapper that becomes a stacked card list on small screens. After each render it
 * copies every column header onto the matching body cells as `data-label`, which the
 * mobile CSS shows before each value. Pages keep writing plain <table> markup.
 */
export function ResponsiveTable({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const table = ref.current?.querySelector('table');
    if (!table) return;
    const headers = Array.from(table.querySelectorAll('thead th')).map((th) => th.textContent?.trim() ?? '');
    table.querySelectorAll('tbody tr').forEach((row) => {
      Array.from(row.children).forEach((cell, i) => {
        const label = headers[i];
        if (label) cell.setAttribute('data-label', label);
        else cell.removeAttribute('data-label');
      });
    });
  });

  return (
    <div className="table-wrap responsive-table" ref={ref}>
      {children}
    </div>
  );
}
