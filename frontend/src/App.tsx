import { Navigate, Route, Routes } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import AccountsPage from './pages/AccountsPage';
import AuthPage from './pages/AuthPage';
import DashboardPage from './pages/DashboardPage';
import EngagementPage from './pages/EngagementPage';
import ListeningPage from './pages/ListeningPage';
import PublisherPage from './pages/PublisherPage';
import SettingsPage from './pages/SettingsPage';

export default function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="auth-page">
        <Loader2 className="spin" />
      </div>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="publisher" element={<PublisherPage />} />
        <Route path="engagement" element={<EngagementPage />} />
        <Route path="listening" element={<ListeningPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
