# EcoPoints

Aplicación Android nativa con Kotlin, Jetpack Compose, MVVM y navegación de una sola Activity.
Permite registrar e iniciar sesión con Firebase Authentication, cuidar una mascota, completar retos, consultar el mapa de reciclaje y recuperar el progreso desde Firestore en otro dispositivo.

## Requisitos de la entrega

| Código | Implementación y evidencia |
| --- | --- |
| G01 | Informe y presentación en `docs/` |
| G02 | Login y registro Firebase, recuperación por correo, bienvenida, inicio, perfil, ajustes y EcoMapa en `ui/`; flujos de retos, fotos y confirmaciones |
| G03 | `CloudPreferences`: fecha y huella de la última sincronización confirmada por UID en SharedPreferences. `LegacyPrefsMigrator`: migración de preferencias antiguas. Ajustes reactivos en DataStore |
| G04 | Room sobre SQLite, seis tablas, DAOs, transacciones y migraciones 1 a 2 y 2 a 3 sin borrar datos; vínculo por UID Firebase |
| G05 | Firebase Authentication y Firestore con guardado automático, cierre de sesión tras confirmación y recuperación del estado actualizado al entrar |
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

7. Recompila la app y revisa Ajustes > Sincronización de cuenta.

La app compila sin `google-services.json`, pero el acceso, el registro y la actualización desde la nube requieren configurar Firebase y una conexión a internet. «Continuar como…» usa la sesión guardada y actualiza el estado de la cuenta. Durante una sesión abierta, los cambios se conservan en Room y se reintenta su sincronización si falla la red.

## Uso y modelo de datos remoto

Existe una sola cuenta Firebase por usuario. **Registrarse** crea la cuenta en Authentication, inicializa la mascota y el progreso en Room y guarda en Firestore tanto el perfil `users/{uid}` como el estado inicial `backups/{uid}`. Ambos documentos se confirman en un mismo lote atómico. **Iniciar sesión** verifica el correo y la contraseña y recupera el estado de la nube en cada acceso, incluso si el dispositivo ya tenía una copia antigua. Si había cambios locales pendientes de una sesión interrumpida, se confirman primero. Si nunca existió estado remoto, se inicializa y guarda el progreso predeterminado.

En **Firestore → Datos → users** aparece un documento por UID con campos legibles: `uid`, `name`, `email`, `petSpecies`, `petName`, `createdAt` y `updatedAt`. No almacena contraseñas. Los usuarios existentes se incorporan a esta colección al volver a iniciar sesión o guardar un respaldo. Cada guardado de respaldo también actualiza el perfil. Las fechas de creación corresponden a la cuenta Authentication y se conservan en las actualizaciones.

**Antes de usar esta actualización, publica la versión actual de `firestore.rules`.** Las reglas anteriores solo autorizaban `backups`; sin la nueva sección `users`, Firestore rechazará la escritura del perfil. Cada usuario puede consultar y modificar únicamente su documento. El correo debe coincidir con su identidad Firebase; las contraseñas permanecen en Authentication.

**La contraseña para iniciar sesión es la que escribiste al registrarte en EcoPoints.** No se solicita una clave de configuración, una contraseña de Google ni una segunda contraseña para los respaldos. Firebase Authentication verifica internamente esa misma contraseña elegida por el usuario.

Las cuentas de una versión antigua podían tener claves diferentes para la app y los respaldos. Para unificarlas, usa «¿Olvidaste tu contraseña?» y elige la contraseña deseada desde el enlace enviado a tu correo. La app no modifica contraseñas antiguas sin verificar el correo ni acepta un hash local como sustituto de la autenticación remota. Las cuentas exclusivamente locales deben registrarse una vez desde el formulario de EcoPoints; su progreso local se conserva al vincularlas. La versión 3 de SQLite añade un UID único y elimina los hashes antiguos al vincular una cuenta tras la autenticación correcta.

La sesión Firebase persiste mediante el SDK. **Cerrar sesión** espera a que Firestore confirme todos los cambios y después cierra la sesión local y remota. El panel bloquea acciones nuevas y espera las operaciones que estaban en curso. Si el guardado falla o no hay red y existen cambios pendientes, muestra un aviso y conserva la sesión para reintentar. «¿Olvidaste tu contraseña?» solicita un enlace por correo; el cambio se completa desde ese enlace. El correo es la identidad de acceso y se muestra como solo lectura en Perfil.

Firestore guarda un documento privado `backups/{firebaseUid}` con `version`, `payload` JSON y `updatedAt` generado por el servidor. El payload incluye nombre de perfil, progreso, mascota, retos, logros, historial y ajustes. Los respaldos antiguos sin nombre de perfil siguen siendo compatibles. No incluye contraseñas, hashes, tokens ni archivos de fotos. Las reglas autorizan únicamente al propietario y rechazan escrituras con campos adicionales.

Para usar tu cuenta en otro teléfono, **inicia sesión directamente**. Se recupera el estado confirmado en la nube, también cuando se vuelve a un dispositivo que tenía una copia antigua. Los identificadores se reasignan para evitar colisiones con otras cuentas. Las tablas se restauran en una transacción y luego se aplican los ajustes de DataStore. Las rutas de evidencia se conservan en el dispositivo original cuando el archivo local sigue existiendo.

### Guardado automático

`CloudSyncCoordinator` observa las seis tablas de Room y los ajustes de DataStore. Agrupa cambios cercanos durante 600 ms y sincroniza el estado completo sin botones de guardar o restaurar. Una exclusión mutua serializa las subidas y el cierre de sesión; si hay cambios durante una subida, confirma también la versión nueva. SharedPreferences conserva por UID la huella de la última versión confirmada, para detectar pendientes al volver a abrir la app y evitar escrituras duplicadas.

Si falla la red, los datos locales permanecen en Room y se reintenta cada cinco segundos mientras el proceso está activo. Al reiniciar la app se comprueba otra vez la versión local pendiente. En Ajustes solo aparece el estado de sincronización y la fecha del último guardado confirmado. No se requiere pulsar ningún botón para guardar.

El documento de estado tiene un límite de 800 KB. No hay combinación de cambios simultáneos entre dispositivos: cada sincronización reemplaza el estado y prevalece la última escritura confirmada. No se copian archivos de fotos ni se publica el ranking en Firebase. La prueba de esta entrega verifica la continuidad entre dispositivos después de cerrar sesión correctamente.

## Clonar en otro equipo

```powershell
git clone https://github.com/AlexiVelasquez/Desarrollo-Soft---ECOPOINT.git
```

Copia el archivo del **mismo proyecto** `ecopoints-3d8bb` en `app/google-services.json`, configura el SDK Android y compila. Ese archivo no está versionado. Después abre la app e inicia sesión con tu correo y la contraseña que elegiste al registrarte en EcoPoints. No uses otro proyecto Firebase, porque sus cuentas y respaldos serían independientes.

## Verificación de la entrega

Consulta `docs/validacion.md` para el resultado de compilación, las pruebas ejecutadas y el recorrido manual propuesto. La cobertura funcional de pantallas no equivale a una certificación visual del 100%.

Documentación oficial: [configuración Android](https://firebase.google.com/docs/android/setup), [autenticación](https://firebase.google.com/docs/auth/android/start), [reglas de acceso](https://firebase.google.com/docs/firestore/security/rules-conditions).
