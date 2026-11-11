# DavTasks

Gestor de tareas para Android con sincronización CalDAV, derivado de [Tasks.org](https://github.com/tasks/tasks) (GPL-3.0). Base: Tasks.org 15.14, commit `da44d2a` del 09/11/2026. El README original se conserva en `README-tasks.org.md`.

## Diferencias con Tasks.org

- Identificador propio (`io.github.jimenezgarciaca.davtasks`) y nombre DavTasks, para instalarse junto a Tasks.org.

- Solo la variante generic: sin Google Play, Firebase ni suscripciones. La sincronización CalDAV no requiere pago.

- Se admiten servidores con `http://` en cualquier dirección. Con `http://` las credenciales viajan sin cifrar salvo que el tráfico vaya por un túnel cifrado (Tailscale, WireGuard).

- Escritura condicional en CalDAV: PUT y DELETE con `If-Match` (o `If-None-Match: *` para objetos nuevos). Ante un 412 no se sobrescribe el cambio de otro cliente: se descarga de nuevo el calendario, se fusionan los cambios y se vuelve a subir.

- Sin Wear OS, Pebble, versión de escritorio ni iOS. En el alta de cuentas solo aparecen CalDAV y DAVx⁵; las cuentas de Tasks.org, Google Tasks, Microsoft y EteSync están ocultas (su código sigue presente).

- Color dinámico (Material You) activado por defecto.

## Compilación

Cada push a `main` compila el APK de depuración en GitHub Actions (workflow "Compilar APK"). El APK se descarga desde la pestaña Actions, en los artefactos de la ejecución.

En local: `./gradlew :app:assembleGenericDebug` (requiere JDK 25 y Android SDK).

## Licencia

GPL-3.0, como Tasks.org. Ver `LICENSE`.
