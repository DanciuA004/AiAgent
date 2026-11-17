"use strict";

const http = require("node:http");
const mineflayer = require("mineflayer");

const host = process.env.MINECRAFT_HOST || "127.0.0.1";
const port = Number.parseInt(process.env.MINECRAFT_PORT || "25565", 10);
const username = process.env.MINECRAFT_USERNAME || "NovaBot";
const version = process.env.MINECRAFT_VERSION || false;
const bridgePort = Number.parseInt(process.env.BRIDGE_PORT || "8765", 10);

let bot = null;
let sessionState = "stopped";
let sessionError = null;
let startPromise = null;

/**
 * Sends a JSON response with the given HTTP status.
 * @param {import("node:http").ServerResponse} response HTTP response to complete
 * @param {number} status HTTP status code
 * @param {object} body JSON-serializable response payload
 */
function send(response, status, body) {
    response.writeHead(status, { "content-type": "application/json; charset=utf-8" });
    response.end(JSON.stringify(body));
}

/**
 * Returns the connection state and basic bot status.
 * @returns {object} current Minecraft session state
 */
function getState() {
    const spawned = Boolean(bot && bot.entity);
    const position = spawned ? bot.entity.position : null;
    return {
        status: sessionState,
        username,
        spawned,
        position: position ? { x: position.x, y: position.y, z: position.z } : null,
        health: spawned ? bot.health : null,
        food: spawned ? bot.food : null,
        dimension: spawned ? bot.game.dimension : null,
        error: sessionError
    };
}

/**
 * Connects the bot and waits until it spawns, reusing an existing live session.
 * @returns {Promise<object>} bot state after spawn
 */
function startSession() {
    if (sessionState === "online") {
        return Promise.resolve(getState());
    }
    if (sessionState === "connecting" && startPromise) {
        return startPromise;
    }

    sessionState = "connecting";
    sessionError = null;

    const pendingStart = new Promise((resolve, reject) => {
        let currentBot;
        let settled = false;
        let timeout;

        const clearStartTimeout = () => {
            if (timeout) clearTimeout(timeout);
        };

        const failStart = error => {
            if (settled) return;
            settled = true;
            clearStartTimeout();
            sessionError = error.message || "Minecraft connection failed.";
            sessionState = "error";
            if (bot === currentBot) bot = null;
            reject(error);
            if (currentBot && typeof currentBot.quit === "function") currentBot.quit();
        };

        try {
            currentBot = mineflayer.createBot({
                host,
                port,
                username,
                ...(version ? { version } : {})
            });
            bot = currentBot;
        } catch (error) {
            failStart(error);
            return;
        }

        timeout = setTimeout(() => {
            failStart(new Error("Timed out waiting for the Minecraft server to spawn the bot."));
        }, 30_000);

        currentBot.once("spawn", () => {
            if (settled || bot !== currentBot) return;
            settled = true;
            clearStartTimeout();
            sessionState = "online";
            resolve(getState());
        });

        currentBot.on("error", error => {
            if (!settled) {
                failStart(error);
            } else if (bot === currentBot) {
                sessionError = error.message;
            }
        });

        currentBot.on("end", reason => {
            const isCurrentBot = bot === currentBot;
            if (!isCurrentBot && settled) return;
            if (isCurrentBot) bot = null;
            if (!settled) {
                settled = true;
                clearStartTimeout();
                sessionError = reason || "Minecraft disconnected before the bot spawned.";
                sessionState = isCurrentBot ? "error" : "stopped";
                reject(new Error(sessionError));
            } else if (isCurrentBot) {
                sessionError = reason || sessionError;
                sessionState = "stopped";
            }
        });
    });

    startPromise = pendingStart;
    pendingStart.then(
        () => {
            if (startPromise === pendingStart) startPromise = null;
        },
        () => {
            if (startPromise === pendingStart) startPromise = null;
        }
    );
    return pendingStart;
}

/**
 * Disconnects the active bot and marks the session stopped.
 * @returns {object} state after stopping the session
 */
function stopSession() {
    if (bot) {
        const currentBot = bot;
        bot = null;
        sessionState = "stopped";
        currentBot.quit("Session stopped");
    } else {
        sessionState = "stopped";
    }
    return getState();
}

/**
 * Handles the session lifecycle routes.
 * @param {import("node:http").IncomingMessage} request incoming request
 * @param {import("node:http").ServerResponse} response response to complete
 */
async function handle(request, response) {
    if (new URL(request.url, "http://127.0.0.1").pathname !== "/session") {
        send(response, 404, { error: "Not found." });
        return;
    }

    try {
        if (request.method === "POST") {
            send(response, 200, await startSession());
        } else if (request.method === "DELETE") {
            send(response, 200, stopSession());
        } else {
            send(response, 405, { error: "Method not allowed." });
        }
    } catch (error) {
        if (!response.headersSent) {
            send(response, error.statusCode || 502, {
                error: error.message || "Minecraft session request failed."
            });
        }
    }
}

const server = http.createServer(handle);
server.listen(bridgePort, "127.0.0.1", () => {
    console.log(`Minecraft bridge listening on 127.0.0.1:${bridgePort}`);
    console.log(`Configured Minecraft server: ${host}:${port}`);
});

/** Stops the bot and closes the local HTTP server on process shutdown. */
function shutdown() {
    stopSession();
    server.close(() => process.exit(0));
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
