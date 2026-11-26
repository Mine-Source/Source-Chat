# Source Chat 3.0 — por Mine Source

Chat para Paper 1.21+ (Java 21). Reemplaza InteractiveChat, ChatManager, TChat, Announcer y ChatGames.
Sin dependencias obligatorias (PlaceholderAPI y Vault opcionales). Premium y no premium.

## Instalar
Suelta el jar en `plugins/`. Abre `/sourcechat`.

## Módulos (`modules:` en config.yml o `/sourcechat module <n> on|off`)
cosmetics · namesync · games · announcements · discord · tokens · mentions · joinquit.
Si uno falla se apaga solo; apagado no consume recursos.

## Comandos
- Jugador: `/sourcechat` `/msg` `/r` `/ignore` `/togglemsg` `/helpop` `/channel` `/nick` `/realname` `/lang` `/coins` `/pay` `/cosmetics` `/chatcolor` `/namecolor` `/prefix` `/tag` `/joinmsg` `/quitmsg` `/jointitle` `/chatgame top|stats`
- Staff: `/mute` `/unmute` `/clearchat` `/lockchat` `/slowmode` `/chatspy` `/broadcast` `/staffchat` `/announce` `/chatgame start|stop`
- Admin: `/sourcechat reload|module|quick|cosmetic|coins|info|game`

## Configuración rápida
`/sourcechat quick <tipo>` y escribe la lista en el chat, o `/sourcechat quick prefix Rey, Reina`.
Archivos `.txt/.yml` en `plugins/SourceChat/quick/` se procesan solos y se mueven a `quick/procesado/`.
Línea: `Nombre | precio | colores | rareza` (colores: `red > gold`, `#hex`, `rainbow`).

## Probar cosméticos
`/sourcechat cosmetic test <jugador> <tipo> <id>` · `give` · `take` · `reset`.

## Idiomas
`plugins/SourceChat/lang/`: copia `es.yml`, renombra (ej. `fr.yml`), traduce y `/sourcechat reload`.
`meta-flag` = textura de cabeza (hash, URL o base64); vacío usa el banner de `meta-icon`.

## Apodos / tab
Límites en `nick:`. PlaceholderAPI: `%sourcechat_displayname%`, `_mini`, `_plain`, `%sourcechat_prefix%`, `%sourcechat_tag%`, `%sourcechat_nick%`.

## Identidad
`identity.mode: auto|uuid|name` (name para cracked/proxys).

## Economía
`economy.provider: auto|internal|vault`.

## Permisos clave
`sourcechat.admin` `sourcechat.mod` `sourcechat.staffchat` `sourcechat.announce` `sourcechat.nick` `sourcechat.nick.color` `sourcechat.minimessage`.
