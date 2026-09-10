# The native bridge is loaded by name and its JNI entry points must remain available.
-keep class com.pocketterminal.core.NativePtyBridge { *; }