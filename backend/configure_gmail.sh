#!/bin/bash
# Use this script to configure your backend with your Gmail credentials
# This sends the credentials to the backend, which securely saves them in Convex.

if [ "$#" -ne 2 ]; then
    echo "Usage: ./configure_gmail.sh <your_email> <app_password>"
    echo "Example: ./configure_gmail.sh keanu@gmail.com abcd1234efgh5678"
    exit 1
fi

EMAIL=$1
APP_PASSWORD=$2

# Assuming we are running this locally against the backend
HOST="http://localhost:8080"

echo "🔌 Connecting Gmail account for $EMAIL..."

# We need a user ID. For this test script, we'll simulate being "user_123"
# In a real scenario, you'd be logged in on the phone.
# We'll use a hardcoded token that the mock auth middleware accepts/extracts ID from.
# The mock middleware does: "user_" + token[:8]
TOKEN="1234567890" # This results in userId "user_12345678"

curl -X POST "$HOST/api/auth/connect/gmail" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"email\": \"$EMAIL\",
    \"appPassword\": \"$APP_PASSWORD\"
  }"

echo -e "\n\n✅ If successful, your Gmail is now linked!"
echo "The backend will use these credentials automatically when finding Northern codes."
