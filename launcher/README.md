# TVCam Launcher (beta)

Launcher de Minecraft para el estudio de eventos **TVCam**. Esta es una version
**muy beta**: al abrirla solo aparece el menu de inicio de sesion con Discord.
Todavia no descarga ni arranca Minecraft.

- Interfaz *liquid glass* en azul lapislazuli sobre grises oscuros.
- Ventana fija de 900x600: **no se puede agrandar ni encoger**, solo minimizar y cerrar.
- Barra de titulo propia, sin boton de maximizar y sin menu.
- Inicio de sesion con Discord (OAuth2) en el navegador del sistema.

## Requisitos

- Node.js 18 o superior.

## Poner en marcha

```bash
npm install
cp launcher.config.example.json launcher.config.json
npm start
```

## Configurar Discord

1. Entra en https://discord.com/developers/applications y crea una aplicacion.
2. En **OAuth2 → Redirects** añade exactamente:
   `http://127.0.0.1:53131/callback`
3. Copia el **Client ID** y pegalo en `launcher.config.json`:

```json
{
  "discordClientId": "123456789012345678",
  "callbackPort": 53131,
  "studioName": "TVCam Studio",
  "requiredGuildId": ""
}
```

| Campo | Para que sirve |
| --- | --- |
| `discordClientId` | Client ID de tu aplicacion de Discord. Obligatorio. |
| `callbackPort` | Puerto local que escucha la vuelta de Discord. Debe coincidir con el redirect. |
| `studioName` | Texto que sale bajo el titulo del launcher. |
| `requiredGuildId` | Si lo rellenas, solo entran quienes esten en ese servidor de Discord. |

Tambien se pueden usar variables de entorno: `DISCORD_CLIENT_ID`,
`DISCORD_CALLBACK_PORT` y `DISCORD_GUILD_ID`. Tienen prioridad sobre el JSON.

`launcher.config.json` esta en el `.gitignore`: no subas tu configuracion.

### Como funciona el login

Se usa el *implicit grant* de Discord, asi que **no hace falta client secret**
(que en una app de escritorio no se puede guardar en secreto de todas formas):

1. El launcher levanta un servidor en `127.0.0.1:<callbackPort>` y abre Discord
   en el navegador.
2. Al autorizar, Discord vuelve a `/callback` con el token en el fragmento de la
   URL; esa pagina se lo reenvia al launcher.
3. El launcher pide `/users/@me` (y `/users/@me/guilds` si hay `requiredGuildId`)
   y muestra la cuenta.

Se comprueba el parametro `state` y el token se queda solo en memoria: **no se
guarda en disco**.

## Compilar un ejecutable

```bash
npm run dist
```

Genera un instalador NSIS en Windows, un AppImage en Linux y un dmg en macOS
(dentro de `dist/`).

## Estructura

```
src/
  main.js           proceso principal de Electron y ajustes de la ventana
  preload.js        puente seguro entre la ventana y el proceso principal
  config.js         lectura de launcher.config.json y variables de entorno
  discord-auth.js   OAuth2 de Discord con servidor local de callback
  renderer/
    index.html      menu de inicio de sesion
    styles.css      estilo liquid glass (lapislazuli + grises oscuros)
    renderer.js     logica del menu
```

## Pendiente para las siguientes betas

- Guardar la sesion entre aperturas.
- Perfiles y versiones de Minecraft del estudio.
- Descarga y arranque del juego.
- Actualizaciones del propio launcher.

---

Proyecto no oficial. No esta afiliado ni respaldado por Mojang, Microsoft ni Discord.
