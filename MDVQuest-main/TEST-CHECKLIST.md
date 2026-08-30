# Checklist MDVQuest 1.3.0

## Menús viewer e interactivo

- [ ] `/quest`, `/quests`, `/misiones` y `/mdvquest` abren el mismo visor público.
- [ ] El botón de MDVSocial abre el visor público.
- [ ] Hacer clic sobre una misión en VIEW_ONLY no abre detalles ni reclama recompensas.
- [ ] Una misión de entrega en VIEW_ONLY no consume objetos.
- [ ] Una misión completada en VIEW_ONLY indica que debe visitarse al encargado.
- [ ] `/mdvquest npc` abre el menú interactivo al administrador.
- [ ] Desde consola, `mdvquest npc <jugador>` abre el menú interactivo del jugador conectado.
- [ ] En INTERACTIVE se abren detalles, se entregan objetos y se reclaman premios como en 1.2.5.
- [ ] Un jugador sin `mdvquest.admin.open-interactive` no puede ejecutar el comando administrativo.
- [ ] Cambiar título, slots o materiales de `menus.viewer` no altera `menus.interactive`.
- [ ] Cambiar título, slots o materiales de `menus.interactive` no altera `menus.viewer`.

## Filtro del catálogo administrativo

- [ ] `/mdvquest admin` abre siempre con General/todos los YAML.
- [ ] El botón de filtro aparece en el slot 46 por defecto.
- [ ] Clic izquierdo avanza al siguiente YAML.
- [ ] Clic derecho vuelve al YAML anterior.
- [ ] Shift-click restablece General.
- [ ] El filtro se combina correctamente con las cuatro categorías de duración.
- [ ] El filtro se conserva al cambiar página y regresar desde una vista previa.
- [ ] Al guardar o mover una misión, se abre el YAML de destino.

## Migración desde 1.2.x

- [ ] Conservar un `config.yml` personalizado de 1.2.x y arrancar 1.3.0.
- [ ] Confirmar que el diseño anterior se copie a `menus.viewer` y `menus.interactive`.
- [ ] Confirmar que `menus.interactive.detail` conserve el detalle anterior.
- [ ] Confirmar que no cambien misiones, familias, rotaciones, progreso ni SQLite.

---

# Checklist de staging — base completa MDVQuest 1.3.0

Prueba esta versión en una copia de Purpur 1.21.6 antes de producción.

## Arranque y migración

- [ ] El servidor inicia sin `NoSuchMethodError`.
- [ ] `/mdvquest status` muestra versión 1.3.0.
- [ ] Las misiones y el progreso existentes de 1.2.0 permanecen.
- [ ] `/mdvquest` abre el menú público.
- [ ] `/mdvquest admin` exige `mdvquest.admin`.

## Pools, permisos y cantidades variables

- [ ] Cada rotación elige una cantidad dentro de `min-missions` / `max-missions` para normal, VIP1 y VIP2.
- [ ] Reiniciar o ejecutar `/mdvquest reload` no cambia la cantidad ni la selección del ciclo actual.
- [ ] El pool normal contiene únicamente definiciones `normal`.
- [ ] El pool VIP1 mezcla normales restantes y definiciones `vip1`, sin repetir las normales ya activas.
- [ ] El pool VIP2 mezcla definiciones `vip1` restantes y `vip2`, sin duplicados.
- [ ] Todos los jugadores ven y progresan misiones normales, VIP1 y VIP2.
- [ ] Sin permisos, VIP1 aparece como panel celeste y VIP2 como panel amarillo.
- [ ] El lore bloqueado usa el texto editable y muestra el rango correcto.
- [ ] `mdvquest.access.vip1` permite reclamar VIP1.
- [ ] `mdvquest.access.vip2` permite reclamar VIP2 y, con herencia activada, también VIP1.
- [ ] Perder el permiso antes de reclamar vuelve a bloquear la recompensa; recuperarlo la habilita mientras la misión no expire.
- [ ] Completar una misión VIP sin permiso no muestra el mensaje final de reclamación ni reproduce el sonido de confirmación.
- [ ] El editor guarda correctamente `access-pool: normal|vip1|vip2`.

## Forzar misión concreta

- [ ] `/mdvquest force <id>` añade la misión a su ciclo actual sin borrar otras instancias ni progreso.
- [ ] Una definición deshabilitada puede forzarse para pruebas si su rotación está habilitada.
- [ ] Forzar una misión ya activa responde correctamente y no crea duplicados.
- [ ] El autocompletado muestra IDs cargados.
- [ ] Un usuario sin `mdvquest.admin.force` no puede ejecutar el comando.

## Reroll

- [ ] `/mdvquest reroll daily` solo muestra la advertencia y no cambia nada.
- [ ] `/mdvquest reroll daily confirmar` regenera los tres pools de la diaria.
- [ ] El reroll cambia cantidad/selección y conserva la expiración del ciclo actual.
- [ ] El progreso y las recompensas pendientes de la rotación afectada se eliminan.
- [ ] `/mdvquest reroll all confirmar` regenera todas las rotaciones habilitadas.
- [ ] Un jugador sin `mdvquest.admin.reroll` no puede ejecutar el comando.

