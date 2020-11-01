@echo off
setlocal enabledelayedexpansion
set app_mods=%1
set libs=%2
set jfx_mods=%JAVAFX_MODS%
set module_path=%jfx_mods%;%app_mods%;%libs%
rem set add_jfx=javafx.base,javafx.controls,javafx.graphics
rem set add_app=vocabularizer.gui,vocabularizer.cli,vocabularizer.dictionary,nicedev.util,gtts
rem set add_lib=org.slf4j
rem set modules=%add_jfx%,%add_app%,%add_lib%
set gui_launcher=gui=vocabularizer.gui/com.nicedev.vocabularizer.ExpositorGUI
set cli_launcher=cli=vocabularizer.cli/com.nicedev.vocabularizer.ExpositorCLI
set tts_launcher=tts=pronouncerApp/com.nicedev.tts.PronouncerApp
jlink --module-path %module_path% --add-modules vocabularizer.gui,vocabularizer.cli,pronouncerApp --bind-services --output .\app --compress=2 --strip-debug --no-man-pages --no-header-files --launcher %gui_launcher% --launcher %cli_launcher% --launcher %tts_launcher%
set app_mods=
set libs=
set add_jfx=
set add_app=
set add_lib=
set modues=
