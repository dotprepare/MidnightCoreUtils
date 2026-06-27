# ============================================================================
# ProGuard rules for MidnightCoreUtils
# NeoForge 1.21.1 + Kotlin + KotlinForForge
# ============================================================================

# ---- Obfuscation Settings ----
-useuniqueclassmembernames
-flattenpackagehierarchy ''
-repackageclasses 'a'
-allowaccessmodification
-renamesourcefileattribute ""
-keepattributes SourceFile,LineNumberTable

# ---- Don't shrink or optimize (safety for NeoForge/Kotlin bytecode) ----
-dontshrink
-dontoptimize

# ============================================================================
# NEOFORGE ENTRY POINTS
# ============================================================================

# @Mod annotated class — entry point for mod discovery
-keep @interface net.neoforged.fml.common.Mod
-keep @net.neoforged.fml.common.Mod class *
-keepclassmembers @net.neoforged.fml.common.Mod class * { *; }

# @EventBusSubscriber — class-level event subscription
-keep @interface net.neoforged.fml.common.EventBusSubscriber
-keep @net.neoforged.fml.common.EventBusSubscriber class *
-keepclassmembers @net.neoforged.fml.common.EventBusSubscriber class * { *; }

# @SubscribeEvent — method-level event handlers
-keep @interface net.neoforged.bus.api.SubscribeEvent
-keepclassmembers class * {
    @net.neoforged.bus.api.SubscribeEvent <methods>;
}

# NeoForge event bus registration
-keep class net.neoforged.bus.api.IEventBus { *; }
-keep class net.neoforged.neoforge.common.NeoForge { *; }

# ============================================================================
# KOTLIN RUNTIME
# ============================================================================

# Kotlin metadata annotation (must be fully retained)
-keep class kotlin.Metadata { *; }
-keep @interface kotlin.Metadata

# Kotlin object singleton (INSTANCE field) — prevents "object Xxx" from breaking
-keepclassmembers class * {
    public static *** INSTANCE;
}

# Kotlin data class component/copy methods
-keepclassmembers class * {
    *** component*();
    *** copy*(...);
}

# Kotlin companion objects
-keepclassmembers class *$Companion {
    *;
}

# Kotlin built-in types
-keep class kotlin.** { *; }

# Kotlin stdlib internals referenced by compiled code
-keepclassmembers class kotlin.jvm.internal.** { *; }

# ============================================================================
# KOTLIN FOR FORGE
# ============================================================================

-keep class thedarkcolour.kotlinforforge.** { *; }
-keepclassmembers class thedarkcolour.kotlinforforge.** { *; }

# ============================================================================
# ANNOTATIONS & REFLECTION ATTRIBUTES
# ============================================================================

-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature,
    RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,
    RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,
    AnnotationDefault

# ============================================================================
# ENUMS (preserve all enum fields and methods)
# ============================================================================

-keepclassmembers enum * {
    <fields>;
    <methods>;
}

# ============================================================================
# NEOFORGE MINECRAFT / API — TREATED AS LIBRARY
# ============================================================================

# All NeoForge/Minecraft API references are library classes — automatically kept.
# No explicit rules needed for libraries; they are on the -libraryjars path.

# ============================================================================
# EXCEPTIONS / SERIALIZATION
# ============================================================================

-keepclassmembers class * extends java.lang.Throwable {
    *;
}

# ============================================================================
# DO NOT WARN ABOUT REFERENCED LIBRARY CLASSES
# ============================================================================

-dontwarn com.mojang.**
-dontwarn net.minecraft.**
-dontwarn net.neoforged.**
-dontwarn org.apache.logging.**
-dontwarn org.slf4j.**
-dontwarn org.objectweb.asm.**
-dontwarn com.google.gson.**
-dontwarn com.google.common.**
-dontwarn com.google.**
-dontwarn it.unimi.dsi.**
-dontwarn javax.annotation.**
-dontwarn org.intellij.lang.annotations.**
-dontwarn org.jetbrains.annotations.**
