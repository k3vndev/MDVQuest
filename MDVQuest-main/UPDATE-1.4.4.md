# MDVQuest 1.4.4 — Cosecha de bayas dulces

## Añadido

- Los objetivos `HARVEST_CROP` con objetivo `SWEET_BERRY_BUSH` progresan al hacer clic derecho sobre un arbusto completamente maduro.
- Se comprueba después de la interacción que el arbusto siga colocado y haya reducido su edad, evitando contar simples clics.
- Romper el arbusto no cuenta como cosecha.
- Los eventos duplicados de ambas manos se deduplican para entregar un solo punto por arbusto cosechado.
- `natural-only` continúa respetándose para arbustos colocados por jugadores.

## YAML

No hay cambios de sintaxis. El objetivo existente funciona así:

```yaml
bayas:
  type: HARVEST_CROP
  amount: 3000
  name: Cosecha arbustos de bayas dulces maduros.
  targets:
  - SWEET_BERRY_BUSH
  mature-only: true
  natural-only: false
  worlds:
  - world
```

## Actualización

Solo reemplaza el JAR. No debes modificar `config.yml`, los YAML de misiones ni `mdvquest.db`.
