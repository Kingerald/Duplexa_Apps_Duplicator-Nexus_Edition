# Duplexa Apps Duplicator - Nexus Edition ProGuard Rules
-keep class com.duplexa.duplicator.nexus.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-dontwarn com.android.apksig.**
-keep class com.android.apksig.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
