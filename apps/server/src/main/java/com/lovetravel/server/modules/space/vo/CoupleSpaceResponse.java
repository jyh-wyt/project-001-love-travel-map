package com.lovetravel.server.modules.space.vo;

public class CoupleSpaceResponse {

    private Long spaceId;
    private String spaceName;
    private String spaceType;
    private String status;
    private Long creatorUserId;
    private Integer memberCount;
    private Integer memberLimit;
    private Boolean current;
    private Boolean editable;

    public CoupleSpaceResponse(Long spaceId, String spaceName, Long creatorUserId, Integer memberCount, Integer memberLimit) {
        this(spaceId, spaceName, "COUPLE", "ACTIVE", creatorUserId, memberCount, memberLimit, true, memberCount != null && memberCount >= 2);
    }

    public CoupleSpaceResponse(
            Long spaceId,
            String spaceName,
            String spaceType,
            String status,
            Long creatorUserId,
            Integer memberCount,
            Integer memberLimit,
            Boolean current,
            Boolean editable) {
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceType = spaceType;
        this.status = status;
        this.creatorUserId = creatorUserId;
        this.memberCount = memberCount;
        this.memberLimit = memberLimit;
        this.current = current;
        this.editable = editable;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public String getSpaceType() {
        return spaceType;
    }

    public String getStatus() {
        return status;
    }

    public Long getCreatorUserId() {
        return creatorUserId;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public Integer getMemberLimit() {
        return memberLimit;
    }

    public Boolean getCurrent() {
        return current;
    }

    public Boolean getEditable() {
        return editable;
    }
}
