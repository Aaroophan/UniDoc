# UniDoc

UniDoc is a local university document-request management system. This repository contains the completed Issue 1–4 backend foundation (persistence, shared API errors, registration/login and stateless JWT role security), plus the Issue 5 frontend authentication and application shell. UniDoc runs locally using Java 21 Spring Boot, React TypeScript, MySQL, and protected local storage. Features scheduled for Issues 6 and later are displayed as honest route placeholders until their APIs and screens are implemented.

## Prerequisites

- Windows 10 or 11
- Java Development Kit 21
- Node.js supported by the selected Vite release and npm
- MySQL Server 26.7.0 and either MySQL Workbench or the MySQL command-line client
- Git and internet access for the initial dependency download

Check the installed tools in PowerShell:

```powershell
java -version
node --version
npm --version
mysql --version
git --version
```

The Java output must identify version 21. Maven does not need to be installed globally because the backend includes the Maven Wrapper.

## 1. Create local configuration

From the repository root:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and replace the database password and JWT placeholder. Generate a 32-byte Base64 JWT secret with:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Do not commit `.env`, expose the JWT secret, or include a Gmail App Password in screenshots. `VITE_API_BASE_URL` is the only browser-visible environment value.

## 2. Prepare MySQL

Run the following statements while connected as a local MySQL administrator. Choose a strong local password and put the same value in `UNIDOC_DB_PASSWORD`.

```sql
CREATE DATABASE IF NOT EXISTS unidoc
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'unidoc_app'@'localhost'
  IDENTIFIED BY 'replace-with-a-strong-local-password';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON unidoc.* TO 'unidoc_app'@'localhost';

FLUSH PRIVILEGES;
```

Verify the account and schema before starting the application:

```sql
SHOW GRANTS FOR 'unidoc_app'@'localhost';
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'unidoc';
```

Test the application account separately:

```powershell
mysql --host=localhost --user=unidoc_app --password unidoc
```

The application account must not be MySQL root and must have access only to the `unidoc` schema. The configured `useSSL=false` is limited to this same-machine local setup.

## 3. Load `.env` into the current PowerShell process

The later one-click launcher will perform this step. For the Issue 1 baseline, load the file manually from the repository root:

```powershell
Get-Content .env |
  Where-Object { $_ -and -not $_.StartsWith('#') } |
  ForEach-Object {
    $name, $value = $_.Split('=', 2)
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
  }
```

Create the frontend's ignored local environment file from the public value:

```powershell
Set-Content frontend/.env.local "VITE_API_BASE_URL=$env:VITE_API_BASE_URL"
```

## 4. Build and test

Run both builds independently from the repository root:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml clean package
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run build
```

The packaged backend is `backend/target/unidoc-backend-0.0.1-SNAPSHOT.jar`.

## 5. Start locally

Start the backend in the configured PowerShell process:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

Open a second PowerShell window, load `.env` as shown above, and start the frontend:

```powershell
npm --prefix frontend run dev
```

Open `http://localhost:5173`. Verify the public API health check separately:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

The response is `{ "status": "ok" }`. Authentication routes are available; the remaining business APIs are added in their approved implementation issues.

The root wrapper names `start-dev.cmd` and `reset-demo-data.cmd` are reserved for the one-click startup and reset work defined later in the implementation plan; they are intentionally not implemented in Issue 1.

## Issue 5 frontend authentication and navigation

After starting MySQL, the Spring Boot API, and Vite using the commands above, open `http://localhost:5173`:

- `/register`: students create ACTIVE STUDENT accounts with the documented password policy. Registration never signs the user in automatically.
- `/login`: students, staff, and administrators sign in using credentials stored in MySQL. Wrong passwords, inactive accounts, and validation failures show safe messages.
- `/account`: the authenticated user can read public account data and sign out. The session belongs to this browser tab (`sessionStorage`), and a refresh triggers `/api/auth/me` verification.
- `/dashboard`: redirects to the current role's entry page. All protected routes require authentication; STUDENT, STAFF, and ADMIN route families also have frontend role guards in addition to the backend's security checks.
- Routes for profile, catalogue, requests, notifications, managed accounts and reports are already registered but intentionally show placeholders until their assigned later implementation issues. Those placeholders do not imply the related business APIs exist.

The frontend API base URL is `VITE_API_BASE_URL` (defaults to `http://localhost:8080`). For a Windows setup, create `frontend/.env.local` with this public value as shown above. Never expose database/JWT/SMTP secrets through `VITE_` variables.

**Manual Issue 5 smoke checks:** register a student; sign in; refresh and confirm the account remains signed in; open `/account` and sign out; verify direct protected URLs redirect to login; verify a valid token revoked by incrementing `users.auth_version` or deactivating the account is rejected on the next protected API request; verify wrong-role route navigation redirects to the appropriate workspace. Repeat the public forms at mobile width and with keyboard-only navigation. Admin and staff accounts are created through the later administration/demo-seed work; they can authenticate now if created independently in MySQL with proper BCrypt hashes.

## Repository boundaries

- `backend/` contains the only server application.
- `frontend/` contains the only browser application.
- `data/uploads/` is private runtime storage and is never served as a public static directory.
- `docs/` contains the approved project documents and implementation plan.
- `scripts/` is reserved for the documented operational PowerShell scripts.

See `docs/09-deployment-strategy.md` for the complete local deployment design and `docs/12-implementation-plan.md` for the remaining implementation sequence.
