import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Ban, CalendarClock, ExternalLink, Pencil, Plus, RotateCcw, Send, Trash2 } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Account, Page, Post, PostAction, PostRequest, PostStatus } from '../api/types';
import { PLATFORM_LIMITS } from '../api/types';
import { Empty, ErrorAlert, Modal, Pagination, PlatformBadge, Skeleton, StatusBadge, useToast } from '../components/ui';
import { fmtDateTime, isoToLocalInput, localInputToIso, PLATFORM_LABEL, timeAgo } from '../lib/format';
import { useAsync } from '../lib/useAsync';

const TABS: { key: PostStatus | 'ALL'; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'SCHEDULED', label: 'Scheduled' },
  { key: 'PUBLISHING', label: 'Publishing' },
  { key: 'PUBLISHED', label: 'Published' },
  { key: 'DRAFT', label: 'Drafts' },
  { key: 'FAILED', label: 'Failed' },
  { key: 'CANCELLED', label: 'Cancelled' },
];

export default function PublisherPage() {
  const toast = useToast();
  const [tab, setTab] = useState<PostStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Post | 'new' | null>(null);

  const accounts = useAsync(() => api.get<Account[]>('/accounts'), []);
  const summary = useAsync(() => api.get<{ counts: Record<PostStatus, number> }>('/posts/summary'), []);
  const posts = useAsync(
    () => api.get<Page<Post>>('/posts', { status: tab === 'ALL' ? undefined : [tab], page, size: 10 }),
    [tab, page],
  );

  // While anything is in flight, poll so the scheduler's progress shows up without a manual refresh.
  const inFlight = posts.data?.content.some((p) => p.status === 'SCHEDULED' || p.status === 'PUBLISHING');
  useEffect(() => {
    if (!inFlight) return;
    const t = setInterval(() => {
      posts.reload(true);
      summary.reload(true);
    }, 8000);
    return () => clearInterval(t);
  }, [inFlight, posts.reload, summary.reload]);

  const refresh = () => {
    posts.reload(true);
    summary.reload(true);
  };

  const act = async (fn: () => Promise<unknown>, success: string) => {
    try {
      await fn();
      toast(success);
      refresh();
    } catch (e) {
      toast(errorMessage(e), true);
    }
  };

  const total = summary.data ? Object.values(summary.data.counts).reduce((a, b) => a + b, 0) : null;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Publisher</h1>
          <p>Create, schedule and track posts across all connected accounts.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing('new')} disabled={!accounts.data?.length}>
          <Plus size={16} /> New post
        </button>
      </div>

      {accounts.data && accounts.data.length === 0 && (
        <div className="alert alert-info" style={{ marginBottom: 16 }}>
          <Link to="/accounts">Connect an account</Link> before scheduling posts.
        </div>
      )}

      <div className="card">
        <div className="tabs" style={{ padding: '0 12px' }}>
          {TABS.map((t) => (
            <button key={t.key} className={`tab ${tab === t.key ? 'active' : ''}`} onClick={() => { setTab(t.key); setPage(0); }}>
              {t.label}
              <span className="count">{t.key === 'ALL' ? total ?? '' : summary.data?.counts[t.key] ?? ''}</span>
            </button>
          ))}
        </div>
        <ErrorAlert message={posts.error} />
        {posts.loading && !posts.data ? (
          <div className="card-pad stack"><Skeleton height={60} /><Skeleton height={60} /></div>
        ) : posts.data?.content.length ? (
          <div>
            {posts.data.content.map((p) => (
              <PostRow key={p.id} post={p}
                       onEdit={() => setEditing(p)}
                       onCancel={() => act(() => api.post(`/posts/${p.id}/cancel`), 'Post cancelled')}
                       onRetry={() => act(() => api.post(`/posts/${p.id}/retry`), 'Retry queued')}
                       onDelete={() => {
                         if (confirm('Delete this post? This cannot be undone.')) act(() => api.del(`/posts/${p.id}`), 'Post deleted');
                       }} />
            ))}
          </div>
        ) : (
          <Empty icon={<CalendarClock size={28} />} title="Nothing here yet">
            {tab === 'ALL' ? 'Create your first post to get started.' : 'No posts with this status.'}
          </Empty>
        )}
        {posts.data && (
          <Pagination page={posts.data.page} totalPages={posts.data.totalPages}
                      totalElements={posts.data.totalElements} onChange={setPage} />
        )}
      </div>

      {editing && accounts.data && (
        <Composer accounts={accounts.data} post={editing === 'new' ? null : editing}
                  onClose={() => setEditing(null)}
                  onSaved={(msg) => { setEditing(null); toast(msg); refresh(); }} />
      )}
    </>
  );
}

