#!/usr/bin/env bash
# Exercises auth, roles/permissions, users, posts and likes against a running app.
# Usage: ./api-test.sh [base-url]   (default: http://localhost:9094/api/vi)
# Admin credentials come from ADMIN_USERNAME / ADMIN_PASSWORD (default admin / admin1234).
set -u

BASE_URL="${1:-http://localhost:9094/api/vi}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin1234}"
PASS=0
FAIL=0
BODY=""
declare -A TOKENS=()
declare -A REFRESH=()

# req <who> <METHOD> <PATH> <EXPECTED_STATUS> [JSON_BODY]
# <who> is a name from TOKENS (sent as a Bearer token), "anon" for no token, or "bad" for an invalid one.
req() {
  local who=$1 method=$2 path=$3 expected=$4 data=${5:-}
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE_URL$path")
  [[ -n $data ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  case $who in
    anon) ;;
    bad)  args+=(-H 'Authorization: Bearer not-a-real-token') ;;
    *)    args+=(-H "Authorization: Bearer ${TOKENS[$who]}") ;;
  esac

  local out status
  if ! out=$(curl "${args[@]}"); then
    echo "Cannot reach $BASE_URL — is the app running?" >&2
    exit 1
  fi
  status=${out##*$'\n'}
  BODY=${out%$'\n'*}

  if [[ $status == "$expected" ]]; then
    PASS=$((PASS + 1)); printf '\033[32mPASS\033[0m'
  else
    FAIL=$((FAIL + 1)); printf '\033[31mFAIL\033[0m'
  fi
  printf '  %-6s %-6s %-30s -> %s (expected %s)\n' "$who" "$method" "$path" "$status" "$expected"
  # Error bodies can include a full stack trace in development, so it is dropped here
  [[ -n $BODY ]] && { echo "$BODY" | jq 'if type == "object" then del(.trace) else . end' 2>/dev/null || echo "$BODY"; }
  echo
}

# check <description> <jq-expression evaluated against $BODY>
check() {
  if echo "$BODY" | jq -e "$2" >/dev/null 2>&1; then
    PASS=$((PASS + 1)); printf '\033[32mPASS\033[0m  %s\n\n' "$1"
  else
    FAIL=$((FAIL + 1)); printf '\033[31mFAIL\033[0m  %s\n\n' "$1"
  fi
}

# login <who> <username> <password>
login() {
  req anon POST /auth/login 200 "{\"username\":\"$2\",\"password\":\"$3\"}"
  TOKENS[$1]=$(echo "$BODY" | jq -r .accessToken)
  REFRESH[$1]=$(echo "$BODY" | jq -r .refreshToken)
}

SUFFIX=$(date +%s)
ALICE="alice_$SUFFIX"
BOB="bob_$SUFFIX"

echo "== Authentication =="
req anon GET  /post 401
req bad  GET  /post 401
req anon POST /auth/register 200 "{\"username\":\"$ALICE\",\"password\":\"secret\"}"
ALICE_ID=$(echo "$BODY" | jq -r .id)
check "password is not returned" 'has("password") | not'
req anon POST /auth/register 409 "{\"username\":\"$ALICE\",\"password\":\"other\"}"
req anon POST /auth/register 400 '{"username":"","password":""}'
req anon POST /auth/register 200 "{\"username\":\"$BOB\",\"password\":\"secret\"}"
BOB_ID=$(echo "$BODY" | jq -r .id)

req anon POST /auth/login 401 "{\"username\":\"$ALICE\",\"password\":\"wrong\"}"
login alice "$ALICE" secret
check "login returns a bearer token" '.tokenType == "Bearer" and (.accessToken | length > 0)'
login bob "$BOB" secret
login admin "$ADMIN_USERNAME" "$ADMIN_PASSWORD"

req alice GET /auth/me 200
check "new users get the USER role" '.roles == ["USER"]'
check "USER has post:read, post:write and post:like only" '.permissions == ["post:like", "post:read", "post:write"]'

echo "== Posts =="
req alice POST /post 200 '{"title":"Hello","content":"First post"}'
POST_ID=$(echo "$BODY" | jq -r .id)
check "author is the logged-in user" ".userAccount.id == $ALICE_ID"
req alice GET /post 200

echo "== Likes (only as yourself) =="
req alice POST   "/user/$ALICE_ID/likes/$POST_ID" 204
req alice POST   "/user/$BOB_ID/likes/$POST_ID"   403
req alice GET    /post 200
check "post is liked once, by alice" \
  "[.[] | select(.id == $POST_ID) | .likedByUsers[].id] == [$ALICE_ID]"
req alice DELETE "/user/$ALICE_ID/likes/$POST_ID" 204
req alice POST   "/user/$ALICE_ID/likes/999999999" 404

echo "== USER cannot use admin endpoints =="
req alice GET  /user 403
req alice POST /user 403 '{"username":"x","password":"y"}'
req alice PUT  "/user/$BOB_ID/roles" 403 '{"roles":["ADMIN"]}'
req alice GET  /role 403
req alice POST /role 403 '{"roleName":"HACKER","permissions":["role:write"]}'

