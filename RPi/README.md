# RPi deployment (business replicas only)

This folder contains only seckill business deployment assets.
Middleware (`nacos/redis/rabbitmq/postgres/nginx`) stays on the PC host.

## Topology

- Each machine runs exactly one seckill instance.
- Use the same `docker-compose.yml` on all machines.
- Different machines provide different `.env` values (`INSTANCE_NAME`, `HOST_PORT`).

## 1) Build application image on each machine

```powershell
cd E:\Github\SecKill-4RPi\seckill
.\mvnw.cmd clean package -DskipTests
docker build -t seckill-app:latest .
```

## 2) Configure machine-specific env

```powershell
cd E:\Github\SecKill-4RPi\RPi
Copy-Item .env.example .env
```

Set `.env` values on each machine:

- machine A: `INSTANCE_NAME=seckill1`
- machine B: `INSTANCE_NAME=seckill2`
- machine C: `INSTANCE_NAME=seckill3`
- machine D: `INSTANCE_NAME=seckill4`

`HOST_PORT` can be same (for example `18081`) because machines are different.

## 3) Start one instance on each machine

```powershell
cd E:\Github\SecKill-4RPi\RPi
docker compose up -d
```

## 4) Initialize stock once

```powershell
curl -X POST "http://127.0.0.1:18081/api/admin/goods/1001/stock/10000"
```

## 5) Configure PC Nginx upstream

Point upstream to 4 machine IPs, for example:

- `192.168.137.11:18081`
- `192.168.137.12:18081`
- `192.168.137.13:18081`
- `192.168.137.14:18081`

## Notes

- `INSTANCE_IP` is not required; Nacos uses runtime network info.
- `instance-id` defaults to `HOSTNAME-server.port`, so it is unique per machine.
- Keep `PC_HOST` in `.env` as the PC middleware LAN IP.
