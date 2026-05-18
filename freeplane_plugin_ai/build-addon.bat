@echo off
REM build-addon.bat - 构建 AI 插件的 .addon.mm 安装包
REM 使用方法: build-addon.bat

echo ========================================
echo  Freeplane AI Plugin .addon.mm 打包工具
echo ========================================
echo.

REM 切换到插件目录
cd /d "%~dp0"

REM 1. 清理并构建 JAR
echo [1/4] 构建 AI 插件 JAR...
call gradlew clean jar
if errorlevel 1 (
    echo ❌ JAR 构建失败
    pause
    exit /b 1
)
echo ✅ JAR 构建成功
echo.

REM 2. 查找生成的 JAR 文件
echo [2/4] 查找 JAR 文件...
for /f "delims=" %%i in ('dir /b /s build\libs\*.jar 2^>nul ^| findstr /v "sources javadoc"') do (
    set JAR_FILE=%%i
)

if not defined JAR_FILE (
    echo ❌ 未找到 JAR 文件
    pause
    exit /b 1
)

echo 找到 JAR: %JAR_FILE%
echo.

REM 3. 获取版本号
echo [3/4] 提取版本信息...
for /f "tokens=2 delims=-" %%v in ('echo %JAR_FILE%') do (
    set VERSION=%%~nv
)
echo 版本: %VERSION%
echo.

REM 4. 创建输出目录
echo [4/4] 生成 .addon.mm 文件...
if not exist "build\outputs" mkdir "build\outputs"

REM 调用 Groovy 脚本生成 .addon.mm
call gradlew -q packageAddonMM

if exist "build\outputs\org.freeplane.plugin.ai.addon.mm" (
    echo.
    echo ========================================
    echo  ✅ 打包成功！
    echo ========================================
    echo.
    echo 文件位置: build\outputs\org.freeplane.plugin.ai.addon.mm
    echo.
    echo 安装方法:
    echo   1. 在 Freeplane 中: 工具 → 管理附加组件 → 安装
    echo   2. 或直接双击 .addon.mm 文件
    echo.
    
    REM 显示文件大小
    for %%A in ("build\outputs\org.freeplane.plugin.ai.addon.mm") do (
        set SIZE=%%~zA
        set /a SIZE_KB=%%~zA/1024
        echo 文件大小: !SIZE_KB! KB
    )
    echo.
) else (
    echo.
    echo ❌ 打包失败，未生成 .addon.mm 文件
    echo.
)

pause
