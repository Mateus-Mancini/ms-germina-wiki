# Quickstart: User Sign-up

## Prerequisites

JDK 21 (or a compatible installed JDK compiling with Maven release 21), Maven wrapper and Docker for disposable PostgreSQL. Existing test JWT/storage placeholders are in test resources; never use production database credentials.

## Automated validation (PowerShell)

```powershell
./mvnw.cmd test '-Dtest=RegistrationServiceTest,RegistrationControllerTest,AuthServiceTest,AuthControllerTest,SecurityConfigTest,JwtConfigurationTest,JwtTokenServiceTest,JwtRoleAuthenticationConverterTest,UserServiceTest,UserControllerTest,AdminOnly*Test'
./mvnw.cmd test '-Dtest=RegistrationPostgresIntegrationTest,AuthIntegrationTest,AuthSecurityIntegrationTest,UserRepositoryPostgresTest,UserOwnProfilePostgresIntegrationTest'
./mvnw.cmd test
```

First command proves service behavior and real security-chain MVC contract. Second uses the migrated PostgreSQL schema and proves registration -> login -> profile, enum/timestamps/hash and concurrent 201/409. The full suite is the regression gate. If Docker is unavailable, report PostgreSQL validation as unverified; H2 is not a substitute.

If this Windows environment reports `Unable to establish loopback connection`, point the JVM's Unix-domain socket directory to the absolute writable `target` directory through Maven's `-DargLine=-Djdk.net.unixdomain.tmpdir=...` option. This is an execution-only environment workaround; the project still compiles for Java 21.

## Manual flow

Configure the disposable JWT variables from README and run `./mvnw.cmd spring-boot:test-run` with Docker. Use a fresh email for each valid registration.

```powershell
$signupBody = @{ name = 'Student'; email = 'student@example.com'; password = 'correct password' } | ConvertTo-Json
$signup = Invoke-RestMethod http://localhost:8080/api/auth/register -Method Post -ContentType application/json -Body $signupBody
$loginBody = @{ email = $signup.email; password = 'correct password' } | ConvertTo-Json
$login = Invoke-RestMethod http://localhost:8080/api/auth/login -Method Post -ContentType application/json -Body $loginBody
Invoke-RestMethod http://localhost:8080/api/users/me -Headers @{ Authorization = "Bearer $($login.accessToken)" }
```

Expected: registration 201 with safe profile, login 200 with token, own profile 200 with same id. Repeat registration: 409; invalid/unknown fields: 400. See [contract](contracts/registration.md) for exact limits and examples. No frontend, email verification or automatic login is included.
