# kotlinx.serialization (Protocol WebSocket messages in :shared)
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class tech.shaffersoftworks.remotescreens.shared.**$$serializer { *; }
-keepclassmembers class tech.shaffersoftworks.remotescreens.shared.** {
    *** Companion;
}
-keepclasseswithmembers class tech.shaffersoftworks.remotescreens.shared.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class tech.shaffersoftworks.remotescreens.shared.** { *; }

# Compose Remote: reflective getRemoteContext + restricted document APIs (alpha)
-keep class androidx.compose.remote.player.view.RemoteComposePlayer {
    *** getRemoteContext(...);
    *;
}
-keep class androidx.compose.remote.** { *; }
