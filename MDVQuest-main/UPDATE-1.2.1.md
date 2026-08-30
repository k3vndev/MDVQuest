# Actualizar MDVQuest 1.2.0 → 1.2.1

## No borres estos archivos

Con el servidor apagado, reemplaza únicamente el JAR. Conserva completos:

- `plugins/MDVQuest/config.yml`
- `plugins/MDVQuest/missions/`
- `plugins/MDVQuest/mdvquest.db`
- `plugins/MDVQuest/families.yml`

La 1.2.1 no cambia el esquema de SQLite, no regenera las rotaciones y no borra progreso ni recompensas pendientes al iniciar.

## Único cambio visual recomendado en tu config actual

Busca:

```yaml
menus:
  main:
    mission-state:
      completed-material: LIME_WOOL
```

Y cambia solo el material:

```yaml
menus:
  main:
    mission-state:
      completed-material: LIME_STAINED_GLASS_PANE
```

No elimines ninguna otra sección. Si no haces este cambio, el plugin seguirá funcionando, pero conservará la lana configurada en tu archivo antiguo.

## Mensajes nuevos

La configuración por defecto incluye estos mensajes para `/mdvquest force`. MDVQuest los carga como valores predeterminados aunque tu archivo anterior no los tenga:

```yaml
messages:
  force-mission-success: "&aMisión forzada: &f%mission% &7(%rotation%). &aExpira en &f%remaining%&a."
  force-mission-already-active: "&eLa misión &f%mission% &eya está activa en la rotación &f%rotation%&e."
  force-mission-not-found: "&cNo existe una misión cargada con el ID &f%mission%&c."
  force-mission-rotation-missing: "&cLa misión &f%mission% &cusa una rotación inexistente."
  force-mission-rotation-disabled: "&cLa rotación de &f%mission% &cestá deshabilitada en config.yml."
  force-mission-database-error: "&cNo se pudo guardar la misión forzada. Revisa la consola y SQLite."
```

Añádelos manualmente solo si quieres tenerlos visibles y editarlos en tu `config.yml`.

## Comando de prueba

```text
/mdvquest force <id-de-mision>
```

También funciona:

```text
/mdvquest forzar <id-de-mision>
```

La misión se añade globalmente a su ciclo actual y conserva la expiración de su rotación. No elimina otras misiones, no ejecuta reroll y no borra progreso. Puede forzar una definición con `enabled: false`, siempre que su rotación esté habilitada.

Permiso:

```text
mdvquest.admin.force
```
