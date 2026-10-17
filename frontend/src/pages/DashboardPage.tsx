import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ExternalLink, Link2, RefreshCw } from 'lucide-react';
import { api } from '../api/client';
import type {
  Account,
  Breakdown,
  Overview,
  Page,
  Platform,
  PostHistoryPoint,
  PostPerformance,
  TrendPoint,
} from '../api/types';
import { PLATFORMS } from '../api/types';
import { ChartTooltip } from '../components/ChartTooltip';
import { Delta, Empty, ErrorAlert, Modal, Pagination, PlatformBadge, Skeleton } from '../components/ui';
import {
  daysAgo,
  fmtCompact,
  fmtDateTime,
  fmtDay,
  fmtNumber,
  fmtPct,
  fmtSigned,
  PLATFORM_LABEL,
  timeAgo,
  utcDate,
} from '../lib/format';
import { useAsync } from '../lib/useAsync';
import { useThemeColors, type ThemeColors } from '../lib/useThemeColors';

const RANGES = [
  { label: '7D', days: 7 },
  { label: '30D', days: 30 },
  { label: '90D', days: 90 },
];
const REFRESH_MS = 60_000;

type AudienceMetric = 'followers' | 'impressions' | 'engagementRate';
const AUDIENCE_METRICS: { key: AudienceMetric; label: string }[] = [
  { key: 'followers', label: 'Followers' },
  { key: 'impressions', label: 'Impressions' },
  { key: 'engagementRate', label: 'Engagement rate' },
];

const ENGAGEMENT_SERIES = [
  { key: 'likes', label: 'Likes', token: 'series-1' },
  { key: 'comments', label: 'Comments', token: 'series-2' },
  { key: 'shares', label: 'Shares', token: 'series-3' },
] as const;

const axisProps = (c: ThemeColors) => ({ tickLine: false, axisLine: false, stroke: c.muted, fontSize: 11 });

