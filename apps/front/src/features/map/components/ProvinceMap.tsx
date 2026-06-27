"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import * as echarts from "echarts/core";
import { MapChart } from "echarts/charts";
import { TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import type { ECElementEvent, EChartsOption } from "echarts";
import { provinces } from "@/data/travelData";
import { requestJson } from "@/shared/lib/api";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

const CHINA_MAP_NAME = "china";
const CHINA_GEOJSON_URL = "/geojson/bound/100000_full.json";

echarts.use([MapChart, TooltipComponent, CanvasRenderer]);

type VisitedRegion = {
  provinceCode: string;
  cityCode: string;
  recordCount: number;
};

export function ProvinceMap() {
  const router = useRouter();
  const { authLoading, currentUser } = useAuthGuard();
  const [mapStatus, setMapStatus] = useState<"loading" | "ready" | "error">("loading");
  const [visitedRegions, setVisitedRegions] = useState<VisitedRegion[]>([]);
  const visitedCountByProvince = useMemo(() => {
    const counts = new Map<string, number>();
    for (const region of visitedRegions) {
      counts.set(region.provinceCode, (counts.get(region.provinceCode) ?? 0) + 1);
    }
    return counts;
  }, [visitedRegions]);

  useEffect(() => {
    let cancelled = false;

    async function loadVisitedRegions() {
      if (authLoading || !currentUser) {
        return;
      }

      try {
        const regions = await requestJson<VisitedRegion[]>("/api/map/visited-regions");
        if (!cancelled) {
          setVisitedRegions(regions);
        }
      } catch {
        if (!cancelled) {
          setVisitedRegions([]);
        }
      }
    }

    loadVisitedRegions();
    return () => {
      cancelled = true;
    };
  }, [authLoading, currentUser]);

  useEffect(() => {
    let cancelled = false;

    async function loadChinaMap() {
      try {
        const response = await fetch(CHINA_GEOJSON_URL);
        if (!response.ok) {
          throw new Error(`GeoJSON request failed: ${response.status}`);
        }
        const geoJson = await response.json();
        if (cancelled) {
          return;
        }
        echarts.registerMap(CHINA_MAP_NAME, geoJson);
        setMapStatus("ready");
      } catch {
        if (!cancelled) {
          setMapStatus("error");
        }
      }
    }

    loadChinaMap();

    return () => {
      cancelled = true;
    };
  }, []);

  const option = useMemo<EChartsOption>(
    () => ({
      animationDuration: 220,
      tooltip: {
        formatter: (params) => {
          const item = Array.isArray(params) ? params[0] : params;
          const province = findProvinceByMapName(String(item.name ?? ""));
          const count = province ? visitedCountByProvince.get(province.code) ?? 0 : 0;
          return `${item.name}<br/>已记录城市：${count}`;
        }
      },
      series: [
        {
          type: "map",
          map: CHINA_MAP_NAME,
          roam: true,
          roamTrigger: "selfRect",
          zoom: 1.18,
          scaleLimit: {
            min: 0.9,
            max: 8
          },
          selectedMode: false,
          label: {
            show: true,
            color: "#33413c",
            fontSize: 10
          },
          itemStyle: {
            areaColor: "#e4e7df",
            borderColor: "#ffffff",
            borderWidth: 1
          },
          emphasis: {
            label: {
              color: "#1f2b27",
              fontWeight: 700
            },
            itemStyle: {
              areaColor: "#b9d2c9"
            }
          },
          data: provinces.map((province) => {
            const count = visitedCountByProvince.get(province.code) ?? 0;
            return {
              name: toMapProvinceName(province.name),
              value: count,
              provinceCode: province.code,
              itemStyle: {
                areaColor: count > 0 ? "#4d8f79" : "#d8d7cf"
              },
              label: {
                color: "#33413c",
                fontWeight: count > 0 ? 700 : 500
              }
            };
          })
        }
      ]
    }),
    [visitedCountByProvince]
  );

  function openProvince(provinceCode: string) {
    const province = provinces.find((item) => item.code === provinceCode);
    const directCity = province ? getDirectCity(province) : null;
    if (province && directCity) {
      router.push(
        `/regions/${directCity.code}?provinceCode=${province.code}&provinceName=${encodeURIComponent(
          province.name
        )}&cityName=${encodeURIComponent(directCity.name)}`
      );
      return;
    }
    router.push(`/provinces/${provinceCode}`);
  }

  return (
    <section className="map-workspace single" aria-label="旅行地图">
      <div className="map-panel">
        <div className="map-header">
          <div>
            <p className="section-label">我们的旅行地图</p>
            <h1>从地图进入每一段回忆</h1>
          </div>
          <div className="map-count" aria-label="已点亮城市数量">
            <strong>{visitedRegions.length}</strong>
            <span>已点亮城市</span>
          </div>
        </div>

        <div className="map-board">
          {mapStatus === "ready" ? (
            <EChartView option={option} onProvinceClick={openProvince} />
          ) : (
            <div className="map-loading" role="status">
              {mapStatus === "loading" ? "正在加载标准中国地图..." : "标准 GeoJSON 加载失败，请检查网络后刷新。"}
            </div>
          )}
        </div>
        <div className="map-tip-row" aria-label="地图操作提示">
          <span>单击进入</span>
          <span>按住拖动</span>
          <span>滚轮或双指缩放</span>
        </div>
        <p className="map-note">上海、重庆会直接进入城市记录，其他省份进入省内城市地图。</p>
      </div>
    </section>
  );
}

function EChartView({
  option,
  onProvinceClick
}: {
  option: EChartsOption;
  onProvinceClick: (provinceCode: string) => void;
}) {
  const ref = useEChart(option, onProvinceClick);
  return <div className="echart-map" ref={ref} role="img" aria-label="可点击省份地图" />;
}

function useEChart(option: EChartsOption, onProvinceClick: (provinceCode: string) => void) {
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
    const openByParams = (params: ECElementEvent) => {
      if (isDragging || lastPointerUpWasDrag) {
        releaseRoam();
        return;
      }
      const province = findProvinceByMapName(String(params.name ?? ""));
      if (province) {
        onProvinceClick(province.code);
      }
      releaseRoam();
    };
    node.addEventListener("pointerdown", trackPointerDown, { passive: true });
    node.addEventListener("pointermove", trackPointerMove, { passive: true });
    node.addEventListener("pointerup", releaseRoam, { passive: true });
    node.addEventListener("pointercancel", releaseRoam, { passive: true });
    chart.on("click", openByParams);
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
      chart.off("click", openByParams);
      node.removeEventListener("pointerdown", trackPointerDown);
      node.removeEventListener("pointermove", trackPointerMove);
      node.removeEventListener("pointerup", releaseRoam);
      node.removeEventListener("pointercancel", releaseRoam);
      chart.dispose();
    };
  }, [node, option, onProvinceClick]);

  return setNode;
}

