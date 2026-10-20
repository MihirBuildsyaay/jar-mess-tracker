# Builds JarMess.apk without Android Studio/Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs JDK 17 (JAVA_HOME) and an Android SDK with build-tools 34.0.0 + platform android-34 (ANDROID_HOME).
# Without those variables it looks in ..\AndroidTools\jdk and ..\AndroidTools\sdk.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "$root\..\AndroidTools\jdk" }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$root\..\AndroidTools\sdk" }
$bt = "$sdk\build-tools\34.0.0"
$androidJar = "$sdk\platforms\android-34\android.jar"
$out = "$root\build"
$keystore = "$root\jarmess.keystore"
# Personal sideload key. Keep jarmess.keystore: updates only install over the old app if signed with it.
$keyPass = 'jarmess-local'

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

function Check($step) { if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE)" } }

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes" | Out-Null

& "$bt\aapt2.exe" compile --dir "$root\res" -o "$out\res.zip"; Check 'aapt2 compile'
& "$bt\aapt2.exe" link -I $androidJar --manifest "$root\AndroidManifest.xml" --java "$out\gen" `
    -o "$out\unsigned.apk" "$out\res.zip"; Check 'aapt2 link'

$sources = Get-ChildItem "$root\src", "$out\gen" -Recurse -Filter *.java | ForEach-Object FullName
# core-lambda-stubs.jar supplies LambdaMetafactory, which android.jar's stubs lack.
& javac -source 8 -target 8 -bootclasspath "$androidJar;$bt\core-lambda-stubs.jar" -encoding UTF-8 -Xlint:-options -d "$out\classes" $sources; Check 'javac'

$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --release --min-api 26 --lib $androidJar --output $out $classes; Check 'd8'

# Append classes.dex without touching the entries aapt2 wrote (resources.arsc must stay uncompressed).
& python -c "import sys, zipfile; z = zipfile.ZipFile(sys.argv[1], 'a', zipfile.ZIP_DEFLATED); z.write(sys.argv[2], 'classes.dex'); z.close()" `
    "$out\unsigned.apk" "$out\classes.dex"; Check 'add classes.dex'

& "$bt\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"; Check 'zipalign'

if (-not (Test-Path $keystore)) {
    & keytool -genkeypair -keystore $keystore -alias jarmess -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $keyPass -keypass $keyPass -dname 'CN=Jar Mess'; Check 'keytool'
}
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass "pass:$keyPass" --out "$root\JarMess.apk" "$out\aligned.apk"; Check 'apksigner'
& "$bt\apksigner.bat" verify "$root\JarMess.apk"; Check 'apksigner verify'

Write-Host "Built $root\JarMess.apk"
