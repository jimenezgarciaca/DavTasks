# DavTasks

Gestor de tareas para Android con sincronización CalDAV, derivado de [Tasks.org](https://github.com/tasks/tasks) (GPL-3.0). Base: Tasks.org 15.14, commit `da44d2a` del 09/11/2026. El README original se conserva en `README-tasks.org.md`.

## Diferencias con Tasks.org

- Identificador propio (`io.github.jimenezgarciaca.davtasks`) y nombre DavTasks, para instalarse junto a Tasks.org.

- Solo la variante generic: sin Google Play, Firebase ni suscripciones. La sincronización CalDAV no requiere pago.

- Se admiten servidores con `http://` en cualquier dirección. Con `http://` las credenciales viajan sin cifrar salvo que el tráfico vaya por un túnel cifrado (Tailscale, WireGuard).

- Escritura condicional en CalDAV: PUT y DELETE con `If-Match` (o `If-None-Match: *` para objetos nuevos). Ante un 412 no se sobrescribe el cambio de otro cliente: se descarga de nuevo el calendario, se fusionan los cambios y se vuelve a subir.

- Sin Wear OS, Pebble (módulo), versión de escritorio ni iOS.

- Sin Google Tasks, Microsoft To Do, EteSync ni cuenta de Tasks.org: se han eliminado su código y sus librerías (etebase, google-api-services-tasks/drive, google-auth-library, AppAuth). En el alta de cuentas quedan CalDAV y DAVx⁵.

- Sin servicios de Tasks.org: ni copia de seguridad en Google Drive, ni pagos (Stripe, patrocinio de GitHub), ni vinculación con escritorio, ni notificaciones push, ni consulta del blog. La geocodificación inversa usa nominatim.openstreetmap.org y la búsqueda de lugares, Mapbox.

- Se conservan las tablas de la base de datos de esas integraciones para no modificar el esquema.

- Color dinámico (Material You) activado por defecto.

- Interfaz ajustada a Material 3:

  - Lista de tareas más compacta (encabezados de sección y separación entre filas menores), chips tonales pequeños y prioridad indicada también con "!", "!!" o "!!!" delante del título.

  - Ajustes y opciones de la lista en el menú de la barra inferior.

  - Edición con X para descartar y botón "Guardar"; sin separadores; prioridad con chips Baja, Media y Alta (pulsar la marcada la quita); los campos vacíos poco usados quedan bajo "Más opciones"; sin el aviso "¿Demasiada información?".

  - Prioridad por defecto de las tareas nuevas: ninguna (en instalaciones nuevas).

  - Menú lateral con las listas de las cuentas antes que etiquetas y lugares, y degradado inferior bajo el botón de búsqueda.

## Compilación

Cada push a `main` compila el APK en GitHub Actions (workflow "Compilar APK"). Si existen los secretos `DAVTASKS_KEYSTORE`, `DAVTASKS_KEYSTORE_PASSWORD`, `DAVTASKS_KEY_ALIAS` y `DAVTASKS_KEY_PASSWORD`, genera el release firmado; si no, el de depuración. El APK se descarga desde la pestaña Actions, en los artefactos de la ejecución.

La búsqueda de lugares (Mapbox) solo funciona si se define la propiedad Gradle `tasks_mapbox_key`.

En local: `./gradlew :app:assembleGenericDebug` (requiere JDK 25 y Android SDK).

## Licencia

GPL-3.0, como Tasks.org. Ver `LICENSE`.
