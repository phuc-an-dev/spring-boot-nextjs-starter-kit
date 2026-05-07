#!/bin/bash
set -e

REGION=ap-southeast-1
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com

echo "Authenticating Docker to ECR..."
aws ecr get-login-password --region ${REGION} | \
  docker login --username AWS --password-stdin ${ECR_REGISTRY}

echo "Pulling latest images..."
cd ~/app
docker compose -f docker-compose.prod.yml --env-file .env.prod pull

echo "Restarting containers..."
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --remove-orphans

echo "Cleaning up old images..."
docker image prune -f

echo "Deploy complete."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
