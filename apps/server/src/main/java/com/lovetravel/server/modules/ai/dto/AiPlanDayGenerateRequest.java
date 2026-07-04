package com.lovetravel.server.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class AiPlanDayGenerateRequest {

    @NotBlank
    private String destination;
    @NotEmpty
    private List<String> places;
    private List<String> mustVisitPlaces;
    private String hotelLocation;
    @NotBlank
    private String morningMode;
    @NotBlank
    private String afternoonMode;
    @NotBlank
    private String eveningMode;
    private String notes;
    private String revisionInstruction;
    private String regenerateMode;
    private Long sourceDraftId;

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public List<String> getPlaces() { return places; }
    public void setPlaces(List<String> places) { this.places = places; }
    public List<String> getMustVisitPlaces() { return mustVisitPlaces; }
    public void setMustVisitPlaces(List<String> mustVisitPlaces) { this.mustVisitPlaces = mustVisitPlaces; }
    public String getHotelLocation() { return hotelLocation; }
    public void setHotelLocation(String hotelLocation) { this.hotelLocation = hotelLocation; }
    public String getMorningMode() { return morningMode; }
    public void setMorningMode(String morningMode) { this.morningMode = morningMode; }
    public String getAfternoonMode() { return afternoonMode; }
    public void setAfternoonMode(String afternoonMode) { this.afternoonMode = afternoonMode; }
    public String getEveningMode() { return eveningMode; }
    public void setEveningMode(String eveningMode) { this.eveningMode = eveningMode; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRevisionInstruction() { return revisionInstruction; }
    public void setRevisionInstruction(String revisionInstruction) { this.revisionInstruction = revisionInstruction; }
    public String getRegenerateMode() { return regenerateMode; }
    public void setRegenerateMode(String regenerateMode) { this.regenerateMode = regenerateMode; }
    public Long getSourceDraftId() { return sourceDraftId; }
    public void setSourceDraftId(Long sourceDraftId) { this.sourceDraftId = sourceDraftId; }
}
