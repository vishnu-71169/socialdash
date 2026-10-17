import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { BrandMark } from '../components/Layout';
import { ErrorAlert } from '../components/ui';

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'login') await login(email, password);
      else await register(name, email, password);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <BrandMark />
        <div>
          <h1>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
          <p className="text-2" style={{ marginTop: 4 }}>
            {mode === 'login'
              ? 'Sign in to manage all your social accounts in one place.'
              : 'Connect accounts, schedule posts and track performance.'}
          </p>
        </div>
        <ErrorAlert message={error} />
        {mode === 'register' && (
          <div className="field">
            <label htmlFor="name">Name</label>
            <input id="name" className="input" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
          </div>
        )}
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" className="input" type="email" autoComplete="email" value={email}
                 onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" className="input" type="password" minLength={mode === 'register' ? 8 : undefined}
                 autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password}
                 onChange={(e) => setPassword(e.target.value)} required />
          {mode === 'register' && <span className="muted small">At least 8 characters.</span>}
        </div>
        <button className="btn btn-primary" disabled={busy} type="submit">
          {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
        {mode === 'login' ? (
          <>
            <p className="text-2 small" style={{ textAlign: 'center' }}>
              No account? <Link to="/register">Create one</Link>
            </p>
            <div className="alert alert-info small">
              Demo data is seeded on first start — see the README for the demo login.
            </div>
          </>
        ) : (
          <p className="text-2 small" style={{ textAlign: 'center' }}>
            Already registered? <Link to="/login">Sign in</Link>
          </p>
        )}
      </form>
    </div>
  );
}