function PostRow({ post, onEdit, onCancel, onRetry, onDelete }: {
  post: Post; onEdit: () => void; onCancel: () => void; onRetry: () => void; onDelete: () => void;
}) {
  const started = post.publications.some((p) => p.status === 'PUBLISHED' || p.status === 'PUBLISHING');
  const canEdit = !started;
  const canCancel = (post.status === 'DRAFT' || post.status === 'SCHEDULED' || post.status === 'PUBLISHING')
    && !post.publications.some((p) => p.status === 'PUBLISHING');
  const canRetry = post.publications.some((p) => p.status === 'FAILED');

  const when = post.status === 'PUBLISHED' || post.status === 'FAILED'
    ? post.publications.find((p) => p.publishedAt)?.publishedAt ?? post.scheduledAt
    : post.scheduledAt;

  return (
    <div className="list-item">
      <div className="body stack" style={{ gap: 8 }}>
        <div className="row">
          <StatusBadge status={post.status} label={post.partiallyPublished ? 'Partially failed' : undefined} />
          <span className="muted small">
            {when ? `${fmtDateTime(when)} · ${timeAgo(when)}` : `Edited ${timeAgo(post.updatedAt)}`}
          </span>
        </div>
        <p style={{ whiteSpace: 'pre-wrap' }}>{post.content}</p>
        {post.mediaUrl && <a className="small" href={post.mediaUrl} target="_blank" rel="noreferrer">{post.mediaUrl}</a>}
        <div className="row">
          {post.publications.map((pub) => (
            <span key={pub.id} className="pub-chip" title={pub.lastError ?? undefined}>
              <PlatformBadge platform={pub.platform} handle={pub.accountHandle} />
              <StatusBadge status={pub.status} />
              {pub.status === 'SCHEDULED' && pub.attemptCount > 0 && pub.nextAttemptAt && (
                <span className="muted small">retry {pub.attemptCount + 1} {timeAgo(pub.nextAttemptAt)}</span>
              )}
              {pub.status === 'FAILED' && <span className="muted small">after {pub.attemptCount} attempts</span>}
              {pub.externalUrl && (
                <a href={pub.externalUrl} target="_blank" rel="noreferrer" aria-label="Open published post"><ExternalLink size={12} /></a>
              )}
            </span>
          ))}
          {post.publications.length === 0 && <span className="muted small">No accounts selected</span>}
        </div>
        {post.publications.filter((p) => p.lastError && p.status !== 'PUBLISHED').map((p) => (
          <span key={p.id} className="small" style={{ color: 'var(--critical)' }}>
            {PLATFORM_LABEL[p.platform]}: {p.lastError}
          </span>
        ))}
      </div>
      <div className="row" style={{ alignItems: 'flex-start', flexWrap: 'nowrap' }}>
        {canEdit && <button className="icon-btn" title="Edit" aria-label="Edit" onClick={onEdit}><Pencil size={16} /></button>}
        {canRetry && <button className="icon-btn" title="Retry failed" aria-label="Retry failed" onClick={onRetry}><RotateCcw size={16} /></button>}
        {canCancel && <button className="icon-btn" title="Cancel" aria-label="Cancel" onClick={onCancel}><Ban size={16} /></button>}
        {!started && <button className="icon-btn" title="Delete" aria-label="Delete" onClick={onDelete}><Trash2 size={16} /></button>}
      </div>
    </div>
  );
}

