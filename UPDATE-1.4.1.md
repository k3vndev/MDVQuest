# MDVQuest 1.4.1 — Ciclo completo de los contratos

- Al expirar una instancia se eliminan conjuntamente su aceptación, progreso, reclamación y víctimas PvP únicas.
- La purga se ejecuta también al iniciar el servidor, antes de cargar o generar la rotación vigente.
- Las instancias vencidas dejan de mostrarse y de ocupar cupo inmediatamente, aunque el mantenimiento periódico todavía no haya retirado su fila de SQLite.
- Reclamar una recompensa conserva el contrato como aceptado hasta el siguiente roll de su rotación.
- Una misión ya reclamada no puede cancelarse para liberar el cupo.
- El nuevo roll o un reroll administrativo elimina los datos del ciclo anterior y recién entonces libera sus cupos.
- No hay cambios manuales de configuración ni migraciones de esquema SQLite.
