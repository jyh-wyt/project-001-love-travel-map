"use client";

import { useLayoutEffect } from "react";

const THEME_KEY = "love-travel-theme";

function applyStoredTheme() {
  const theme = window.localStorage.getItem(THEME_KEY) === "dark" ? "dark" : "light";
  document.documentElement.dataset.theme = theme;
}

export function ThemeInitializer() {
  useLayoutEffect(() => {
    applyStoredTheme();

    function handleStorage(event: StorageEvent) {
      if (event.key === THEME_KEY) {
        applyStoredTheme();
      }
    }

    window.addEventListener("storage", handleStorage);
    return () => window.removeEventListener("storage", handleStorage);
  }, []);

  return null;
}