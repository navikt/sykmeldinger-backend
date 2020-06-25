echo "Bygger sykmeldinger-backend latest for docker compose utvikling"

./gradlew shadowJar -x test

docker build . -t sykmeldinger-backend:latest
