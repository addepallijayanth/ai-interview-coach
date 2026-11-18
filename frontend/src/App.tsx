import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { RequireAuth } from './components/RequireAuth';
import { CheckEmailPage } from './pages/CheckEmailPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { HistoryPage } from './pages/HistoryPage';
import { InterviewPage } from './pages/InterviewPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ReportPage } from './pages/ReportPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { ResultsPage } from './pages/ResultsPage';
import { UploadPage } from './pages/UploadPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/check-email" element={<CheckEmailPage />} />
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />
            <Route path="/" element={<RequireAuth><UploadPage /></RequireAuth>} />
            <Route path="/history" element={<RequireAuth><HistoryPage /></RequireAuth>} />
            <Route path="/analyses/:id" element={<RequireAuth><ResultsPage /></RequireAuth>} />
            <Route path="/interviews/:id" element={<RequireAuth><InterviewPage /></RequireAuth>} />
            <Route path="/reports/:id" element={<RequireAuth><ReportPage /></RequireAuth>} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

function NotFound() {
  return (
    <div className="empty">
      <h1>Page not found</h1>
      <Link to="/">Back to the start</Link>
    </div>
  );
}
