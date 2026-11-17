package com.agent.game;

/**
 * Minecraft session status returned when a connection starts or stops.
 *
 * @param status        current bridge session state, such as {@code online} or {@code stopped}
 * @param username      configured in-game username for the bot
 * @param spawned       whether the bot has spawned into the world
 * @param position      bot's world coordinates, or {@code null} before it spawns
 * @param health        bot's health, or {@code null} when unavailable
 * @param food          bot's food level, or {@code null} when unavailable
 * @param dimension     current Minecraft dimension, or {@code null} when unavailable
 * @param error         latest session error, if one has occurred
 */
public record GameState(
        String status,
        String username,
        boolean spawned,
        Position position,
        Integer health,
        Integer food,
        String dimension,
        String error) {

    /**
     * Position in Minecraft world coordinates.
     *
     * @param x east-west coordinate
     * @param y vertical coordinate
     * @param z north-south coordinate
     */
    public record Position(double x, double y, double z) {
    }

}
