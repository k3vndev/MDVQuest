# Changelog

## 1.0.3
- Corregida la verificacion de bytecode de `CraftItemEvent#getInventory()`: Paper/Purpur 1.21.6 devuelve `CraftingInventory`, no `Inventory`.
- El listener de crafteo usa directamente `CraftingInventory` para reflejar la firma oficial.
- La verificacion tambien impide empaquetar clases Bukkit/Paper dentro del JAR.
- No cambia configuraciones, base SQLite ni comportamiento de las misiones.

## 1.0.3
- El proyecto Maven compila exclusivamente contra `paper-api:1.21.6-R0.1-SNAPSHOT`.
- Se tiparon explicitamente los retornos de `openInventory`, `setItemMeta`, eventos de inventario y proyectiles para impedir compilaciones con stubs Bukkit incorrectos.
- Se agrego GitHub Actions para compilar con Java 21 y Maven.
- Se agrego una comprobacion de bytecode que rechaza firmas incompatibles antes de publicar el JAR.

## 1.0.1
- Primer intento de correccion binaria para firmas Bukkit. Sustituido por la compilacion limpia de 1.0.3.

## 1.0.0
- Primera version de MDVQuest V1.
