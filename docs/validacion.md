# Validación de EcoPoints

## Resultado automatizado

Verificación del 25 de octubre de 2026:

| Comprobación | Resultado |
| --- | --- |
| `:app:testDebugUnitTest` | 56 pruebas aprobadas, 0 fallos |
| `:app:assembleDebug` | APK generado correctamente |
| `:app:connectedDebugAndroidTest` | 18 pruebas aprobadas en Pixel_8 API 37, incluida migración SQLite 1 a 2 |
| `:app:lintDebug` | Sin errores; 34 advertencias y 1 sugerencia de mantenimiento |
| `git diff --check` | Sin errores de espacios |
| Informe DOCX | Cinco páginas renderizadas con Word y revisadas visualmente |
| Presentación PPTX | Nueve diapositivas renderizadas y revisadas; validación de estructura, geometría, fuentes e importación aprobada |

Las advertencias de lint incluyen disponibilidad de nuevas versiones, recursos sin uso, ubicación de imágenes y sugerencias de estilo. No bloquean la compilación.

## Verificación de Firebase real

El 25 de octubre de 2026 pasó `FirebaseBackupSmokeTest` en el emulador Pixel_8 contra el proyecto `ecopoints-3d8bb`: 1 prueba, 0 fallos y 0 omitidas. Se verificaron registro con Authentication, guardado en Firestore, restauración de saldo, nombre de mascota y ajustes, y denegación de lectura sin autenticación. La cuenta y el documento temporales se eliminaron al finalizar correctamente.

La prueba usa Room en memoria y un DataStore temporal, sin reemplazar los datos locales de la aplicación. Es optativa y requiere habilitar explícitamente `firebaseSmoke=true`; en ejecuciones normales queda omitida. Para repetirla en PowerShell:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.ecopoints.app.data.FirebaseBackupSmokeTest' '-Pandroid.testInstrumentationRunnerArguments.firebaseSmoke=true'
```

Este comando requiere el archivo local de configuración, un emulador con red y las reglas publicadas. Crea una cuenta de prueba en el proyecto real y elimina exclusivamente esa cuenta y su respaldo al terminar. Si falla la conexión durante la limpieza, revisa la cuenta `ecopoints.qa.*@example.com` en Authentication y su documento por UID en Firestore.

## Recorrido de aceptación

1. Registrar dos cuentas locales con correos diferentes. Verificar elección y nombre de mascota, bienvenida y bono inicial.
2. Cerrar y abrir la app. Continuar sesión y verificar persistencia.
3. Crear, editar, completar con evidencia y eliminar un reto. Verificar saldo e historial.
4. Alimentar y jugar con la mascota. Activar confirmación de gasto y verificar que cancelar conserva el saldo.
5. Editar perfil y nombre de mascota. Cambiar tema, tamaño de texto y radio del mapa.
6. Abrir EcoMapa con ubicación permitida y denegada. Revisar estados de red.
7. Sin configuración Firebase, verificar que Ajustes muestre el aviso y botones deshabilitados.
8. Con Firebase configurado, crear cuenta de nube y respaldar. Verificar `backups/{uid}` en consola.
9. Modificar progreso local, restaurar y confirmar que vuelve a la copia. Cancelar primero y verificar que no cambia nada.
10. Reiniciar la app y comprobar que SharedPreferences conserva la fecha del respaldo de esa cuenta.
11. Probar contraseña incorrecta, red desconectada, copia inexistente y reglas sin permiso. Confirmar mensaje y posibilidad de reintentar.
12. Con dos usuarios Firebase, intentar acceder al documento del otro desde Emulator Suite y confirmar denegación. Confirmar que consultas de lista y clientes sin autenticar quedan denegados.

## Alcance

Esta lista define el recorrido manual y no afirma que se haya ejecutado completo. Se incorporó localmente el archivo `app/google-services.json` del proyecto `ecopoints-3d8bb`, cuyo paquete coincide con `com.example.ecopoints.app`. El procesamiento de Google Services fue correcto y la prueba remota confirmó el funcionamiento de Authentication, escritura y lectura autenticadas y bloqueo de lectura anónima. Las reglas fueron publicadas por el usuario desde la consola; no se ha comparado automáticamente todo su contenido con el archivo local ni probado aún el aislamiento entre dos usuarios autenticados.
