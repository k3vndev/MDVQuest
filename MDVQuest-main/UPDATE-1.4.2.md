# MDVQuest 1.4.2 — Permisos VIP y entrega rápida

- Una instancia VIP1 o VIP2 no puede aceptarse sin el permiso correspondiente.
- VIP2 sigue heredando acceso a VIP1 cuando `access-tiers.vip2-inherits-vip1: true`.
- La comprobación se realiza dentro de `ProgressService`, por lo que también protege llamadas externas y no solamente clicks del menú.
- En el menú interactivo del NPC, un contrato aceptado con objetivos de entrega pendientes muestra `Click derecho: entregar objetos.`.
- Ese click intenta entregar todos los objetivos `DELIVER_MMOITEM` y `DELIVER_VANILLA_ITEM` pendientes de la misión.
- Shift + click derecho continúa cancelando el contrato y tiene prioridad sobre la entrega rápida.
- Las misiones sin objetivos de entrega no muestran ni ejecutan esta acción.
- Los iconos de contratos en curso fuerzan el glint incluso si el objeto base trae el override desactivado, como ocurría con algunos cofres personalizados.
- Las acciones de ver detalles, aceptar y cancelar se separan en líneas configurables.
- Los valores antiguos de `accepted-controls` y `available-controls` guardados como una sola línea se dividen automáticamente, por lo que no es obligatorio regenerar `config.yml`.
