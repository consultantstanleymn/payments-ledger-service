import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { getApiKey } from './api/session';
import Layout from './components/Layout';
import AccountsPage from './pages/AccountsPage';
import OperationsPage from './pages/OperationsPage';
import PaymentsPage from './pages/PaymentsPage';
import SignInPage from './pages/SignInPage';

function RequireAuth({ children }: { children: ReactNode }) {
  return getApiKey() ? children : <Navigate to="/signin" replace />;
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
          <Route path="/operations" element={<OperationsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </HashRouter>
  );
}
