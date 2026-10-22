import React, { useState } from "react";
import { ArrowLeft, User, Mail, Lock, Check, AlertCircle, Loader2 } from "lucide-react";
import { UserRole } from "../types/database";
import { DzirentService } from "../lib/supabaseClient";

interface SignUpScreenProps {
  onSuccess: (role: UserRole) => void;
  onGoToLogin: () => void;
  onBack: () => void;
}

export const SignUpScreen: React.FC<SignUpScreenProps> = ({
  onSuccess,
  onGoToLogin,
  onBack,
}) => {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<UserRole>("both");
  const [adultConfirmed, setAdultConfirmed] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!displayName.trim()) {
      setErrorMessage("Please enter your display name");
      return;
    }
    if (!email.trim() || !email.includes("@")) {
      setErrorMessage("Please enter a valid email address");
      return;
    }
    if (password.length < 6) {
      setErrorMessage("Password must be at least 6 characters");
      return;
    }
    if (!adultConfirmed) {
      setErrorMessage("You must be 18 or older to use Dzirent");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const res = await DzirentService.signUp({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
        role,
        adultConfirmed,
        city: "Current Location",
        lat: 37.77,
        lng: -122.42,
      });

      if (res.error) {
        setErrorMessage(res.error);
      } else {
        onSuccess(role);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || "Sign up failed. Please try again.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col p-6 bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 overflow-y-auto">
      {/* Top Bar */}
      <div className="flex items-center space-x-3 mb-6">
        <button
          onClick={onBack}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300"
          aria-label="Go back"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h2 className="text-xl font-bold">Create your account</h2>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4.5 max-w-sm mx-auto w-full flex-1 flex flex-col justify-between">
        <div className="space-y-4">
          {/* Display Name */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Display Name
            </label>
            <div className="relative">
              <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
              <input
                type="text"
                required
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="e.g. Alex Rivera"
                className="w-full pl-10 pr-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 dark:focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Email */}
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

          {/* Password */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Password
            </label>
            <div className="relative">
              <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
              <input
                type="password"
                required
                minLength={6}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 6 characters"
                className="w-full pl-10 pr-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 dark:focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Role Choice */}
          <div className="space-y-2 pt-1">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              How do you plan to use Dzirent?
            </label>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => setRole("renter")}
                className={`py-3 px-2 rounded-2xl border text-xs font-semibold flex flex-col items-center justify-center space-y-1 transition-all ${
                  role === "renter"
                    ? "bg-blue-50 border-blue-600 text-blue-700 dark:bg-blue-950/60 dark:border-blue-500 dark:text-blue-300 shadow-xs"
                    : "border-neutral-200 dark:border-neutral-800 bg-neutral-50 dark:bg-neutral-800/60 text-neutral-600 dark:text-neutral-400 hover:border-neutral-300"
                }`}
              >
                <span>Rent outfits</span>
              </button>

              <button
                type="button"
                onClick={() => setRole("lender")}
                className={`py-3 px-2 rounded-2xl border text-xs font-semibold flex flex-col items-center justify-center space-y-1 transition-all ${
                  role === "lender"
                    ? "bg-blue-50 border-blue-600 text-blue-700 dark:bg-blue-950/60 dark:border-blue-500 dark:text-blue-300 shadow-xs"
                    : "border-neutral-200 dark:border-neutral-800 bg-neutral-50 dark:bg-neutral-800/60 text-neutral-600 dark:text-neutral-400 hover:border-neutral-300"
                }`}
              >
                <span>Lend clothes</span>
              </button>

              <button
                type="button"
                onClick={() => setRole("both")}
                className={`py-3 px-2 rounded-2xl border text-xs font-semibold flex flex-col items-center justify-center space-y-1 transition-all ${
                  role === "both"
                    ? "bg-blue-50 border-blue-600 text-blue-700 dark:bg-blue-950/60 dark:border-blue-500 dark:text-blue-300 shadow-xs"
                    : "border-neutral-200 dark:border-neutral-800 bg-neutral-50 dark:bg-neutral-800/60 text-neutral-600 dark:text-neutral-400 hover:border-neutral-300"
                }`}
              >
                <span>Both</span>
              </button>
            </div>
          </div>

          {/* 18+ Checkbox (Required) */}
          <label className="flex items-start space-x-3 pt-2 cursor-pointer select-none">
            <div className="relative flex items-center justify-center mt-0.5">
              <input
                type="checkbox"
                required
                checked={adultConfirmed}
                onChange={(e) => setAdultConfirmed(e.target.checked)}
                className="w-5 h-5 rounded-lg border-neutral-300 dark:border-neutral-700 text-blue-600 focus:ring-blue-500 focus:ring-offset-0 cursor-pointer"
              />
            </div>
            <span className="text-xs text-neutral-600 dark:text-neutral-300 leading-tight">
              I am 18 years of age or older and agree to peer-to-peer rental guidelines.
            </span>
          </label>

          {errorMessage && (
            <div className="p-3 rounded-2xl bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 text-xs text-red-600 dark:text-red-300 flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}
        </div>

        {/* Submit */}
        <div className="pt-6 space-y-3">
          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-4 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-base shadow-lg shadow-blue-500/25 flex items-center justify-center space-x-2 transition-all m3-button disabled:opacity-50"
          >
            {isLoading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <span>Create Account</span>
            )}
          </button>

          <p className="text-center text-xs text-neutral-500">
            Already have an account?{" "}
            <button
              type="button"
              onClick={onGoToLogin}
              className="font-semibold text-blue-600 hover:underline dark:text-blue-400"
            >
              Log in
            </button>
          </p>
        </div>
      </form>
    </div>
  );
};
