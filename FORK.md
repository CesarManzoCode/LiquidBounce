# Fork personal de LiquidBounce

Notas para compilar, publicar y usar este fork con un LiquidLauncher propio.

## Qué cambia respecto al upstream

- **Aimbot – Target Lock**: nuevas opciones `LockTarget` y `LockDuration` (segundos)
  en el ClickGUI del módulo *Aimbot*. Al golpear a un enemigo, la mira se queda
  fijada en ese objetivo durante el tiempo configurado en lugar de saltar al que
  se acerque más. Útil cuando hay varios enemigos y quieres centrarte en uno.
  El bloqueo se suelta antes si expira el tiempo o el objetivo deja de ser
  válido (muere, sale de rango, etc.). Muestra una notificación al fijar uno nuevo.

- **Aimbot – Whitelist**: sub-grupo `Whitelist` en el módulo *Aimbot* con
  objetivos a los que el aimbot **nunca** apuntará. Solo afecta al Aimbot (otros
  módulos de combate siguen igual). Fuentes:
  - `Usernames`: lista manual de nicks (fiable al 100%).
  - `AutoTeam`: detecta clan/compañeros automáticamente con señales fiables que
    el servidor ya manda al cliente (scoreboard team vía `isAlliedTo`, color del
    nombre, prefijo/tag de clan). **No** usa color de armadura (da falsos
    positivos entre enemigos). Cada detección automática se anuncia una vez en el
    chat para que veas a quién detecta y puedas desactivarlo si tu server no es
    compatible. No existe una "API de clanes" universal: esto funciona en la
    medida en que el servidor exponga la relación de equipo al cliente.

- **ArmorDurability** (alias *ArmorOverlay*, categoría *Render*): muestra la
  durabilidad de cada pieza de armadura enemiga como un número flotante a la
  altura de cada parte del cuerpo (offset al lado para no tapar la visión). Misma
  info que el resource pack de durabilidad, sin Optifine. El cliente conoce esa
  durabilidad porque el servidor envía el ItemStack completo del equipo. Opciones:
  `Range`, `Scale`, `SideOffset`, `RoundToTens` (múltiplos de 10), `HideFull`,
  `ColorByDurability` (verde→rojo) y `Color`.

- **LowEffects** (categoría *Render*): tweaks "low" de PvP en un solo módulo,
  cada uno toggleable. `LowFire` baja/encoge la geometría de las llamas
  (`Scale`/`Offset`, distinto del `FireOpacity` de AntiBlind). `LowTotem` quita
  solo la animación del pop del totem (a diferencia de `FloatingItems` de
  AntiBlind, que las quita todas). Pensado para añadir más tweaks fácilmente.

- **Categoría `xTetradox`**: nueva categoría en el ClickGUI/TabGUI (el menú de
  Shift derecho) que agrupa los módulos de uso frecuente en un solo sitio. De
  momento contiene **Aimbot**, **AutoClicker**, **ArmorDurability** y
  **LowEffects**. Como en LiquidBounce cada módulo pertenece a una sola
  categoría, estos se **mueven** ahí (ya no aparecen en Combat/Render). Es solo
  organizativo; los ajustes de cada módulo se conservan. Para añadir más,
  cambia su `ModuleCategories.X` por `ModuleCategories.XTETRADOX`.

### Nota: cosas que ya existían en AntiBlind

Antes de duplicar: **AntiBlind** ya trae `FireOpacity` (fuego transparente) y
`FloatingItems` (quita la animación del totem y otros item activations). LowEffects
solo añade lo que faltaba (bajar la geometría del fuego y un low-totem específico).

## Compilar en local (Fedora)

Requisitos:

- **JDK 25** (Temurin recomendado). El proyecto exige exactamente esa versión
  (`gradle/libs.versions.toml` → `jdk = "25"`).
- Node/npm los gestiona Gradle automáticamente para compilar el tema web
  (`src-theme`, el ClickGUI).

```bash
./gradlew build -x test -x detekt
```

El JAR final queda en `build/libs/liquidbounce-<version>.jar`
(la versión sale de `gradle.properties` → `mod_version`). Ese es el archivo que
carga Minecraft/Fabric. Los `*-sources.jar` y `*-dev.jar` no se usan.

## Publicar el JAR en GitHub Releases

Hay un workflow propio del fork, `.github/workflows/release-fork.yml`, que
compila y sube el JAR como asset de un Release. Es independiente del workflow
oficial `build.yml` (ese sube a `api.liquidbounce.net`, el servidor de CCBlueX,
y **fallará en el fork** porque no tienes el secret `NIGHTLY_PASS` ni acceso —
puedes ignorar su check en rojo o desactivar ese workflow en *Settings → Actions*).

Formas de lanzarlo:

- **Por tag**: `git tag v0.38.1-mine && git push origin v0.38.1-mine`
- **Manual**: pestaña *Actions → Release Fork JAR → Run workflow* (te pide el tag)

Resultado: un Release con `liquidbounce-<version>.jar` adjunto, con URL estable
de descarga del estilo
`https://github.com/<usuario>/LiquidBounce/releases/download/<tag>/liquidbounce-<version>.jar`.

## Lado del LiquidLauncher

El LiquidLauncher oficial (CCBlueX/LiquidLauncher, Rust + Tauri) **no** descarga
de releases arbitrarias: consulta la API oficial de versiones
(`api.liquidbounce.net`) y baja el build que esa API indica. Para que use tu JAR
tienes dos caminos, ambos en el repo del **launcher**, no en este:

1. **Parchear el launcher** para que, en vez de pedir el build a la API, descargue
   directamente el JAR de tu Release de GitHub (la URL de arriba) y lo coloque
   donde el launcher espera el mod de LiquidBounce. Es lo más simple para uso
   personal y es justo la parte que "se hace desde el LiquidLauncher".
2. **Levantar tu propia API** compatible con el endpoint que consume el launcher
   y apuntar el launcher a ella. Más trabajo; solo merece la pena si quieres
   replicar el flujo de nightlies completo.

Desde el lado de LiquidBounce (este repo) ya queda todo lo necesario: build
reproducible con JDK 25 y publicación automática del JAR en Releases.
