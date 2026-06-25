"use client";

import { useEffect, useState } from "react";
import { fetchCurrentUser, toErrorMessage, type CurrentUser } from "@/shared/lib/api";

type AuthGuardState = {
  authLoading: boolean;
  authErrorMessage: string;
  currentUser: CurrentUser | null;
};

export function useAuthGuard(): AuthGuardState {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [authErrorMessage, setAuthErrorMessage] = useState("");

  useEffect(() => {
    let ignore = false;

    async function syncSession() {
      try {
        setAuthLoading(true);
        setAuthErrorMessage("");
        const user = await fetchCurrentUser();
        if (!ignore) {
          setCurrentUser(user);
        }
      } catch (error) {
        if (!ignore) {
          setCurrentUser(null);
          setAuthErrorMessage(toErrorMessage(error));
        }
      } finally {
        if (!ignore) {
          setAuthLoading(false);
        }
      }
    }

    syncSession();
    return () => {
      ignore = true;
    };
  }, []);

  return {
    authLoading,
    authErrorMessage,
    currentUser
  };
}
