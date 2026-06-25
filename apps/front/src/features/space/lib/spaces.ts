"use client";

import { requestJson } from "@/shared/lib/api";

export type TravelSpace = {
  spaceId: number;
  spaceName: string;
  spaceType: "PERSONAL" | "COUPLE";
  status: "ACTIVE" | "WAITING";
  creatorUserId: number;
  memberCount: number;
  memberLimit: number;
  current: boolean;
  editable: boolean;
};

export type InviteCode = {
  code: string;
  expireAt: string;
};

export function fetchCurrentSpace() {
  return requestJson<TravelSpace>("/api/spaces/current");
}

export function fetchSpaces() {
  return requestJson<TravelSpace[]>("/api/spaces");
}

export function createCoupleSpace() {
  return requestJson<TravelSpace>("/api/spaces/couple", {
    method: "POST",
    body: JSON.stringify({})
  });
}

export function activateSpace(spaceId: number) {
  return requestJson<TravelSpace>(`/api/spaces/${spaceId}/activate`, {
    method: "POST",
    body: JSON.stringify({})
  });
}

export function createInviteCode(spaceId: number) {
  return requestJson<InviteCode>(`/api/spaces/${spaceId}/invite-code`, {
    method: "POST",
    body: JSON.stringify({})
  });
}

export function joinSpaceByInviteCode(code: string) {
  return requestJson<TravelSpace>("/api/invite-codes/join", {
    method: "POST",
    body: JSON.stringify({
      code: code.trim().toUpperCase()
    })
  });
}

export function getSpaceTypeLabel(space: TravelSpace) {
  return space.spaceType === "PERSONAL" ? "个人空间" : "情侣空间";
}

export function getSpaceStatusLabel(space: TravelSpace) {
  if (space.spaceType === "COUPLE" && space.status === "WAITING") {
    return "未邀请你的伴侣不可使用。";
  }
  return "已开启";
}
