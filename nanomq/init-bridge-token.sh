#!/bin/sh
set -e

echo "NanoMQ Bridge Token Initialization Script"
echo "=========================================="

SPRING_BACKEND_URL="${SPRING_BACKEND_URL:-http://app:8080}"
BRIDGE_USERNAME="${BRIDGE_USERNAME:-admin}"
BRIDGE_PASSWORD="${BRIDGE_PASSWORD:-admin123}"
NANOMQ_CONF="${NANOMQ_CONF:-/etc/nanomq/nanomq.conf}"

echo "Fetching JWT token from Spring Backend..."
echo "Backend URL: $SPRING_BACKEND_URL"

LOGIN_RESPONSE=$(curl -s -X POST "$SPRING_BACKEND_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$BRIDGE_USERNAME\",\"password\":\"$BRIDGE_PASSWORD\"}")

LOGIN_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"data":"[^"]*"' | sed 's/"data":"//;s/"//')

if [ -z "$LOGIN_TOKEN" ]; then
  echo "ERROR: Failed to login to Spring Backend"
  echo "Response: $LOGIN_RESPONSE"
  exit 1
fi

echo "Login successful, fetching bridge token..."

BRIDGE_RESPONSE=$(curl -s -X GET "$SPRING_BACKEND_URL/api/auth/bridge-token" \
  -H "Authorization: Bearer $LOGIN_TOKEN")

BRIDGE_TOKEN=$(echo "$BRIDGE_RESPONSE" | grep -o '"data":"[^"]*"' | sed 's/"data":"//;s/"//')

if [ -z "$BRIDGE_TOKEN" ]; then
  echo "ERROR: Failed to get bridge token"
  echo "Response: $BRIDGE_RESPONSE"
  exit 1
fi

echo "Bridge token obtained successfully!"
echo "Token length: ${#BRIDGE_TOKEN}"

echo "Updating NanoMQ configuration..."
sed -i "s|BRIDGE_TOKEN_PLACEHOLDER|$BRIDGE_TOKEN|g" "$NANOMQ_CONF"

echo "Configuration updated. Starting NanoMQ..."
exec nanomq start --conf "$NANOMQ_CONF"
