# Minecraft bridge

This local Mineflayer companion connects to one configured Minecraft server. It
binds its HTTP API to loopback only; the Spring application calls it through
`MinecraftAdapter`.

## Run

Requires Node.js 20+ and a Minecraft Java server that permits the configured
bot account. From this directory:

```powershell
npm install
$env:MINECRAFT_HOST = "127.0.0.1"
$env:MINECRAFT_PORT = "25565"
$env:MINECRAFT_USERNAME = "NovaBot"
npm start
```

Set `MINECRAFT_VERSION` when automatic protocol detection is unsuitable. Start
the Spring application separately; its bridge URL defaults to
`http://127.0.0.1:8765` and can be overridden with `MINECRAFT_BRIDGE_URL`.

## Spring API

- `POST /games/minecraft/session` connects the bot.
- `DELETE /games/minecraft/session` disconnects the bot.

The bridge API accepts only `POST /session` and `DELETE /session`; it binds to
loopback and returns basic bot session status.
