'use strict';

const http = require('http');
const crypto = require('crypto');
const { shell } = require('electron');
const config = require('./config');

const AUTH_URL = 'https://discord.com/oauth2/authorize';
const API = 'https://discord.com/api/v10';
const TIMEOUT_MS = 3 * 60 * 1000;

// Pagina que se muestra en el navegador al volver de Discord. El token viene en
// el fragmento (#access_token=...), que el navegador NO manda al servidor, asi
// que lo reenviamos nosotros con un fetch a /token.
const CALLBACK_HTML = `<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>TVCam Launcher</title>
<style>
  body{margin:0;height:100vh;display:grid;place-items:center;background:#0d1220;
       color:#dbe6ff;font:16px/1.5 system-ui,Segoe UI,sans-serif}
  .card{padding:32px 40px;border-radius:20px;background:rgba(38,92,196,.14);
        border:1px solid rgba(120,170,255,.25);text-align:center}
  b{color:#6f9dff}
</style></head><body>
<div class="card"><p><b>Sesion iniciada.</b></p><p id="m">Ya puedes volver al launcher.</p></div>
<script>
  const p = new URLSearchParams(location.hash.slice(1));
  fetch('/token', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      access_token: p.get('access_token'),
      token_type: p.get('token_type'),
      state: p.get('state'),
      error: p.get('error'),
      error_description: p.get('error_description')
    })
  }).catch(() => { document.getElementById('m').textContent =
    'Vuelve al launcher e intentalo otra vez.'; });
</script></body></html>`;

async function discordGet(endpoint, token, tokenType) {
  const res = await fetch(`${API}${endpoint}`, {
    headers: { Authorization: `${tokenType || 'Bearer'} ${token}` }
  });
  if (!res.ok) {
    throw new Error(`Discord respondio ${res.status} en ${endpoint}`);
  }
  return res.json();
}

// Levanta un servidor local, abre el navegador y espera a que Discord vuelva.
// Devuelve { id, username, globalName, avatarUrl }.
function login() {
  if (!config.isConfigured) {
    return Promise.reject(new Error(
      'Falta el DISCORD_CLIENT_ID. Copia launcher.config.example.json a ' +
      'launcher.config.json y pon el ID de tu aplicacion de Discord.'
    ));
  }

  const state = crypto.randomBytes(16).toString('hex');
  const scopes = ['identify'];
  if (config.requiredGuildId) scopes.push('guilds');

  return new Promise((resolve, reject) => {
    let done = false;
    const finish = (err, value) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      server.close();
      err ? reject(err) : resolve(value);
    };

    const server = http.createServer((req, res) => {
      const url = new URL(req.url, `http://127.0.0.1:${config.callbackPort}`);

      if (req.method === 'GET' && url.pathname === '/callback') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(CALLBACK_HTML);
        return;
      }

      if (req.method === 'POST' && url.pathname === '/token') {
        let body = '';
        req.on('data', (chunk) => {
          body += chunk;
          if (body.length > 8192) req.destroy();
        });
        req.on('end', async () => {
          res.writeHead(204).end();
          let payload;
          try {
            payload = JSON.parse(body);
          } catch {
            return finish(new Error('Respuesta de Discord ilegible.'));
          }
          if (payload.error) {
            return finish(new Error(payload.error_description || payload.error));
          }
          if (payload.state !== state) {
            return finish(new Error('El "state" no coincide, se cancelo el inicio de sesion.'));
          }
          if (!payload.access_token) {
            return finish(new Error('Discord no devolvio ningun token.'));
          }
          try {
            const user = await discordGet('/users/@me', payload.access_token, payload.token_type);
            if (config.requiredGuildId) {
              const guilds = await discordGet('/users/@me/guilds', payload.access_token, payload.token_type);
              if (!guilds.some((g) => g.id === config.requiredGuildId)) {
                return finish(new Error('Esta cuenta no esta en el Discord del estudio.'));
              }
            }
            finish(null, {
              id: user.id,
              username: user.username,
              globalName: user.global_name || user.username,
              avatarUrl: user.avatar
                ? `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png?size=128`
                : `https://cdn.discordapp.com/embed/avatars/0.png`
            });
          } catch (err) {
            finish(err);
          }
        });
        return;
      }

      res.writeHead(404).end();
    });

    const timer = setTimeout(
      () => finish(new Error('Se acabo el tiempo para iniciar sesion.')),
      TIMEOUT_MS
    );

    server.on('error', (err) => finish(
      err.code === 'EADDRINUSE'
        ? new Error(`El puerto ${config.callbackPort} esta ocupado. Cambialo en launcher.config.json.`)
        : err
    ));

    server.listen(config.callbackPort, '127.0.0.1', () => {
      const params = new URLSearchParams({
        client_id: config.discordClientId,
        redirect_uri: config.redirectUri,
        response_type: 'token',
        scope: scopes.join(' '),
        state,
        prompt: 'consent'
      });
      shell.openExternal(`${AUTH_URL}?${params}`);
    });
  });
}

module.exports = { login };
