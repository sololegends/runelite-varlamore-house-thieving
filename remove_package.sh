#!/bin/bash

# Vars
KEY=$MAVEN_REPO_TOKEN
VERSION=$1
PROJ_ID=$CI_PROJECT_ID

echo "Getting latest ID from Gitlab"
LATEST_ID=$(curl --header "PRIVATE-TOKEN: $KEY" "https://$CI_SERVER_URL/api/v4/projects/$PROJ_ID/packages/" | jq ".[] | select(.version==\"$VERSION\").id")

if [ "$LATEST_ID" == "" ]; then
  echo "No package for \"$VERSION\" found.."
else
  echo "Detected ID [$LATEST_ID]"
  echo "Removing package $VERSION from Gitlab"
  curl --request DELETE --header "PRIVATE-TOKEN: $KEY" "https://$CI_SERVER_URL/api/v4/projects/$PROJ_ID/packages/$LATEST_ID" | jq .
fi
