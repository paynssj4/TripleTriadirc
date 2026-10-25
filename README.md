# Triple Triad – Android (Java)

Compilation (PowerShell, depuis le dossier qui contient settings.gradle) :

    $env:JAVA_HOME = (Get-ChildItem C:\dev\jdk17 | Select-Object -First 1).FullName
    $env:ANDROID_HOME = "C:\dev\android-sdk"
    $env:Path = "$env:JAVA_HOME\bin;C:\dev\gradle-8.7\bin;C:\dev\android-sdk\cmdline-tools\latest\bin;$env:Path"
    gradle assembleDebug

APK : app\build\outputs\apk\debug\TripleTriad.apk

Images des cartes : app\src\main\assets\Img\001.png ... (ou .webp si CardRenderer est adapté)
Sons (facultatif)  : app\src\main\assets\Sounds\ (sound-start.wav, sound-card.wav, sound-turn.wav, bgm.mp3)
