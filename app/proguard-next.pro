# LibVLC resolves Java bindings, nested track classes and callbacks by JNI names.
# Next-only: preserve this small native API boundary in optimized builds.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
