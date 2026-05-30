$ErrorActionPreference = "Stop"

$classes = Join-Path $PSScriptRoot "build\selftest\classes"
New-Item -ItemType Directory -Force $classes | Out-Null

$sources = @(
    (Join-Path $PSScriptRoot "app\src\main\java\com\example\screenlocktodo\TodoItem.java"),
    (Join-Path $PSScriptRoot "app\src\main\java\com\example\screenlocktodo\TodoCodec.java"),
    (Join-Path $PSScriptRoot "tools\TodoCodecSelfTest.java")
)

& javac -encoding UTF-8 -d $classes $sources
& java -cp $classes com.example.screenlocktodo.TodoCodecSelfTest
