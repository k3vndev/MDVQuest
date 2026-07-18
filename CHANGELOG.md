# Changelog

## 1.2.2

- Las misiones VIP completadas sin permiso de reclamación ya no muestran el mensaje final que invita a cobrar ni reproducen el sonido de confirmación.
- Añadido `/mdvquest force <id-de-mision>` (`/mdvquest forzar`) para insertar una misión concreta en el ciclo global actual sin reroll, sin eliminar otras misiones y sin borrar progreso.
- El comando `force` admite también definiciones deshabilitadas para facilitar pruebas; la rotación de la misión debe existir y estar habilitada.
- Añadido permiso `mdvquest.admin.force` y autocompletado de IDs de misión.
- El estado completado pendiente de reclamar usa por defecto `LIME_STAINED_GLASS_PANE` en lugar de lana verde.
- Rediseño pequeño del menú principal: columna separadora configurable en los slots 10/19/28/37 con panel marrón o morado según la categoría seleccionada.
- Las misiones del catálogo principal ahora ocupan por defecto los slots 11–17, 20–26, 29–35 y 38–44.
- Orden del catálogo principal: primero misiones normales, luego VIP1 y después VIP2.
- Añadidas líneas configurables para identificar misiones VIP desbloqueadas en el lore del catálogo principal.
- El workflow de GitHub Actions ahora detecta automáticamente el JAR generado y el nombre del artefacto, para que no tengas que editar `.github/workflows/build.yml` en cada versión nueva.
- No hay cambios de esquema en SQLite ni migraciones destructivas.

## 1.2.0

- Cantidad variable por rotación y pool mediante `min-missions` / `max-missions`.
- Tres selecciones globales: normal, VIP1 y VIP2.
- Pool normal: definiciones normales.
- Pool VIP1: definiciones normales restantes + definiciones VIP1.
- Pool VIP2: definiciones VIP1 restantes + definiciones VIP2.
- Sin porcentajes ni pesos por origen del pool; se conservan los pesos individuales existentes de cada misión.
- No se repite una misma definición entre pools durante el mismo ciclo.
- Todos los jugadores pueden ver y progresar todas las misiones activas.
- Las recompensas VIP requieren permiso al reclamar.
- Panel celeste para misiones VIP1 bloqueadas y panel amarillo para VIP2 bloqueadas.
- Selector Normal/VIP1/VIP2 dentro del editor visual.
- Migración automática de SQLite con la columna `access_pool`; instalaciones 1.1.x se conservan.
- `/mdvquest reroll <rotación|all> confirmar` con advertencia obligatoria.

## 1.1.1

- Eliminados el reloj de página y el botón de cerrar del menú público principal.
- La cabeza de volver ahora ocupa el slot 49 y ejecuta el comando configurado, por defecto `/social`.
- Flechas de página en los extremos inferiores; solo aparecen cuando existen más páginas y muestran `página/páginas`.
- Los libros de duración muestran progreso completado/total y el tiempo hasta la próxima rotación del grupo.
- Para grupos 2–3 y 4–6 días se calcula la rotación más próxima entre todas sus duraciones.
- Rediseñado el detalle: objetivos en slots 10–16, recompensas en 29–33 y marco verde configurable.
- Paginación independiente de recompensas en slots 45 y 53; volver en 49 restaura grupo y página anteriores.
- Eliminado el botón de cerrar y el indicador extra de reclamación del detalle.
- Todos los slots, materiales, nombres, lore, títulos y marco de ambos menús públicos son configurables.
- Los nombres de objetos vanilla se muestran con componentes traducibles del cliente.
- MMOItems y objetos MythicMobs/Crucible muestran el nombre efectivo configurado en el objeto real.
- Añadidos nombres configurables para la experiencia principal y profesiones MMOCore.
- Las recompensas de ejemplo quedaron limitadas a poca EXP principal y lingotes de hierro, sin dinero.
- Migración opcional y de una sola vez para sanear `missions/examples.yml` antiguo sin tocar otros YAML.
- El editor limita por defecto a siete objetivos para coincidir con los slots 10–16.

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
- Añadido menú de detalle con objetivos, experiencia y recompensas paginadas.
- Las flechas aparecen únicamente cuando son necesarias.
- La misión completada sin reclamar usa lana verde; la reclamada usa tinte gris.
- Iconos y herramientas del menú ocultan atributos y tooltips vanilla.
- Añadida cabeza custom configurable para volver.
- Escrituras YAML atómicas y soporte para mover/renombrar misiones entre archivos.
- Se conserva compatibilidad con YAML y SQLite de 1.0.x.

## 1.0.3

- Corregida la firma de `CraftItemEvent#getInventory()` para Paper/Purpur 1.21.6.
- Compilación limpia contra `paper-api:1.21.6-R0.1-SNAPSHOT`.
- GitHub Actions con Java 21 y verificación de bytecode.

## 1.0.0

- Primera versión de MDVQuest V1.
