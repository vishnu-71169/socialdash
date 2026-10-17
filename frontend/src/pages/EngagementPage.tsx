import { useEffect, useState } from 'react';
import { AtSign, CheckCircle2, Inbox, Mail, MessageCircle, Reply, RotateCcw, Search } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Engagement, EngagementStatus, EngagementSummary, EngagementType, Page, Platform } from '../api/types';
import { PLATFORMS } from '../api/types';
import { Empty, ErrorAlert, Pagination, PlatformBadge, Skeleton, useToast } from '../components/ui';
import { fmtDateTime, PLATFORM_LABEL, timeAgo } from '../lib/format';
import { useAsync } from '../lib/useAsync';

const TYPE_META: Record<EngagementType, { label: string; icon: JSX.Element }> = {
  COMMENT: { label: 'Comment', icon: <MessageCircle size={14} /> },
  MENTION: { label: 'Mention', icon: <AtSign size={14} /> },
  DIRECT_MESSAGE: { label: 'Message', icon: <Mail size={14} /> },
};

const STATUS_TABS: { key: EngagementStatus | ''; label: string }[] = [
  { key: 'OPEN', label: 'Open' },
  { key: 'REPLIED', label: 'Replied' },
  { key: 'RESOLVED', label: 'Resolved' },
  { key: '', label: 'All' },
];

export default function EngagementPage() {
  const toast = useToast();
  const [status, setStatus] = useState<EngagementStatus | ''>('OPEN');
  const [type, setType] = useState<EngagementType | ''>('');
  const [platform, setPlatform] = useState<Platform | ''>('');
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => setQ(search), 300);
    return () => clearTimeout(t);
  }, [search]);
  useEffect(() => setPage(0), [status, type, platform, q]);

  const summary = useAsync(() => api.get<EngagementSummary>('/engagements/summary'), []);
  const list = useAsync(
    () => api.get<Page<Engagement>>('/engagements', { status: status || undefined, type: type || undefined, platform: platform || undefined, q, page, size: 15 }),
    [status, type, platform, q, page],
  );

  const update = (updated: Engagement, message: string) => {
    toast(message);
    list.setData((d) => d && { ...d, content: d.content.map((e) => (e.id === updated.id ? updated : e)) });
    summary.reload(true);
  };

  const c = summary.data?.counts;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Engagement</h1>
          <p>Comments, mentions and messages from every connected account in one inbox.</p>
        </div>
      </div>

      <div className="kpis" style={{ marginBottom: 16 }}>
        <SummaryTile label="Needs response" value={c?.OPEN} />
        <SummaryTile label="Replied" value={c?.REPLIED} />
        <SummaryTile label="Resolved" value={c?.RESOLVED} />
        <SummaryTile label="Avg. response time (30d)"
                     value={summary.data ? formatMinutes(summary.data.avgResponseMinutes) : undefined} />
      </div>

      <div className="card">
        <div className="tabs" style={{ padding: '0 12px' }}>
          {STATUS_TABS.map((t) => (
            <button key={t.label} className={`tab ${status === t.key ? 'active' : ''}`} onClick={() => setStatus(t.key)}>
              {t.label}
              {t.key && c && <span className="count">{c[t.key]}</span>}
            </button>
          ))}
        </div>
        <div className="filters" style={{ padding: '12px 20px 0' }}>
          <div className="row" style={{ position: 'relative', flex: '1 1 220px' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, color: 'var(--muted)' }} />
            <input className="input" style={{ paddingLeft: 32 }} placeholder="Search text or author" value={search}
                   onChange={(e) => setSearch(e.target.value)} />
          </div>
          <select className="select" value={type} onChange={(e) => setType(e.target.value as EngagementType | '')} aria-label="Type">
            <option value="">All types</option>
            {Object.entries(TYPE_META).map(([k, v]) => <option key={k} value={k}>{v.label}s</option>)}
          </select>
          <select className="select" value={platform} onChange={(e) => setPlatform(e.target.value as Platform | '')} aria-label="Platform">
            <option value="">All platforms</option>
            {PLATFORMS.map((p) => <option key={p} value={p}>{PLATFORM_LABEL[p]}</option>)}
          </select>
        </div>
        <ErrorAlert message={list.error} />
        {list.loading && !list.data ? (
          <div className="card-pad stack"><Skeleton height={56} /><Skeleton height={56} /><Skeleton height={56} /></div>
        ) : list.data?.content.length ? (
          list.data.content.map((e) => <EngagementRow key={e.id} item={e} onUpdated={update} onError={(m) => toast(m, true)} />)
        ) : (
          <Empty icon={<Inbox size={28} />} title={status === 'OPEN' ? 'Inbox zero' : 'No interactions found'}>
            {status === 'OPEN' ? 'Everything has been handled. New interactions are synced every few minutes.' : 'Try changing the filters.'}
          </Empty>
        )}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onChange={setPage} />}
      </div>
    </>
  );
}

