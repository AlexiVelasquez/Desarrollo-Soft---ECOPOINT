# Validación de EcoPoints

## Resultado automatizado

Verificación del 25 de octubre de 2026:

| Comprobación | Resultado |
| --- | --- |
| `:app:testDebugUnitTest` | 58 pruebas aprobadas, 0 fallos |
| `:app:assembleDebug` | APK generado correctamente |
| `:app:connectedDebugAndroidTest` con `firebaseSmoke=true` | 19 pruebas aprobadas en Pixel_8 API 37, incluidas migraciones SQLite hasta versión 3 y acceso Firebase desde dos cachés independientes |
| `:app:lintDebug` | Sin errores; 34 advertencias y 1 sugerencia de mantenimiento |
| `git diff --check` | Sin errores de espacios |
| Informe DOCX | Cinco páginas renderizadas con Word y revisadas visualmente |
| Presentación PPTX | Nueve diapositivas renderizadas y revisadas; validación de estructura, geometría, fuentes e importación aprobada |

Las advertencias de lint incluyen disponibilidad de nuevas versiones, recursos sin uso, ubicación de imágenes y sugerencias de estilo. No bloquean la compilación.

## Verificación de Firebase real

El 25 de octubre de 2026 pasó `FirebaseBackupSmokeTest` en el emulador Pixel_8 contra el proyecto `ecopoints-3d8bb`: 1 prueba, 0 fallos y 0 omitidas. El registro por `UserRepository` crea una cuenta Authentication y un respaldo inicial. Una segunda base Room y un segundo DataStore vacíos simulan otro dispositivo: se inicia sesión directamente, sin registrarse de nuevo, y se verifican UID, nombre de perfil, saldo, mascota, retos y ajustes recuperados. También se comprueban contraseña incorrecta sin sesión ni filas locales, rechazo de registro duplicado, conservación de cambios locales sin respaldar, sesión Firebase tras guardar y bloqueo de lectura sin autenticación. La cuenta y el documento temporales se eliminaron al finalizar correctamente.

