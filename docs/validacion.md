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

Esta lista define pruebas manuales y no afirma que ya se hayan ejecutado. La verificación remota requiere un proyecto Firebase configurado. No se dispone aún del archivo `app/google-services.json`, por lo que no se han publicado las reglas ni probado una transferencia contra una base remota real.