function SummaryTile({ label, value }: { label: string; value: number | string | undefined }) {
  return (
    <div className="card kpi">
      <div className="label">{label}</div>
      {value === undefined ? <Skeleton height={30} width={70} /> : <div className="value">{value}</div>}
    </div>
  );
}

function formatMinutes(m: number | null) {
  if (m === null) return '—';
  if (m < 60) return `${Math.round(m)}m`;
  return `${(m / 60).toFixed(1)}h`;
}

function EngagementRow({ item, onUpdated, onError }: {
  item: Engagement; onUpdated: (e: Engagement, msg: string) => void; onError: (m: string) => void;
}) {
  const [replying, setReplying] = useState(false);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  const run = async (fn: () => Promise<Engagement>, msg: string) => {
    setBusy(true);
    try {
      onUpdated(await fn(), msg);
      setReplying(false);
      setText('');
    } catch (e) {
      onError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const meta = TYPE_META[item.type];
  return (
    <div className="list-item">
      <div className="avatar">{item.authorName.charAt(0)}</div>
      <div className="body stack" style={{ gap: 6 }}>
        <div className="row">
          <b>{item.authorName}</b>
          <span className="muted small">{item.authorHandle}</span>
          <span className="badge">{meta.icon}{meta.label}</span>
          <span className="muted small">on</span>
          <PlatformBadge platform={item.platform} handle={item.accountHandle} />
          <span className="spacer" />
          <span className="muted small" title={fmtDateTime(item.receivedAt)}>{timeAgo(item.receivedAt)}</span>
        </div>
        <p>{item.content}</p>
        {item.replyContent && (
          <div className="alert alert-info small">
            <b>Your reply</b> · {fmtDateTime(item.repliedAt)}<br />{item.replyContent}
          </div>
        )}
        {replying && (
          <div className="stack" style={{ gap: 8 }}>
            <textarea className="textarea" style={{ minHeight: 70 }} autoFocus value={text} maxLength={2000}
                      onChange={(e) => setText(e.target.value)} placeholder={`Reply to ${item.authorName}…`} />
            <div className="row">
              <button className="btn btn-primary btn-sm" disabled={busy || !text.trim()}
                      onClick={() => run(() => api.post<Engagement>(`/engagements/${item.id}/reply`, { message: text }), 'Reply sent')}>
                Send reply
              </button>
              <button className="btn btn-sm" onClick={() => setReplying(false)}>Cancel</button>
            </div>
          </div>
        )}
        {!replying && (
          <div className="row">
            {item.status !== 'RESOLVED' && (
              <button className="btn btn-sm" onClick={() => setReplying(true)}><Reply size={14} /> Reply</button>
            )}
            {item.status !== 'RESOLVED' ? (
              <button className="btn btn-sm" disabled={busy}
                      onClick={() => run(() => api.post<Engagement>(`/engagements/${item.id}/resolve`), 'Marked as resolved')}>
                <CheckCircle2 size={14} /> Resolve
              </button>
            ) : (
              <button className="btn btn-sm" disabled={busy}
                      onClick={() => run(() => api.post<Engagement>(`/engagements/${item.id}/reopen`), 'Reopened')}>
                <RotateCcw size={14} /> Reopen
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
