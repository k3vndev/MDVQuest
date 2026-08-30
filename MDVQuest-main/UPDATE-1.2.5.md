# MDVQuest 1.2.5

## Permiso de editor separado

Se añade `mdvquest.editor` para abrir `/mdvquest admin` y `/mdvquest crear` sin conceder los comandos técnicos de `mdvquest.admin`.

- `mdvquest.editor`: catálogo, creación y edición visual de misiones.
- `mdvquest.admin`: recarga, estado, reportes manuales y administración técnica; hereda el editor.
- `mdvquest.admin.force` y `mdvquest.admin.reroll` se mantienen separados.
- El editor vuelve a comprobar el permiso mientras la GUI está abierta.

Permisos recomendados para un administrador de contenido:

```text
mdvquest.editor true
mdvquest.admin false
mdvquest.admin.force false
mdvquest.admin.reroll false
```
