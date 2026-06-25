"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight, Map, MapPinned, Settings, UserRound, UsersRound } from "lucide-react";
import { BottomNav } from "@/shared/components/BottomNav";
import { getCurrentUser, toErrorMessage, type CurrentUser } from "@/shared/lib/api";
import { fetchCurrentSpace, getSpaceStatusLabel, getSpaceTypeLabel, type TravelSpace } from "@/features/space/lib/spaces";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

export default function SpacePage() {
  const { authLoading, authErrorMessage, currentUser: guardedUser } = useAuthGuard();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [space, setSpace] = useState<TravelSpace | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let ignore = false;

    async function loadSpace() {
      if (authLoading) {
        return;
      }
      if (!guardedUser) {
        setErrorMessage(authErrorMessage);
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setErrorMessage("");
        setCurrentUser(guardedUser);
        const currentSpace = await fetchCurrentSpace();
        if (!ignore) {
          setSpace(currentSpace);
        }
      } catch (error) {
        if (!ignore) {
          setErrorMessage(toErrorMessage(error));
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    loadSpace();
    return () => {
      ignore = true;
    };
  }, [authErrorMessage, authLoading, guardedUser]);

  const user = currentUser ?? getCurrentUser();

  return (
    <main className="app-shell">
      <section className="page-panel">
        <p className="section-label">我</p>
        <div className="profile-card" aria-label="当前账号">
          <div className="profile-avatar">
            <UserRound aria-hidden="true" size={22} />
          </div>
          <div>
            <strong>{user?.nickname ?? "未登录"}</strong>
            <span>账号：{user?.account ?? "-"}</span>
          </div>
        </div>

        {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}

        <Link className="current-space-card" href="/spaces" aria-label="切换旅行空间">
          <div className={space?.spaceType === "COUPLE" ? "space-icon couple" : "space-icon personal"}>
            {space?.spaceType === "COUPLE" ? <UsersRound aria-hidden="true" size={22} /> : <Map aria-hidden="true" size={22} />}
          </div>
          <div>
            <span>当前空间</span>
            <strong>{loading ? "正在同步旅行空间" : space?.spaceName ?? "旅行空间"}</strong>
            <p>{space ? `${getSpaceTypeLabel(space)} · ${getSpaceStatusLabel(space)}` : "点击管理你的旅行空间"}</p>
          </div>
          <ChevronRight aria-hidden="true" size={20} />
        </Link>

        <div className="space-home-grid">
          <article>
            <MapPinned aria-hidden="true" size={20} />
            <strong>地图和记录跟随当前空间</strong>
            <p>切换空间后，首页地图、城市日记、照片和旅行日期都会换成该空间的数据。</p>
          </article>
          <article>
            <UsersRound aria-hidden="true" size={20} />
            <strong>情侣空间需要伴侣加入</strong>
            <p>伴侣加入前可以生成邀请码，但不能编辑计划和记录。</p>
          </article>
        </div>

        <section className="settings-panel" aria-label="设置">
          <Link className="settings-link" href="/settings">
            <span>
              <Settings aria-hidden="true" size={18} />
              设置
            </span>
            <ChevronRight aria-hidden="true" size={18} />
          </Link>
        </section>
      </section>
      <BottomNav active="space" />
    </main>
  );
}