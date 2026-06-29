import type { Metadata } from "next";
import Script from "next/script";
import { ThemeInitializer } from "@/shared/components/ThemeInitializer";
import "@baseline-ui/css";
import "./styles.css";

export const metadata: Metadata = {
  title: "\u60c5\u4fa3\u65c5\u884c\u5730\u56fe",
  description: "\u7ed9\u60c5\u4fa3\u4f7f\u7528\u7684\u79c1\u5bc6\u65c5\u884c\u8bb0\u5f55\u4e0e\u65c5\u884c\u8ba1\u5212\u7f51\u9875",
  icons: {
    icon: [
      { url: "/love-travel-icon-192.png", sizes: "192x192", type: "image/png" },
      { url: "/love-travel-icon.png", sizes: "512x512", type: "image/png" }
    ],
    shortcut: ["/love-travel-icon-192.png"],
    apple: [{ url: "/love-travel-icon.png", sizes: "512x512", type: "image/png" }]
  }
};

const themeInitScript = `
(function () {
  try {
    var theme = window.localStorage.getItem("love-travel-theme") === "dark" ? "dark" : "light";
    document.documentElement.dataset.theme = theme;
  } catch (error) {
    document.documentElement.dataset.theme = "light";
  }
})();
`;

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <Script id="theme-init" strategy="beforeInteractive" dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>
        <ThemeInitializer />
        {children}
      </body>
    </html>
  );
}
