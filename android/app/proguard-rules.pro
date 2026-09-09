# NewPipeExtractor ships Rhino (JS engine used to decipher YouTube stream URLs)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
