# UniDoc

UniDoc is a local university document-request management system. This repository contains the Issue 1–4 backend foundation (persistence, shared API errors, registration/login and stateless JWT role security), the Issue 5 React application shell, Issue 6 student profile management, and Issue 7 administrator account management. UniDoc runs locally using Java 21 Spring Boot, React TypeScript, MySQL, and protected local storage. Features scheduled for Issue 8 onward retain their explicitly labeled placeholders until their APIs and screens are implemented.

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

The later one-click launcher will perform this step. Until the later one-click launcher is implemented, load the file manually from the repository root:

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

The response is `{ "status": "ok" }`. Authentication, student profile and administrator account management APIs are available; other business APIs are added in their approved implementation issues.

The root wrapper names `start-dev.cmd` and `reset-demo-data.cmd` are reserved for the one-click startup and reset work defined later in the implementation plan; they are intentionally not implemented in Issue 1.

## Issue 5 frontend authentication and navigation

After starting MySQL, the Spring Boot API, and Vite using the commands above, open `http://localhost:5173`:

- `/register`: students create ACTIVE STUDENT accounts with the documented password policy. Registration never signs the user in automatically.
- `/login`: students, staff, and administrators sign in using credentials stored in MySQL. Wrong passwords, inactive accounts, and validation failures show safe messages.
- `/account`: the authenticated user can read public account data and sign out. The session belongs to this browser tab (`sessionStorage`), and a refresh triggers `/api/auth/me` verification.
- `/dashboard`: redirects to the current role's entry page. All protected routes require authentication; STUDENT, STAFF, and ADMIN route families also have frontend role guards in addition to the backend's security checks.
- The `/profile` route now has the functional Issue 6 student profile form, and `/requests/new` checks saved profile completeness. Catalogue, request submission/tracking, notifications and reports retain placeholders until their assigned issues. The administrator account management page is functional in Issue 7. Those placeholders do not imply the business APIs exist.

The frontend API base URL is `VITE_API_BASE_URL` (defaults to `http://localhost:8080`). For a Windows setup, create `frontend/.env.local` with this public value as shown above. Never expose database/JWT/SMTP secrets through `VITE_` variables.

**Manual Issue 5 smoke checks:** register a student; sign in; refresh and confirm the account remains signed in; open `/account` and sign out; verify direct protected URLs redirect to login; verify a valid token revoked by incrementing `users.auth_version` or deactivating the account is rejected on the next protected API request; verify wrong-role route navigation redirects to the appropriate workspace. Repeat the public forms at mobile width and with keyboard-only navigation. Admin and staff accounts are created through the later administration/demo-seed work; they can authenticate now if created independently in MySQL with proper BCrypt hashes.

## Issue 6 student profile management

Authenticated STUDENT users can open `/profile` to view their own academic profile, complete it for the first time, and edit it again without a database administrator. Both `GET /api/student/profile` and `PUT /api/student/profile` derive ownership from the Bearer token; there is no user-ID parameter or staff/admin profile editing endpoint.

The GET response includes account full name and immutable email. If no profile exists, its academic fields and `updatedAt` are `null` and `complete` is `false`. PUT accepts full name, registration number, optional phone, faculty, department, degree programme and academic year (1–8). The service normalizes values, updates the account name and academic profile in one transaction, and returns the canonical saved profile. Duplicate registration numbers produce HTTP 409 `REGISTRATION_NUMBER_ALREADY_EXISTS`; field validation produces HTTP 400 `VALIDATION_ERROR`. A saved name change is reflected in the navigation/account view by refreshing `/api/auth/me`.

The student `/requests` landing page highlights missing profile details, while `/requests/new` checks the authoritative saved profile and provides a `/profile` link when it is incomplete. The backend also exposes `StudentProfileService.requireCompleteForSubmission(currentUserId)` for Issue 11 request creation. The request creation endpoint **does not exist yet**; once implemented in Issue 11 it must independently invoke that service and return 400 `PROFILE_INCOMPLETE` for incomplete profiles. The new-request page itself will be implemented in Issue 12.

### Manual Issue 6 verification

1. Sign in as a newly registered student. Confirm the `/requests` landing page highlights missing profile details; then open `/profile` and check the empty academic form, read-only email and profile-incomplete explanation.
2. Open `/requests/new` directly and confirm it asks for profile completion rather than displaying the planned request submission form.
3. Fill in all required fields, using a registration number unique to this student, and save. Confirm success, the saved data, and the updated full name on `/account`.
4. Reload `/profile`; verify persisted values, `complete=true`, and the Edit profile action. Edit and save with an unchanged registration number, then try a different number and academic years 1 and 8.
5. Test invalid/blank values, academic years 0 and 9, an unsupported registration character, and an invalid phone. Confirm field errors, preservation of input, and focus on an invalid field.
6. Use a second student account and attempt the first student's registration number (including lowercase). Confirm the 409 response, a registration-field error, and no disclosed owner information.
7. Attempt both endpoints without authentication (401) and using STAFF/ADMIN tokens (403). Confirm that changing the JSON `userId`, `role`, `email` or `status` cannot change account ownership/permissions.
8. Repeat the profile form and prerequisite page at widths of about 360 px, 768 px and 1366 px, including keyboard-only navigation. Finish with `backend\mvnw.cmd -f backend\pom.xml clean test`, `npm --prefix frontend run lint`, and `npm --prefix frontend run build`.

