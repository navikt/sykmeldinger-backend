# sykmeldinger-backend
Backend-app for sykmeldinger-frontend

## Deploy redis to dev:
Deploying redis can be done with the following command: `kubectl apply --context dev-fss --namespace default -f redis.yaml`

## Deploy redis to prod:
Deploying redis can be done with the following command: `kubectl apply --context prod-fss --namespace default -f redis.yaml`
