import React, { useState, useEffect } from "react";
import { CheckCircle2, HelpCircle } from "lucide-react";
import { DzirentService } from "../lib/supabaseClient";

interface RentalFollowUpCardProps {
  conversationId: string;
  userId: string;
  createdAt: string;
}

export const RentalFollowUpCard: React.FC<RentalFollowUpCardProps> = ({
  conversationId,
  userId,
  createdAt,
}) => {
  const [outcome, setOutcome] = useState<boolean | null>(null);
  const [isAnswered, setIsAnswered] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Check if 3 days have passed since conversation creation
  // For easy demonstration/testing, we also show it if conversation was created or older
  const conversationDate = new Date(createdAt).getTime();
  const now = Date.now();
  const threeDaysMs = 3 * 24 * 60 * 60 * 1000;
  const isEligible = now - conversationDate >= threeDaysMs || createdAt.includes("followup_test");

  useEffect(() => {
    let mounted = true;
    DzirentService.getRentalOutcome(conversationId, userId).then((res) => {
      if (mounted && res) {
        setIsAnswered(true);
        setOutcome(res.happened);
      }
    });
    return () => {
      mounted = false;
    };
  }, [conversationId, userId]);

  if (!isEligible || isAnswered) return null;

  const handleAnswer = async (happened: boolean) => {
    setIsSubmitting(true);
    try {
      await DzirentService.submitRentalOutcome(conversationId, userId, happened);
      setOutcome(happened);
      setIsAnswered(true);
    } catch (e) {
      console.error("Failed to submit rental outcome:", e);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-4 my-3 p-4 rounded-2xl bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800 shadow-sm transition-all animate-in fade-in">
      <div className="flex items-start space-x-3">
        <HelpCircle className="w-5 h-5 text-blue-600 dark:text-blue-400 shrink-0 mt-0.5" />
        <div className="flex-1 space-y-1">
          <h4 className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
            Did this rental happen?
          </h4>
          <p className="text-xs text-neutral-600 dark:text-neutral-300">
            Your anonymous response helps community trust and reliability.
          </p>

          <div className="pt-2 flex items-center space-x-2">
            <button
              onClick={() => handleAnswer(true)}
              disabled={isSubmitting}
              className="py-1.5 px-4 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-xs transition-colors flex items-center space-x-1 disabled:opacity-50"
            >
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>Yes</span>
            </button>
            <button
              onClick={() => handleAnswer(false)}
              disabled={isSubmitting}
              className="py-1.5 px-4 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-200/50 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300 text-xs font-semibold transition-colors disabled:opacity-50"
            >
              <span>No</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