## Menú público

- [ ] Los cuatro libros cambian entre 1d, 2–3d, 4–6d y 7d.
- [ ] Las flechas no aparecen cuando hay una sola página.
- [ ] El lore muestra descripción, objetivos, recompensas y expiración.
- [ ] Las herramientas no muestran daño, atributos o tooltip vanilla innecesario.
- [ ] La cabeza de volver del catálogo ejecuta el comando configurado y la del detalle regresa a la página exacta anterior.
- [ ] Una misión terminada pendiente de reclamar usa panel verde claro, no lana.
- [ ] El detalle muestra hasta 7 objetivos en los slots 10 al 16.
- [ ] Las recompensas físicas y de EXP se ven correctamente y con nombres visibles, no IDs internos.
- [ ] La paginación de recompensas aparece solo con más de 5 entradas.

## Editor

- [ ] `/mdvquest crear` abre el selector de 1–7 días.
- [ ] Se pueden cambiar ID, nombre, lore, icono, duración, peso y YAML.
- [ ] `cancelar` en cualquier prompt vuelve sin aplicar el paso.
- [ ] Se pueden añadir varios objetivos del mismo tipo.
- [ ] `Shift + clic derecho` en añadir objetivos abre la administración.
- [ ] Clic izquierdo edita un objetivo y clic derecho lo elimina.
- [ ] El límite de objetivos muestra un error sin romper el borrador.
- [ ] Guardar crea la misión en el YAML elegido.
- [ ] Clic derecho sobre una misión existente permite editarla y guardar cambios.
- [ ] Renombrar o mover una misión no pierde la original si falla una escritura.

## Recompensas del editor

- [ ] Depositar un item vanilla simple crea `vanilla-items`.
- [ ] Depositar un objeto vanilla con meta crea `exact-items`.
- [ ] Depositar un MMOItem guarda su TYPE e ID.
- [ ] Depositar un objeto Mythic/Crucible guarda su ID interno.
- [ ] Aceptar devuelve los objetos depositados al administrador.
- [ ] Los nuevos objetos se añaden a los actuales.
- [ ] Limpiar elimina solo las recompensas físicas.
- [ ] Cancelar o cerrar devuelve todo lo depositado, incluso con inventario casi lleno.
- [ ] Se pueden configurar EXP `main` y de profesión.

## Reclamación segura

- [ ] Una recompensa de 65 objetos apilables exige 2 slots.
- [ ] Sin espacio suficiente, la misión no queda reclamada.
- [ ] El chat indica exactamente cuántos slots faltan.
- [ ] Con espacio, todos los objetos se entregan en slots reservados.
- [ ] Si se ocupa un slot reservado durante el tick de entrega, el objeto intruso cae al suelo y la recompensa se coloca correctamente.
- [ ] Doble clic o dos misiones reclamadas al mismo tiempo no duplican premios.
- [ ] Un ID MMOItems o Mythic inválido bloquea el reclamo y deja la misión pendiente.
- [ ] EXP y comandos se ejecutan una sola vez después de entregar los objetos.

## Objetivos

- [ ] MINE_BLOCK rechaza bloques colocados con `natural-only`.
- [ ] BREAK_CUSTOM_ORE cuenta el evento real de MDVHeadOres.
- [ ] CUT_LOG y HARVEST_CROP cuentan solo acciones válidas.
- [ ] KILL_VANILLA_MOB cuenta EntityType configurados.
- [ ] KILL_MYTHIC_MOB, familia y miniboss no duplican la misma muerte.
- [ ] KILL_ANY_HOSTILE_MOB cuenta monstruos vanilla y MythicMobs.
- [ ] CRAFT_VANILLA_ITEM respeta cantidades y shift-click.
- [ ] CRAFT_RECIPE/CATEGORY cuentan MDVRecetas y no el crafteo Bukkit duplicado.
- [ ] OBTAIN_MMOITEM no permite tirar y recoger repetidamente el mismo objeto propio.
- [ ] DELIVER_MMOITEM y DELIVER_VANILLA_ITEM retiran cantidades parciales exactas.
- [ ] USE_CONSUMABLE solo suma cuando el objeto realmente se consume.
- [ ] PLAYER_KILL exige víctimas diferentes y respeta antiabuso.
- [ ] EARN_PROFESSION_EXP y COMPLETE_EVENT funcionan mediante sus puentes/API.

## Rendimiento

- [ ] No aparecen tareas de escaneo continuo en timings/spark.
- [ ] SQLite no genera errores `database is locked`.
- [ ] El guardado periódico no produce picos perceptibles.
- [ ] Una rotación expirada elimina su progreso y recompensas no reclamadas.

## Editor de recompensas 1.2.4

