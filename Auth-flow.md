# Authentication, Tokens and Authorization

How a request goes from "username + password" to "allowed to call this endpoint", which files take part and which methods run.

All paths below are relative to `src/main/java/com/example/demo/`.

## The big picture

```
                 ┌────────────── once at startup ──────────────┐
                 │ seeder/DataSeeder.run()                      │
                 │   permissions → roles → admin account        │
                 └──────────────────────────────────────────────┘

 register ──► UserService.createUser()            (user gets role USER)
 login    ──► AuthenticationManager (password check)
              └► SessionService.login() ──► TokenService.issue()  ──► access token (JWT, 15m)
                                                                      refresh token (random, 7d)
 any call ──► Bearer filter: verify JWT ──► SecurityConfig.authenticate()
              └► URL rule (authenticated?) ──► @PreAuthorize (permission?) ──► controller
 refresh  ──► SessionService.refresh() ──► TokenService.issue()   (new pair, same session)
 logout   ──► SessionService.logout() / logoutAll()               (revoke session rows)

                 ┌────────────── every hour ───────────────────┐
                 │ scheduler/RefreshTokenCleanupJob             │
                 │   deletes expired refresh tokens             │
                 └──────────────────────────────────────────────┘
```

Two kinds of token:

| | Access token | Refresh token |
|---|---|---|
| Format | Signed JWT (HS256) | 32 random bytes, Base64URL |
| Lifetime | `app.jwt.access-token-ttl` (15m) | `app.jwt.refresh-token-ttl` (7d) |
| Stored in DB | No | Yes, **only its SHA-256 hash** (`refresh_tokens.token_hash`) |
| Sent as | `Authorization: Bearer <jwt>` on every call | Body of `POST /auth/refresh` only |
| Single use | No | Yes; reusing one signs the session out |

Every login creates a **session id** (UUID). It goes into the access token as the `sid` claim and into every refresh token row for that login. Logout revokes by session id, which is how a stateless JWT can be cancelled before it expires.

## Files involved

| File | Role |
|---|---|
| `config/properties/JwtProperties.java` | `app.jwt.*`: secret (≥ 32 bytes, checked at startup), token lifetimes |
| `config/properties/AdminProperties.java` | `app.admin.*`: the first admin account, used only by the seeder |
| `config/security/SecurityConfig.java` | Filter chain, public URLs, bearer token → user, password encoder, `AuthenticationManager` |
| `config/security/JwtConfig.java` | HS256 `SecretKey`, `JwtEncoder` (signs), `JwtDecoder` (verifies) |
| `config/security/SessionValidator.java` | Interface the filter uses to ask "is this session still active?" |
| `config/security/UserJwtAuthenticationToken.java` | The authenticated request: holds the `UserAccount` and the `Jwt` |
| `controller/AuthController.java` | `/auth/register`, `/login`, `/refresh`, `/me`, `/logout`, `/logout-all` |
| `controller/UserController.java`, `RoleController.java`, `PostController.java` | Protected endpoints, guarded by `@PreAuthorize` |
| `service/SessionService.java` | Login, refresh, logout; implements `SessionValidator` |
| `service/TokenService.java` | Builds the JWT, creates and hashes refresh tokens |
| `service/UserService.java` | Creates users with a BCrypt password and the default role |
| `model/UserAccount.java` | Implements `UserDetails`; `getAuthorities()` turns roles and permissions into authorities |
| `model/Role.java`, `Permission.java`, `RefreshToken.java` | Entities |
| `dao/AuthRepositories.java` | `UserRepository`, `RoleRepository`, `PermissionRepository`, `RefreshTokenRepository` |
| `exception/GlobalExceptionHandler.java` | Turns auth errors into JSON 401 / 403 responses |
| `seeder/DataSeeder.java` | Creates permissions, roles and the admin once |
| `scheduler/RefreshTokenCleanupJob.java` | Deletes expired refresh tokens every hour |

## 0. Startup

1. `JwtProperties` and `AdminProperties` are bound from `application.yaml` (enabled by `@ConfigurationPropertiesScan` in `DemoApplication`). A missing value or a secret shorter than 32 bytes **stops the app**.
2. `JwtConfig.jwtSecretKey()` builds the HS256 key; `jwtEncoder()` and `jwtDecoder()` use it.
3. `SecurityConfig.securityFilterChain()` builds the filter chain (see step 3).
4. `DataSeeder.run()` creates any missing permissions, the roles `USER`, `MODERATOR` and `ADMIN`, and the admin account. Rows that already exist are left alone.

