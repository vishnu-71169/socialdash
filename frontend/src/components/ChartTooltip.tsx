import type { TooltipProps } from 'recharts';

interface Props extends TooltipProps<number, string> {
  labelFormatter?: (label: string) => string;
  valueFormatter?: (value: number, key: string) => string;
}

/** Tooltip styled with the app's tokens; text stays in ink colors, the swatch carries series identity. */
export function ChartTooltip({ active, payload, label, labelFormatter, valueFormatter }: Props) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <div className="t-title">{labelFormatter ? labelFormatter(String(label)) : label}</div>
      {payload.map((p) => (
        <div className="t-row" key={String(p.dataKey)}>
          <span className="dot" style={{ background: p.color }} />
          {p.name}
          <b>{valueFormatter ? valueFormatter(Number(p.value), String(p.dataKey)) : p.value}</b>
        </div>
      ))}
    </div>
  );
}