echo "== Delete permissions =="
req alice POST   /post 200 '{"title":"Second","content":"Will be moderated"}'
POST2_ID=$(echo "$BODY" | jq -r .id)
req alice POST   "/user/$ALICE_ID/likes/$POST2_ID" 204
req bob   DELETE "/post/$POST2_ID" 403   # not the author, no post:moderate

echo "== Admin =="
req admin GET /user 200
check "user list includes alice and bob" "any(.[]; .id == $ALICE_ID) and any(.[]; .id == $BOB_ID)"
req admin GET /role 200
check "USER, MODERATOR and ADMIN roles exist" '[.[].roleName] | contains(["USER", "MODERATOR", "ADMIN"])'
req admin GET /role/permissions 200
check "8 permissions are seeded" 'length >= 8'

req admin PUT "/user/$BOB_ID/roles" 400 '{"roles":["NOPE"]}'
req admin PUT "/user/$BOB_ID/roles" 200 '{"roles":["MODERATOR"]}'
check "bob is now a moderator" '.roles == ["MODERATOR"] and (.permissions | index("post:moderate"))'

echo "== Role change applies to bob's existing token =="
req bob   DELETE "/post/$POST2_ID" 204   # liked post, deleted by a moderator
req bob   GET    /user 200               # MODERATOR has user:read
req bob   POST   /role 403 '{"roleName":"X"}'
req alice DELETE "/post/$POST_ID" 204    # authors can delete their own posts
req alice DELETE "/post/$POST_ID" 404

echo "== Role management =="
EDITOR="EDITOR_$SUFFIX"
req admin POST /role 400 "{\"roleName\":\"$EDITOR\",\"permissions\":[\"no:such\"]}"
req admin POST /role 200 "{\"roleName\":\"$EDITOR\",\"roleDescription\":\"Read-only\",\"permissions\":[\"post:read\"]}"
EDITOR_ID=$(echo "$BODY" | jq -r .id)
req admin POST /role 409 "{\"roleName\":\"$EDITOR\"}"
req admin PUT  "/user/$ALICE_ID/roles" 200 "{\"roles\":[\"$EDITOR\"]}"
req alice POST /post 403 '{"title":"Nope","content":"read-only now"}'
req admin PUT  "/role/$EDITOR_ID/permissions" 200 '{"permissions":["post:read","post:write"]}'
req alice POST /post 200 '{"title":"Back","content":"write permission restored"}'
req alice DELETE "/post/$(echo "$BODY" | jq -r .id)" 204

echo "== Logout =="
OLD_ALICE_TOKEN=${TOKENS[alice]}
login alice "$ALICE" secret                # a second session for alice
TOKENS[alice_old]=$OLD_ALICE_TOKEN
req alice     POST /auth/logout 204
req alice     GET  /auth/me     401        # this token is revoked
req alice_old GET  /auth/me     200        # the other session is still logged in
req anon      POST /auth/logout 401

echo "== Refresh token =="
login bob2 "$BOB" secret                   # a fresh session for bob
check "login returns a refresh token" '.refreshToken | length > 0'
FIRST_REFRESH=${REFRESH[bob2]}
req anon POST /auth/refresh 200 "{\"refreshToken\":\"$FIRST_REFRESH\"}"   # no access token needed
check "refresh returns a new refresh token" ".refreshToken != \"$FIRST_REFRESH\""
TOKENS[bob2]=$(echo "$BODY" | jq -r .accessToken)
SECOND_REFRESH=$(echo "$BODY" | jq -r .refreshToken)
req bob2 GET  /auth/me 200                 # the new access token works
req anon POST /auth/refresh 401 "{\"refreshToken\":\"$FIRST_REFRESH\"}"   # reuse: session signed out
req anon POST /auth/refresh 401 "{\"refreshToken\":\"$SECOND_REFRESH\"}"  # ...including its newest token
req bob2 GET  /auth/me 401                 # ...and its access tokens, immediately
req anon POST /auth/refresh 401 '{"refreshToken":"made-up"}'
req anon POST /auth/refresh 400 '{}'

echo "== Logout also ends the refresh token =="
login bob3 "$BOB" secret
req bob3 POST /auth/logout 204
req anon POST /auth/refresh 401 "{\"refreshToken\":\"${REFRESH[bob3]}\"}"

echo "== Log out from all devices =="
login dev1 "$BOB" secret
login dev2 "$BOB" secret
req dev1 POST /auth/logout-all 204
req dev1 GET  /auth/me 401                 # this device...
req dev2 GET  /auth/me 401                 # ...and every other one
req anon POST /auth/refresh 401 "{\"refreshToken\":\"${REFRESH[dev2]}\"}"
req anon POST /auth/logout-all 401

echo "== Summary: $PASS passed, $FAIL failed =="
[[ $FAIL -eq 0 ]]
