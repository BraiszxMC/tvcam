'use strict';

const els = {
  minimize: document.getElementById('btn-minimize'),
  close: document.getElementById('btn-close'),
  discord: document.getElementById('btn-discord'),
  discordLabel: document.querySelector('.discord-btn-label'),
  status: document.getElementById('status'),
  studioLine: document.getElementById('studio-line'),
  version: document.getElementById('version'),
  account: document.getElementById('account'),
  accountAvatar: document.getElementById('account-avatar'),
  accountName: document.getElementById('account-name'),
  accountTag: document.getElementById('account-tag')
};

function setStatus(text, kind) {
  els.status.textContent = text || '';
  els.status.className = 'status' + (kind ? ` is-${kind}` : '');
}

function setLoading(loading) {
  els.discord.disabled = loading;
  els.discord.classList.toggle('is-loading', loading);
  els.discordLabel.textContent = loading
    ? 'Esperando a Discord...'
    : 'Iniciar sesion con Discord';
}

els.minimize.addEventListener('click', () => window.launcher.minimize());
els.close.addEventListener('click', () => window.launcher.close());

els.discord.addEventListener('click', async () => {
  setLoading(true);
  setStatus('Te hemos abierto Discord en el navegador. Autoriza el acceso y vuelve aqui.');

  const result = await window.launcher.loginWithDiscord();

  setLoading(false);

  if (!result.ok) {
    setStatus(result.error, 'error');
    return;
  }

  const user = result.user;
  els.accountAvatar.src = user.avatarUrl;
  els.accountAvatar.alt = `Avatar de ${user.globalName}`;
  els.accountName.textContent = user.globalName;
  els.accountTag.textContent = `@${user.username}`;
  els.account.hidden = false;

  els.discord.hidden = true;
  setStatus('Sesion iniciada. El resto del launcher llega en la siguiente beta.', 'ok');
});

(async function init() {
  const info = await window.launcher.getInfo();
  els.studioLine.textContent = info.studioName;
  els.version.textContent = `v${info.version}`;

  if (!info.isConfigured) {
    setStatus(
      'Falta configurar el DISCORD_CLIENT_ID: copia launcher.config.example.json ' +
      'a launcher.config.json y pon el ID de tu app de Discord.',
      'error'
    );
  }
})();
