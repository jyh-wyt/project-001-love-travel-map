"use client";

import Link from "next/link";
import { CalendarDays, Heart, Map } from "lucide-react";

type BottomNavProps = {
  active: "map" | "plans" | "space";
};

const items = [
  { key: "map", href: "/", label: "地图", Icon: Map },
  { key: "plans", href: "/plans", label: "计划", Icon: CalendarDays },
  { key: "space", href: "/space", label: "我", Icon: Heart }
] as const;

export function BottomNav({ active }: BottomNavProps) {
  return (
    <nav className="bottom-nav" aria-label="主导航">
      {items.map(({ key, href, label, Icon }) => (
        <Link
          className={active === key ? "bottom-nav-item active" : "bottom-nav-item"}
          href={href}
          key={key}
        >
          <Icon aria-hidden="true" size={20} strokeWidth={2} />
          <span>{label}</span>
        </Link>
      ))}
    </nav>
  );
}
