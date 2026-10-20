package com.clashtracker.integration.supercell;

public interface SupercellPlayerClient {
    SupercellPlayerProfile fetchPlayer(String playerTag) throws SupercellApiException;
}
