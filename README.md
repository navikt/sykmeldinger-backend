# sykmeldinger-backend
[![Deploy to dev and prod](https://github.com/navikt/sykmeldinger-backend/actions/workflows/deploy.yml/badge.svg)](https://github.com/navikt/sykmeldinger-backend/actions/workflows/deploy.yml)

Backend-app for sykmeldinger-frontend

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Jackson
* Redis

#### Requirements

* JDK 17

## Deploy redis to dev:
Deploying redis can be done with the following command: `kubectl apply --context dev-fss --namespace default -f redis.yaml`

## Deploy redis to prod:
Deploying redis can be done with the following command: `kubectl apply --context prod-fss --namespace default -f redis.yaml`
