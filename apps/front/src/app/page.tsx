import { BottomNav } from "@/shared/components/BottomNav";
import { ProvinceMap } from "@/features/map/components/ProvinceMap";

export default function HomePage() {
  return (
    <main className="app-shell">
      <ProvinceMap />
      <BottomNav active="map" />
    </main>
  );
}
