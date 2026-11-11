package com.clashtracker.web.dto;

/**
 * Projection of the most recent official Supercell API collection produced by {@code fetch-player}.
 * The public player API exposes only the documented top-level fields and the troop/spell/hero
 * arrays; it does NOT expose buildings, Supercharges, queues or timers, so those are never inferred
 * here. When no collection exists yet, {@code available} is false and the rest is null.
 */
public record OfficialProfileResponse(
        boolean available,
        String message,
        String collectionId,
        String requestedTag,
        String returnedTag,
        Integer httpStatus,
        String outcome,
        String collectedAt,
        String bodySha256,
        PlayerProfile profile) {

    public record PlayerProfile(
            String tag,
            String name,
            Integer townHallLevel,
            Integer expLevel,
            int troopCount,
            int spellCount,
            int heroCount,
            int heroEquipmentCount) {
    }

    public static OfficialProfileResponse unavailable(String message) {
        return new OfficialProfileResponse(false, message, null, null, null, null, null, null, null, null);
    }
}
