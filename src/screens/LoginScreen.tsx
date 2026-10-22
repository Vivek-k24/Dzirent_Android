import React, { useState } from "react";
import { ArrowLeft, Mail, Lock, AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { DzirentService } from "../lib/supabaseClient";

interface LoginScreenProps {
  onSuccess: () => void;
  onGoToSignUp: () => void;
  onBack: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({
  onSuccess,
  onGoToSignUp,
  onBack,
}) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Forgot password modal state
  const [isForgotModalOpen, setIsForgotModalOpen] = useState(false);
  const [forgotEmail, setForgotEmail] = useState("");
  const [forgotStatus, setForgotStatus] = useState<string | null>(null);
  const [isSendingReset, setIsSendingReset] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password) {
      setErrorMessage("Please enter both email and password");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const res = await DzirentService.signIn(email.trim(), password);
      if (res.error) {
        setErrorMessage(res.error);
      } else {
        onSuccess();
      }
    } catch (err: any) {
      setErrorMessage(err?.message || "Sign in failed. Check credentials.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendResetEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!forgotEmail.trim()) return;

    setIsSendingReset(true);
    setForgotStatus(null);
    try {
      const res = await DzirentService.resetPassword(forgotEmail.trim());
      if (res.success) {
        setForgotStatus("Password reset link has been sent to your email!");
      } else {
        setForgotStatus(`Error: ${res.error || "Failed to send reset email"}`);
      }
    } catch (e: any) {
      setForgotStatus(`Error: ${e?.message || "Failed to send reset email"}`);
    } finally {
      setIsSendingReset(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col p-6 bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 overflow-y-auto">
      {/* Top Bar */}
      <div className="flex items-center space-x-3 mb-8">
        <button
          onClick={onBack}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300"
          aria-label="Go back"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h2 className="text-xl font-bold">Welcome back</h2>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm mx-auto w-full flex-1 flex flex-col justify-between">
        <div className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Email
            </label>
            <div className="relative">
              <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="alex@example.com"
                className="w-full pl-10 pr-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 dark:focus:ring-blue-500"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
                Password
              </label>
              <button
                type="button"
                onClick={() => {
                  setForgotEmail(email);
                  setIsForgotModalOpen(true);
                }}
                className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium"
              >
                Forgot password?
              </button>
            </div>
            <div className="relative">
              <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Your password"
                className="w-full pl-10 pr-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 dark:focus:ring-blue-500"
              />
            </div>
          </div>

          {errorMessage && (
            <div className="p-3 rounded-2xl bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 text-xs text-red-600 dark:text-red-300 flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}
        </div>

        <div className="pt-6 space-y-3">
          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-4 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-base shadow-lg shadow-blue-500/25 flex items-center justify-center space-x-2 transition-all m3-button disabled:opacity-50"
          >
            {isLoading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <span>Log in</span>
            )}
          </button>

          <p className="text-center text-xs text-neutral-500">
            Don't have an account?{" "}
            <button
              type="button"
              onClick={onGoToSignUp}
              className="font-semibold text-blue-600 hover:underline dark:text-blue-400"
            >
              Sign up
            </button>
          </p>
        </div>
      </form>

      {/* Forgot Password Modal */}
      {isForgotModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
            <h3 className="text-lg font-bold text-neutral-900 dark:text-neutral-100">
              Reset Password
            </h3>
            <p className="text-xs text-neutral-500 dark:text-neutral-400">
              Enter your email address and Supabase Auth will send a secure password reset link.
            </p>

            <form onSubmit={handleSendResetEmail} className="space-y-3">
              <input
                type="email"
                required
                value={forgotEmail}
                onChange={(e) => setForgotEmail(e.target.value)}
                placeholder="your.email@example.com"
                className="w-full px-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />

              {forgotStatus && (
                <div
                  className={`p-3 rounded-2xl text-xs flex items-center space-x-2 ${
                    forgotStatus.startsWith("Error")
                      ? "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300"
                      : "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300"
                  }`}
                >
                  {forgotStatus.startsWith("Error") ? (
                    <AlertCircle className="w-4 h-4 shrink-0" />
                  ) : (
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                  )}
                  <span>{forgotStatus}</span>
                </div>
              )}

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsForgotModalOpen(false)}
                  className="flex-1 py-2.5 rounded-full border border-neutral-300 dark:border-neutral-700 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSendingReset}
                  className="flex-1 py-2.5 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md disabled:opacity-50"
                >
                  {isSendingReset ? "Sending..." : "Send Reset Link"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
