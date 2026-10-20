package com.clashtracker.domain;

public record QueueWorkload(
        long builderSeconds,
        long laboratorySeconds,
        long petSeconds,
        int permanentBuilderCapacity,
        boolean goblinBuilderTemporary) {
}
