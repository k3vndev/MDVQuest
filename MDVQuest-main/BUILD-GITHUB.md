# Compilar MDVQuest 1.4.4 con GitHub Actions

## Subir el proyecto

1. Descomprime el ZIP fuente.
2. Sube el contenido de la carpeta `MDVQuest-main` a la raíz del repositorio.
3. Comprueba que en la raíz estén `pom.xml`, `src/`, `scripts/` y `.github/`.
4. Haz commit y push a `main` o `master`.

## Compilar

1. Abre la pestaña **Actions**.
2. Selecciona **Build MDVQuest**.
3. Pulsa **Run workflow**.
4. Al finalizar, descarga el artefacto `MDVQuest-1.3.0`.
5. Dentro estará `MDVQuest-1.3.0.jar`.

El workflow ejecuta:

```bash
mvn -B -U clean package
bash scripts/verify-bytecode.sh target/MDVQuest-1.3.0.jar
```

La segunda fase rechaza JAR que empaqueten Bukkit/Paper o que vuelvan a contener firmas erróneas como `openInventory(...): void` o `setItemMeta(...): void`.

## Compilación local

Requiere JDK 21 y Maven:

```bash
mvn -B -U clean package
bash scripts/verify-bytecode.sh target/MDVQuest-1.3.0.jar
```

Resultado:

```text
target/MDVQuest-1.3.0.jar
```

## Instalación sobre 1.2.0

1. Apaga completamente Purpur.
2. Elimina solamente el JAR viejo de MDVQuest.
3. Copia `MDVQuest-1.3.0.jar`.
4. Conserva `plugins/MDVQuest/` y `mdvquest.db`.
5. Arranca primero en staging y completa `TEST-CHECKLIST.md`.

No uses gestores de recarga de plugins para sustituir el JAR.
