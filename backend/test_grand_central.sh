#!/bin/bash
# Test the Grand Central automation endpoint
# Usage: ./test_grand_central.sh [email]

EMAIL=${1:-"trainstudent.test@imperial.ac.uk"}
FIRST_NAME="Test"
LAST_NAME="Student"

echo "🚂 Testing Grand Central Student Discount Automation..."
echo "Target: http://localhost:8080/api/codes/grandcentral"
echo "Payload: $FIRST_NAME $LAST_NAME <$EMAIL>"

curl -v -X POST http://localhost:8080/api/codes/grandcentral \
  -H "Content-Type: application/json" \
  -d "{\"firstName\": \"$FIRST_NAME\", \"lastName\": \"$LAST_NAME\", \"email\": \"$EMAIL\"}"

echo -e "\n\n✅ Done. If successful, check the email inbox for the code."
