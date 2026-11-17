package com.agent.controller;

import com.agent.game.GameState;
import com.agent.game.MinecraftAdapter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for connecting to and disconnecting from Minecraft.
 *
 * <p>Both routes are rooted at {@code /games/minecraft/session}.</p>
 */
@RestController
@RequestMapping("/games/minecraft")
public class MinecraftController {

    private final MinecraftAdapter adapter;

    /**
     * Creates the controller with its Minecraft session adapter.
     *
     * @param adapter adapter that starts and stops the Minecraft session
     */
    public MinecraftController(MinecraftAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Starts a session with the configured Minecraft server.
     *
     * @return the bot's initial state
     */
    @PostMapping("/session")
    public GameState start() {
        return adapter.start();
    }

    /**
     * Stops the current Minecraft session.
     *
     * @return the state after the bot disconnects
     */
    @DeleteMapping("/session")
    public GameState stop() {
        return adapter.stop();
    }
}
