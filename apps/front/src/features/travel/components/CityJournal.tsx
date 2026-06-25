"use client";

import type { CSSProperties, PointerEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { Euler, Vector3 } from "three";
import { ImagePlus, Pencil, Plus, Trash2, X } from "lucide-react";
import { API_BASE_URL, requestJson, toErrorMessage } from "@/shared/lib/api";
import { fetchCurrentSpace, type TravelSpace } from "@/features/space/lib/spaces";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

type ImageItem = {
  id: number;
  imageUrl: string;
  ossObjectKey?: string;
  sortOrder: number;
};

type UploadedImage = {
  imageUrl: string;
  ossObjectKey: string;
};

type OssStatus = {
  configured: boolean;
  endpoint: string;
  bucket: string;
  signedUrlExpireMinutes: number;
};

type PostItem = {
  id: number;
  authorUserId: number;
  authorNickname?: string;
  content: string;
  locationName?: string;
  createdAt: string;
  images: ImageItem[];
};

type TravelPage = {
  tripId: number;
  provinceCode: string;
  provinceName: string;
  cityCode: string;
  cityName: string;
  title: string;
  coverImageUrl?: string;
  startDate?: string;
  endDate?: string;
  posts: PostItem[];
};

type DraftRecord = {
  text: string;
  images: UploadedImage[];
};

type DateDraft = {
  startDate: string;
  endDate: string;
};

type PendingDelete =
  | {
      type: "record";
      recordId: number;
      title: string;
      message: string;
    }
  | {
      type: "image";
      imageId: number;
      title: string;
      message: string;
    };

type CityJournalProps = {
  provinceCode: string;
  provinceName: string;
  cityCode: string;
  cityName: string;
};

type PreviewImage = {
  id: number | string;
  imageUrl: string;
  alt: string;
};

const EMPTY_DRAFT: DraftRecord = {
  text: "",
  images: []
};

const EMPTY_DATE_DRAFT: DateDraft = {
  startDate: "",
  endDate: ""
};

const MAX_IMAGES = 6;
const MIN_SPHERE_PANELS = 14;
const MAX_SPHERE_PANELS = 22;

export function CityJournal({ provinceCode, provinceName, cityCode, cityName }: CityJournalProps) {
  const { authLoading, authErrorMessage, currentUser } = useAuthGuard();
  const [travel, setTravel] = useState<TravelPage | null>(null);
  const [space, setSpace] = useState<TravelSpace | null>(null);
  const [ossStatus, setOssStatus] = useState<OssStatus | null>(null);
  const [draft, setDraft] = useState<DraftRecord>(EMPTY_DRAFT);
  const [dateDraft, setDateDraft] = useState<DateDraft>(EMPTY_DATE_DRAFT);
  const [editingPostId, setEditingPostId] = useState<number | null>(null);
  const [editingContent, setEditingContent] = useState("");
  const [editingImages, setEditingImages] = useState<UploadedImage[]>([]);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [previewImage, setPreviewImage] = useState<PreviewImage | null>(null);

  const posts = travel?.posts ?? [];
  const hasRecords = posts.length > 0;
  const sphereImages = useMemo(
    () =>
      posts
        .flatMap((post) =>
          post.images.map((image) => ({
            id: image.id,
            imageUrl: image.imageUrl,
            alt: `${cityName}旅行照片`
          }))
        )
        .slice(0, 18),
    [cityName, posts]
  );
  const currentDateRange = useMemo(
    () => formatDateRange(travel?.startDate, travel?.endDate),
    [travel?.startDate, travel?.endDate]
  );
  const remainingDraftImageCount = MAX_IMAGES - draft.images.length;
  const canSaveDate = Boolean(dateDraft.startDate || dateDraft.endDate);
  const canPublishRecord = Boolean(draft.text.trim() || draft.images.length > 0);
  const canEdit = Boolean(space?.editable);

  useEffect(() => {
    let ignore = false;

    async function loadTravel() {
      if (authLoading) {
        return;
      }
      if (!currentUser) {
        setErrorMessage(authErrorMessage);
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setErrorMessage("");
        setSuccessMessage("");
        if (ignore) {
          return;
        }
        const [page, status, currentSpace] = await Promise.all([
          fetchTravel(),
          requestJson<OssStatus>("/api/oss/status").catch(() => null),
          fetchCurrentSpace()
        ]);
        if (!ignore) {
          setTravel(page);
          setSpace(currentSpace);
          setDateDraft({
            startDate: page.startDate || "",
            endDate: page.endDate || ""
          });
          setOssStatus(status);
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

    loadTravel();
    return () => {
      ignore = true;
    };
  }, [authErrorMessage, authLoading, cityCode, cityName, currentUser, provinceCode, provinceName]);

  useEffect(() => {
    if (!successMessage) {
      return;
    }
    const timer = window.setTimeout(() => setSuccessMessage(""), 3200);
    return () => window.clearTimeout(timer);
  }, [successMessage]);

  useEffect(() => {
    if (!previewImage) {
      return;
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setPreviewImage(null);
      }
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [previewImage]);

  async function fetchTravel() {
    return requestJson<TravelPage>(
      `/api/regions/${cityCode}/travel?provinceCode=${provinceCode}&provinceName=${encodeURIComponent(
        provinceName
      )}&cityName=${encodeURIComponent(cityName)}`
    );
  }

  async function reloadTravel() {
    setTravel(await fetchTravel());
  }

  async function uploadImages(files: FileList | null, mode: "new" | "edit" = "new", existingImageCount = 0) {
    if (!files || uploading || !canEdit) {
      return;
    }

    if (ossStatus && !ossStatus.configured) {
      setErrorMessage("OSS 还没有配置完整，请先检查后端环境变量。");
      setSuccessMessage("");
      return;
    }

    const currentDraftImages = mode === "edit" ? editingImages : draft.images;
    const availableSlots = MAX_IMAGES - existingImageCount - currentDraftImages.length;
    const selectedFiles = Array.from(files).slice(0, availableSlots);
    if (selectedFiles.length === 0) {
      setErrorMessage(`最多只能上传 ${MAX_IMAGES} 张图片。`);
      setSuccessMessage("");
      return;
    }

    try {
      setUploading(true);
      setErrorMessage("");
      setSuccessMessage("");
      const uploadedImages: UploadedImage[] = [];
      for (const file of selectedFiles) {
        const formData = new FormData();
        formData.append("file", file);
        const response = await fetch(`${API_BASE_URL}/api/oss/images`, {
          method: "POST",
          credentials: "include",
          body: formData
        });
        if (!response.ok) {
          const data = (await response.json().catch(() => null)) as { message?: string } | null;
          throw new Error(data?.message || "图片上传失败，请稍后再试");
        }
        uploadedImages.push((await response.json()) as UploadedImage);
      }
      if (mode === "edit") {
        setEditingImages((current) => [...current, ...uploadedImages].slice(0, MAX_IMAGES - existingImageCount));
      } else {
        setDraft((current) => ({
          ...current,
          images: [...current.images, ...uploadedImages].slice(0, MAX_IMAGES)
        }));
      }
      setSuccessMessage(`已上传 ${uploadedImages.length} 张照片，确认发布后会同步给对方。`);
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
      setSuccessMessage("");
    } finally {
      setUploading(false);
    }
  }

  async function publishRecord() {
    if (!travel || saving || uploading || !canEdit) {
      return;
    }

    if (!draft.text.trim() && draft.images.length === 0) {
      setErrorMessage("日记文字和图片至少填写一项。");
      setSuccessMessage("");
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      setSuccessMessage("");
      await requestJson<PostItem>(`/api/trips/${travel.tripId}/posts`, {
        method: "POST",
        body: JSON.stringify({
          content: draft.text.trim(),
          images: draft.images.map((image, index) => ({
            imageUrl: image.imageUrl,
            ossObjectKey: image.ossObjectKey,
            sortOrder: index
          }))
        })
      });
      setDraft(EMPTY_DRAFT);
      await reloadTravel();
      setSuccessMessage("记录已发布，对方也可以看到。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
      setSuccessMessage("");
    } finally {
      setSaving(false);
    }
  }

  async function saveDateRange() {
    if (!travel || saving || !canEdit) {
      return;
    }

    if (!dateDraft.startDate && !dateDraft.endDate) {
      setErrorMessage("请先填写旅行日期。");
      setSuccessMessage("");
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      setSuccessMessage("");
      const page = await requestJson<TravelPage>(`/api/trips/${travel.tripId}/date-range`, {
        method: "PUT",
        body: JSON.stringify({
          startDate: dateDraft.startDate || null,
          endDate: dateDraft.endDate || dateDraft.startDate || null
        })
      });
      setTravel(page);
      setDateDraft({
        startDate: page.startDate || "",
        endDate: page.endDate || ""
      });
      setSuccessMessage("旅行日期已保存，返回地图后会显示在城市名下方。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
      setSuccessMessage("");
    } finally {
      setSaving(false);
    }
  }

  async function confirmDelete() {
    if (!pendingDelete || saving || !canEdit) {
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      setSuccessMessage("");
      if (pendingDelete.type === "record") {
        await requestJson<{ success: boolean }>(`/api/posts/${pendingDelete.recordId}`, {
          method: "DELETE"
        });
        await reloadTravel();
        setSuccessMessage("记录已删除。");
      } else {
        const page = await requestJson<TravelPage>(`/api/images/${pendingDelete.imageId}`, {
          method: "DELETE"
        });
        setTravel(page);
        setSuccessMessage("照片已删除。");
      }
      setPendingDelete(null);
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
      setSuccessMessage("");
    } finally {
      setSaving(false);
    }
  }

  function beginEditPost(post: PostItem) {
    if (!canEdit) {
      return;
    }
    setEditingPostId(post.id);
    setEditingContent(post.content || "");
    setEditingImages([]);
    setErrorMessage("");
    setSuccessMessage("");
  }

  function cancelEditPost() {
    setEditingPostId(null);
    setEditingContent("");
    setEditingImages([]);
    setErrorMessage("");
    setSuccessMessage("");
  }

  async function saveEditedPost(postId: number) {
    if (saving || !canEdit) {
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      setSuccessMessage("");
      await requestJson<PostItem>(`/api/posts/${postId}`, {
        method: "PUT",
        body: JSON.stringify({
          content: editingContent.trim(),
          images: editingImages.map((image, index) => ({
            imageUrl: image.imageUrl,
            ossObjectKey: image.ossObjectKey,
            sortOrder: index
          }))
        })
      });
      setEditingPostId(null);
      setEditingContent("");
      setEditingImages([]);
      await reloadTravel();
      setSuccessMessage("记录已保存。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
      setSuccessMessage("");
    } finally {
      setSaving(false);
    }
  }

  function removeDraftImage(index: number) {
    setDraft((current) => ({
      ...current,
      images: current.images.filter((_, currentIndex) => currentIndex !== index)
    }));
  }

  function removeEditingImage(index: number) {
    setEditingImages((current) => current.filter((_, currentIndex) => currentIndex !== index));
  }

  return (
    <div className="journal-layout">
      <section className="city-hero">
        <div className="city-hero-copy">
          <p className="section-label">城市记录流</p>
          <h1>{cityName}</h1>
          <p>{currentDateRange || "保存旅行日期或发布记录后，这座城市会显示在地图上。"}</p>
        </div>
        <PhotoSphere cityName={cityName} images={sphereImages} onPreview={setPreviewImage} />
      </section>

      {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}
      {successMessage ? <p className="plan-feedback success">{successMessage}</p> : null}
      {space && !space.editable ? <p className="plan-feedback warning">未邀请你的伴侣不可使用。</p> : null}

      <section className="compose-panel" aria-label="设置旅行日期">
        <div className="panel-title-row">
          <div>
            <p className="section-label">旅行日期</p>
            <h2>{currentDateRange || "先标注这次旅行的时间"}</h2>
          </div>
          <span>只需保存一次</span>
        </div>

        <div className="date-range-fields">
          <label className="field-label">
            旅行开始日期
            <input
              disabled={loading || saving || !canEdit}
              onChange={(event) => setDateDraft({ ...dateDraft, startDate: event.target.value })}
              type="date"
              value={dateDraft.startDate}
            />
          </label>
          <label className="field-label">
            旅行结束日期
            <input
              disabled={loading || saving || !canEdit}
              onChange={(event) => setDateDraft({ ...dateDraft, endDate: event.target.value })}
              type="date"
              value={dateDraft.endDate}
            />
          </label>
        </div>

        <div className="compose-actions left">
          <button className="secondary-button" disabled={loading || saving || !canSaveDate || !canEdit} onClick={saveDateRange} type="button">
            {saving ? "保存中" : "保存旅行日期"}
          </button>
        </div>
      </section>

      <section className="compose-panel" aria-label="发布旅行记录">
        <div className="panel-title-row">
          <div>
            <p className="section-label">发布记录</p>
            <h2>图片和日记一起发布</h2>
          </div>
          <span>最多 {MAX_IMAGES} 张</span>
        </div>

        {ossStatus && !ossStatus.configured ? (
          <p className="plan-feedback warning">OSS 配置未完成，暂时不能上传照片。</p>
        ) : null}

        <label className="field-label">
          日记
          <textarea
            disabled={loading || saving || !canEdit}
            onChange={(event) => setDraft({ ...draft, text: event.target.value })}
            placeholder="写下这一天发生了什么"
            value={draft.text}
          />
        </label>

        <div className="upload-area">
          <div className="upload-headline">
            <span>照片</span>
            <span>还可以上传 {remainingDraftImageCount} 张</span>
          </div>
          <label className="upload-target file-upload-target">
            <ImagePlus aria-hidden="true" size={22} />
            {uploading ? "正在上传到 OSS" : remainingDraftImageCount > 0 ? "选择照片上传 OSS" : "已达到 6 张上限"}
            <input
              accept="image/jpeg,image/png,image/webp,image/gif"
              disabled={loading || saving || uploading || draft.images.length >= MAX_IMAGES || !canEdit}
              multiple
              onChange={(event) => {
                uploadImages(event.target.files);
                event.target.value = "";
              }}
              type="file"
            />
          </label>

          {draft.images.length > 0 ? (
            <div className="draft-image-grid" aria-label="待发布照片">
              {draft.images.map((image, index) => (
                <div className="draft-image-cell" key={image.ossObjectKey}>
                  <img alt={`待发布照片 ${index + 1}`} src={image.imageUrl} />
                  <button
                    aria-label="移除这张待发布照片"
                    className="image-delete-button"
                    disabled={saving || uploading || !canEdit}
                    onClick={() => removeDraftImage(index)}
                    type="button"
                  >
                    <X aria-hidden="true" size={14} />
                  </button>
                </div>
              ))}
            </div>
          ) : null}
        </div>

        <div className="compose-actions">
          <button className="secondary-button" disabled={saving || uploading || !canPublishRecord || !canEdit} onClick={() => setDraft(EMPTY_DRAFT)} type="button">
            取消
          </button>
          <button className="primary-button" disabled={loading || saving || uploading || !canPublishRecord || !canEdit} onClick={publishRecord} type="button">
            <Plus aria-hidden="true" size={18} />
            {saving ? "发布中" : "发布"}
          </button>
        </div>
      </section>

      <section className="record-flow" aria-label={`${cityName}记录`}>
        {loading ? (
          <div className="empty-state">
            <strong>正在加载记录</strong>
            <p>正在同步这座城市里的照片和日记。</p>
          </div>
        ) : !hasRecords ? (
          <div className="empty-state">
            <strong>这里还没有记录</strong>
            <p>发布第一张照片和第一篇日记后，对方也能在这里看到。</p>
          </div>
        ) : (
          posts.map((post) => (
            <article className="record-card" key={post.id}>
              <div className="record-meta">
                <div>
                  <strong>{post.authorNickname?.trim() || `用户${post.authorUserId}`}</strong>
                </div>
                <div className="record-actions">
                  {editingPostId === post.id ? null : (
                    <button
                      className="secondary-text-button"
                      disabled={saving || !canEdit}
                      onClick={() => beginEditPost(post)}
                      type="button"
                    >
                      <Pencil aria-hidden="true" size={16} />
                      编辑
                    </button>
                  )}
                </div>
              </div>
              {editingPostId === post.id ? (
                <div className="record-edit-box">
                  <label className="field-label">
                    修改日记
                    <textarea
                      disabled={saving || !canEdit}
                      onChange={(event) => setEditingContent(event.target.value)}
                      value={editingContent}
                    />
                  </label>
                  <div className="upload-area">
                    <div className="upload-headline">
                      <span>新增照片</span>
                      <span>原有 {post.images.length} 张，还可以新增 {MAX_IMAGES - post.images.length - editingImages.length} 张</span>
                    </div>
                    <label className="upload-target file-upload-target">
                      <ImagePlus aria-hidden="true" size={22} />
                      {uploading
                        ? "正在上传到 OSS"
                        : post.images.length + editingImages.length >= MAX_IMAGES
                          ? "已达到 6 张上限"
                          : "继续添加照片"}
                      <input
                        accept="image/jpeg,image/png,image/webp,image/gif"
                        disabled={saving || uploading || post.images.length + editingImages.length >= MAX_IMAGES || !canEdit}
                        multiple
                        onChange={(event) => {
                          uploadImages(event.target.files, "edit", post.images.length);
                          event.target.value = "";
                        }}
                        type="file"
                      />
                    </label>
                    {editingImages.length > 0 ? (
                      <div className="draft-image-grid" aria-label="待新增照片">
                        {editingImages.map((image, index) => (
                          <div className="draft-image-cell" key={image.ossObjectKey}>
                            <img alt={`待新增照片 ${index + 1}`} src={image.imageUrl} />
                            <button
                              aria-label="移除这张待新增照片"
                              className="image-delete-button"
                              disabled={saving || uploading || !canEdit}
                              onClick={() => removeEditingImage(index)}
                              type="button"
                            >
                              <X aria-hidden="true" size={14} />
                            </button>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </div>
                  <div className="compose-actions">
                    <button
                      className="danger-text-button record-delete-action"
                      disabled={saving || uploading || !canEdit}
                      onClick={() =>
                        setPendingDelete({
                          type: "record",
                          recordId: post.id,
                          title: "删除这条记录？",
                          message: "删除后，这条日记和其中的照片都会从当前页面移除。"
                        })
                      }
                      type="button"
                    >
                      <Trash2 aria-hidden="true" size={16} />
                      删除记录
                    </button>
                    <button className="secondary-button" disabled={saving || uploading} onClick={cancelEditPost} type="button">
                      取消
                    </button>
                    <button className="primary-button" disabled={saving || uploading || !canEdit} onClick={() => saveEditedPost(post.id)} type="button">
                      {saving ? "保存中" : "确定保存"}
                    </button>
                  </div>
                </div>
              ) : (
                <p>{post.content}</p>
              )}
              {post.images.length > 0 ? (
                <div className={`record-images ${getRecordImageGridClass(post.images.length)}`}>
                  {post.images.map((image) => (
                    <div className="record-image-cell" key={image.id}>
                      <button
                        className="record-image"
                        disabled={saving}
                        onClick={() =>
                          setPreviewImage({
                            id: image.id,
                            imageUrl: image.imageUrl,
                            alt: `${cityName}旅行照片`
                          })
                        }
                        type="button"
                      >
                        <img alt={`${cityName}旅行照片`} src={image.imageUrl} />
                      </button>
                      {editingPostId === post.id ? (
                        <button
                          aria-label="删除这张照片"
                          className="image-delete-button"
                          disabled={saving || !canEdit}
                          onClick={() =>
                            setPendingDelete({
                              type: "image",
                              imageId: image.id,
                              title: "删除这张照片？",
                              message: "删除后，这张照片会从当前记录中移除。"
                            })
                          }
                          type="button"
                        >
                          <Trash2 aria-hidden="true" size={14} />
                        </button>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : null}
            </article>
          ))
        )}
      </section>

      {pendingDelete ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="confirm-dialog" aria-label="删除确认" role="dialog" aria-modal="true">
            <h2>{pendingDelete.title}</h2>
            <p>{pendingDelete.message}</p>
            <div className="compose-actions">
              <button className="secondary-button" disabled={saving} onClick={() => setPendingDelete(null)} type="button">
                取消
              </button>
              <button className="danger-button" disabled={saving || !canEdit} onClick={confirmDelete} type="button">
                确认删除
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {previewImage ? (
        <div className="image-preview-backdrop" onClick={() => setPreviewImage(null)} role="presentation">
          <section className="image-preview-dialog" aria-label="照片预览" role="dialog" aria-modal="true">
            <button className="image-preview-close" onClick={() => setPreviewImage(null)} type="button" aria-label="关闭预览">
              <X aria-hidden="true" size={20} />
            </button>
            <img alt={previewImage.alt} src={previewImage.imageUrl} onClick={(event) => event.stopPropagation()} />
          </section>
        </div>
      ) : null}
    </div>
  );
}

function PhotoSphere({
  cityName,
  images,
  onPreview
}: {
  cityName: string;
  images: PreviewImage[];
  onPreview: (image: PreviewImage) => void;
}) {
  const [rotation, setRotation] = useState({ x: -0.16, y: 0.34 });
  const [dragState, setDragState] = useState<{
    active: boolean;
    startX: number;
    startY: number;
    lastX: number;
    lastY: number;
    moved: boolean;
  } | null>(null);

  const panels = useMemo(() => {
    const displayImages = buildSphereImages(images);
    const count = displayImages.length;
    if (count === 0) {
      return [];
    }

    const sphereRadius = 126;
    const perspective = 520;
    const goldenAngle = Math.PI * (3 - Math.sqrt(5));
    const euler = new Euler(rotation.x, rotation.y, 0, "XYZ");

    return displayImages.map((image, index) => {
      const normalizedY = count === 1 ? 0 : 1 - (index / (count - 1)) * 2;
      const orbitRadius = Math.sqrt(Math.max(0, 1 - normalizedY * normalizedY));
      const theta = goldenAngle * index;
      const position = new Vector3(
        Math.cos(theta) * orbitRadius,
        normalizedY,
        Math.sin(theta) * orbitRadius
      )
        .multiplyScalar(sphereRadius)
        .applyEuler(euler);
      const depth = perspective / (perspective - position.z);
      const frontRatio = (position.z + sphereRadius) / (sphereRadius * 2);
      return {
        image,
        key: `${image.id}-${index}`,
        left: position.x * depth,
        top: position.y * depth,
        scale: Math.max(0.62, depth * (0.82 + frontRatio * 0.18)),
        opacity: Math.max(0.58, 0.7 + frontRatio * 0.3),
        zIndex: Math.round((position.z + sphereRadius) * 10)
      };
    });
  }, [images, rotation]);

  function handlePointerDown(event: PointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragState({
      active: true,
      startX: event.clientX,
      startY: event.clientY,
      lastX: event.clientX,
      lastY: event.clientY,
      moved: false
    });
  }

  function handlePointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!dragState?.active) {
      return;
    }

    const deltaX = event.clientX - dragState.lastX;
    const deltaY = event.clientY - dragState.lastY;
    const totalMove = Math.abs(event.clientX - dragState.startX) + Math.abs(event.clientY - dragState.startY);
    setRotation((current) => ({
      x: Math.max(-1.15, Math.min(1.15, current.x - deltaY * 0.006)),
      y: current.y + deltaX * 0.008
    }));
    setDragState({
      ...dragState,
      lastX: event.clientX,
      lastY: event.clientY,
      moved: dragState.moved || totalMove > 8
    });
  }

  function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    window.setTimeout(() => setDragState(null), 0);
  }

  function previewFromPanel(image: PreviewImage) {
    if (dragState?.moved) {
      return;
    }
    onPreview(image);
  }

  return (
    <div
      className={`photo-sphere-card${dragState?.active ? " dragging" : ""}`}
      onPointerCancel={handlePointerUp}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
    >
      {images.length === 0 ? (
        <div className="photo-sphere-empty">
          <strong>还没有照片</strong>
          <span>发布第一条记录后，这里会生成照片球。</span>
        </div>
      ) : (
        <div className="photo-sphere-stage" aria-label={`${cityName}照片球`}>
          {panels.map((panel) => (
            <button
              className="photo-sphere-panel"
              key={panel.key}
              onClick={() => previewFromPanel(panel.image)}
              style={
                {
                  "--panel-x": `${panel.left}px`,
                  "--panel-y": `${panel.top}px`,
                  "--panel-scale": panel.scale,
                  opacity: panel.opacity,
                  zIndex: panel.zIndex
                } as CSSProperties
              }
              type="button"
            >
              <img alt={panel.image.alt} src={panel.image.imageUrl} />
            </button>
          ))}
          <span className="photo-sphere-hint">按住拖动查看背面</span>
        </div>
      )}
    </div>
  );
}

function buildSphereImages(images: PreviewImage[]) {
  if (images.length === 0) {
    return [];
  }
  if (images.length >= MIN_SPHERE_PANELS) {
    return images.slice(0, MAX_SPHERE_PANELS);
  }

  const repeated: PreviewImage[] = [];
  for (let index = 0; index < MIN_SPHERE_PANELS; index++) {
    repeated.push(images[index % images.length]);
  }
  return repeated;
}

function getRecordImageGridClass(imageCount: number) {
  if (imageCount <= 1) {
    return "record-images-single";
  }
  if (imageCount === 2) {
    return "record-images-pair";
  }
  return "record-images-grid";
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
