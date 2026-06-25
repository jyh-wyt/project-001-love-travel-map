@echo off
set JAVA_HOME=D:\develop\jdk17
set PATH=D:\develop\jdk17\bin;D:\develop\apache-maven-3.9.4\bin;%PATH%

cd /d "%~dp0..\apps\server"
mvn spring-boot:run

