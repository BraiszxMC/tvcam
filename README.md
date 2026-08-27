# TVCam

Mod **cliente** de Fabric para **Minecraft 1.21.11** que abre una **segunda ventana**
con la imagen limpia de una camara, para retransmitir estilo television: tu juegas
en tu ventana y el espectador ve el plano de camara.

* Minecraft **1.21.11**, Fabric, Java 21
* **Solo cliente**: el servidor no necesita el mod. Funciona en singleplayer y en
  cualquier servidor, incluso siguiendo la pelota de **BlockBall**.
* Requiere **Fabric API**

---

## Calidad de imagen y FPS

Esto es lo que hay que entender para saber si te sale a cuenta:

**La resolucion de la emision es la de tu ventana de Minecraft.** TVCam no
renderiza el mundo aparte: coge el frame que el juego acaba de dibujar y lo
presenta en la otra ventana. Si juegas a pantalla completa en 1080p, la emision
es 1080p real. Si juegas en una ventanita de 1280x720, la emision es 720p por
mucho que agrandes la ventana de camara.

**La ventana de camara sale a 1920x1080** por defecto, que es el tamano que
captura OBS. Se cambia con `/tvcam res <ancho> <alto>`, y `/tvcam info` te dice
las dos resoluciones (origen y ventana) para que veas si estas escalando hacia
arriba sin ganar detalle.

**Los FPS se reparten.** Por defecto 1 de cada 2 frames va a la emision, asi que
cada ventana va a la mitad de los FPS del juego:

| FPS del juego | Emision | Tu ventana |
|---|---|---|
| 60 (vsync) | 30 | 30 |
| 80 | 40 | 40 |
| 120 | 60 | 60 |

Para tu objetivo de **30-40 fps a 1080p necesitas que Minecraft te vaya a 60-80
fps a 1080p**, que es facil en cualquier equipo decente sin shaders. Consejo:
quita el vsync y pon el limite de FPS en 120; con vsync a 60 te quedas clavado en
30 y 30.

Si prefieres priorizar la emision o tu propia vista, `/tvcam ratio <n>` cambia el
reparto: con `ratio 3` la emision se lleva 1 de cada 3 frames (a 120 fps serian 40
para la emision y 80 para ti).

Lo importante: **el mundo no se renderiza dos veces**, asi que el coste total es
practicamente el de jugar sin el mod. No pierdes la mitad del rendimiento, solo
repartes los frames que ya tenias.

---

## Retransmitir un partido de BlockBall

La pelota de BlockBall no es una entidad normal del servidor: el plugin la manda
por paquetes como un **armor stand invisible con un item en la cabeza**. Eso llega
igual a tu cliente, asi que TVCam la reconoce y la sigue **sin que el servidor
tenga que instalar nada**. Cuando marcan gol el plugin borra la pelota y crea otra;
TVCam se da cuenta y engancha la nueva sola.

Montar una retransmision:

```
/tvcam ball                 marca la pelota como objetivo
(te colocas en la banda)
/tvcam add Banda
/tvcam mode 1 seguir        esa camara ya no se mueve, pero gira siguiendo la pelota
(te colocas detras de la porteria)
/tvcam add Porteria
/tvcam mode 2 seguir
(te colocas en alto sobre el centro del campo)
/tvcam add General
/tvcam mode 3 seguir
/tvcam auto true            el realizador corta solo al mejor plano
```

Y ya esta: capturas la ventana `TVCam` en OBS y tienes un partido realizado.

### La mesa de realizacion

Tecla **M** (o `/tvcam desk`) y se abre la mesa, con aspecto de mezclador de
video: **multiviewer** arriba con la imagen de cada camara, **bus de programa**
abajo con una tecla por plano y piloto de tally rojo en el que esta al aire, y el
panel de la camara seleccionada a la derecha.

**Los monitores son imagen de verdad, no un icono.** Una miniatura no se puede
"capturar": el mundo hay que dibujarlo desde esa camara. Mientras la mesa esta
abierta, el realizador dedica **uno de cada cuatro frames** a una camara, rotando
entre todas, y en ese frame **el juego dibuja el mundo directamente dentro del
monitor**, a 256x144.

Que se dibuje dentro y no se copie no es un detalle: la copia del juego respeta el
canal alfa y el render del mundo lo deja a cero, asi que copiar daba monitores en
negro. Ademas, al renderizar a 256x144 en lugar de a pantalla completa, un frame
de monitor cuesta bastante menos que un frame normal. Con la mesa cerrada no se
gasta ni un frame en esto.