function Composer({ accounts, post, onClose, onSaved }: {
  accounts: Account[]; post: Post | null; onClose: () => void; onSaved: (msg: string) => void;
}) {
  const [content, setContent] = useState(post?.content ?? '');
  const [mediaUrl, setMediaUrl] = useState(post?.mediaUrl ?? '');
  const [selected, setSelected] = useState<number[]>(post?.publications.map((p) => p.accountId) ?? []);
  const [when, setWhen] = useState(isoToLocalInput(post?.scheduledAt ?? null));
  const [busy, setBusy] = useState<PostAction | null>(null);
  const [error, setError] = useState<string | null>(null);

  const overLimit = useMemo(
    () => accounts.filter((a) => selected.includes(a.id) && content.trim().length > PLATFORM_LIMITS[a.platform]),
    [accounts, selected, content],
  );
  const tightestLimit = Math.min(...accounts.filter((a) => selected.includes(a.id)).map((a) => PLATFORM_LIMITS[a.platform]), Infinity);

  const toggle = (id: number) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  const save = async (action: PostAction) => {
    setBusy(action);
    setError(null);
    const body: PostRequest = {
      content,
      mediaUrl: mediaUrl || null,
      accountIds: selected,
      scheduledAt: localInputToIso(when),
      action,
    };
    try {
      if (post) await api.put(`/posts/${post.id}`, body);
      else await api.post('/posts', body);
      onSaved(action === 'DRAFT' ? 'Draft saved' : action === 'SCHEDULE' ? 'Post scheduled' : 'Publishing now');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const canPublish = content.trim().length > 0 && selected.length > 0 && overLimit.length === 0;

  return (
    <Modal title={post ? 'Edit post' : 'New post'} onClose={onClose} footer={
      <>
        <button className="btn" disabled={!!busy || !content.trim()} onClick={() => save('DRAFT')}>
          {busy === 'DRAFT' ? 'Saving…' : 'Save draft'}
        </button>
        <button className="btn" disabled={!!busy || !canPublish} onClick={() => save('PUBLISH_NOW')}>
          <Send size={14} /> {busy === 'PUBLISH_NOW' ? 'Queuing…' : 'Publish now'}
        </button>
        <button className="btn btn-primary" disabled={!!busy || !canPublish || !when} onClick={() => save('SCHEDULE')}>
          <CalendarClock size={14} /> {busy === 'SCHEDULE' ? 'Scheduling…' : 'Schedule'}
        </button>
      </>
    }>
      <ErrorAlert message={error} />
      <div className="field">
        <label htmlFor="content">Content</label>
        <textarea id="content" className="textarea" value={content} maxLength={5000}
                  onChange={(e) => setContent(e.target.value)} placeholder="What do you want to share?" />
        <div className="row small">
          <span className="muted">{content.trim().length} characters{isFinite(tightestLimit) ? ` · limit ${tightestLimit}` : ''}</span>
          {overLimit.map((a) => (
            <span key={a.id} style={{ color: 'var(--critical)' }}>Too long for {PLATFORM_LABEL[a.platform]}</span>
          ))}
        </div>
      </div>
      <div className="field">
        <label>Publish to</label>
        <div className="grid grid-2" style={{ gap: 8 }}>
          {accounts.map((a) => (
            <label key={a.id} className={`checkbox-card ${selected.includes(a.id) ? 'checked' : ''}`}>
              <input type="checkbox" checked={selected.includes(a.id)} onChange={() => toggle(a.id)}
                     aria-label={`${a.handle} on ${PLATFORM_LABEL[a.platform]}`} />
              <PlatformBadge platform={a.platform} handle={a.handle} />
            </label>
          ))}
        </div>
      </div>
      <div className="field">
        <label htmlFor="media">Media URL <span className="muted">(optional)</span></label>
        <input id="media" className="input" type="url" value={mediaUrl} onChange={(e) => setMediaUrl(e.target.value)}
               placeholder="https://…" />
      </div>
      <div className="field">
        <label htmlFor="when">Schedule for</label>
        <input id="when" className="input" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
        <span className="muted small">
          Your local time ({Intl.DateTimeFormat().resolvedOptions().timeZone}). Stored in UTC.
        </span>
      </div>
    </Modal>
  );
}
