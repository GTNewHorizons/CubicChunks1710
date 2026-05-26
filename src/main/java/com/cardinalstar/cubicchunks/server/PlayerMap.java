package com.cardinalstar.cubicchunks.server;

import com.cardinalstar.cubicchunks.server.CubicPlayerManager.WatchingPlayer;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;

public class PlayerMap {

    private static final WatchingPlayer[] EMPTY_PLAYER_ARRAY = new WatchingPlayer[0];

    private final Int2ObjectOpenHashMap<WatchingPlayer> players = new Int2ObjectOpenHashMap<>();

    @Getter
    private WatchingPlayer[] playerArray = EMPTY_PLAYER_ARRAY;

    public void addPlayer(WatchingPlayer player) {
        this.players.put(player.player.getEntityId(), player);

        playerArray = players.values()
            .toArray(EMPTY_PLAYER_ARRAY);
    }

    public WatchingPlayer removePlayer(int playerId) {
        WatchingPlayer player = this.players.remove(playerId);

        if (player != null) {
            playerArray = players.values()
                .toArray(EMPTY_PLAYER_ARRAY);
        }

        return player;
    }

    public WatchingPlayer get(int playerId) {
        return this.players.get(playerId);
    }

    public boolean isEmpty() {
        return this.players.isEmpty();
    }
}