Un clic en un monitor lo selecciona para editarlo; **dos clics lo ponen al aire**,
igual que las teclas del bus de programa o el numpad, que tambien funcionan con la
mesa abierta.

En la fila **ROTULOS** se escribe el texto que sale al marcar (dejalo vacio y no se
canta el gol), se prueba con **PROBAR GOL** sin esperar a que nadie marque, y se
elige si las hitboxes salen o no en la emision.

* **La emision sigue saliendo con la mesa abierta**, y ninguna pantalla del juego
  aparece en la emision: puedes tocar ajustes viendo el resultado en directo en la
  ventana que captura OBS. Tampoco pausa la partida en un mundo propio.
* Desde ahi se crea, renombra, duplica, reordena, recoloca y borra cada camara sin
  escribir un solo comando.

**El item que abre la mesa.** Si configuras `/tvcam wand minecraft:clock`, llevar
ese item en la mano abre la mesa sola y guardarlo la cierra. Es un atajo comodo,
pero **la tecla funciona siempre**: de espectador, sin inventario o si el servidor
te limpia la mochila al entrar a la arena, que es justo cuando el item te dejaria
tirado.

**Con Stream Deck.** La mesa no hace falta: cada accion tiene su tecla (todas
reasignables en los controles de Minecraft), asi que basta con configurar cada
boton fisico con la tecla que quieras.

### La emision sale limpia

Lo que ve el espectador nunca lleva encima nada tuyo:

* **Sin HUD, sin chat, sin titulos y sin la mano.**
* **Sin menus**: puedes abrir la mesa, el inventario o el menu de pausa sin que
  salgan en la emision.
* **Sin hitboxes ni ayudas de F3**, aunque las tengas puestas en tu pantalla.
  Se desactiva con `/tvcam debug true` si alguna vez quieres que salgan.
* **Tu personaje si sale**: aunque tu juegues en primera persona, en el plano de
  camara se te ve entero.

La imagen de la emision se recoge **justo cuando el mundo esta dibujado y antes de
que el juego pinte nada encima**. Es el unico instante del frame en el que el
framebuffer contiene solo la camara, y hacerlo mas tarde metia trozos de la
interfaz y de los atlas de texturas en la emision.

### Cada camara con su propio objetivo

Cada camara guarda **a quien sigue ella**, no lo que haga el resto:

| Ajuste de la camara | Que hace |
|---|---|
| `general` | sigue al objetivo general de la retransmision (lo que marques con `/tvcam ball`) |
| `pelota` | la pelota del campo activo, pase lo que pase con el objetivo general |
| un jugador | ese jugador por nombre |
| `nadie` | no sigue a nadie aunque este en modo seguir |

Asi puedes tener a la vez la camara 1 barriendo la pelota, la 2 pegada a tu
delantero y la 3 quieta en la grada. Se cambia en la mesa con el boton **Sigue a**
(que va pasando por la pelota y por cada jugador conectado) o con
`/tvcam target <n> ball|player <nombre>|global|none`.

Ademas, cada camara puede llevar **su propio zoom, su propio zoom automatico y su
propio pulso de seguimiento**, o dejarlos en `general` para heredar el ajuste
comun. Una camara de banda con pulso lento y una de porteria con pulso rapido,
cada una a su aire.

**Modos de camara**

| Modo | Que hace |
|---|---|
| `fija` | no se mueve ni gira. El plano de recurso de siempre |
| `seguir` | se queda en su sitio y gira siguiendo al objetivo, como una camara sobre tripode. **Es el plano de futbol de toda la vida** |
| `acompanar` | se mueve con el objetivo manteniendo la distancia con la que la creaste, y lo encuadra. Para planos de seguimiento cercanos |

### Varios campos en el mismo mundo

Si tienes mas de un campo, marca con cual estas trabajando y TVCam dejara de
liarse entre pelotas:

```
(te pones en el centro del campo)
/tvcam field add CampoNorte 60      el numero es el radio en bloques
/tvcam field goal 1                 puesto en una porteria
/tvcam field goal 2                 y en la otra
```

Con un campo activo, al buscar la pelota **solo se miran las de dentro**. Eso
importa porque cuando marcan gol el plugin borra la pelota y crea otra: sin campo
marcado, la nueva podia ser la de la pista de al lado. `/tvcam field use <nombre>`
cambia de campo y `/tvcam field none` vuelve a buscar por todo el mundo.

