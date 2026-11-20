import { useState } from 'react';
import type { FC, FormEvent } from 'react';
import { authApi } from '../../api/authApi';
import { ApiError } from '../../api/httpClient';
import logo from '../../assets/logo.jpg';
import { MIN_LENGTH, passwordProblem } from '../../utils/password';

interface ResetPasswordProps {
  token: string;
  onDone: () => void;
}

export const ResetPassword: FC<ResetPasswordProps> = ({ token, onDone }) => {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const problem = passwordProblem(password);
    if (problem) {
      setError(problem);
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      const reason = err instanceof ApiError ? (err.details as { reason?: string } | undefined)?.reason : undefined;
      setError(
        reason === 'weak_password'
          ? 'The password is too weak.'
          : reason === 'invalid_reset_token'
            ? 'This link is invalid or has expired. Request a new one.'
            : 'Could not reset the password. Please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-pink-50 p-4">
      <form onSubmit={handleSubmit} noValidate className="bg-white w-full max-w-sm rounded-2xl p-6 space-y-4 shadow-xl">
        <img src={logo} alt="Ilussiones Boutique" className="w-20 h-20 rounded-full mx-auto object-cover" />
        <h1 className="text-center font-bold text-gray-800 text-sm uppercase tracking-wider">Choose a new password</h1>
        {done ? (
          <>
            <p className="text-xs sm:text-sm text-gray-600 text-center">Your password was updated. You can now sign in.</p>
            <button
              type="button"
              onClick={onDone}
              className="w-full py-2.5 bg-pink-600 hover:bg-pink-700 text-white rounded-xl text-xs font-bold cursor-pointer"
            >
              Back to login
            </button>
          </>
        ) : (
          <>
            <div>
              <label htmlFor="new-password" className="block text-xs font-bold text-gray-700 mb-1">New password</label>
              <input
                id="new-password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full p-3 text-xs sm:text-sm border border-gray-200 rounded-xl bg-gray-50 focus:bg-white focus:border-pink-500 outline-none"
              />
            </div>
            <div>
              <label htmlFor="confirm-password" className="block text-xs font-bold text-gray-700 mb-1">Confirm password</label>
              <input
                id="confirm-password"
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                className="w-full p-3 text-xs sm:text-sm border border-gray-200 rounded-xl bg-gray-50 focus:bg-white focus:border-pink-500 outline-none"
              />
            </div>
            <p className="text-[11px] text-gray-500">At least {MIN_LENGTH} characters, with letters and numbers.</p>
            {error && (
              <p role="alert" className="bg-red-50 border border-red-200 text-red-600 text-xs font-semibold p-2.5 rounded-xl text-center">
                {error}
              </p>
            )}
            <button
              type="submit"
              disabled={submitting}
              className="w-full py-2.5 bg-pink-600 hover:bg-pink-700 disabled:opacity-60 text-white rounded-xl text-xs font-bold cursor-pointer"
            >
              {submitting ? 'Saving...' : 'Save password'}
            </button>
            <button type="button" onClick={onDone} className="w-full text-xs text-pink-600 font-semibold cursor-pointer">
              Cancel
            </button>
          </>
        )}
      </form>
    </div>
  );
};

export default ResetPassword;
