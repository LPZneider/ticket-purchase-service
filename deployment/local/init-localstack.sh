#!/bin/sh
set -e

REGION="us-east-1"

awslocal sqs create-queue --queue-name purchase-requests --region "$REGION"

# Sample message matching the seeded RESERVED ticket/order in init-dynamodb.sh, so a
# fresh docker-compose up exercises the full confirm flow without any manual step.
awslocal sqs send-message \
  --queue-url "http://localhost:4566/000000000000/purchase-requests" \
  --region "$REGION" \
  --message-body '{"orderId":"sample-order-1","eventId":"sample-event-1","ticketIds":["t1"],"userId":"sample-user-1","requestedAt":"2026-07-01T10:00:00Z"}'

echo "LocalStack queues ready"
