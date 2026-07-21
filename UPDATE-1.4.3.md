# MDVQuest 1.4.3 — Limpieza inmediata de contratos vencidos

## Corregido

- `/mdvquest reroll <rotación> confirmar` y `reroll all` eliminan también el estado conservado en la caché de los jugadores conectados.
- Una misión sorteada nuevamente dentro del mismo ciclo usa un ID de instancia nuevo, por lo que no puede heredar aceptación o progreso del contrato eliminado.
- El menú `/quest` y el menú interactivo sincronizan las rotaciones antes de mostrarse. Al cambiar el día, los contratos vencidos desaparecen inmediatamente aunque el mantenimiento periódico todavía no se haya ejecutado.

## Actualización

Solo reemplaza el JAR. No debes modificar `config.yml`, los YAML de misiones ni `mdvquest.db`.
