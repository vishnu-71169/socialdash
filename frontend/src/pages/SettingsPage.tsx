import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api/client';
import type { User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorAlert, useToast } from '../components/ui';

export default function SettingsPage() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [name, setName] = useState(user?.name ?? '');
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [profileError, setProfileError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const saveProfile = async (e: FormEvent) => {
    e.preventDefault();
    setProfileError(null);
    try {
      setUser(await api.put<User>('/users/me', { name }));
      toast('Profile updated');
    } catch (err) {
      setProfileError(errorMessage(err));
    }
  };

  const changePassword = async (e: FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    try {
      await api.put('/users/me/password', { currentPassword: current, newPassword: next });
      setCurrent('');
      setNext('');
      toast('Password changed');
    } catch (err) {
      setPasswordError(errorMessage(err));
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Settings</h1>
          <p>Manage your profile and sign-in details.</p>
        </div>
      </div>
      <div className="grid grid-2">
        <form className="card card-pad stack" onSubmit={saveProfile}>
          <h2>Profile</h2>
          <ErrorAlert message={profileError} />
          <div className="field">
            <label htmlFor="name">Name</label>
            <input id="name" className="input" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
          </div>
          <div className="field">
            <label>Email</label>
            <input className="input" value={user?.email ?? ''} disabled />
          </div>
          <div><button className="btn btn-primary" disabled={!name.trim() || name === user?.name}>Save profile</button></div>
        </form>
        <form className="card card-pad stack" onSubmit={changePassword}>
          <h2>Password</h2>
          <ErrorAlert message={passwordError} />
          <div className="field">
            <label htmlFor="cur">Current password</label>
            <input id="cur" className="input" type="password" autoComplete="current-password" value={current}
                   onChange={(e) => setCurrent(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="new">New password</label>
            <input id="new" className="input" type="password" autoComplete="new-password" minLength={8} value={next}
                   onChange={(e) => setNext(e.target.value)} required />
          </div>
          <div><button className="btn btn-primary" disabled={!current || next.length < 8}>Change password</button></div>
        </form>
      </div>
    </>
  );
}
