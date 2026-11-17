package com.agent.game;

/**
 * Common session lifecycle implemented by game-specific adapters.
 *
 * @param <S> the type of game observation returned by this adapter
 */
public interface GameAdapter<S> {

    /**
     * Starts or connects the game session.
     *
     * @return the initial game state
     */
    S start();

    /**
     * Stops or disconnects the game session.
     *
     * @return the state after the session is stopped
     */
    S stop();
}
