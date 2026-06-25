import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { CityJournal } from "@/features/travel/components/CityJournal";
import { findCity, findProvince, type City } from "@/data/travelData";

type CityPageProps = {
  params: Promise<{
    cityCode: string;
  }>;
  searchParams: Promise<{
    provinceCode?: string;
    provinceName?: string;
    cityName?: string;
  }>;
};

export default async function CityPage({ params, searchParams }: CityPageProps) {
  const { cityCode } = await params;
  const query = await searchParams;
  const result = findCity(cityCode);
  const province = query.provinceCode ? findProvince(query.provinceCode) : result?.province;
  const city = query.cityName ? createEmptyCity(cityCode, query.cityName) : result?.city;

  if (!province || !city) {
    notFound();
  }

  const provinceName = query.provinceName ? decodeURIComponent(query.provinceName) : province.name;
  const backToChinaMap = isDirectCityProvince(province);
  const backHref = backToChinaMap ? "/" : `/provinces/${province.code}`;
  const backLabel = backToChinaMap ? "返回中国地图" : `返回${provinceName}地图`;

  return (
    <main className="app-shell detail-shell">
      <Link className="back-link" href={backHref}>
        <ChevronLeft aria-hidden="true" size={18} />
        {backLabel}
      </Link>
      <CityJournal
        cityCode={city.code}
        cityName={city.name}
        provinceCode={province.code}
        provinceName={provinceName}
      />
    </main>
  );
}

function createEmptyCity(cityCode: string, cityName?: string): City | null {
  if (!cityName) {
    return null;
  }

  return {
    code: cityCode,
    name: decodeURIComponent(cityName),
    visited: false,
    cover: "",
    dateRange: "",
    records: []
  };
}

function isDirectCityProvince(province: NonNullable<ReturnType<typeof findProvince>>) {
  if (province.cities.length !== 1) {
    return false;
  }
  return normalizeRegionName(province.name) === normalizeRegionName(province.cities[0].name);
}

function normalizeRegionName(name: string) {
  return name.replace(/[省市]$/, "");
}
