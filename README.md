# EcoPoints

Aplicación Android nativa con Kotlin, Jetpack Compose, MVVM y navegación de una sola Activity.
Permite registrar cuentas locales, cuidar una mascota, completar retos, consultar el mapa de reciclaje y respaldar los datos en Firebase.

## Requisitos de la entrega

| Código | Implementación y evidencia |
| --- | --- |
| G01 | Informe y presentación en `docs/` |
| G02 | Login, registro, recuperación local, bienvenida, inicio, perfil, ajustes y EcoMapa en `ui/`; flujos de retos, fotos y confirmaciones |
| G03 | `CloudPreferences`: fecha del último respaldo por usuario en SharedPreferences. `LegacyPrefsMigrator`: migración de preferencias antiguas. Ajustes reactivos en DataStore |
| G04 | Room sobre SQLite, seis tablas, DAOs, transacciones y migración 1 a 2 sin borrar datos |
| G05 | Firebase Authentication y Cloud Firestore: crear cuenta de nube, guardar y restaurar respaldos desde Ajustes |
| G06 | Repositorios, ViewModels, validación, coroutines, inyección manual y pruebas |

## Compilación

Configura el SDK Android en `local.properties` (no versionado). Gradle utiliza el JDK 25 declarado en `gradle/gradle-daemon-jvm.properties`.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

El segundo comando requiere un emulador o dispositivo conectado. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Activar Firebase

Proyecto vinculado: `ecopoints-3d8bb` (alias predeterminado en `.firebaserc`). El archivo `app/google-services.json` ya está instalado en este equipo y permanece excluido de Git. En otros equipos debe copiarse de nuevo. La activación de Authentication y Firestore y la publicación de reglas se realizan en la consola del proyecto; el archivo de configuración Android no habilita esos servicios por sí solo.

1. Crea o selecciona tu proyecto en la consola Firebase.
2. Registra una app Android con paquete `com.example.ecopoints.app`.
3. Descarga el archivo y guárdalo como `app/google-services.json`. Está excluido de Git.
4. Habilita Authentication > Métodos de acceso > Correo electrónico/contraseña.
5. Crea una base de datos **Cloud Firestore**, modo producción.
6. Publica `firestore.rules` desde la consola o con Firebase CLI:

```powershell
firebase login
firebase deploy --only firestore:rules --project TU_ID_DE_PROYECTO
```

7. Recompila la app y abre Ajustes > Base de datos Firebase.

La app compila sin `google-services.json`; en ese caso la sección indica que falta configuración y deshabilita las acciones remotas. No se incluye un proyecto de prueba ni credenciales inventadas.

## Uso y modelo de datos remoto

La sesión de la app es local. El respaldo usa una cuenta Firebase independiente con **el mismo correo de la cuenta local**. La contraseña de nube puede ser distinta y solo se mantiene en memoria durante la operación. Usa «Crear cuenta Firebase y respaldar» una vez y «Guardar respaldo» en las siguientes ocasiones. Si la creación de cuenta funciona pero falla la escritura, reintenta con «Guardar respaldo».

Firestore guarda un documento privado `backups/{firebaseUid}` con `version`, `payload` JSON y `updatedAt` generado por el servidor. El payload incluye progreso, mascota, retos, logros, historial y ajustes. No incluye contraseñas locales, hashes, tokens ni archivos de fotos. Las reglas autorizan únicamente al propietario y rechazan escrituras con campos adicionales.

Para usar un respaldo en otro teléfono, crea una cuenta local con el mismo correo y luego restaura con la contraseña Firebase. La restauración pide confirmación y reemplaza los datos locales de esa cuenta. Los identificadores se reasignan para evitar colisiones con otras cuentas del dispositivo. La restauración de las tablas es transaccional; los ajustes DataStore se aplican después. Si esa última escritura falla, reintenta la restauración.

Los respaldos son manuales, de hasta 800 KB. Cada escritura reemplaza la copia anterior. No hay combinación automática entre dispositivos, ranking público en Firebase ni copia de fotos. El ranking existente sigue siendo local. La recuperación de contraseña local no modifica la contraseña Firebase; para esta última usa el flujo de recuperación de Authentication del proyecto.

## Verificación de la entrega

Consulta `docs/validacion.md` para el resultado de compilación, las pruebas ejecutadas y el recorrido manual propuesto. La cobertura funcional de pantallas no equivale a una certificación visual del 100%.

Documentación oficial: [configuración Android](https://firebase.google.com/docs/android/setup), [autenticación](https://firebase.google.com/docs/auth/android/start), [reglas de acceso](https://firebase.google.com/docs/firestore/security/rules-conditions).