## 1. Register: `POST /api/vi/auth/register`

Public URL (listed in `SecurityConfig.PUBLIC_ENDPOINTS`).

```
AuthController.register(UserRequest)            @Valid: username ≤ 50, password ≤ 72 chars
 └► UserService.createUser(request)
     ├► UserRepository.existsByUsername()       taken → ApiException 409 CONFLICT
     ├► PasswordEncoder.encode(password)         BCrypt hash, never the plain password
     ├► RoleService.findRoles({"USER"})          Role.DEFAULT_ROLE
     └► UserRepository.save(UserAccount)
 ◄── 200 UserResponse  (no token: the user must log in)
```

## 2. Login: `POST /api/vi/auth/login`

Public URL.

```
AuthController.login(UserRequest)
 ├► AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user, pass))
 │   └► ProviderManager ► DaoAuthenticationProvider
 │       ├► UserDetailsService.loadUserByUsername()      SecurityConfig.userDetailsService()
 │       │   └► UserRepository.findByUsername()           loads roles + permissions in one query
 │       ├► checks UserAccount.isEnabled()                 false → DisabledException
 │       └► PasswordEncoder.matches(raw, bcryptHash)      false → BadCredentialsException
 │
 │   On failure an AuthenticationException is THROWN: the next line never runs, no token is made.
 │   GlobalExceptionHandler → 401 "Invalid username or password" / "Account is disabled".
 │   An unknown username also gives BadCredentialsException, so login can't reveal which users exist.
 │
 └► SessionService.login(UserAccount)                    @Transactional
     └► TokenService.issue(user, sessionId = new UUID)
         ├► randomToken()                                32 bytes from SecureRandom
         ├► RefreshTokenRepository.save(hash(refresh), sessionId, user, expiresAt = now + 7d)
         ├► JwtClaimsSet: jti, iss="demo", sub=username, iat, exp = now + 15m, sid = sessionId
         └► JwtEncoder.encode()                          HS256 signature with app.jwt.secret
 ◄── 200 TokenResponse { accessToken, tokenType: "Bearer", expiresIn, refreshToken, refreshExpiresIn }
```

The access token holds **no roles or permissions**, only who (`sub`) and which session (`sid`).

## 3. Every protected request: authentication

Example: `GET /api/vi/post` with `Authorization: Bearer <accessToken>`.

```
Spring Security filter chain (SecurityConfig.securityFilterChain)
 ├ CSRF off, no HTTP session (STATELESS): every request must carry its token
 ├ BearerTokenAuthenticationFilter         (from .oauth2ResourceServer().jwt())
 │   ├ no header on a protected URL       → InsufficientAuthenticationException → 401
 │   ├► JwtDecoder.decode()                JwtConfig: checks HS256 signature and exp
 │   │     bad signature / expired / garbage → InvalidBearerTokenException → 401
 │   └► jwtAuthenticationConverter → SecurityConfig.authenticate(jwt, users, sessions)
 │       ├► SessionValidator.isSessionActive(jwt)     = SessionService.isSessionActive
 │       │     └► RefreshTokenRepository.existsBySessionIdAndRevokedTrue(sid)
 │       │     no sid or revoked                 → InvalidBearerTokenException → 401
 │       ├► UserDetailsService.loadUserByUsername(jwt.sub)
 │       │     user deleted                      → UsernameNotFoundException  → 401
 │       ├► user.isEnabled()                     false → DisabledException     → 401
 │       └► new UserJwtAuthenticationToken(user, jwt)
 │             authorities = UserAccount.getAuthorities()   (fresh from the DB)
 │             stored in the SecurityContext for this request
 └ AuthorizationFilter: URL rules (step 4)
```

Because the session, account and permissions are read from the database **on every request**, these take effect immediately, even for access tokens that haven't expired yet:
- logout / logout-all
- disabling an account
- changing a user's roles or a role's permissions

The cost is two small queries per request.

## 4. Authorization: what the user may do

Two layers.

### Layer 1: URL rules (`SecurityConfig.securityFilterChain`)

