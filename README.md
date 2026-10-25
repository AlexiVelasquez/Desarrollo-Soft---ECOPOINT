# EcoPoints

Aplicación Android nativa con Kotlin, Jetpack Compose, MVVM y navegación de una sola Activity.
Permite registrar e iniciar sesión con Firebase Authentication, cuidar una mascota, completar retos, consultar el mapa de reciclaje y recuperar el progreso desde Firestore en otro dispositivo.

## Requisitos de la entrega

| Código | Implementación y evidencia |
| --- | --- |
| G01 | Informe y presentación en `docs/` |
| G02 | Login y registro Firebase, recuperación por correo, bienvenida, inicio, perfil, ajustes y EcoMapa en `ui/`; flujos de retos, fotos y confirmaciones |
| G03 | `CloudPreferences`: fecha del último respaldo por usuario en SharedPreferences. `LegacyPrefsMigrator`: migración de preferencias antiguas. Ajustes reactivos en DataStore |
| G04 | Room sobre SQLite, seis tablas, DAOs, transacciones y migraciones 1 a 2 y 2 a 3 sin borrar datos; vínculo por UID Firebase |
| G05 | Firebase Authentication para registro, login, sesión y recuperación; Firestore para respaldo inicial y recuperación automática en una instalación nueva |
| G06 | Repositorios, ViewModels, validación, coroutines, inyección manual y pruebas |

## Compilación

Configura el SDK Android en `local.properties` (no versionado). Gradle utiliza el JDK 25 declarado en `gradle/gradle-daemon-jvm.properties`.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

El segundo comando requiere un emulador o dispositivo conectado. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Activar Firebase

Proyecto vinculado: `ecopoints-3d8bb` (alias predeterminado en `.firebaserc`). El archivo `app/google-services.json` ya está instalado en este equipo y permanece excluido de Git. En otros equipos debe copiarse de nuevo. Authentication y Firestore están activos: la prueba remota del 25 de octubre de 2026 verificó guardado, restauración y rechazo de lectura sin autenticar. Consulta `docs/validacion.md`. Los pasos siguientes permiten configurar otra instalación; el archivo Android no habilita esos servicios por sí solo.

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

La app compila sin `google-services.json`, pero el acceso y el registro requieren configurar Firebase y una conexión a internet. No existe un inicio de sesión alternativo basado en contraseñas locales. Una sesión Firebase ya guardada permite «Continuar como…» para trabajar con los datos locales sin iniciar sesión otra vez.

## Uso y modelo de datos remoto

Existe una sola cuenta Firebase por usuario. **Registrarse** crea la cuenta en Authentication, inicializa la mascota y el progreso en Room y guarda en Firestore tanto el perfil `users/{uid}` como el respaldo inicial `backups/{uid}`. Ambos documentos se confirman en un mismo lote atómico: la app no anuncia registro correcto si Firestore rechaza el guardado. **Iniciar sesión** verifica el correo y la contraseña con Firebase. Cuando la cuenta aún no existe en la caché del dispositivo, recupera su respaldo de Firestore y crea la copia local automáticamente: no es necesario volver a registrarse. Si existe la cuenta Firebase pero nunca tuvo respaldo, se inicializa el progreso predeterminado.

En **Firestore → Datos → users** aparece un documento por UID con campos legibles: `uid`, `name`, `email`, `petSpecies`, `petName`, `createdAt` y `updatedAt`. No almacena contraseñas. Los usuarios existentes se incorporan a esta colección al volver a iniciar sesión o guardar un respaldo. Cada guardado de respaldo también actualiza el perfil. Las fechas de creación corresponden a la cuenta Authentication y se conservan en las actualizaciones.

**Antes de usar esta actualización, publica la versión actual de `firestore.rules`.** Las reglas anteriores solo autorizaban `backups`; sin la nueva sección `users`, Firestore rechazará la escritura del perfil. Cada usuario puede consultar y modificar únicamente su documento. El correo debe coincidir con su identidad Firebase; las contraseñas permanecen en Authentication.

Las cuentas de respaldo creadas con la versión anterior siguen funcionando: usa su contraseña Firebase en la pantalla de inicio de sesión. La contraseña local antigua puede ser distinta y ya no se utiliza para autenticar. Las cuentas exclusivamente locales deben registrarse una vez en Firebase desde el formulario de registro; su progreso local se conserva al vincularlas. La versión 3 de SQLite añade un UID único y elimina los hashes antiguos al vincular una cuenta tras la autenticación correcta.

La sesión Firebase persiste mediante el SDK y se cierra con «Cerrar sesión». Ajustes usa esa misma sesión para **Guardar respaldo** y **Restaurar desde Firebase**, sin crear otra cuenta ni pedir de nuevo la contraseña. «¿Olvidaste tu contraseña?» solicita un enlace por correo a Firebase; el cambio se completa desde ese enlace. El correo es la identidad de acceso y se muestra como solo lectura en Perfil; el nombre y el nombre de mascota continúan siendo editables.

Firestore guarda un documento privado `backups/{firebaseUid}` con `version`, `payload` JSON y `updatedAt` generado por el servidor. El payload incluye nombre de perfil, progreso, mascota, retos, logros, historial y ajustes. Los respaldos antiguos sin nombre de perfil siguen siendo compatibles. No incluye contraseñas, hashes, tokens ni archivos de fotos. Las reglas autorizan únicamente al propietario y rechazan escrituras con campos adicionales.

Para usar tu cuenta en otro teléfono, **inicia sesión directamente**. Se recupera automáticamente el último respaldo al crear la caché de esa cuenta. En un dispositivo donde la cuenta ya tiene datos locales, el login conserva los cambios aún no respaldados. Para reemplazarlos por la copia remota, usa «Restaurar desde Firebase» y confirma. Los identificadores se reasignan para evitar colisiones con otras cuentas. Las tablas se restauran en una transacción; los ajustes DataStore se aplican después. Si esa última escritura falla, reintenta la restauración.

Después del registro inicial, los respaldos son manuales, de hasta 800 KB. Guarda un respaldo antes de cambiar de dispositivo para recuperar tus últimos cambios. Cada escritura reemplaza la copia anterior. No hay combinación automática entre dispositivos, ranking público en Firebase ni copia de fotos. El ranking existente sigue siendo local.

## Clonar en otro equipo

```powershell
git clone https://github.com/AlexiVelasquez/Desarrollo-Soft---ECOPOINT.git
```

Copia el archivo del **mismo proyecto** `ecopoints-3d8bb` en `app/google-services.json`, configura el SDK Android y compila. Ese archivo no está versionado. Después abre la app e inicia sesión con tu correo y contraseña Firebase existentes. No uses otro proyecto Firebase, porque sus cuentas y respaldos serían independientes.

## Verificación de la entrega

Consulta `docs/validacion.md` para el resultado de compilación, las pruebas ejecutadas y el recorrido manual propuesto. La cobertura funcional de pantallas no equivale a una certificación visual del 100%.

Documentación oficial: [configuración Android](https://firebase.google.com/docs/android/setup), [autenticación](https://firebase.google.com/docs/auth/android/start), [reglas de acceso](https://firebase.google.com/docs/firestore/security/rules-conditions).
