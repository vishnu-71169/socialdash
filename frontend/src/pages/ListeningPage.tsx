import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ExternalLink, Plus, Radar, Search, X } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Keyword, Mention, Page, Platform, TermCount, Volume } from '../api/types';
import { PLATFORMS } from '../api/types';
import { ChartTooltip } from '../components/ChartTooltip';
import { Delta, Empty, ErrorAlert, Pagination, PlatformBadge, Skeleton, useToast } from '../components/ui';
import { fmtDateTime, fmtDay, fmtNumber, PLATFORM_LABEL, timeAgo } from '../lib/format';
import { useAsync } from '../lib/useAsync';
import { useThemeColors } from '../lib/useThemeColors';

// Keyword series follow the categorical order; colour is tied to keyword position, capped at four lines.
const SERIES_TOKENS = ['series-1', 'series-2', 'series-3', 'series-4'] as const;
const MAX_LINES = 4;

export default function ListeningPage() {
  const c = useThemeColors();
  const toast = useToast();
  const [newKeyword, setNewKeyword] = useState('');
  const [newPlatform, setNewPlatform] = useState<Platform | ''>('');
  const [adding, setAdding] = useState(false);
  const [keywordId, setKeywordId] = useState<number | null>(null);
  const [platform, setPlatform] = useState<Platform | ''>('');
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [days, setDays] = useState(14);
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => setQ(search), 300);
    return () => clearTimeout(t);
  }, [search]);
  useEffect(() => setPage(0), [keywordId, platform, q]);

  const keywords = useAsync(() => api.get<Keyword[]>('/listening/keywords'), []);
  const volume = useAsync(() => api.get<Volume>('/listening/volume', { keywordId, days }), [keywordId, days]);
  const terms = useAsync(() => api.get<TermCount[]>('/listening/top-terms', { keywordId, days }), [keywordId, days]);
  const mentions = useAsync(
    () => api.get<Page<Mention>>('/listening/mentions', { keywordId, platform: platform || undefined, q, page, size: 12 }),
    [keywordId, platform, q, page],
  );

  const reloadAll = () => {
    keywords.reload(true);
    volume.reload(true);
    terms.reload(true);
    mentions.reload(true);
  };

  const add = async (e: FormEvent) => {
    e.preventDefault();
    setAdding(true);
    try {
      await api.post('/listening/keywords', { keyword: newKeyword, platform: newPlatform || null });
      setNewKeyword('');
      toast('Now tracking — loaded the last 14 days of mentions');
      reloadAll();
    } catch (err) {
      toast(errorMessage(err), true);
    } finally {
      setAdding(false);
    }
  };

  const remove = async (k: Keyword) => {
    if (!confirm(`Stop tracking "${k.keyword}"? Its collected mentions will be deleted.`)) return;
    try {
      await api.del(`/listening/keywords/${k.id}`);
      if (keywordId === k.id) setKeywordId(null);
      reloadAll();
    } catch (err) {
      toast(errorMessage(err), true);
    }
  };

  // Stable colour per keyword (by its position in the tracked list), regardless of the current filter.
  const colorFor = useMemo(() => {
    const map = new Map<number, string>();
    (keywords.data ?? []).forEach((k, i) => map.set(k.id, c[SERIES_TOKENS[i % SERIES_TOKENS.length]]));
    return (id: number) => map.get(id) ?? c['series-1'];
  }, [keywords.data, c]);

  const chartSeries = useMemo(() => (volume.data?.series ?? []).slice(0, MAX_LINES), [volume.data]);
  const chartData = useMemo(
    () => (volume.data?.dates ?? []).map((date, i) => {
      const row: Record<string, string | number> = { date };
      chartSeries.forEach((s) => (row[`k${s.keywordId}`] = s.counts[i]));
      return row;
    }),
    [volume.data, chartSeries],
  );
  const totals = (volume.data?.series ?? []).reduce<{ total: number; prev: number | null }>(
    (acc, s) => ({
      total: acc.total + s.total,
      prev: acc.prev === null || s.previousTotal === null ? null : acc.prev + s.previousTotal,
    }),
    { total: 0, prev: 0 },
  );
  const maxTerm = Math.max(1, ...(terms.data ?? []).map((t) => t.count));

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Social listening</h1>
          <p>Track brand mentions, hashtags and keywords across platforms.</p>
        </div>
      </div>

      <div className="card card-pad stack" style={{ marginBottom: 16, gap: 12 }}>
        <form className="row" onSubmit={add}>
          <input className="input" style={{ flex: '1 1 220px', width: 'auto' }} placeholder="Add keyword or #hashtag"
                 value={newKeyword} onChange={(e) => setNewKeyword(e.target.value)} maxLength={100} />
          <select className="select" value={newPlatform} onChange={(e) => setNewPlatform(e.target.value as Platform | '')} aria-label="Platform to track">
            <option value="">All platforms</option>
            {PLATFORMS.map((p) => <option key={p} value={p}>{PLATFORM_LABEL[p]}</option>)}
          </select>
          <button className="btn btn-primary" disabled={adding || newKeyword.trim().length < 2}><Plus size={15} /> Track</button>
        </form>
        <div className="row">
          <button className={`badge ${keywordId === null ? 'badge-active' : ''}`} style={chipStyle(keywordId === null)} onClick={() => setKeywordId(null)}>
            All keywords
          </button>
          {keywords.data?.map((k) => (
            <span key={k.id} className="badge" style={chipStyle(keywordId === k.id)}>
              <button onClick={() => setKeywordId(k.id)} style={{ all: 'unset', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span className="dot" style={{ background: colorFor(k.id) }} />
                {k.keyword}
                {k.platform && <span className="muted">· {PLATFORM_LABEL[k.platform]}</span>}
                <span className="muted mono">{k.mentionsLast24h} today</span>
              </button>
              <button className="icon-btn" style={{ padding: 0 }} aria-label={`Stop tracking ${k.keyword}`} onClick={() => remove(k)}>
                <X size={13} />
              </button>
            </span>
          ))}
        </div>
      </div>

      {keywords.data?.length === 0 ? (
        <div className="card">
          <Empty icon={<Radar size={28} />} title="No keywords tracked yet">
            Add your brand name, product names or campaign hashtags above.
          </Empty>
        </div>
      ) : (
        <>
          <div className="grid grid-3" style={{ marginBottom: 16 }}>
            <div className="card">
              <div className="card-header">
                <div>
                  <h2>Mention volume</h2>
                  <div className="row small" style={{ marginTop: 2 }}>
                    <b className="mono">{fmtNumber(totals.total)}</b><span className="muted">mentions</span>
                    <Delta pct={totals.prev ? ((totals.total - totals.prev) / totals.prev) * 100 : null} />
                  </div>
                </div>
                <div className="segmented">
                  {[7, 14, 30].map((d) => (
                    <button key={d} className={days === d ? 'active' : ''} onClick={() => setDays(d)}>{d}D</button>
                  ))}
                </div>
              </div>
              {chartSeries.length > 1 && (
                <div className="legend" style={{ paddingTop: 10 }}>
                  {chartSeries.map((s) => <span key={s.keywordId}><i style={{ background: colorFor(s.keywordId) }} />{s.keyword}</span>)}
                  {(volume.data?.series.length ?? 0) > MAX_LINES && <span className="muted">Showing first {MAX_LINES} keywords</span>}
                </div>
              )}
              <div className="chart-box" style={{ height: 260 }}>
                {volume.loading && !volume.data ? <Skeleton height={230} /> : (
                  <ResponsiveContainer>
                    <LineChart data={chartData} margin={{ top: 12, right: 8, left: 0, bottom: 0 }}>
                      <CartesianGrid vertical={false} stroke={c.grid} />
                      <XAxis dataKey="date" tickFormatter={fmtDay} minTickGap={24} tickLine={false} axisLine={false} stroke={c.muted} fontSize={11} />
                      <YAxis width={40} allowDecimals={false} tickLine={false} axisLine={false} stroke={c.muted} fontSize={11} />
                      <Tooltip cursor={{ stroke: c['border-strong'] }} content={<ChartTooltip labelFormatter={fmtDay} />} />
                      {chartSeries.map((s) => (
                        <Line key={s.keywordId} type="monotone" dataKey={`k${s.keywordId}`} name={s.keyword}
                              stroke={colorFor(s.keywordId)} strokeWidth={2} dot={false}
                              activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
                      ))}
                    </LineChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            <div className="card">
              <div className="card-header"><h2>Trending terms</h2><span className="sub">Last {days} days</span></div>
              <div className="card-pad stack" style={{ gap: 8 }}>
                {terms.loading && !terms.data ? <Skeleton height={200} /> : terms.data?.length ? terms.data.slice(0, 10).map((t) => (
                  <div key={t.term} className="bar-row">
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.term}</span>
                    <div className="bar-track"><div className="bar-fill" style={{ width: `${(t.count / maxTerm) * 100}%` }} /></div>
                    <span className="mono muted" style={{ textAlign: 'right' }}>{t.count}</span>
                  </div>
                )) : <span className="muted">No mentions yet.</span>}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-header"><h2>Mentions</h2></div>
            <div className="filters" style={{ padding: '12px 20px 0' }}>
              <div className="row" style={{ position: 'relative', flex: '1 1 220px' }}>
                <Search size={15} style={{ position: 'absolute', left: 10, color: 'var(--muted)' }} />
                <input className="input" style={{ paddingLeft: 32 }} placeholder="Search mentions" value={search}
                       onChange={(e) => setSearch(e.target.value)} />
              </div>
              <select className="select" value={platform} onChange={(e) => setPlatform(e.target.value as Platform | '')} aria-label="Platform">
                <option value="">All platforms</option>
                {PLATFORMS.map((p) => <option key={p} value={p}>{PLATFORM_LABEL[p]}</option>)}
              </select>
            </div>
            <ErrorAlert message={mentions.error} />
            {mentions.data?.content.map((m) => (
              <div key={m.id} className="list-item">
                <div className="body stack" style={{ gap: 4 }}>
                  <div className="row">
                    <b>{m.authorHandle}</b>
                    <PlatformBadge platform={m.platform} />
                    <span className="badge"><span className="dot" style={{ background: colorFor(m.keywordId) }} />{m.keyword}</span>
                    <span className="spacer" />
                    <span className="muted small" title={fmtDateTime(m.detectedAt)}>{timeAgo(m.detectedAt)}</span>
                    {m.url && <a href={m.url} target="_blank" rel="noreferrer" aria-label="Open mention"><ExternalLink size={13} /></a>}
                  </div>
                  <p>{m.content}</p>
                </div>
              </div>
            ))}
            {mentions.data?.totalElements === 0 && <Empty title="No mentions match these filters" />}
            {mentions.data && <Pagination page={mentions.data.page} totalPages={mentions.data.totalPages} totalElements={mentions.data.totalElements} onChange={setPage} />}
          </div>
        </>
      )}
    </>
  );
}

function chipStyle(active: boolean) {
  return {
    cursor: 'pointer',
    border: `1px solid ${active ? 'var(--accent)' : 'var(--border)'}`,
    background: active ? 'var(--accent-soft)' : 'var(--surface-2)',
    color: active ? 'var(--accent)' : 'var(--text-2)',
    padding: '4px 10px',
  } as const;
}
