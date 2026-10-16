# TheLine2 🚀🌌 (Edición Extendida)

Secuela / Versión extendida de **TheLine**, un juego arcade 2D de acción y esquiva espacial desarrollado nativamente en Android con **Jetpack Compose**, **Custom Canvas** y **Kotlin**. Esta edición incluye pantalla de inicio (SplashScreen), nuevos sectores y planetas, animaciones de destrucción y efectos de sonido avanzados.

---

## 🌟 Novedades en TheLine2

- **Pantalla de Carga (SplashScreen):** Pantalla de presentación estilizada antes del menú principal.
- **Gráficos Espaciales Mejorados:** Planetas detallados, campos de estrellas animados, efectos de llamas en naves y animaciones de destrucción.
- **Nuevos Efectos de Sonido:** SFX ampliados con explosiones multisatélite y pistas de audio espacial.
- **Motor 2D Optimizado:** Generación procedural de carreteras espaciales ("RoadGenerator") con colisiones mejoradas.
- **Integración AdMob:** Soporte de anuncios Banner, Intersticiales y Recompensados configurados con IDs de prueba oficiales de Google.

---

## 🛠️ Stack Tecnológico y Prerrequisitos

### Stack Tecnológico
- **Lenguaje:** Kotlin 2.x
- **UI Framework:** Jetpack Compose + Custom Canvas 2D
- **Arquitectura:** MVVM (Model-View-ViewModel) con Coroutines
- **Monetización:** Google Mobile Ads (AdMob SDK)
- **Persistencia Local:** Jetpack DataStore

### Prerrequisitos
- **Android Studio:** Ladybug / Meerkat o superior.
- **JDK:** Java 11 o Java 17.
- **Android SDK:**
  - `compileSdk`: 35
  - `minSdk`: 33 (Android 13)
  - `targetSdk`: 36

---

## ⚙️ Configuración de Firebase (Opcional)

Para habilitar analíticas o servicios de Firebase en tu propio entorno:
1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Registra la aplicación Android con el paquete `es.romsolutions.theline`.
3. Descarga el archivo `google-services.json` y colócalo en `app/` (utiliza `google-services.json.template` como referencia).

---

## ⚡ Compilación y Ejecución (Quickstart)

### 1. Clonar el repositorio
```bash
git clone https://github.com/tu-usuario/TheLine2.git
cd TheLine2
```

### 2. Abrir en Android Studio
1. Abre **Android Studio**.
2. Selecciona **Open** y navega hasta la carpeta `TheLine2`.
3. Sincroniza las dependencias con Gradle (**Gradle Sync**).

### 3. Compilar desde la línea de comandos
```bash
# En Linux / macOS:
./gradlew assembleDebug

# En Windows:
gradlew.bat assembleDebug
```
El archivo APK generado estará disponible en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Licencia
Este proyecto se distribuye bajo la licencia **GNU Affero General Public License v3 (AGPL-3.0)** con cláusula de uso ético y libre disposición del código fuente.
