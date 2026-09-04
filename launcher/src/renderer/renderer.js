'use strict';

/* -----------------------------------------------------------------
   Solo se tocan transform, opacity y filter. El puntero se interpola
   con un muelle en rAF y el bucle se para solo cuando todo esta
   quieto: la ventana no gasta CPU mientras nadie la mueve.
   ----------------------------------------------------------------- */

const els = {
  body: document.body,
  minimize: document.getElementById('btn-minimize'),
  close: document.getElementById('btn-close'),
  discord: document.getElementById('btn-discord'),
  discordLabel: document.querySelector('.discord-btn-label'),
  discordInner: document.querySelector('.discord-btn-inner'),
  status: document.getElementById('status'),
  studioLine: document.getElementById('studio-line'),
  version: document.getElementById('version'),
  account: document.getElementById('account'),
  accountAvatar: document.getElementById('account-avatar'),
  accountName: document.getElementById('account-name'),
  accountTag: document.getElementById('account-tag'),
  lede: document.querySelector('.lede'),
  parallax: document.querySelector('.parallax'),
  specular: document.querySelector('.card-specular'),
  markTilt: document.querySelector('.mark-tilt'),
  card: document.querySelector('.card')
};

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

/* ============================ PUNTERO ============================
   Los valores no saltan a la posicion del raton: la persiguen. Sin
   ese retardo el movimiento se ve mecanico, no vivo. */

const pointer = {
  targetX: 0, targetY: 0,   // -1 .. 1 respecto al centro de la ventana
  x: 0, y: 0,
  inside: false
};

const STIFFNESS = 0.075;   // cuanto se acerca al objetivo por frame
const SETTLED = 0.0004;    // por debajo de esto se considera parado

let frame = null;

function render() {
  const dx = pointer.targetX - pointer.x;
  const dy = pointer.targetY - pointer.y;

  pointer.x += dx * STIFFNESS;
  pointer.y += dy * STIFFNESS;

  // El fondo va al contrario que el puntero y muy poco: 10px de tope.
  els.parallax.style.transform =
    `translate3d(${(-pointer.x * 10).toFixed(2)}px, ${(-pointer.y * 10).toFixed(2)}px, 0)`;

  // El reflejo del cristal si acompaña al puntero, con mas recorrido.
  els.specular.style.transform =
    `translate3d(${(pointer.x * 150).toFixed(2)}px, ${(pointer.y * 110).toFixed(2)}px, 0)`;

  // La marca se inclina apenas 5 grados: insinua volumen, no lo grita.
  els.markTilt.style.transform =
    `rotateY(${(pointer.x * 5).toFixed(2)}deg) rotateX(${(-pointer.y * 5).toFixed(2)}deg)`;

  if (Math.abs(dx) < SETTLED && Math.abs(dy) < SETTLED) {
    frame = null;   // quieto: se apaga el bucle
    return;
  }
  frame = requestAnimationFrame(render);
}

function wake() {
  if (frame === null) frame = requestAnimationFrame(render);
}

if (!reduceMotion.matches) {
  window.addEventListener('pointermove', (event) => {
    pointer.targetX = (event.clientX / window.innerWidth) * 2 - 1;
    pointer.targetY = (event.clientY / window.innerHeight) * 2 - 1;
    pointer.inside = true;
    wake();
  });

  // Al salir la escena vuelve al centro en vez de quedarse torcida.
  window.addEventListener('pointerleave', () => {
    pointer.targetX = 0;
    pointer.targetY = 0;
    pointer.inside = false;
    wake();
  });
}

/* ============================ ESTADO ============================
   El texto no se sustituye en seco: se difumina, cambia y vuelve.
   Sin el blur se ven dos textos distintos solapados. */

let statusTimer = null;

function setStatus(text, kind) {
  clearTimeout(statusTimer);

  const apply = () => {
    els.status.textContent = text || '';
    els.status.className = 'status' + (kind ? ` is-${kind}` : '');
    els.status.dataset.swapping = 'false';
  };

  if (!els.status.textContent) {
    apply();
    return;
  }

  els.status.dataset.swapping = 'true';
  statusTimer = setTimeout(apply, 200);
}

// Mismo truco que el estado: difuminar, cambiar, volver.
function swapText(el, text) {
  el.dataset.swapping = 'true';
  setTimeout(() => {
    el.textContent = text;
    el.dataset.swapping = 'false';
  }, 200);
}

function setLabel(text) {
  els.discord.dataset.swapping = 'true';
  setTimeout(() => {
    els.discordLabel.textContent = text;
    els.discord.dataset.swapping = 'false';
  }, 200);
}

function setBusy(busy) {
  els.body.dataset.busy = busy ? 'true' : 'false';
  els.discord.disabled = busy;
}

/* =========================== ACCIONES =========================== */

els.minimize.addEventListener('click', () => window.launcher.minimize());
els.close.addEventListener('click', () => window.launcher.close());

els.discord.addEventListener('click', async () => {
  setBusy(true);
  setLabel('Esperando a Discord...');
  setStatus('Te hemos abierto Discord en el navegador. Autoriza el acceso y vuelve aqui.');

  const result = await window.launcher.loginWithDiscord();

  setBusy(false);

  if (!result.ok) {
    setLabel('Reintentar con Discord');
    setStatus(result.error, 'error');
    return;
  }

  const user = result.user;
  els.accountAvatar.src = user.avatarUrl;
  els.accountAvatar.alt = `Avatar de ${user.globalName}`;
  els.accountName.textContent = user.globalName;
  els.accountTag.textContent = `@${user.username}`;

  swapText(els.lede, `Hola, ${user.globalName}.`);
  els.body.dataset.signedIn = 'true';
  setStatus('Sesion iniciada. El resto del launcher llega en la siguiente beta.', 'ok');
});

/* ============================ ARRANQUE ============================
   La ventana ya es visible cuando esto corre: se espera un frame
   para que la transicion de entrada tenga de donde salir. */

(async function init() {
  const info = await window.launcher.getInfo();
  els.studioLine.textContent = info.studioName;
  els.version.textContent = `v${info.version}`;

  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      els.body.dataset.phase = 'ready';
    });
  });

  if (!info.isConfigured) {
    // Despues de la cascada de entrada, para no pisarla.
    setTimeout(() => {
      setStatus(
        'Falta configurar el DISCORD_CLIENT_ID: copia launcher.config.example.json ' +
        'a launcher.config.json y pon el ID de tu app de Discord.',
        'error'
      );
    }, 700);
  }
})();