### Cantar los goles

Cuando hay gol, sobre la emision aparece un **GOL** grande con el nombre del autor
y el marcador. En tu pantalla no sale nada: es un rotulo de television, va solo en
la ventana que captura OBS. `/tvcam testgoal` lo lanza para que veas como queda.

Hay **dos maneras** de enterarse del gol, porque ninguna vale siempre:

| Via | Cuando funciona | Que saca |
|---|---|---|
| **El titulo del servidor** | solo si **estas jugando el partido**: BlockBall manda ese titulo unicamente a los jugadores de los dos equipos | el autor exacto y el marcador |
| **Ver entrar la pelota** | siempre, aunque retransmitas de espectador; necesita las dos porterias marcadas | el autor por el ultimo que la toco |

La primera no depende del idioma: se reconoce el titulo por su forma de marcador
(`3 : 1`) y el autor se busca comparando el subtitulo con la lista de jugadores
conectados, asi que da igual como tenga el servidor traducido el mensaje.

**Objetivos**

| Comando | Que sigue |
|---|---|
| `/tvcam ball` | la pelota de BlockBall (se reengancha sola tras cada gol) |
| `/tvcam lock` | aquello a lo que estas apuntando, hasta 160 bloques |
| `/tvcam player <nombre>` | a un jugador concreto |

El **realizador automatico** (`/tvcam auto true`) corta solo a la camara que mejor
ve la jugada: descarta las que tienen la pelota fuera de plano, prefiere una
distancia de tele (ni pegada ni en la otra punta) y aguanta cada plano unos
segundos para no marear. Con `/tvcam auto false` vuelves a cortar tu a mano.

### Zoom automatico

`/tvcam autozoom true` y la camara aprieta cuando la jugada se aleja y se abre
cuando se acerca, para que la pelota se vea siempre parecida de grande. Es lo que
hace un camara real en la banda.

| Comando | Que hace | Por defecto |
|---|---|---|
| `/tvcam autozoom <true\|false>` | activarlo | apagado |
| `/tvcam autozoom dist <bloques>` | distancia a la que se queda en x1; mas lejos, aprieta | 25 |
| `/tvcam autozoom max <1-10>` | tope, para que no acabe mirando por una pajita | 3 |
| `/tvcam autozoom speed <0-100>` | lo rapido que se mueve el zoom | 30 |

Se mueve **despacio y con zona muerta**: no persigue cada centimetro de la pelota,
porque un zoom nervioso marea y delata que lo lleva una maquina. Con `speed 10`
queda de cine y con `speed 70` reacciona casi al momento. El automatico se
multiplica por el zoom manual de la camara, asi que puedes tener una camara ya
apretada de base que ademas se ajuste sola.

Necesita objetivo (`/tvcam ball`) y funciona tambien en camaras `fija`: aunque no
giren, aprietan cuando la jugada se va lejos.

---

## Como se usa

1. Entra al mundo, ponte donde quieras la camara mirando hacia donde quieras.
2. `/tvcam add Torre` (o **Numpad ✱**).
3. **Numpad 1-9** corta a esa camara. Al cortar por primera vez se abre la ventana.
4. En OBS: *Captura de ventana* -> `TVCam`.
5. **Numpad 0** para la emision.

| Comando | Que hace |
|---|---|
| `/tvcam add [nombre]` | crea una camara donde estas, mirando a donde miras |
| `/tvcam list` | lista las camaras de este mundo |
| `/tvcam del <n>` / `/tvcam clear` | borra una o todas |
| `/tvcam cut <n>` | corta a esa camara (`0` = parar) |
| `/tvcam mode <n> <modo>` | fija, seguir o acompanar |
| `/tvcam zoom <1-10>` | zoom de la camara al aire |
| `/tvcam ball` · `/tvcam lock` · `/tvcam player <n>` | a quien enfocan |
| `/tvcam res <ancho> <alto>` | tamano de la ventana de camara |
| `/tvcam transition <ms>` | duracion del travelling al cortar (0 = corte seco) |
| `/tvcam smooth <0-100>` | pulso del camara al seguir |
| `/tvcam ratio <2-10>` | reparto de frames |
| `/tvcam auto <true\|false>` | realizador automatico |
| `/tvcam info` | resoluciones y ajustes actuales |
| `/tvcam window` | abre o cierra la ventana |