- [ ] Abrir el editor sin recompensas: todos los slots 9–44 deben estar vacíos, sin paneles negros/grises.
- [ ] Editar una misión con recompensas existentes: deben aparecer dentro de los slots 9–44, no en el slot 0.
- [ ] Mover recompensas entre slots y modificar cantidades con clic izquierdo, derecho y arrastre.
- [ ] Hacer Shift-click sobre un objeto del inventario: debe entrar únicamente en la cuadrícula editable.
- [ ] Hacer Shift-click sobre una plantilla real en la cuadrícula: debe volver al inventario.
- [ ] Hacer Shift-click sobre una recompensa virtual existente: debe eliminarse sin entregar una copia al administrador.
- [ ] Añadir un objeto vanilla, MMOItem y Mythic/Crucible desde el inventario.
- [ ] Guardar: la cuadrícula reemplaza las recompensas físicas anteriores sin paneles ni duplicados.
- [ ] Cancelar o cerrar: los objetos usados como plantilla vuelven al inventario y no se obtienen copias virtuales.
- [ ] La EXP y los comandos configurados permanecen sin cambios.

## Movimiento entre YAML 1.2.4

- [ ] Mover una misión cuyo ID original contiene `_` a otro YAML.
- [ ] Confirmar que desaparece del archivo anterior y aparece una sola vez en el nuevo.
- [ ] Repetir con un ID que usa `-`.
- [ ] Si existían copias históricas del mismo ID normalizado, guardar la misión debe dejar una sola copia.

## 1.4.1 — expiración y ocupación de cupos

- Aceptar un contrato, avanzar parcialmente y esperar/forzar el fin de su rotación: desaparece de `/quest`, se borra su progreso y deja de ocupar cupo.
- Aceptar y completar un contrato, reclamarlo: continúa contando en `%accepted%/%limit%` hasta el próximo roll.
- Intentar cancelar una misión ya reclamada: no libera cupo ni elimina la reclamación.
- Ejecutar reroll de una rotación: se eliminan aceptación, progreso y claim del ciclo anterior antes de mostrar el nuevo catálogo.
- Reiniciar con instancias ya vencidas en SQLite: se purgan durante el arranque y no reaparecen.

## MDVQuest 1.4.2 — permisos VIP, entrega rápida y brillo

- [ ] Sin `mdvquest.access.vip1`, un jugador puede ver una misión VIP pero el clic derecho no la acepta y muestra el requisito de rango.
- [ ] Sin `mdvquest.access.vip2`, un jugador no puede aceptar una misión VIP 2.
- [ ] Con `access-tiers.vip2-inherits-vip1: true`, el permiso VIP 2 también permite aceptar misiones VIP 1.
- [ ] Una misión aceptada con al menos un objetivo `DELIVER_MMOITEM` o `DELIVER_VANILLA_ITEM` pendiente muestra `Click derecho: entregar objetos` en el menú interactivo del NPC.
- [ ] El clic derecho entrega objetos a todos los objetivos de entrega pendientes de esa misión y actualiza sus progresos sin abrir detalles.
- [ ] `Shift + clic derecho` conserva prioridad y cancela la misión; no entrega objetos.
- [ ] Las misiones sin objetivos de entrega pendientes no muestran la acción rápida ni consumen objetos con clic derecho.
- [ ] Los iconos de cofre personalizados de una misión aceptada muestran brillo encantado, igual que los demás materiales.
- [ ] `Click izquierdo: ver detalles` y `Click derecho: aceptar contrato` aparecen en líneas separadas, también tras migrar una configuración 1.4.1 existente.


## MDVQuest 1.4.3 — limpieza de reroll y vencimiento

- [ ] Aceptar una misión y ejecutar `/mdvquest reroll daily confirmar`: la misión anterior desaparece de `/quest` inmediatamente.
- [ ] Si el reroll vuelve a seleccionar la misma definición, aparece como contrato nuevo y no aceptado, con progreso 0.
- [ ] Aceptar misiones de varias duraciones y ejecutar `/mdvquest reroll all confirmar`: `/quest` queda vacío hasta aceptar contratos del nuevo roll.
- [ ] Mantener un jugador conectado durante el cambio real de día y abrir `/quest`: los contratos diarios vencidos desaparecen sin reconectar ni esperar el intervalo de limpieza.
- [ ] Confirmar en SQLite que no quedan filas antiguas de aceptación, progreso, claims o víctimas PvP para las instancias eliminadas.
- [ ] Reiniciar después de un reroll y verificar que ningún contrato anterior reaparece.


## MDVQuest 1.4.4 — cosecha de bayas dulces

1. Acepta una misión `HARVEST_CROP` cuyo objetivo sea `SWEET_BERRY_BUSH`, con `mature-only: true`.
2. Haz clic derecho sobre un arbusto en edad máxima y confirma que suma exactamente `1`.
3. Confirma que el arbusto permanece colocado y vuelve a una edad inferior después de soltar las bayas.
4. Haz clic derecho sobre un arbusto inmaduro y confirma que no suma progreso.
5. Rompe un arbusto maduro y confirma que no suma progreso.
6. Haz clic derecho teniendo objetos en ambas manos y confirma que solo suma una vez.
7. Con `natural-only: true`, confirma que un arbusto colocado por un jugador no suma y uno natural sí.
