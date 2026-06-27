"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import * as echarts from "echarts/core";
import { MapChart } from "echarts/charts";
import { GeoComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import type { ECElementEvent, EChartsOption } from "echarts";
import type { Province } from "@/data/travelData";
import { requestJson } from "@/shared/lib/api";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

type GeoFeature = {
  properties?: {
    name?: string;
    adcode?: number | string;
    center?: [number, number];
    centroid?: [number, number];
  };
};

type ProvinceGeoJson = {
  type: "FeatureCollection";
  features?: GeoFeature[];
};

type MapCity = {
  code: string;
  name: string;
  cover: string;
  dateRange: string;
  hasRecord: boolean;
};

type VisitedRegion = {
  provinceCode: string;
  cityCode: string;
  cityName: string;
  coverImageUrl?: string;
  startDate?: string;
  endDate?: string;
  recordCount: number;
};

const LOCAL_BOUNDARY_URL = "/geojson/bound";

echarts.use([MapChart, GeoComponent, TooltipComponent, CanvasRenderer]);

export function ProvinceCityMap({ province }: { province: Province }) {
  const router = useRouter();
  const { authLoading, currentUser } = useAuthGuard();
  const mapName = `province-${province.code}`;
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [mapCities, setMapCities] = useState<MapCity[]>([]);

  useEffect(() => {
    let cancelled = false;

    async function loadProvinceMap() {
      if (authLoading) {
        return;
      }
      if (!currentUser) {
        setStatus("error");
        return;
      }

      try {
        const response = await fetch(`${LOCAL_BOUNDARY_URL}/${province.code}_full.json`);
        if (!response.ok) {
          throw new Error(`Province GeoJSON request failed: ${response.status}`);
        }
        const visitedRegions = await requestJson<VisitedRegion[]>(`/api/map/visited-regions?provinceCode=${province.code}`);
        const visitedByCode = new Map(visitedRegions.map((region) => [region.cityCode, region]));
        const geoJson = (await response.json()) as ProvinceGeoJson;
        if (cancelled) {
          return;
        }

        echarts.registerMap(mapName, geoJson as Parameters<typeof echarts.registerMap>[1]);
        const nextCities: MapCity[] = [];
        for (const feature of geoJson.features ?? []) {
          const name = feature.properties?.name;
          const code = String(feature.properties?.adcode ?? "");
          if (name && code) {
            const regionByCode = visitedByCode.get(code);
            const region = regionByCode && isSameCityName(regionByCode.cityName, name) ? regionByCode : undefined;
            nextCities.push({
              code,
              name,
              cover: region?.coverImageUrl ?? "",
              dateRange: formatDateRange(region?.startDate, region?.endDate),
              hasRecord: Boolean(region)
            });
          }
        }
        setMapCities(nextCities);
        setStatus("ready");
      } catch {
        if (!cancelled) {
          setStatus("error");
        }
      }
    }

    loadProvinceMap();

    return () => {
      cancelled = true;
    };
  }, [authLoading, currentUser, mapName, province.code]);

  const option = useMemo<EChartsOption>(
    () => ({
      animationDuration: 220,
      tooltip: {
        trigger: "item",
        formatter: (params) => {
          const item = Array.isArray(params) ? params[0] : params;
          const data = item.data as { cityCode?: string } | undefined;
          const itemName = String(item.name ?? "");
          const city = mapCities.find((current) => current.code === data?.cityCode) ?? findCityByMapName(mapCities, itemName);
          if (!city?.hasRecord) {
            return `${itemName}<br/>还没有旅行记录`;
          }
          return `${itemName}<br/>${city.dateRange || "已有旅行记录"}`;
        }
      },
      series: [
        {
          type: "map",
          map: mapName,
          silent: false,
          zlevel: 2,
          roam: true,
          roamTrigger: "selfRect",
          zoom: 1.05,
          scaleLimit: {
            min: 0.8,
            max: 10
          },
          itemStyle: {
            areaColor: "#e8ece4",
            borderColor: "#ffffff",
            borderWidth: 1
          },
          emphasis: {
            itemStyle: {
              areaColor: "#c9ded5"
            },
            label: {
              color: "#1f2b27",
              fontWeight: 700,
              show: true
            }
          },
          label: {
            show: true,
            formatter: (params) => formatMapCityLabel(params, mapCities),
            color: "#33413c",
            fontSize: 11,
            lineHeight: 15,
            rich: {
              city: {
                color: "#33413c",
                fontSize: 11,
                fontWeight: 700,
                lineHeight: 15
              },
              date: {
                color: "#1f2b27",
                fontSize: 11,
                fontWeight: 800,
                lineHeight: 14
              }
            }
          },
          data: mapCities.map((city) => ({
            name: city.name,
            value: city.hasRecord ? 1 : 0,
            cityCode: city.code,
            cityName: city.name,
            itemStyle: {
              areaColor: city.hasRecord ? "#4d8f79" : "#e8ece4"
            },
            emphasis: {
              itemStyle: {
                areaColor: city.hasRecord ? "#4d8f79" : "#c9ded5"
              },
              label: {
                color: city.hasRecord ? "#1f2b27" : "#1f2b27",
                fontWeight: 700,
                show: true
              }
            }
          }))
        }
      ]
    }),
    [mapCities, mapName]
  );

  const openCity = useCallback((cityCode: string, cityName: string) => {
    router.push(
      `/regions/${cityCode}?provinceCode=${province.code}&provinceName=${encodeURIComponent(province.name)}&cityName=${encodeURIComponent(cityName)}`
    );
  }, [province.code, province.name, router]);

  return (
    <section className="province-map-page" aria-label={`${province.name}城市地图`}>
      <div className="province-map-header">
        <div>
          <p className="section-label">{province.name}</p>
          <h1>点击城市查看记录</h1>
        </div>
        <p>有旅行内容的城市会显示日期，所有城市都可以点击进入记录。</p>
      </div>

      <div className="province-map-board">
        {status === "ready" ? (
          <ProvinceEChartView option={option} cities={mapCities} onCityClick={openCity} />
        ) : (
          <div className="map-loading" role="status">
            {status === "loading" ? "正在加载省内城市地图..." : "省内 GeoJSON 加载失败，请检查网络后刷新。"}
          </div>
        )}
      </div>
      <div className="map-tip-row" aria-label="地图操作提示">
        <span>单击城市进入</span>
        <span>按住拖动</span>
        <span>滚轮或双指缩放</span>
      </div>
    </section>
  );
}

function ProvinceEChartView({
  option,
  cities,
  onCityClick
}: {
  option: EChartsOption;
  cities: MapCity[];
  onCityClick: (cityCode: string, cityName: string) => void;
}) {
  const ref = useProvinceEChart(option, cities, onCityClick);
  return <div className="echart-map" ref={ref} role="img" aria-label="可点击城市地图" />;
}

function useProvinceEChart(
  option: EChartsOption,
  cities: MapCity[],
  onCityClick: (cityCode: string, cityName: string) => void
) {
  const [node, setNode] = useState<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!node) {
      return;
    }
    const chart = echarts.init(node);
    chart.setOption(option);
    let pointerDownPoint: [number, number] | null = null;
    let isDragging = false;
    let lastPointerUpWasDrag = false;
    const releaseRoam = () => {
      pointerDownPoint = null;
      lastPointerUpWasDrag = isDragging;
      window.setTimeout(() => {
        isDragging = false;
        lastPointerUpWasDrag = false;
      }, 220);
    };
    const trackPointerDown = (event: PointerEvent) => {
      pointerDownPoint = [event.clientX, event.clientY];
      isDragging = false;
      lastPointerUpWasDrag = false;
    };
    const trackPointerMove = (event: PointerEvent) => {
      if (!pointerDownPoint) {
        return;
      }
      const distance = Math.hypot(event.clientX - pointerDownPoint[0], event.clientY - pointerDownPoint[1]);
      if (distance > 6) {
        isDragging = true;
      }
    };
    const openCityByEventParams = (item: ECElementEvent) => {
      if (isDragging || lastPointerUpWasDrag) {
        releaseRoam();
        return;
      }
      const data = getCityEventData(item.data);
      const city =
        cities.find((current) => current.code === data.cityCode) ??
        findCityByMapName(cities, data.cityName || String(item.name ?? ""));
      if (city) {
        onCityClick(city.code, city.name);
      }
      releaseRoam();
    };
    node.addEventListener("pointerdown", trackPointerDown, { passive: true });
    node.addEventListener("pointermove", trackPointerMove, { passive: true });
    node.addEventListener("pointerup", releaseRoam, { passive: true });
    node.addEventListener("pointercancel", releaseRoam, { passive: true });
    chart.on("click", openCityByEventParams);
    const resize = () => chart.resize();
    window.addEventListener("resize", resize);
    window.addEventListener("mouseup", releaseRoam);
    window.addEventListener("touchend", releaseRoam);
    window.addEventListener("pointerup", releaseRoam);
    return () => {
      window.removeEventListener("resize", resize);
      window.removeEventListener("mouseup", releaseRoam);
      window.removeEventListener("touchend", releaseRoam);
      window.removeEventListener("pointerup", releaseRoam);
      chart.off("click", openCityByEventParams);
      node.removeEventListener("pointerdown", trackPointerDown);
      node.removeEventListener("pointermove", trackPointerMove);
      node.removeEventListener("pointerup", releaseRoam);
      node.removeEventListener("pointercancel", releaseRoam);
      chart.dispose();
    };
  }, [cities, node, onCityClick, option]);

  return setNode;
}

