Raspberry Pi 一键部署说明

概述

本文档说明如何在 Raspberry Pi（Debian/Raspberry Pi OS）上一键启动 Seckill 项目（包含 Postgres、Redis、RabbitMQ 和应用服务）。脚本：deploy_rpi.sh；Compose 文件：docker-compose.rpi.yml。

快速开始（在 Pi 上）

1. 把仓库克隆到树莓派或将脚本复制到仓库根目录。

2. 运行脚本（可能需要 sudo）:

   ./deploy_rpi.sh

脚本会：
- 安装 Docker 和 docker compose 插件（如果缺少）
- 使用容器化 JDK 构建项目 jar（避免在 Pi 上安装 JDK/Maven）
- 通过 docker compose 启动 Postgres、Redis、RabbitMQ 和应用容器

注意事项与调优

- 构建时间：在 Pi 上用容器化 maven 构建会比较慢（取决于网络和 SD 卡速度）。如果太慢，建议在你本地 PC 使用 Docker buildx 构建 multi-arch 镜像并推送到 registry，然后在 Pi 上直接 docker pull 并运行。

- 平台：脚本会根据 uname -m 自动选择 DOCKER_PLATFORM（arm64/arm/v7）。如需覆盖可在环境中设置 DOCKER_PLATFORM=linux/arm64。

健康检查

- 应用健康端点: http://<PI_IP>:8080/actuator/health
- RabbitMQ 管理界面: http://<PI_IP>:15672 （默认帐号在 .env）

回退方案（在 PC 构建镜像）

1. 在 PC 上启用 buildx: docker buildx create --use
2. docker buildx build --platform linux/arm64,linux/amd64 -t <your-registry>/seckill:latest --push .
3. 在 Pi 上使用 docker pull <your-registry>/seckill:latest，然后编辑 docker-compose.rpi.yml 将 app.image 设置为 <your-registry>/seckill:latest 并去掉 build 部分。

常见问题

- 权限问题：如果运行 docker 需要 sudo，确保添加当前用户到 docker 组并重新登录。
- 数据持久性：Compose 使用 named volumes（默认），如果需要备份请挂载到主机目录。


