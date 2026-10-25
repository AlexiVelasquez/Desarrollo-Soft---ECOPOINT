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
8. Registrar una cuenta desde la app y verificar el respaldo inicial `backups/{uid}` en consola. Guardar desde Ajustes sin introducir otra contraseña.
9. Modificar progreso local, restaurar y confirmar que vuelve a la copia. Cancelar primero y verificar que no cambia nada.
10. Reiniciar la app y comprobar que SharedPreferences conserva la fecha del respaldo de esa cuenta.
11. Probar contraseña incorrecta, red desconectada, copia inexistente y reglas sin permiso. Confirmar mensaje y posibilidad de reintentar.
12. Con dos usuarios Firebase, intentar acceder al documento del otro desde Emulator Suite y confirmar denegación. Confirmar que consultas de lista y clientes sin autenticar quedan denegados.
13. Abrir «¿Olvidaste tu contraseña?», solicitar el enlace al correo propio y completar allí el cambio. Verificar acceso con la nueva contraseña y rechazo de la anterior. Este envío de correo no se realizó desde la prueba automatizada.

## Actualización de autenticación

SQLite versión 3 añade `firebase_uid` con índice único. Las migraciones 1 a 2 y 2 a 3 conservan las cuentas y el saldo. Al autenticar una cuenta antigua se vincula el UID y se borran sus hashes locales. «Continuar como…» solo se ofrece cuando la sesión Firebase coincide con el correo y el UID de la copia local. La validación de acceso conserva el mínimo de 8 caracteres existente.

El informe y las diapositivas iniciales de G01 documentan la etapa anterior de respaldos. El comportamiento de autenticación actualizado está descrito en README.md y en esta validación.

## Alcance

Esta lista define el recorrido manual y no afirma que se haya ejecutado completo. Se incorporó localmente el archivo `app/google-services.json` del proyecto `ecopoints-3d8bb`, cuyo paquete coincide con `com.example.ecopoints.app`. El procesamiento de Google Services fue correcto y la prueba remota confirmó el funcionamiento de Authentication, escritura y lectura autenticadas y bloqueo de lectura anónima. Las reglas fueron publicadas por el usuario desde la consola; no se ha comparado automáticamente todo su contenido con el archivo local ni probado aún el aislamiento entre dos usuarios autenticados.
