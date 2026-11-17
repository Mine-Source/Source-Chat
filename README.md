# NovaChat 2.0

El chat todo-en-uno para **Paper 1.21+** (Java 21): chat interactivo, canales, moderación, minijuegos, menús y PlaceholderAPI. Pensado para reemplazar a un plugin de chat interactivo y a un gestor de chat a la vez.

Sin NMS ni reflexión: solo API de Paper/Adventure, para que no se rompa con cada versión.

## Características

**Chat**
- Formatos por permiso con MiniMessage (degradados, hover, click) y **PlaceholderAPI** (también acepta `&a` y `&#RRGGBB`).
- **Canales** (`global`, `local` por radio, `staff`) con permiso, radio, mundo, formato propio y atajo (`!hola`, `#hola`). Cambia con `/channel`.
- **Tokens interactivos**: `[item]`, `[inv]`, `[ender]`, `[pos]` (vista de solo lectura; `[pos]` copia las coordenadas).
- **Placeholders propios** en `config.yml`: escribir `[discord]` se convierte en un texto con hover y click (abrir URL, ejecutar comando, copiar...).
- **Emojis** `:smile:`, `:star:`... configurables.
- **Menciones** `@jugador` con resaltado, action bar y sonido.

**Moderación**
- `/mute <jugador> [tiempo] [razón]` (`10m`, `1h30m`, `2d`, `perm`), `/unmute`, persistente entre reinicios.
- `/clearchat`, `/lockchat`, `/slowmode <seg|off>`, `/broadcast`.
- Filtros: cooldown, anti-repetición, anti-mayúsculas, anti-flood (`holaaaaa`), **anti-links** con lista blanca y palabras bloqueadas (regex).
- `/chatspy`: ve canales restringidos y mensajes privados.

**Social**
- `/msg`, `/r`, `/ignore`, menú de preferencias `/novachat menu`.
- **Minijuegos automáticos** en el chat (matemáticas, ordenar letras, escribir primero, trivia) con recompensas por comando.
- **Anuncios automáticos** rotativos.
- **Puente a Discord** (chat → webhook), asíncrono y agrupado.

**Placeholders (PlaceholderAPI)**: `%novachat_mentions%`, `%novachat_sounds%`, `%novachat_messages%`, `%novachat_chat%`, `%novachat_ignored%`, `%novachat_muted%`, `%novachat_channel%`, `%novachat_game_active%`.

## Permisos principales

| Permiso | Para qué |
|---|---|
| `novachat.use` | Comandos básicos (por defecto, todos) |
| `novachat.mod` | mute, clearchat, lockchat, slowmode, broadcast |
| `novachat.admin` | `/novachat reload` y `/novachat game` |
| `novachat.spy` | `/chatspy` |
| `novachat.minimessage` | Colores y degradados en el chat |
| `novachat.token.item\|inv\|ender\|pos` | Usar cada token |
| `novachat.bypass.filter\|links\|lock\|clear\|ignore` | Saltarse filtros |

## Compilar

```bash
mvn package
```
El jar queda en `target/NovaChat-2.0.0.jar`.

## Publicar en GitHub (compila y crea el release solo)

1. Crea un repositorio y sube esta carpeta (incluye `.github/workflows/build.yml`).
2. En **Settings → Actions → General → Workflow permissions** elige *Read and write permissions*.
3. Cada `push` a `main` compila y publica un release con el `.jar`. Para una versión fija: `git tag v2.0.0 && git push --tags`.
