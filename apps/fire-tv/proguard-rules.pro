# GameDay ships unminified today. These rules keep a future minified build safe.
-keep class app.gameday.host.GameDayApplication { *; }
-keep class app.gameday.host.ui.** { *; }
-keep class app.gameday.host.install.InstallResultReceiver { *; }
-keep class app.gameday.host.diagnostics.DiagnosticShareProvider { *; }
-keepattributes *Annotation*
