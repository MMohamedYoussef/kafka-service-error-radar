param([switch]$Demo, [string]$Broker)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $env:JAVA_HOME -or -not $env:KAFKA_HOME) { throw 'Set JAVA_HOME (JDK 21) and KAFKA_HOME (Kafka 4.3.1 distribution).' }
$javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$libs = Join-Path $env:KAFKA_HOME 'libs/*'
$build = Join-Path $root 'build'
New-Item -ItemType Directory -Force -Path $build | Out-Null
& $javac -encoding UTF-8 -cp $libs -d $build (Join-Path $root 'src/App.java') (Join-Path $root 'src/BrokerReplay.java') (Join-Path $root 'src/AppTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& $java -cp "$build;$libs" AppTest
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
Push-Location $root
try {
    if ($Demo) {
        & $java -cp "$build;$libs" App
        if ($LASTEXITCODE -ne 0) { throw 'Offline demo failed' }
    }
    if ($Broker) {
        & $java -cp "$build;$libs" App --broker $Broker
        if ($LASTEXITCODE -ne 0) { throw 'Broker demo failed' }
    }
} finally { Pop-Location }
