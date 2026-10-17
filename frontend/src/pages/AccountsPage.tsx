import { useState, type FormEvent } from 'react';
import { Link2, Plus, RefreshCw, Unlink } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Account, Platform } from '../api/types';
import { PLATFORMS } from '../api/types';
import { Empty, ErrorAlert, Modal, PlatformBadge, SimulatedBadge, Skeleton, platformColor, useToast } from '../components/ui';
import { fmtNumber, PLATFORM_LABEL, timeAgo } from '../lib/format';
import { useAsync } from '../lib/useAsync';

export default function AccountsPage() {
  const toast = useToast();
  const accounts = useAsync(() => api.get<Account[]>('/accounts'), []);
  const [connecting, setConnecting] = useState(false);
  const [syncing, setSyncing] = useState<number | null>(null);

  const sync = async (a: Account) => {
    setSyncing(a.id);
    try {
      const updated = await api.post<Account>(`/accounts/${a.id}/sync`);
      accounts.setData((list) => list?.map((x) => (x.id === a.id ? updated : x)) ?? null);
      toast(`${a.handle} synced`);
    } catch (e) {
      toast(errorMessage(e), true);
    } finally {
      setSyncing(null);
    }
  };

  const disconnect = async (a: Account) => {
    if (!confirm(`Disconnect ${a.handle}? Its analytics history, interactions and publications will be removed.`)) return;
    try {
      await api.del(`/accounts/${a.id}`);
      toast(`${a.handle} disconnected`);
      accounts.reload(true);
    } catch (e) {
      toast(errorMessage(e), true);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Connected accounts</h1>
          <p>Manage the social profiles SocialDash reads from and publishes to.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setConnecting(true)}><Plus size={16} /> Connect account</button>
      </div>

      <div className="alert alert-info" style={{ marginBottom: 16 }}>
        Live connections need approved developer apps on each platform (Meta Graph API, X API, LinkedIn Marketing API).
        Until those credentials are configured, accounts are <b>simulated</b>: metrics, mentions and interactions are
        generated, and publishing goes through the full scheduling pipeline against a simulated API.
      </div>

      <ErrorAlert message={accounts.error} />
      {accounts.loading && !accounts.data ? (
        <div className="grid grid-2"><Skeleton height={120} /><Skeleton height={120} /></div>
      ) : accounts.data?.length ? (
        <div className="grid grid-2">
          {accounts.data.map((a) => (
            <div key={a.id} className="card card-pad stack" style={{ gap: 12, borderTop: `3px solid ${platformColor(a.platform)}` }}>
              <div className="row">
                <div>
                  <h2>{a.displayName}</h2>
                  <div className="muted">{a.handle}</div>
                </div>
                <span className="spacer" />
                {a.simulated && <SimulatedBadge />}
                <PlatformBadge platform={a.platform} />
              </div>
              <div className="row" style={{ gap: 24 }}>
                <div>
                  <div className="muted small">Followers</div>
                  <div style={{ fontSize: 20, fontWeight: 650 }} className="mono">{a.followers != null ? fmtNumber(a.followers) : '—'}</div>
                </div>
                <div>
                  <div className="muted small">Connected</div>
                  <div>{new Date(a.connectedAt).toLocaleDateString()}</div>
                </div>
                <div>
                  <div className="muted small">Last synced</div>
                  <div>{a.lastSyncedAt ? timeAgo(a.lastSyncedAt) : 'never'}</div>
                </div>
              </div>
              <div className="row">
                <button className="btn btn-sm" onClick={() => sync(a)} disabled={syncing === a.id}>
                  <RefreshCw size={14} className={syncing === a.id ? 'spin' : ''} /> Sync now
                </button>
                <button className="btn btn-sm btn-danger" onClick={() => disconnect(a)}><Unlink size={14} /> Disconnect</button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="card">
          <Empty icon={<Link2 size={28} />} title="No accounts connected">
            Connect Instagram, Facebook, X or LinkedIn to start tracking performance.
          </Empty>
        </div>
      )}

      {connecting && (
        <ConnectModal onClose={() => setConnecting(false)}
                      onConnected={(a) => { setConnecting(false); toast(`${a.handle} connected with 120 days of history`); accounts.reload(true); }} />
      )}
    </>
  );
}

function ConnectModal({ onClose, onConnected }: { onClose: () => void; onConnected: (a: Account) => void }) {
  const [platform, setPlatform] = useState<Platform>('INSTAGRAM');
  const [handle, setHandle] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onConnected(await api.post<Account>('/accounts/simulated', { platform, handle, displayName: displayName || null }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="Connect an account" onClose={onClose} footer={
      <>
        <button className="btn" onClick={onClose}>Cancel</button>
        <button className="btn btn-primary" disabled={busy || !handle.trim()} onClick={() => submit()}>
          {busy ? 'Connecting…' : 'Connect simulated account'}
        </button>
      </>
    }>
      <form className="stack" style={{ gap: 14 }} onSubmit={submit}>
        <ErrorAlert message={error} />
        <div className="field">
          <label>Platform</label>
          <div className="grid grid-2" style={{ gap: 8 }}>
            {PLATFORMS.map((p) => (
              <label key={p} className={`checkbox-card ${platform === p ? 'checked' : ''}`}>
                <input type="radio" name="platform" checked={platform === p} onChange={() => setPlatform(p)} />
                <span className="dot" style={{ background: platformColor(p) }} />
                {PLATFORM_LABEL[p]}
              </label>
            ))}
          </div>
        </div>
        <div className="field">
          <label htmlFor="handle">Handle</label>
          <input id="handle" className="input" placeholder="@yourbrand" value={handle} onChange={(e) => setHandle(e.target.value)} maxLength={50} />
        </div>
        <div className="field">
          <label htmlFor="display">Display name <span className="muted">(optional)</span></label>
          <input id="display" className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={150} />
        </div>
        <p className="muted small">A simulated account comes with 120 days of generated history so charts are meaningful immediately.</p>
      </form>
    </Modal>
  );
}
