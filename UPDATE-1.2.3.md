# Actualizar MDVQuest 1.2.2 → 1.2.3

## Qué corrige

- El editor de recompensas muestra las recompensas actuales dentro de la cuadrícula editable, no en el slot 0 como vista bloqueada.
- La cuadrícula de slots 9–44 representa la recompensa final de objetos.
- Puedes mover objetos, cambiar cantidades, añadir otros y eliminarlos con Shift + clic derecho.
- Al guardar, se reemplazan solamente las recompensas físicas; la EXP y los comandos no se modifican.
- Cambiar una misión de YAML ahora la mueve realmente y elimina la copia anterior.
- También limpia duplicados históricos del mismo ID normalizado, por ejemplo `caza_diaria` y `caza-diaria`.

## Actualización segura

Apaga completamente el servidor y reemplaza únicamente el JAR. Conserva:

- `plugins/MDVQuest/config.yml`
- `plugins/MDVQuest/missions/`
- `plugins/MDVQuest/families.yml`
- `plugins/MDVQuest/mdvquest.db`

No hay cambios de configuración obligatorios ni migraciones de SQLite. No se pierden misiones activas, progreso ni recompensas pendientes.

## Prueba recomendada

1. Abre `/mdvquest admin`.
2. Edita una misión que ya tenga recompensa física.
3. Entra al editor de recompensas y verifica que aparezca dentro de los slots 9–44.
4. Cambia la cantidad, elimina una entrada con Shift + clic derecho y añade otra plantilla.
5. Guarda las recompensas y después guarda la misión.
6. Cambia el archivo YAML de esa misión y confirma que queda solo en el archivo nuevo.

Compila con GitHub Actions. El workflow detecta automáticamente el nombre del JAR y no necesita editarse por versión.
