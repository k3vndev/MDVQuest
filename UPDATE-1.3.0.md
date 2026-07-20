# Actualización MDVQuest 1.3.0

## Cambios principales

### Dos variantes del menú

- `/mdvquest`, `/quest`, `/quests`, `/misiones` y MDVSocial abren `VIEW_ONLY`.
- El visor muestra progreso, objetivos, recompensas, estados VIP y expiración.
- Las misiones del visor no tienen acción: no abren detalle, no entregan objetos y no reclaman recompensas.
- `/mdvquest npc [jugador]` abre `INTERACTIVE`, que conserva el comportamiento completo de 1.2.5.

### Citizens / NPC

Ejecuta desde consola:

```text
mdvquest npc <jugador>
```

El jugador debe estar conectado. El permiso administrativo es `mdvquest.admin.open-interactive`; `mdvquest.admin` lo hereda. Desde el juego, un administrador puede probar su propio menú con `/mdvquest npc`.

### Personalización

- Visor: `menus.viewer`.
- Menú completo: `menus.interactive`.
- Detalle completo: `menus.interactive.detail`.

Ambas variantes tienen configuración separada para título, tamaño, relleno, slots, categorías, separadores, materiales, estados, lore, paginación y regreso.

### Filtro por YAML

En `/mdvquest admin`, el filtro aparece por defecto en el slot 46, a la derecha de Volver.

- General/todos los YAML es la primera opción en cada apertura nueva del catálogo.
- Clic izquierdo avanza.
- Clic derecho retrocede.
- Shift-click vuelve a General.
- Se combina con duración y paginación.
- Al guardar o mover una misión, el catálogo queda filtrado por el YAML de destino.

## Migración

No borres `config.yml`. En la primera carga, MDVQuest copia las antiguas personalizaciones de `menus.main`, `menus.detail`, `menus.page-buttons` y `menus.back-command` a las nuevas variantes. El visor elimina solamente las instrucciones de interacción y redirige al NPC.

No cambia:

- `mdvquest.db` ni su esquema.
- El progreso activo.
- Las rotaciones.
- Los YAML de misiones.
- `families.yml`.

## Instalación

1. Apaga completamente el servidor.
2. Conserva `plugins/MDVQuest/` y `mdvquest.db`.
3. Sustituye únicamente el JAR por `MDVQuest-1.3.0.jar`.
4. Inicia el servidor y revisa las nuevas secciones de `config.yml`.
5. Prueba `/quest`, `/mdvquest npc` y el filtro de `/mdvquest admin`.

No uses `/reload` de Bukkit para reemplazar el JAR.
