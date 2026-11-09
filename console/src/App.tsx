import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { getApiKey } from './api/session';
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
          path="/"
          element={
            <RequireAuth>
              <main>
                <h1>Ledger console</h1>
                <p>Operations console for the payments ledger.</p>
              </main>
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </HashRouter>
  );
}
