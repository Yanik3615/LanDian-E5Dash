$ErrorActionPreference = "Stop"

$proj = "C:\Users\606443\.gemini\antigravity\scratch\CarDashboard"
$toolchain = "C:\Users\606443\.gemini\antigravity\scratch\toolchain"
$jdkHome = (Get-ChildItem "$toolchain\jdk" -Directory | Select-Object -First 1).FullName
$sdkDir = "$toolchain\android-sdk"
$buildTools = "$sdkDir\build-tools\30.0.3"
$androidJar = "$sdkDir\platforms\android-28\android.jar"

$aapt2 = "$buildTools\aapt2.exe"
$d8Jar = "$buildTools\lib\d8.jar"
$zipalign = "$buildTools\zipalign.exe"
$apksignerJar = "$buildTools\lib\apksigner.jar"
$java = "$jdkHome\bin\java.exe"
$javac = "$jdkHome\bin\javac.exe"
$keytool = "$jdkHome\bin\keytool.exe"

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

$buildDir = "$proj\build"
$resZip = "$buildDir\res.zip"
$genDir = "$buildDir\gen"
$objDir = "$buildDir\obj"
$dexDir = "$buildDir\dex"
$outDir = "$proj\out"

if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Force -Path $buildDir, $genDir, $objDir, $dexDir, $outDir | Out-Null

Write-Host "[1/5] Compiling resources with aapt2..."
$resFiles = Get-ChildItem -Recurse -File "$proj\app\src\main\res" | Select-Object -ExpandProperty FullName
& $aapt2 compile -o $resZip @resFiles
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed ($LASTEXITCODE)" }

Write-Host "[2/5] Linking APK and generating R.java..."
$unalignedApk = "$buildDir\app-unaligned.apk"
$aaptRules = "$buildDir\aapt_rules.pro"
& $aapt2 link `
    -o $unalignedApk `
    -I $androidJar `
    --manifest "$proj\app\src\main\AndroidManifest.xml" `
    --java $genDir `
    --proguard $aaptRules `
    --min-sdk-version 28 `
    --target-sdk-version 28 `
    --version-code 1 `
    --version-name "1.0.0" `
    -R $resZip `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed ($LASTEXITCODE)" }

Write-Host "[3/5] Compiling Java sources with javac (target 1.8, -g:none)..."
$javaFiles = @(Get-ChildItem -Recurse -Filter "*.java" "$proj\app\src\main\java" | Select-Object -ExpandProperty FullName)
$javaFiles += @(Get-ChildItem -Recurse -Filter "*.java" $genDir | Select-Object -ExpandProperty FullName)
& $javac -g:none -Xlint:-options -source 1.8 -target 1.8 -encoding UTF-8 -classpath $androidJar -d $objDir @javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

Write-Host "[4/5] Shrinking & obfuscating bytecode with R8..."
$classFiles = Get-ChildItem -Recurse -Filter "*.class" $objDir | Select-Object -ExpandProperty FullName
& $java -Xmx1024M -cp $d8Jar com.android.tools.r8.R8 --release --min-api 28 --lib $androidJar --output $dexDir --pg-conf "$proj\proguard-rules.pro" --pg-conf $aaptRules @classFiles
if ($LASTEXITCODE -ne 0) { throw "R8 failed ($LASTEXITCODE)" }

Write-Host "[5/5] Packaging, zipaligning, and signing CarDashboard.apk..."
# Add classes.dex into unaligned APK
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unalignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$dexDir\classes.dex", "classes.dex") | Out-Null
$zip.Dispose()

$alignedApk = "$buildDir\app-aligned.apk"
& $zipalign -f 4 $unalignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }

$keystore = "$toolchain\debug.keystore"
if (-not (Test-Path $keystore)) {
    & $keytool -genkeypair -v -keystore $keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CarDashboard,O=BW,C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE)" }
}

$finalApk = "$outDir\CarDashboard.apk"
& $java -Xmx512M -jar $apksignerJar sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $finalApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed ($LASTEXITCODE)" }

Write-Host "SUCCESS! APK built at: $finalApk"
Get-Item $finalApk | Select-Object Name, Length, LastWriteTime
