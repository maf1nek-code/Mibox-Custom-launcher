// Throwaway test apps: fake "installed apps" used to exercise the launcher on the emulator.
plugins {
    id("com.android.application")
}

class Demo(val id: String, val label: String, val color: String, val tv: Boolean)

val demos = listOf(
    Demo("cinema", "Kino Plus", "#B3261E", true),
    Demo("stream", "StreamBox", "#1565C0", true),
    Demo("music", "Fonia", "#1DB954", true),
    Demo("sport", "Arena Sport", "#EF6C00", true),
    Demo("kids", "Bajkowo", "#8E24AA", true),
    Demo("news", "Wiadomosci 24", "#37474F", true),
    Demo("nature", "Planeta", "#2E7D32", true),
    Demo("retro", "Retro TV", "#C2185B", true),
    Demo("radio", "Radio Fala", "#00838F", true),
    Demo("files", "Pliki", "#F9A825", false),
    Demo("browser", "Przegladarka", "#5E35B1", false),
    Demo("mono", "Terminal", "#9E9E9E", false),
)

android {
    namespace = "com.lumen.demo"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "app"
    productFlavors {
        demos.forEach { d ->
            create(d.id) {
                dimension = "app"
                applicationId = "com.lumen.demo.${d.id}"
                resValue("string", "demo_label", d.label)
                resValue("color", "demo_color", d.color)
                manifestPlaceholders["category"] =
                    if (d.tv) "android.intent.category.LEANBACK_LAUNCHER" else "android.intent.category.LAUNCHER"
                manifestPlaceholders["banner"] = if (d.tv) "@drawable/demo_banner" else "@null"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
