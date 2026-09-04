'use strict';

const fs = require('fs');
const path = require('path');

// La configuracion se lee, en este orden, de:
//   1. variables de entorno (DISCORD_CLIENT_ID, DISCORD_CALLBACK_PORT)
//   2. launcher.config.json en la raiz del proyecto
//   3. los valores por defecto de aqui abajo
const DEFAULTS = {
  discordClientId: '',
  callbackPort: 53131,
  studioName: 'TVCam Studio',
  // Si se rellena, solo dejamos entrar a miembros de este servidor de Discord.
  requiredGuildId: ''
};

function readFileConfig() {
  const file = path.join(__dirname, '..', 'launcher.config.json');
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (err) {
    if (err.code !== 'ENOENT') {
      console.warn('[config] launcher.config.json no se pudo leer:', err.message);
    }
    return {};
  }
}

const fileConfig = readFileConfig();

const config = {
  ...DEFAULTS,
  ...fileConfig,
  discordClientId: process.env.DISCORD_CLIENT_ID || fileConfig.discordClientId || DEFAULTS.discordClientId,
  callbackPort: Number(process.env.DISCORD_CALLBACK_PORT || fileConfig.callbackPort || DEFAULTS.callbackPort),
  requiredGuildId: process.env.DISCORD_GUILD_ID || fileConfig.requiredGuildId || DEFAULTS.requiredGuildId
};

config.redirectUri = `http://127.0.0.1:${config.callbackPort}/callback`;
config.isConfigured = Boolean(config.discordClientId);

module.exports = config;