export default function DashboardPage() {
  const c = useThemeColors();
  const [rangeDays, setRangeDays] = useState<number | null>(30);
  const [startDate, setStartDate] = useState(daysAgo(29));
  const [endDate, setEndDate] = useState(utcDate(new Date()));
  const [platform, setPlatform] = useState<Platform | ''>('');
  const [accountId, setAccountId] = useState<number | ''>('');
  const [audienceMetric, setAudienceMetric] = useState<AudienceMetric>('followers');
  const [postSort, setPostSort] = useState<'engagement' | 'recent'>('engagement');
  const [postPage, setPostPage] = useState(0);
  const [selectedPost, setSelectedPost] = useState<PostPerformance | null>(null);
  const [lastUpdated, setLastUpdated] = useState(new Date());
  const [, forceTick] = useState(0);

  const accounts = useAsync(() => api.get<Account[]>('/accounts'), []);
  const filter = { platform: platform || undefined, accountId: accountId || undefined, startDate, endDate };
  const filterKey = JSON.stringify(filter);

  const overview = useAsync(() => api.get<Overview>('/analytics/overview', filter), [filterKey]);
  const trends = useAsync(() => api.get<TrendPoint[]>('/analytics/trends', filter), [filterKey]);
  const platforms = useAsync(() => api.get<Breakdown[]>('/analytics/platforms', filter), [filterKey]);
  const posts = useAsync(
    () => api.get<Page<PostPerformance>>('/analytics/posts', { ...filter, sort: postSort, page: postPage, size: 8 }),
    [filterKey, postSort, postPage],
  );

  const refreshAll = () => {
    overview.reload(true);
    trends.reload(true);
    platforms.reload(true);
    posts.reload(true);
    setLastUpdated(new Date());
  };

  // "Real-time": refresh silently while the tab is open; the backend collector updates today's snapshot.
  useEffect(() => {
    const t = setInterval(() => {
      if (document.visibilityState === 'visible') refreshAll();
    }, REFRESH_MS);
    const tick = setInterval(() => forceTick((n) => n + 1), 15_000);
    return () => {
      clearInterval(t);
      clearInterval(tick);
    };
  }, [filterKey, postSort, postPage]);

  useEffect(() => setPostPage(0), [filterKey, postSort]);

  const pickRange = (days: number) => {
    setRangeDays(days);
    setStartDate(daysAgo(days - 1));
    setEndDate(utcDate(new Date()));
  };

  const visibleAccounts = (accounts.data ?? []).filter((a) => !platform || a.platform === platform);
  const hasAccounts = (accounts.data?.length ?? 0) > 0;
  const o = overview.data;

  if (accounts.data && !hasAccounts) {
    return (
      <>
        <div className="page-header"><h1>Dashboard</h1></div>
        <div className="card">
          <Empty icon={<Link2 size={28} />} title="Connect your first account">
            <p>Analytics appear here once at least one social account is connected.</p>
            <Link to="/accounts" className="btn btn-primary">Connect an account</Link>
          </Empty>
        </div>
      </>
    );
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p>
            {fmtDay(startDate)} – {fmtDay(endDate)} · {o?.accountCount ?? '–'} accounts · {o?.publishedPosts ?? '–'} posts published
          </p>
        </div>
        <div className="row">
          <span className="muted small">Updated {timeAgo(lastUpdated.toISOString())}</span>
          <button className="btn btn-sm" onClick={refreshAll}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      <div className="filters">
        <div className="segmented" role="group" aria-label="Date range">
          {RANGES.map((r) => (
            <button key={r.days} className={rangeDays === r.days ? 'active' : ''} onClick={() => pickRange(r.days)}>
              {r.label}
            </button>
          ))}
        </div>
        <input className="input" style={{ width: 'auto' }} type="date" value={startDate} max={endDate}
               aria-label="Start date" onChange={(e) => { setRangeDays(null); setStartDate(e.target.value); }} />
        <span className="muted">to</span>
        <input className="input" style={{ width: 'auto' }} type="date" value={endDate} min={startDate}
               max={utcDate(new Date())} aria-label="End date"
               onChange={(e) => { setRangeDays(null); setEndDate(e.target.value); }} />
        <select className="select" value={platform} aria-label="Platform"
                onChange={(e) => { setPlatform(e.target.value as Platform | ''); setAccountId(''); }}>
          <option value="">All platforms</option>
          {PLATFORMS.map((p) => <option key={p} value={p}>{PLATFORM_LABEL[p]}</option>)}
        </select>
        <select className="select" value={accountId} aria-label="Account"
                onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">All accounts</option>
          {visibleAccounts.map((a) => <option key={a.id} value={a.id}>{a.handle} · {PLATFORM_LABEL[a.platform]}</option>)}
        </select>
      </div>

      <ErrorAlert message={overview.error ?? trends.error} />

      <div className="kpis" style={{ marginBottom: 16 }}>
        <Kpi label="Total followers" value={o && fmtCompact(o.followers.value)} pct={o?.followers.changePct} />
        <Kpi label="Follower growth" value={o && fmtSigned(o.followerGrowth.value)} pct={o?.followerGrowth.changePct} />
        <Kpi label="Impressions" value={o && fmtCompact(o.impressions.value)} pct={o?.impressions.changePct} />
        <Kpi label="Engagement rate" value={o && fmtPct(o.engagementRate.value)} pct={o?.engagementRate.changePct}
             hint="(likes + comments + shares) ÷ impressions" />
        <Kpi label="Likes" value={o && fmtCompact(o.likes.value)} pct={o?.likes.changePct} />
        <Kpi label="Comments" value={o && fmtCompact(o.comments.value)} pct={o?.comments.changePct} />
        <Kpi label="Shares" value={o && fmtCompact(o.shares.value)} pct={o?.shares.changePct} />
      </div>

      <div className="grid grid-2" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-header">
            <h2>{AUDIENCE_METRICS.find((m) => m.key === audienceMetric)?.label} over time</h2>
            <div className="segmented">
              {AUDIENCE_METRICS.map((m) => (
                <button key={m.key} className={audienceMetric === m.key ? 'active' : ''} onClick={() => setAudienceMetric(m.key)}>
                  {m.label}
                </button>
              ))}
            </div>
          </div>
          <div className="chart-box" style={{ height: 280 }}>
            {trends.loading && !trends.data ? <Skeleton height={250} /> : (
              <ResponsiveContainer>
                <AreaChart data={trends.data ?? []} margin={{ top: 16, right: 8, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="audienceFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={c['series-1']} stopOpacity={0.18} />
                      <stop offset="100%" stopColor={c['series-1']} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid vertical={false} stroke={c.grid} />
                  <XAxis dataKey="date" tickFormatter={fmtDay} minTickGap={28} {...axisProps(c)} />
                  <YAxis width={52} domain={audienceMetric === 'followers' ? ['auto', 'auto'] : [0, 'auto']}
                         tickFormatter={(v) => (audienceMetric === 'engagementRate' ? `${v}%` : fmtCompact(v))} {...axisProps(c)} />
                  <Tooltip
                    cursor={{ stroke: c['border-strong'] }}
                    content={<ChartTooltip labelFormatter={fmtDay}
                      valueFormatter={(v) => (audienceMetric === 'engagementRate' ? fmtPct(v) : fmtNumber(v))} />}
                  />
                  <Area type="monotone" dataKey={audienceMetric}
                        name={AUDIENCE_METRICS.find((m) => m.key === audienceMetric)?.label}
                        stroke={c['series-1']} strokeWidth={2} fill="url(#audienceFill)"
                        activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h2>Daily engagement</h2>
            <span className="sub">Stacked by interaction type</span>
          </div>
          <div className="legend" style={{ paddingTop: 10 }}>
            {ENGAGEMENT_SERIES.map((s) => (
              <span key={s.key}><i style={{ background: c[s.token], height: 8, width: 8, borderRadius: 2 }} />{s.label}
                {o && <b className="mono" style={{ color: 'var(--text)' }}>{fmtCompact(o[s.key].value)}</b>}
              </span>
            ))}
          </div>
          <div className="chart-box" style={{ height: 252 }}>
            {trends.loading && !trends.data ? <Skeleton height={230} /> : (
              <ResponsiveContainer>
                <BarChart data={trends.data ?? []} margin={{ top: 12, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke={c.grid} />
                  <XAxis dataKey="date" tickFormatter={fmtDay} minTickGap={28} {...axisProps(c)} />
                  <YAxis width={52} tickFormatter={fmtCompact} {...axisProps(c)} />
                  <Tooltip cursor={{ fill: c['surface-2'] }}
                           content={<ChartTooltip labelFormatter={fmtDay} valueFormatter={(v) => fmtNumber(v)} />} />
                  {ENGAGEMENT_SERIES.map((s, i) => (
                    <Bar key={s.key} dataKey={s.key} name={s.label} stackId="e" fill={c[s.token]}
                         stroke={c.surface} strokeWidth={1}
                         radius={i === ENGAGEMENT_SERIES.length - 1 ? [4, 4, 0, 0] : 0} maxBarSize={22} />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header">
          <h2>Platform comparison</h2>
          <span className="sub">Engagement rate = engagements ÷ impressions</span>
        </div>
        <PlatformTable rows={platforms.data} loading={platforms.loading} />
      </div>

      <div className="card">
        <div className="card-header">
          <h2>Post performance</h2>
          <div className="segmented">
            <button className={postSort === 'engagement' ? 'active' : ''} onClick={() => setPostSort('engagement')}>Top</button>
            <button className={postSort === 'recent' ? 'active' : ''} onClick={() => setPostSort('recent')}>Recent</button>
          </div>
        </div>
        <div className="table-wrap" style={{ marginTop: 8 }}>
          <table>
            <thead>
              <tr>
                <th>Post</th><th>Account</th><th>Published</th>
                <th className="num">Impressions</th><th className="num">Likes</th><th className="num">Comments</th>
                <th className="num">Shares</th><th className="num">Eng. rate</th>
              </tr>
            </thead>
            <tbody>
              {posts.data?.content.map((p) => (
                <tr key={p.publicationId} style={{ cursor: 'pointer' }} onClick={() => setSelectedPost(p)}>
                  <td><div className="clamp">{p.content}</div></td>
                  <td><PlatformBadge platform={p.platform} handle={p.accountHandle} /></td>
                  <td className="text-2" style={{ whiteSpace: 'nowrap' }}>{fmtDateTime(p.publishedAt)}</td>
                  <td className="num">{fmtNumber(p.impressions)}</td>
                  <td className="num">{fmtNumber(p.likes)}</td>
                  <td className="num">{fmtNumber(p.comments)}</td>
                  <td className="num">{fmtNumber(p.shares)}</td>
                  <td className="num">{fmtPct(p.engagementRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {posts.data && posts.data.totalElements === 0 && (
            <Empty title="No published posts in this period">
              <Link to="/publisher">Schedule a post</Link>
            </Empty>
          )}
        </div>
        {posts.data && (
          <Pagination page={posts.data.page} totalPages={posts.data.totalPages}
                      totalElements={posts.data.totalElements} onChange={setPostPage} />
        )}
      </div>

      {selectedPost && <PostHistoryModal post={selectedPost} onClose={() => setSelectedPost(null)} />}
    </>
  );
}

function Kpi({ label, value, pct, hint }: { label: string; value: string | null | undefined; pct?: number | null; hint?: string }) {
  return (
    <div className="card kpi" title={hint}>
      <div className="label">{label}</div>
      {value == null ? <Skeleton height={30} width={90} /> : <div className="value">{value}</div>}
      {value != null && <Delta pct={pct ?? null} />}
    </div>
  );
}

function PlatformTable({ rows, loading }: { rows: Breakdown[] | null; loading: boolean }) {
  const maxEngagements = useMemo(
    () => Math.max(1, ...(rows ?? []).map((r) => r.likes + r.comments + r.shares)),
    [rows],
  );
  if (loading && !rows) return <div className="card-pad"><Skeleton height={120} /></div>;
  if (!rows?.length) return <Empty title="No data for this selection" />;
  return (
    <div className="table-wrap" style={{ marginTop: 8 }}>
      <table>
        <thead>
          <tr>
            <th>Platform</th><th className="num">Followers</th><th className="num">Growth</th>
            <th className="num">Impressions</th><th style={{ width: '28%' }}>Engagements</th><th className="num">Eng. rate</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const engagements = r.likes + r.comments + r.shares;
            return (
              <tr key={r.key}>
                <td><PlatformBadge platform={r.platform} />{r.accountCount > 1 && <span className="muted small"> ×{r.accountCount}</span>}</td>
                <td className="num">{fmtNumber(r.followers)}</td>
                <td className="num">{fmtSigned(r.followerGrowth)}</td>
                <td className="num">{fmtCompact(r.impressions)}</td>
                <td>
                  <div className="row" style={{ flexWrap: 'nowrap' }}>
                    <div className="bar-track" style={{ flex: 1 }}>
                      <div className="bar-fill" style={{ width: `${(engagements / maxEngagements) * 100}%` }} />
                    </div>
                    <span className="mono small" style={{ minWidth: 48, textAlign: 'right' }}>{fmtCompact(engagements)}</span>
                  </div>
                </td>
                <td className="num">{fmtPct(r.engagementRate)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function PostHistoryModal({ post, onClose }: { post: PostPerformance; onClose: () => void }) {
  const c = useThemeColors();
  const history = useAsync(
    () => api.get<PostHistoryPoint[]>(`/analytics/posts/${post.publicationId}/history`),
    [post.publicationId],
  );
  return (
    <Modal title="Post performance" onClose={onClose}>
      <div className="row">
        <PlatformBadge platform={post.platform} handle={post.accountHandle} />
        <span className="muted small">Published {fmtDateTime(post.publishedAt)}</span>
        {post.externalUrl && (
          <a className="small row" href={post.externalUrl} target="_blank" rel="noreferrer" style={{ gap: 4 }}>
            View post <ExternalLink size={12} />
          </a>
        )}
      </div>
      <p>{post.content}</p>
      <div className="kpis">
        <div><div className="muted small">Impressions</div><b className="mono">{fmtNumber(post.impressions)}</b></div>
        <div><div className="muted small">Engagements</div><b className="mono">{fmtNumber(post.engagements)}</b></div>
        <div><div className="muted small">Eng. rate</div><b className="mono">{fmtPct(post.engagementRate)}</b></div>
      </div>
      <div>
        <h3 style={{ marginBottom: 6 }}>Cumulative engagement since publishing</h3>
        <div className="legend" style={{ padding: 0, marginBottom: 4 }}>
          {ENGAGEMENT_SERIES.map((s) => <span key={s.key}><i style={{ background: c[s.token] }} />{s.label}</span>)}
        </div>
        <div style={{ height: 220 }}>
          {history.loading ? <Skeleton height={200} /> : history.data?.length ? (
            <ResponsiveContainer>
              <LineChart data={history.data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke={c.grid} />
                <XAxis dataKey="recordedAt" tickFormatter={(v) => fmtDateTime(v)} minTickGap={40} {...axisProps(c)} />
                <YAxis width={48} tickFormatter={fmtCompact} {...axisProps(c)} />
                <Tooltip cursor={{ stroke: c['border-strong'] }}
                         content={<ChartTooltip labelFormatter={(l) => fmtDateTime(l)} valueFormatter={(v) => fmtNumber(v)} />} />
                {ENGAGEMENT_SERIES.map((s) => (
                  <Line key={s.key} type="monotone" dataKey={s.key} name={s.label} stroke={c[s.token]} strokeWidth={2}
                        dot={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
                ))}
              </LineChart>
            </ResponsiveContainer>
          ) : <Empty title="No history recorded yet" />}
        </div>
      </div>
    </Modal>
  );
}
