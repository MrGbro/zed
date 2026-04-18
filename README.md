# zed

A simple gateway implementation by java.

## Zed Gateway Multi-Module

This repository is initialized as a Maven multi-module project.

## Modules
- gateway-common
- gateway-core
- gateway-bootstrap
- gateway-admin
- gateway-dist
- gateway-transport
- gateway-plugin
- gateway-config
- gateway-registry
- architecture-tests

## Build
`mvn -q -DskipTests validate`

## Bootstrap Config
`gateway-bootstrap` now loads startup config from YAML with this priority:

1. JVM system property `gateway.bootstrap.config` (external file path)
2. classpath resource `bootstrap.yaml` (`gateway-bootstrap/src/main/resources/bootstrap.yaml`)
3. built-in defaults in `BootstrapConfig.defaultConfig()`

Example with external file:
`mvn -pl gateway-bootstrap -DskipTests exec:java -Dexec.mainClass=io.homeey.gateway.bootstrap.BootstrapApplication -Dgateway.bootstrap.config=F:/config/bootstrap.yaml`
