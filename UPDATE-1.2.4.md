# Actualizar MDVQuest 1.2.3 → 1.2.4

## Qué corrige

El editor de recompensas ahora deja vacíos los slots 9–44 y permite editar libremente los objetos. Los paneles de relleno de MDVSocial ya no ocupan ni se guardan en esa zona.

Controles:

- Click normal/derecho/arrastre: mover, dividir y combinar objetos dentro de la cuadrícula.
- Shift-click desde tu inventario: añadir el stack a la cuadrícula.
- Shift-click sobre la cuadrícula: retirar una plantilla real o eliminar una recompensa virtual existente.
- Guardar recompensas: reemplaza las recompensas físicas por el contenido visible de la cuadrícula.
- Cancelar/cerrar: devuelve las plantillas reales y descarta cambios.

## Actualización segura

Con el servidor apagado, reemplaza únicamente el JAR. No borres ni reemplaces:

- `plugins/MDVQuest/config.yml`
- `plugins/MDVQuest/missions/`
- `plugins/MDVQuest/families.yml`
- `plugins/MDVQuest/mdvquest.db`

No necesitas añadir ni eliminar ninguna clave de `config.yml`. No hay migración de SQLite ni cambios en el formato YAML.
