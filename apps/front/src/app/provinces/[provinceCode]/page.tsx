import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { ProvinceCityMap } from "@/features/map/components/ProvinceCityMap";
import { findProvince } from "@/data/travelData";

type ProvincePageProps = {
  params: Promise<{
    provinceCode: string;
  }>;
};

export default async function ProvincePage({ params }: ProvincePageProps) {
  const { provinceCode } = await params;
  const province = findProvince(provinceCode);
  if (!province) {
    notFound();
  }

  return (
    <main className="app-shell detail-shell">
      <Link className="back-link" href="/">
        <ChevronLeft aria-hidden="true" size={18} />
        返回中国地图
      </Link>
      <ProvinceCityMap province={province} />
    </main>
  );
}
