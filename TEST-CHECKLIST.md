# Checklist de staging — MDVQuest 1.1.0

Prueba esta versión en una copia de Purpur 1.21.6 antes de producción.

## Arranque y migración

- [ ] El servidor inicia sin `NoSuchMethodError`.
- [ ] `/mdvquest status` muestra versión 1.1.0.
- [ ] Las misiones y el progreso existentes de 1.0.3 permanecen.
- [ ] `/mdvquest` abre el menú público.
- [ ] `/mdvquest admin` exige `mdvquest.admin`.

## Menú público

- [ ] Los cuatro libros cambian entre 1d, 2–3d, 4–6d y 7d.
- [ ] Las flechas no aparecen cuando hay una sola página.
- [ ] El lore muestra descripción, objetivos, recompensas y expiración.
- [ ] Las herramientas no muestran daño, atributos o tooltip vanilla innecesario.
- [ ] La cabeza de volver ejecuta el comando configurado.
- [ ] El detalle muestra hasta 9 objetivos.
- [ ] Las recompensas físicas y de EXP se ven correctamente.
- [ ] La paginación de recompensas aparece solo con más de 10 entradas.

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