| Tecla | Que hace |
|---|---|
| Numpad 1-9 | cortar a la camara 1-9 |
| Numpad 0 | parar la emision |
| Numpad ✱ | crear camara aqui |
| Numpad . | abrir/cerrar la ventana |
| Numpad + / - | zoom de la camara al aire |
| Numpad / | marcar como objetivo aquello a lo que apuntas (o la pelota si no apuntas a nada) |
| Numpad Intro | realizador automatico on/off |

Los ajustes y las camaras se guardan en `config/tvcam.json`, separadas por
servidor y dimension.

---

## Transiciones

Al cortar de una camara a otra, TVCam hace un **travelling** de 700 ms por defecto:
interpola posicion y giro con aceleracion y frenada suaves, tomando siempre el
camino corto en el giro. `/tvcam transition 0` deja el corte seco de toda la vida,
y `/tvcam transition 1500` lo hace mas cinematografico.

El seguimiento tambien esta suavizado: la camara persigue al objetivo con un poco
de retraso, como el pulso de un camara humano, en vez de clavarse en el como un
misil. `/tvcam smooth 0` lo hace instantaneo, `/tvcam smooth 90` muy perezoso.

---

## Como funciona por dentro

Minecraft dibuja **un** frame por vuelta, desde tus ojos, en un unico framebuffer.
Para emitir hacen falta dos imagenes distintas. Renderizar el mundo dos veces por
frame cuesta la mitad de los FPS, asi que TVCam **alterna**:

* los frames de camara se dibujan desde la camara, con el HUD y la mano ocultos,
  y se presentan en la ventana de camara;
* el resto se dibujan normal y se presentan en tu ventana.

Como el juego solo tiene un framebuffer, en los frames de camara tu ventana
mostraria la vista de la camara: por eso se guarda una copia del ultimo frame tuyo
y se vuelve a pintar en tu ventana, que asi no parpadea.

| Fichero | Papel |
|---|---|
| `camera/CameraDirector` | el realizador: camaras, cortes, travelling, reparto de frames |
| `camera/CameraPoint` · `CameraMode` | una camara: sitio, encuadre, modo y zoom |
| `camera/CameraPose` | posicion + giro en un instante, con interpolacion por el camino corto |
| `camera/TargetTracker` | a quien se enfoca; reconoce la pelota de BlockBall y la reengancha tras cada gol |
| `camera/CameraStore` · `TVCamSettings` | guardado en `config/tvcam.json` |
| `render/CameraWindow` | la segunda ventana GLFW; comparte contexto de OpenGL con el juego |
| `render/FrameMirror` | copia de tu ultimo frame, para que tu ventana no parpadee |
| `mixin/MinecraftClientMixin` | marca el principio y el final de cada frame |
| `mixin/CameraMixin` | mueve el punto de vista a donde diga el realizador |
| `mixin/WindowMixin` | reparte cada frame a su ventana antes de presentarlo |
| `mixin/GameRendererMixin` | oculta la mano en la emision y aplica el zoom |
| `mixin/SoundSystemMixin` | el oido se queda contigo y no salta a la camara |

La ventana secundaria se crea con `glfwCreateWindow` **compartiendo el contexto**
de la ventana del juego. Compartir contexto es lo que hace que esto sea barato: la
imagen ya esta en la GPU y solo se presenta en otra ventana, sin copiar pixeles por
la CPU.

---

## Limitaciones conocidas

* **La camara solo ve chunks cargados.** Para un campo de futbol no es problema,
  pero no pongas una camara a 300 bloques de donde estas.
* **Sin probar con Sodium/Iris.** Es lo siguiente en la lista.
* El reconocimiento de la pelota busca un armor stand invisible con algo en la
  cabeza. Si el mundo tiene otras decoraciones asi cerca, usa `/tvcam lock`
  apuntando a la pelota para no dejar lugar a dudas.
* En los frames de camara el sonido se queda en tu posicion a proposito, para que
  no salte de sitio; la emision lleva tu audio, no el de la camara.

---

## Compilar

```
./gradlew build
```

El jar sale en `build/libs/tvcam-<version>.jar`. Ponlo en `mods/` junto a
Fabric API.

Para comprobar que la ventana secundaria funciona en tu equipo sin tener que
montar nada, arranca con la variable `TVCAM_SELFTEST=1`: el mod abre la ventana,
presenta unos frames, guarda una captura de lo que sale en ella y lo escribe todo
en el log.
