# TG3_Equipo7 · SonarQube y refactorización de json-simple

Deber #3 de **Construcción y Evolución de Software (ISWD633)** · Escuela Politécnica Nacional · GR1SW · 2026-B

**Equipo 7:** Jorge Romario Camino Guzmán, Oscar José Fierro Aules, Joel Alexander Places Lucero, Josué Ismael Quintana Remache.

## Contenido
- **Proyecto analizado:** [json-simple](https://github.com/fangyidong/json-simple), librería Java para leer y escribir JSON. Su README original está en [README-json-simple.md](README-json-simple.md).
- **Informe:** [informe/TG3_Equipo7.pdf](informe/TG3_Equipo7.pdf)
- **Versiones:** la etiqueta [`v1.0`](https://github.com/JoelAlexanderProg/TG3_Equipo7/tree/v1.0) es el código original y la etiqueta [`v1.1`](https://github.com/JoelAlexanderProg/TG3_Equipo7/tree/v1.1) el código refactorizado.

## Refactorizaciones
Cada refactorización está en su propio commit; después de cada una se compiló el proyecto y se ejecutaron las 45 pruebas.

| N.º | Refactorización | Regla Sonar | Código | Commit |
|---|---|---|---|---|
| 1 | Rename Variable | `java:S1117` | `JSONParser.peekStatus()` | [`3b5b819`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/3b5b819) |
| 2 | Split Variable | `java:S9364` | `JSONParser.parse(Reader, ContainerFactory)` | [`af694a3`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/af694a3) |
| 3 | Remove Dead Code | `java:S2737` | `JSONParser.parse(Reader, ContainerFactory)` | [`76e9f09`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/76e9f09) |
| 4 | Replace Nested Conditional with Guard Clauses | `java:S3776` | `JSONParser` (los dos `parse`) | [`3b13c9d`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/3b13c9d) |
| 5 | Extract Method | `java:S3776` | `JSONParser` (los dos `parse`) | [`5b73b90`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/5b73b90) |
| 6 | Introduce Assertion | `java:S131` | `switch(status)` de `JSONParser` | [`8f916b4`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/8f916b4) |
| 7 | Consolidate Duplicate Conditional Fragments | `java:S1854` | `JSONParser.parse(Reader, ContentHandler, boolean)` | [`0fda0d4`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/0fda0d4) |
| 8 | Introduce Special Case | `java:S131` | `Yytoken.toString()` | [`6d96bd9`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/6d96bd9) |
| 9 | Remove Setting Method | `java:S1165` | `ParseException` | [`bdc8a55`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/bdc8a55) |
| 10 | Agregar `@Override` | `java:S1161` | `ParseException`, `JSONObject`, `JSONArray` | [`1c12d6f`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/1c12d6f) |
| 11 | Hide Method (constructor) | `java:S1118` | `JSONValue` | [`21709ee`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/21709ee) |
| 12 | Move Method | `java:S3776` | `JSONValue.writeJSONString()` → `JSONArray` | [`170f7a3`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/170f7a3) |
| 13 | Consolidate Conditional Expression | `java:S3776` | `JSONValue.writeJSONString()` | [`419d451`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/419d451) |
| 14 | Decompose Conditional | `java:S3776` | `JSONValue.escape()` | [`320b441`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/320b441) |
| 15 | Substitute Algorithm | `java:S3776` | `JSONValue.escape()` | [`36ad4e4`](https://github.com/JoelAlexanderProg/TG3_Equipo7/commit/36ad4e4) |

## Resultados en SonarQube
| Métrica | v1.0 | v1.1 |
|---|---|---|
| Code smells | 75 | 54 |
| Deuda técnica (min) | 872 | 606 |
| Complejidad cognitiva | 343 | 273 |
| Cobertura | 74.4 % | 75.8 % |
| Bugs / vulnerabilidades | 0 / 0 | 0 / 0 |
| Quality Gate | Aprobado | Aprobado |

## Cómo reproducir el análisis
```powershell
docker network create net-sonar
docker volume create sonarqube_data; docker volume create sonarqube_extensions; docker volume create sonarqube_logs
docker run -d --name sonarqube --network net-sonar -p 9000:9000 `
  -v sonarqube_data:/opt/sonarqube/data -v sonarqube_extensions:/opt/sonarqube/extensions `
  -v sonarqube_logs:/opt/sonarqube/logs sonarqube:community
```
En http://localhost:9000 se cambia la contraseña de `admin`, se genera un token de análisis y se marca como predeterminado el perfil de Java **Sonar way comprehensive**. Luego, desde la carpeta del proyecto:
```powershell
docker run --rm --network net-sonar -v "${PWD}:/src" -v maven-repo:/root/.m2 -w /src maven:3-eclipse-temurin-21 `
  mvn -B clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report `
  org.sonarsource.scanner.maven:sonar-maven-plugin:sonar "-Dsonar.host.url=http://sonarqube:9000" "-Dsonar.token=<token>" `
  "-Dsonar.projectKey=TG3_Equipo7-v1.1" "-Dsonar.projectName=json-simple v1.1" "-Dsonar.projectVersion=1.1"
```
