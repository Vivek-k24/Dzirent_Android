import React from "react";
import { Sparkles, ArrowRight, ShieldCheck, MapPin, MessageCircle } from "lucide-react";

interface WelcomeScreenProps {
  onGoToSignUp: () => void;
  onGoToLogin: () => void;
}

export const WelcomeScreen: React.FC<WelcomeScreenProps> = ({
  onGoToSignUp,
  onGoToLogin,
}) => {
  return (
    <div className="flex-1 flex flex-col justify-between p-6 bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 min-h-[580px]">
      {/* Top Branding */}
      <div className="pt-6 space-y-2 text-center">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-3xl bg-blue-600 text-white font-black text-2xl shadow-xl shadow-blue-500/20 mb-4 animate-in zoom-in-90 duration-300">
          DZ
        </div>
        <h1 className="text-3xl font-extrabold tracking-tight">Dzirent</h1>
        <p className="text-base text-neutral-600 dark:text-neutral-300 font-medium max-w-xs mx-auto">
          Rent outfits from closets near you
        </p>
      </div>

      {/* Middle Highlights */}
      <div className="my-8 space-y-4 max-w-xs mx-auto w-full">
        <div className="flex items-center space-x-3.5 p-3 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-100 dark:border-neutral-800">
          <div className="w-10 h-10 rounded-xl bg-blue-100 dark:bg-blue-950 flex items-center justify-center text-blue-600 dark:text-blue-400 shrink-0">
            <MapPin className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-sm font-semibold">Hyper-local & Private</h4>
            <p className="text-xs text-neutral-500">
              Only approximate ~1km distance is shared.
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-3.5 p-3 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-100 dark:border-neutral-800">
          <div className="w-10 h-10 rounded-xl bg-emerald-100 dark:bg-emerald-950 flex items-center justify-center text-emerald-600 dark:text-emerald-400 shrink-0">
            <MessageCircle className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-sm font-semibold">Direct Peer Messaging</h4>
            <p className="text-xs text-neutral-500">
              Connect directly with owners to rent clothes.
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-3.5 p-3 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-100 dark:border-neutral-800">
          <div className="w-10 h-10 rounded-xl bg-purple-100 dark:bg-purple-950 flex items-center justify-center text-purple-600 dark:text-purple-400 shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-sm font-semibold">Zero Middleman Fees</h4>
            <p className="text-xs text-neutral-500">
              No payment processing or hidden commission.
            </p>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="space-y-3 max-w-sm mx-auto w-full pb-4">
        <button
          onClick={onGoToSignUp}
          className="w-full py-4 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-base shadow-lg shadow-blue-500/25 flex items-center justify-center space-x-2 transition-all m3-button"
        >
          <span>Sign up</span>
          <ArrowRight className="w-5 h-5" />
        </button>

        <button
          onClick={onGoToLogin}
          className="w-full py-3.5 px-6 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 font-semibold text-sm text-neutral-800 dark:text-neutral-200 transition-colors m3-button"
        >
          Log in
        </button>
      </div>
    </div>
  );
};