See `docs/issue-06-implementation-checklist.md` for the exact automated checks and Issue 11 dependency boundary.

## Issue 7 administrator account management

Sign in with an existing **ADMIN** account and open `/admin/users`. This route now provides server-filtered and paginated accounts, sorting, a staff/administrator creation form, activation/deactivation confirmations, and a manual password reset dialog. STAFF and STUDENT cannot enter the page or use its API endpoints. No account deletion, role editing, or forced first-login password change is implemented.

Available administrator API endpoints (all require the ADMIN Bearer token):

| Endpoint | Purpose | Success |
| --- | --- | --- |
| `GET /api/admin/users?q=&role=&status=&sort=&direction=&page=&size=` | Search, filter and paginate all accounts | 200 `PageResponse<UserSummary>` |
| `POST /api/admin/users` | Create STAFF or ADMIN with full name, email, role and initial password | 201 `UserSummary` |
| `PATCH /api/admin/users/{userId}/status` | Body `{ "status": "ACTIVE" }` or `INACTIVE` | 200 `UserSummary` |
| `POST /api/admin/users/{userId}/reset-password` | Body `{ "newPassword": "...", "confirmPassword": "..." }` | 204 empty body |

Account creation uses the same password strength rules as registration (8–72 characters with uppercase, lowercase, number and a special character), BCrypt strength 12, and normalized unique email. Both status changes and resets invalidate old tokens by increasing `authVersion`. Repeated setting of the same status does not increase it; self-deactivation is forbidden. Password resets leave inactive accounts inactive. The administrator must communicate new/initial passwords securely **outside** UniDoc: passwords are never displayed, emailed or stored in browser storage.

**Manual Issue 7 acceptance test:**

1. Build with `.\backend\mvnw.cmd -f backend\pom.xml clean test` and `.\backend\mvnw.cmd -f backend\pom.xml clean package` from the repository root on Windows; run `npm --prefix frontend ci`, `npm --prefix frontend run lint`, and `npm --prefix frontend run build`.
2. Run MySQL, backend and frontend as above; sign in as an existing ADMIN and visit `/admin/users`. Confirm the table/cards show full name, email, role, status and creation date. Search by name/email, filter STUDENT/STAFF/ADMIN and ACTIVE/INACTIVE, apply each sort/direction, clear filters and navigate pages.
3. Create one STAFF and one ADMIN with unique email addresses. Check they are ACTIVE, can log in and have only their role's workspace. Attempt the same email with different casing, weak password and mismatched/reset confirmation; expect an inline error. The creation role selector must never offer STUDENT.
4. Log in as the newly created STAFF in another browser tab. As the original admin, deactivate that account. The staff's very next authenticated request must return 403 `ACCOUNT_INACTIVE` and subsequent login must fail. Repeat deactivation and confirm no extra revocation. Reactivate; confirm a fresh login succeeds, but the old token remains invalid (401 `SESSION_REVOKED`).
5. Reset that user's password. Confirm HTTP 204, old password and old Bearer token fail (401), new password succeeds, and an inactive user's reset does not reactivate their account. Check self-deactivation returns 409 `SELF_DEACTIVATION_NOT_ALLOWED` via a direct API call. If an admin resets their own password using the UI, the frontend should immediately sign out.
6. Confirm `GET /api/admin/users` returns 401 without a token and 403 with STAFF/STUDENT tokens; repeat against mutations. Test bad `page`, `size`, `sort`, `direction` and `role` query values: expected 400. Confirm no password hash, `authVersion` or DELETE action is exposed.
7. Repeat at approximately 360 px, 768 px and 1366 px and with keyboard-only navigation. Check dialog focus, Escape/cancel behavior, validation, error recovery and horizontal/mobile layout.

See `docs/issue-07-implementation-checklist.md` for feature boundaries and local verification results.

## Repository boundaries

- `backend/` contains the only server application.
- `frontend/` contains the only browser application.
- `data/uploads/` is private runtime storage and is never served as a public static directory.
- `docs/` contains the approved project documents and implementation plan.
- `scripts/` is reserved for the documented operational PowerShell scripts.

See `docs/09-deployment-strategy.md` for the complete local deployment design and `docs/12-implementation-plan.md` for the remaining implementation sequence.
