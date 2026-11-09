import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { getApiKey } from './api/session';
import Layout from './components/Layout';
import AccountsPage from './pages/AccountsPage';
import PaymentsPage from './pages/PaymentsPage';
import SignInPage from './pages/SignInPage';

function RequireAuth({ children }: { children: ReactNode }) {
  return getApiKey() ? children : <Navigate to="/signin" replace />;
}

function PlaceholderPage({ title }: { title: string }) {
  return (
    <section>
      <h1>{title}</h1>
      <p>This section is not available yet.</p>
    </section>
  );
}

export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/signin" element={<SignInPage />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Navigate to="/accounts" replace />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/payments" element={<PaymentsPage />} />
          <Route path="/operations" element={<PlaceholderPage title="Operations" />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </HashRouter>
  );
}