function findProvinceByMapName(mapName: string) {
  const normalized = normalizeProvinceName(mapName);
  return provinces.find((province) => normalizeProvinceName(province.name) === normalized);
}

function toMapProvinceName(name: string) {
  const suffixMap: Record<string, string> = {
    北京: "北京市",
    上海: "上海市",
    天津: "天津市",
    重庆: "重庆市",
    广西: "广西壮族自治区",
    内蒙古: "内蒙古自治区",
    宁夏: "宁夏回族自治区",
    新疆: "新疆维吾尔自治区",
    西藏: "西藏自治区",
    香港: "香港特别行政区",
    澳门: "澳门特别行政区"
  };

  return suffixMap[name] ?? `${name}省`;
}

function normalizeProvinceName(name: string) {
  return name
    .replace(/特别行政区$/, "")
    .replace(/维吾尔自治区$/, "")
    .replace(/壮族自治区$/, "")
    .replace(/回族自治区$/, "")
    .replace(/自治区$/, "")
    .replace(/[省市]$/, "");
}

function getDirectCity(province: (typeof provinces)[number]) {
  if (province.cities.length !== 1) {
    return null;
  }
  const city = province.cities[0];
  return normalizeProvinceName(province.name) === normalizeProvinceName(city.name) ? city : null;
}
