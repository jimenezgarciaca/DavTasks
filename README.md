# DavTasks

Gestor de tareas para Android con sincronización CalDAV, derivado de [Tasks.org](https://github.com/tasks/tasks) (GPL-3.0). Base: Tasks.org 15.14, commit `da44d2a` del 09/11/2026. El README original se conserva en `README-tasks.org.md`.

## Diferencias con Tasks.org

- Identificador propio (`io.github.jimenezgarciaca.davtasks`) y nombre DavTasks, para instalarse junto a Tasks.org.

- Solo la variante generic: sin Google Play, Firebase ni suscripciones. La sincronización CalDAV no requiere pago.

- Se admiten servidores con `http://` en cualquier dirección. Con `http://` las credenciales viajan sin cifrar salvo que el tráfico vaya por un túnel cifrado (Tailscale, WireGuard).

- Escritura condicional en CalDAV: PUT y DELETE con `If-Match` (o `If-None-Match: *` para objetos nuevos). Ante un 412 no se sobrescribe el cambio de otro cliente: se descarga de nuevo el calendario, se fusionan los cambios y se vuelve a subir.

- Sin Wear OS, Pebble (módulo), versión de escritorio ni iOS.

- Sin Google Tasks, Microsoft To Do, EteSync ni cuenta de Tasks.org: se han eliminado su código y sus librerías (etebase, google-api-services-tasks/drive, google-auth-library, AppAuth). En el alta de cuentas quedan CalDAV y DAVx⁵.

- Sin servicios de Tasks.org: ni copia de seguridad en Google Drive, ni sistema de compras o suscripciones (Google Play, Stripe, patrocinio de GitHub), ni migración de listas locales a Tasks.org, ni configuración remota, ni vinculación con escritorio, ni notificaciones push, ni consulta del blog. Todas las funciones que Tasks.org reservaba a Pro están disponibles. La geocodificación inversa usa nominatim.openstreetmap.org y la búsqueda de lugares, Mapbox.

- Sin extensión de DashClock (aplicación abandonada).

- Las tareas escritas por DavTasks se identifican con `PRODID:+//IDN davtasks//android-<versión>//EN`. Se reconocen como cliente con sincronización de recordatorios tanto DavTasks como Tasks.org.

- Base de datos: esquema 98, el mismo que Tasks.org 15.14, sin migraciones anteriores (no hay instalaciones de DavTasks con esquemas previos). Las integraciones retiradas no tenían tablas propias; usaban las de CalDAV con otros tipos de cuenta, que ya no existen. Al importar una copia de Tasks.org, las cuentas de servicios no soportados (Tasks.org, EteSync, Microsoft To Do, Google Tasks) se importan como cuentas locales para conservar sus listas y tareas.

- Sin analítica ni informes de errores: los fallos se registran en el log del dispositivo.

- Sin el proveedor de contenido antiguo de Astrid. Se mantienen el proveedor de lectura para ToDo Agenda y la API de contenido documentada en `CONTENT_PROVIDER.md`, con la autoridad y los permisos de DavTasks.

- Solo inglés y español (también en las librerías incluidas). La ayuda enlaza a este README y a los sitios de DAVx⁵, DecSync, EteSync y Tasker; la documentación de tasks.org ya no se enlaza.

- Color dinámico (Material You) activado por defecto.

- Bienvenida simplificada: conectar una cuenta (CalDAV o DAVx⁵), usar sin sincronizar o importar una copia de seguridad.

- Interfaz ajustada a Material 3:

  - Lista de tareas más compacta (encabezados de sección y separación entre filas menores), chips tonales pequeños y prioridad indicada con un icono de barras delante del título; la casilla de completar tiene el mismo color para todas las prioridades.

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
