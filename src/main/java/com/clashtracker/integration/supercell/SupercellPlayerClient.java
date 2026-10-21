package com.clashtracker.integration.supercell;

public interface SupercellPlayerClient {
    SupercellPlayerCapture capturePlayer(String playerTag) throws SupercellApiException;

    default SupercellPlayerProfile fetchPlayer(String playerTag) throws SupercellApiException {
        return capturePlayer(playerTag).requireProfile();
    }
}
