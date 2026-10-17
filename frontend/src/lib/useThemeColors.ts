import { useEffect, useState } from 'react';

const TOKENS = ['series-1', 'series-2', 'series-3', 'series-4', 'grid', 'muted', 'surface', 'surface-2', 'border-strong'] as const;
export type ThemeColors = Record<(typeof TOKENS)[number], string>;

function read(): ThemeColors {
  const style = getComputedStyle(document.documentElement);
  return Object.fromEntries(TOKENS.map((t) => [t, style.getPropertyValue(`--${t}`).trim()])) as ThemeColors;
}

/**
 * SVG presentation attributes (which Recharts writes) don't resolve CSS custom properties, so charts take
 * concrete colours from the current theme and re-read them whenever the theme changes.
 */
export function useThemeColors(): ThemeColors {
  const [colors, setColors] = useState<ThemeColors>(read);
  useEffect(() => {
    const update = () => setColors(read());
    const observer = new MutationObserver(update);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    media.addEventListener('change', update);
    return () => {
      observer.disconnect();
      media.removeEventListener('change', update);
    };
  }, []);
  return colors;
}