| Rule | Effect |
|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/refresh` | `permitAll()`: no token needed |
| `/error` | `permitAll()` |
| everything else | `authenticated()`: needs a valid token (step 3) |

### Layer 2: method rules (`@PreAuthorize`, enabled by `@EnableMethodSecurity`)

`UserAccount.getAuthorities()` gives each user:
- `ROLE_<name>` for every **enabled** role, e.g. `ROLE_ADMIN`
- every **enabled** permission of those roles, e.g. `post:read`

Controllers check permissions, not role names:

| Endpoint | Required authority |
|---|---|
| `GET /post` | `post:read` |
| `POST /post` | `post:write` |
| `DELETE /post/{id}` | `post:write` or `post:moderate`, then `PostService.deletePost` checks you're the author unless you have `post:moderate` |
| `POST/DELETE /user/{id}/likes/{postId}` | `post:like`, and `{id}` must be you (`UserController.self`) |
| `GET /user` | `user:read` |
| `POST /user`, `PUT /user/{id}/roles` | `user:write` |
| `GET /role`, `GET /role/permissions` | `role:read` |
| `POST /role`, `PUT /role/{id}/permissions` | `role:write` |
| `GET /auth/me`, `POST /auth/logout`, `/auth/logout-all` | any logged-in user |

Default roles (`DataSeeder`):

| Role | Permissions |
|---|---|
| `USER` | `post:read`, `post:write`, `post:like` |
| `MODERATOR` | `USER` + `post:moderate`, `user:read` |
| `ADMIN` | all permissions |

Missing authority → `AccessDeniedException` → `GlobalExceptionHandler` → **403**.

Since checks use permissions, a new role (e.g. `EDITOR`) can be created through `POST /role` with any permissions and works without code changes.

## 5. Refresh: `POST /api/vi/auth/refresh`

Public URL, so it works after the access token has expired.

```
AuthController.refresh(RefreshRequest)
 └► SessionService.refresh(rawRefreshToken)         @Transactional(noRollbackFor = ApiException)
     ├► RefreshTokenRepository.findByTokenHash(TokenService.hash(raw))
     │     not found                     → 401 "Invalid refresh token"
     ├ usedAt != null  (token REUSED)
     │     → revokeSession(sessionId)     the whole session is signed out (kept despite the exception)
     │     → 401 "...this session has been signed out"
     ├ revoked                           → 401 "Refresh token has been revoked"
     ├ expiresAt < now                   → 401 "Refresh token has expired"
     ├ user disabled                     → 401 "Account is disabled"
     ├ token.setUsedAt(now)              this refresh token can't be used again
     └► TokenService.issue(user, SAME sessionId)
 ◄── 200 new access token + new refresh token
```

**Rotation and reuse detection.** Each refresh token works once. If a stolen token is used after the real client already used it, or the other way round, the second use is detected and the whole session is cut off for both parties.

## 6. Logout

| Endpoint | Method chain | Effect |
|---|---|---|
| `POST /auth/logout` | `AuthController.logout(UserJwtAuthenticationToken)` → `SessionService.logout(jwt)` → `revokeSession(sid)` | This session only: its access token fails at step 3 and its refresh tokens are revoked |
| `POST /auth/logout-all` | `AuthController.logoutAll(user)` → `SessionService.logoutAll(userId)` → `revokeAllForUser(userId)` | Every session of the user, on all devices |

`UserJwtAuthenticationToken` exists so the controller can get the `Jwt` (for its `sid`). Spring's standard tokens would have erased it.

## 7. Errors

Errors raised inside the security filters don't reach controllers. `SecurityConfig` routes them, through `handlerExceptionResolver`, to the same `GlobalExceptionHandler` used by controllers, so every error has the same JSON shape (`ApiError`).

| Exception | Status | Message |
|---|---|---|
| `InsufficientAuthenticationException` | 401 | Authentication required: send a token in the 'Authorization: Bearer <token>' header |
| `InvalidBearerTokenException` | 401 | Invalid token: … (bad signature, expired, signed out) |
| `UsernameNotFoundException` | 401 | Invalid token: the user no longer exists |
| `BadCredentialsException` | 401 | Invalid username or password |
| `DisabledException` | 401 | Account is disabled |
| `ApiException(UNAUTHORIZED)` | 401 | Refresh errors: … log in again |
| `AccessDeniedException` | 403 | Names the missing authority |

401 responses also carry `WWW-Authenticate: Bearer`.

## 8. Housekeeping

`RefreshTokenCleanupJob.deleteExpiredRefreshTokens()` runs every hour (`@Scheduled`, enabled by `@EnableScheduling` in `DemoApplication`) and calls `RefreshTokenRepository.deleteExpired(now)`. Revoked but unexpired rows are kept until they expire, because they are what makes a signed-out access token fail at step 3.
