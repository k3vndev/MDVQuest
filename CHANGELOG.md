# Changelog

## 1.1.0

- Añadido editor visual completo de misiones con selector de 1 a 7 días reales.
- Añadido catálogo administrativo agrupado por duración.
- Clic izquierdo para visualizar una misión y clic derecho para editarla.
- Edición in-game de ID, nombre, lore, icono exacto, duración, peso, archivo YAML y estado habilitado.
- Añadido asistente por chat para todos los objetivos V1; `cancelar` vuelve sin guardar el paso.
- Permitidos varios objetivos, incluidos objetivos repetidos del mismo tipo.
- Añadida administración visual: clic izquierdo edita y clic derecho elimina.
- Añadido `KILL_ANY_HOSTILE_MOB` para monstruos vanilla y MythicMobs.
- Añadido editor visual de recompensas físicas mediante depósito de objetos.
- Identificación automática de recompensas vanilla, MMOItems y MythicMobs/Crucible.
- Añadidas recompensas estructuradas de experiencia principal y profesiones MMOCore.
- Añadidos `mythic-items`, `exact-items` y `experience` al formato YAML.
- Reclamación segura con validación previa de IDs, stacks y slots vacíos.
- Reserva de slots; cualquier objeto intruso colocado durante la entrega se expulsa y no reemplaza la recompensa.
- Bloqueo por jugador para impedir reclamaciones simultáneas en carrera.
- Rediseñado el menú público en categorías 1 día, 2–3 días, 4–6 días y 7 días.
- Añadido menú de detalle con objetivos, experiencia y hasta 10 recompensas visibles por página.
- Las flechas aparecen únicamente cuando son necesarias.
- La misión completada sin reclamar usa lana verde; la reclamada usa tinte gris.
- Iconos y herramientas del menú ocultan atributos y tooltips vanilla.
- Añadida cabeza custom configurable para volver.
- Escrituras YAML atómicas y soporte para mover/renombrar misiones entre archivos.
- Se conserva compatibilidad con YAML y SQLite de 1.0.x.
- Ampliada la verificación de bytecode a las nuevas GUI, RewardService e ItemUtil.

## 1.0.3

- Corregida la firma de `CraftItemEvent#getInventory()` para Paper/Purpur 1.21.6.
- Compilación limpia contra `paper-api:1.21.6-R0.1-SNAPSHOT`.
- GitHub Actions con Java 21 y verificación de bytecode.

## 1.0.0

- Primera versión de MDVQuest V1.