function formatDateRange(startDate?: string, endDate?: string) {
  if (!startDate && !endDate) {
    return "";
  }

  const start = parseDateParts(startDate || endDate || "");
  const end = parseDateParts(endDate || startDate || "");
  if (!start) {
    return startDate || endDate || "";
  }
  if (!end || startDate === endDate || !endDate) {
    return `${start.year}年${start.month}.${start.day}`;
  }
  return start.year === end.year
    ? `${start.year}年${start.month}.${start.day}-${end.month}.${end.day}`
    : `${start.year}年${start.month}.${start.day}-${end.year}年${end.month}.${end.day}`;
}

function parseDateParts(value: string) {
  const matched = value.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (!matched) {
    return null;
  }
  return {
    year: Number(matched[1]),
    month: Number(matched[2]),
    day: Number(matched[3])
  };
}

function normalizeCityName(name: string) {
  return name.replace(/[市区县盟州地区]$/, "");
}

function isSameCityName(left: string, right: string) {
  return normalizeCityName(left) === normalizeCityName(right);
}

function formatMapCityLabel(params: unknown, cities: MapCity[]) {
  const itemName = String((params as { name?: string }).name ?? "");
  const city = findCityByMapName(cities, itemName);
  if (!city?.hasRecord) {
    return itemName;
  }
  return `{city|${city.name}}\n{date|${city.dateRange || "已有记录"}}`;
}

function findCityByMapName(cities: MapCity[], name: string) {
  return cities.find((city) => city.name === name || isSameCityName(city.name, name));
}

function getCityEventData(data: ECElementEvent["data"]) {
  if (data && typeof data === "object" && !Array.isArray(data) && !(data instanceof Date)) {
    const item = data as { cityCode?: unknown; cityName?: unknown };
    return {
      cityCode: typeof item.cityCode === "string" ? item.cityCode : "",
      cityName: typeof item.cityName === "string" ? item.cityName : ""
    };
  }
  return {
    cityCode: "",
    cityName: ""
  };
}
