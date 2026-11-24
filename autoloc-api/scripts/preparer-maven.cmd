@echo off
rem Cree la configuration du Maven Wrapper si elle manque.
rem Elle indique quelle version de Maven telecharger : Maven 3.9.11, depot officiel Apache.
if exist "%~dp0..\.mvn\wrapper\maven-wrapper.properties" exit /b 0
echo Creation de .mvn\wrapper\maven-wrapper.properties (Maven 3.9.11, depot officiel Apache)
mkdir "%~dp0..\.mvn\wrapper" 2>NUL
> "%~dp0..\.mvn\wrapper\maven-wrapper.properties" echo wrapperVersion=3.3.4
>> "%~dp0..\.mvn\wrapper\maven-wrapper.properties" echo distributionType=only-script
>> "%~dp0..\.mvn\wrapper\maven-wrapper.properties" echo distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip
exit /b 0
