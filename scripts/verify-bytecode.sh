#!/usr/bin/env bash
set -euo pipefail

JAR="${1:-target/MDVQuest-1.0.2.jar}"
if [[ ! -f "$JAR" ]]; then
  echo "No existe el JAR: $JAR" >&2
  exit 1
fi

DUMP="$(mktemp)"
trap 'rm -f "$DUMP"' EXIT

javap -classpath "$JAR" -verbose \
  com.mdvcraft.mdvquest.gui.QuestMenuManager \
  com.mdvcraft.mdvquest.hook.MDVSocialHook \
  com.mdvcraft.mdvquest.listener.GameplayListener > "$DUMP"

forbidden=(
  'openInventory:(Lorg/bukkit/inventory/Inventory;)V'
  'setItemMeta:(Lorg/bukkit/inventory/meta/ItemMeta;)V'
  'InventoryCloseEvent.getPlayer:()Ljava/lang/Object;'
  'getWhoClicked:()Ljava/lang/Object;'
  'Projectile.getShooter:()Ljava/lang/Object;'
  'CraftItemEvent.getInventory:()Ljava/lang/Object;'
)

for signature in "${forbidden[@]}"; do
  if grep -Fq "$signature" "$DUMP"; then
    echo "Firma Bukkit incompatible detectada: $signature" >&2
    exit 1
  fi
done

required=(
  'openInventory:(Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;'
  'setItemMeta:(Lorg/bukkit/inventory/meta/ItemMeta;)Z'
  'InventoryCloseEvent.getPlayer:()Lorg/bukkit/entity/HumanEntity;'
  'getWhoClicked:()Lorg/bukkit/entity/HumanEntity;'
  'Projectile.getShooter:()Lorg/bukkit/projectiles/ProjectileSource;'
  'CraftItemEvent.getInventory:()Lorg/bukkit/inventory/Inventory;'
)

for signature in "${required[@]}"; do
  if ! grep -Fq "$signature" "$DUMP"; then
    echo "No se encontro la firma oficial esperada: $signature" >&2
    exit 1
  fi
done

echo "Bytecode verificado: las firmas Bukkit/Paper son correctas."
