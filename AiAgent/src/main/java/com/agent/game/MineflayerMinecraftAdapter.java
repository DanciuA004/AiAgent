package com.agent.game;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Java-side adapter for the local Mineflayer companion process.
 */
@Service
public class MineflayerMinecraftAdapter implements MinecraftAdapter {

    /** HTTP client used to call the local Mineflayer bridge. */
    private final RestClient restClient;

    /**
     * Creates an adapter pointed at the local Mineflayer bridge.
     *
     * @param restClientBuilder Spring-configured HTTP client builder
     * @param bridgeUrl base URL of the bridge process
     */
    public MineflayerMinecraftAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${minecraft.bridge-url:http://127.0.0.1:8765}") String bridgeUrl) {
        this.restClient = restClientBuilder.baseUrl(bridgeUrl).build();
    }

    /**
     * Requests that the bridge connect the bot to its configured Minecraft server.
     *
     * @return the bot state after it spawns
     */
    @Override
    public GameState start() {
        return restClient.post()
                .uri("/session")
                .retrieve()
                .body(GameState.class);
    }

    /**
     * Requests that the bridge disconnect the bot from the server.
     *
     * @return the state after disconnection
     */
    @Override
    public GameState stop() {
        return restClient.delete()
                .uri("/session")
                .retrieve()
                .body(GameState.class);
    }
}
