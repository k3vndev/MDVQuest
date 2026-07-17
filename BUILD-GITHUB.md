# Compilar MDVQuest 1.0.3 en GitHub

1. Sube todo el contenido de `MDVQuest-main` a la raíz del repositorio.
2. Abre la pestaña **Actions**.
3. Ejecuta el workflow **Build MDVQuest** mediante `Run workflow`, o realiza un push a `main`.
4. Cuando termine, abre la ejecución y descarga el artefacto `MDVQuest-1.0.3`.
5. Dentro estará `MDVQuest-1.0.3.jar`.

El workflow utiliza Java 21, ejecuta `mvn -B -U clean package` contra el repositorio oficial de Paper y luego inspecciona el bytecode. La compilación falla si reaparece alguna firma falsa como `openInventory(...): void` o `setItemMeta(...): void`.

También puedes compilar localmente:

```bash
mvn -B -U clean package
bash scripts/verify-bytecode.sh target/MDVQuest-1.0.3.jar
```

## Nota sobre Purpur

Purpur 1.21.6 es compatible con plugins compilados contra `paper-api:1.21.6-R0.1-SNAPSHOT`. No debes cambiar la dependencia a un JAR de Purpur ni añadir el JAR del servidor al repositorio.