La prueba usa dos bases Room en memoria, dos DataStores temporales y una instancia Firebase nombrada independiente, sin reemplazar los datos ni cerrar la sesión real de la aplicación. Es optativa y requiere habilitar explícitamente `firebaseSmoke=true`; en ejecuciones normales queda omitida. Para repetirla en PowerShell:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.ecopoints.app.data.FirebaseBackupSmokeTest' '-Pandroid.testInstrumentationRunnerArguments.firebaseSmoke=true'
```

Este comando requiere el archivo local de configuración, un emulador con red y las reglas publicadas. Crea una cuenta de prueba en el proyecto real y elimina exclusivamente esa cuenta y su respaldo al terminar. Si falla la conexión durante la limpieza, revisa la cuenta `ecopoints.qa.*@example.com` en Authentication y su documento por UID en Firestore.

## Recorrido de aceptación

1. Registrar dos cuentas Firebase con correos diferentes. Verificar elección y nombre de mascota, bienvenida y bono inicial. En una instalación nueva, iniciar sesión con una cuenta existente sin repetir el registro.
2. Cerrar y abrir la app. Continuar sesión y verificar persistencia.
3. Crear, editar, completar con evidencia y eliminar un reto. Verificar saldo e historial.
4. Alimentar y jugar con la mascota. Activar confirmación de gasto y verificar que cancelar conserva el saldo.
5. Editar nombre de perfil y nombre de mascota. Verificar que el correo de acceso se muestra como solo lectura. Cambiar tema, tamaño de texto y radio del mapa.
6. Abrir EcoMapa con ubicación permitida y denegada. Revisar estados de red.
7. Sin configuración Firebase, verificar que Ajustes muestre el aviso y botones deshabilitados.
8. Registrar una cuenta y verificar los documentos `users/{uid}` y `backups/{uid}`. Modificar datos y comprobar la sincronización automática, sin botones de guardar o restaurar.
9. Cerrar sesión inmediatamente después de cambiar el progreso. Iniciar sesión desde otra instalación y comprobar que ve el cambio. Volver al primer dispositivo y confirmar que actualiza su caché antigua.
10. Reiniciar la app y comprobar que SharedPreferences conserva la huella y fecha de la última sincronización. Si existen cambios pendientes, comprobar que se reintentan.
11. Probar contraseña incorrecta, red desconectada, copia inexistente y reglas sin permiso. Confirmar mensaje y posibilidad de reintentar.
12. Con dos usuarios Firebase, intentar acceder al documento del otro desde Emulator Suite y confirmar denegación. Confirmar que consultas de lista y clientes sin autenticar quedan denegados.
13. Abrir «¿Olvidaste tu contraseña?», solicitar el enlace al correo propio y completar allí el cambio. Verificar acceso con la nueva contraseña y rechazo de la anterior. Este envío de correo no se realizó desde la prueba automatizada.

## Actualización de autenticación

SQLite versión 3 añade `firebase_uid` con índice único. Las migraciones 1 a 2 y 2 a 3 conservan las cuentas y el saldo. Al autenticar una cuenta antigua se vincula el UID y se borran sus hashes locales. «Continuar como…» solo se ofrece cuando la sesión Firebase coincide con el correo y el UID de la copia local. La validación de acceso conserva el mínimo de 8 caracteres existente.

El informe y las diapositivas iniciales de G01 documentan la etapa anterior de respaldos. El comportamiento de autenticación actualizado está descrito en README.md y en esta validación.

## Perfiles visibles en Firestore

El registro ahora guarda `users/{uid}` con nombre, correo, especie y nombre de mascota, fecha de creación de la cuenta y fecha de actualización. El perfil y el respaldo inicial se escriben en un mismo lote: si falla cualquiera, no se confirma el registro como correcto. Authentication conserva las credenciales; las contraseñas no se escriben en Firestore.

Al iniciar sesión, los usuarios anteriores también se incorporan a `users` sin reemplazar su respaldo. Guardar un respaldo actualiza el perfil. Se conserva la validación de correo existente, que admite distintos proveedores con formato válido, y el mínimo de ocho caracteres de contraseña. No se cambió el diseño de las mascotas.

La compilación, las 58 pruebas unitarias y las 18 pruebas locales del emulador pasaron después de este cambio. Lint terminó sin errores, con 34 advertencias y una sugerencia. Tras la publicación de las reglas por el usuario, **la prueba remota ampliada de perfiles pasó contra `ecopoints-3d8bb`: 1 prueba, 0 fallos, 0 omitidas**. Verificó los campos del perfil, la ausencia de contraseña en Firestore, el rechazo de cambio de UID, la creación del perfil al entrar con una cuenta antigua y el rechazo de lectura anónima. También confirmó recuperación desde una segunda caché vacía y conservación de cambios locales. Se eliminaron perfil, respaldo y cuenta temporales al finalizar.

## Contraseña elegida en EcoPoints

El registro y el inicio de sesión utilizan la misma contraseña ingresada por el usuario en EcoPoints; Firebase la valida internamente. Se corrigieron los textos de acceso, registro, error y recuperación para no solicitar una segunda «contraseña Firebase». Para cuentas de la etapa antigua con dos claves distintas, el enlace de recuperación permite elegir la contraseña deseada tras verificar el correo. No se sustituyó la autenticación remota por una contraseña local ni se modificaron claves existentes sin verificación.

Después de esta corrección pasaron la compilación, las 58 pruebas unitarias y la prueba remota de registro, acceso desde una caché vacía, perfiles y respaldos (1 prueba, 0 fallos, 0 omitidas). La contraseña elegida durante el registro permitió iniciar sesión en la segunda caché; la incorrecta fue rechazada. Se eliminaron los datos temporales al finalizar.

## Sincronización automática y cierre de sesión

Se eliminaron los botones Guardar respaldo y Restaurar desde Firebase. `CloudSyncCoordinator` observa cambios en las seis tablas y los ajustes, agrupa cambios cercanos y guarda perfil y estado de la cuenta automáticamente. SharedPreferences conserva la huella confirmada por UID; no se marca como sincronizada una escritura que solo está pendiente en el cliente.

Home y Bienvenida ahora llaman al cierre centralizado de `UserRepository`, que espera la confirmación de Firestore antes de borrar la sesión. El panel bloquea acciones nuevas y espera las mutaciones que estaban en curso. Si hay cambios pendientes sin red, el cierre falla conservando la sesión y los datos locales; se reintenta el guardado. El inicio de sesión recupera la nube incluso si ya había una caché local y «Continuar como…» también actualiza el estado.

La prueba remota ampliada ya pasó con una cuenta temporal: verificó guardado automático de perfil, mascota, puntos, retos, logros, historial y ajustes sin invocar un guardado manual; cierre justo después de una modificación; recuperación en un segundo dispositivo simulado y actualización posterior del primero; y bloqueo del cierre con Firestore desconectado. Después de reactivar la red se confirmó el cambio pendiente y pudo cerrarse la sesión. La cuenta y sus documentos temporales se eliminaron.

Las fotos de evidencia no se suben a Firebase. Al refrescar una cuenta en su dispositivo original, las rutas de archivos que todavía existen se conservan. La sincronización de estados completos no combina ediciones simultáneas de una misma cuenta entre dispositivos.

## Alcance de la verificación

Esta lista define el recorrido manual y no afirma que se haya ejecutado completo. Se incorporó localmente el archivo `app/google-services.json` del proyecto `ecopoints-3d8bb`, cuyo paquete coincide con `com.example.ecopoints.app`. El procesamiento de Google Services fue correcto y la prueba remota confirmó el funcionamiento de Authentication, escritura y lectura autenticadas y bloqueo de lectura anónima. Las reglas fueron publicadas por el usuario desde la consola; no se ha comparado automáticamente todo su contenido con el archivo local ni probado aún el aislamiento entre dos usuarios autenticados.
