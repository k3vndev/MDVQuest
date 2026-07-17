# Changelog

## 1.0.2
- El proyecto Maven compila exclusivamente contra `paper-api:1.21.6-R0.1-SNAPSHOT`.
- Se tiparon explicitamente los retornos de `openInventory`, `setItemMeta`, eventos de inventario y proyectiles para impedir compilaciones con stubs Bukkit incorrectos.
- Se agrego GitHub Actions para compilar con Java 21 y Maven.
- Se agrego una comprobacion de bytecode que rechaza firmas incompatibles antes de publicar el JAR.

## 1.0.1
- Primer intento de correccion binaria para firmas Bukkit. Sustituido por la compilacion limpia de 1.0.2.

## 1.0.0
- Primera version de MDVQuest V1.
