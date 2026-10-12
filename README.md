# UniDoc

UniDoc is a local university document-request management system built with Java 21/Spring Boot, React/TypeScript, MySQL and protected local file storage. This source implements Issues 1–14 of the approved implementation plan: authentication, profiles, account/catalogue administration, notifications, protected files, student requests and tracking, staff work list and review entry, and **Issue 14 staff decisions plus the additional-information response cycle**. Electronic issued-document delivery, physical collection completion, administrator reporting, deterministic demo data and release completion belong to later issues.


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

The response is `{ "status": "ok" }`. Authentication, student profile, administrator account management and document catalogue APIs are available; other business APIs are added in their approved implementation issues.

The root wrapper names `start-dev.cmd` and `reset-demo-data.cmd` are reserved for the one-click startup and reset work defined later in the implementation plan; they are intentionally not implemented in Issue 1.

## Issue 5 frontend authentication and navigation

After starting MySQL, the Spring Boot API, and Vite using the commands above, open `http://localhost:5173`:

- `/register`: students create ACTIVE STUDENT accounts with the documented password policy. Registration never signs the user in automatically.
- `/login`: students, staff, and administrators sign in using credentials stored in MySQL. Wrong passwords, inactive accounts, and validation failures show safe messages.
- `/account`: the authenticated user can read public account data and sign out. The session belongs to this browser tab (`sessionStorage`), and a refresh triggers `/api/auth/me` verification.
- `/dashboard`: redirects to the current role's entry page. All protected routes require authentication; STUDENT, STAFF, and ADMIN route families also have frontend role guards in addition to the backend's security checks.
- The `/profile` route now has the functional Issue 6 student profile form, and `/requests/new` checks saved profile completeness. Document catalogue browsing and administrator document-type management are functional in Issue 8; the student request submission/tracking pages are functional as of Issue 12, while staff workflow and reports retain placeholders; notifications are functional as of Issue 9. The administrator account management page is functional in Issue 7. Those placeholders do not imply the business APIs exist.

The frontend API base URL is `VITE_API_BASE_URL` (defaults to `http://localhost:8080`). For a Windows setup, create `frontend/.env.local` with this public value as shown above. Never expose database/JWT/SMTP secrets through `VITE_` variables.

**Manual Issue 5 smoke checks:** register a student; sign in; refresh and confirm the account remains signed in; open `/account` and sign out; verify direct protected URLs redirect to login; verify a valid token revoked by incrementing `users.auth_version` or deactivating the account is rejected on the next protected API request; verify wrong-role route navigation redirects to the appropriate workspace. Repeat the public forms at mobile width and with keyboard-only navigation. Admin and staff accounts are created through the later administration/demo-seed work; they can authenticate now if created independently in MySQL with proper BCrypt hashes.

## Issue 6 student profile management

Authenticated STUDENT users can open `/profile` to view their own academic profile, complete it for the first time, and edit it again without a database administrator. Both `GET /api/student/profile` and `PUT /api/student/profile` derive ownership from the Bearer token; there is no user-ID parameter or staff/admin profile editing endpoint.

The GET response includes account full name and immutable email. If no profile exists, its academic fields and `updatedAt` are `null` and `complete` is `false`. PUT accepts full name, registration number, optional phone, faculty, department, degree programme and academic year (1–8). The service normalizes values, updates the account name and academic profile in one transaction, and returns the canonical saved profile. Duplicate registration numbers produce HTTP 409 `REGISTRATION_NUMBER_ALREADY_EXISTS`; field validation produces HTTP 400 `VALIDATION_ERROR`. A saved name change is reflected in the navigation/account view by refreshing `/api/auth/me`.

The student `/requests` page lists their requests, while `/requests/new?documentTypeId=<id>` checks the authoritative saved profile and provides a `/profile` link when it is incomplete. The backend also exposes `StudentProfileService.requireCompleteForSubmission(currentUserId)` for Issue 11 request creation. The Issue 11 request creation endpoint now independently enforces profile completeness and returns 400 `PROFILE_INCOMPLETE` for an incomplete profile. The browser request submission/tracking pages were completed in Issue 12.

### Manual Issue 6 verification

1. Sign in as a newly registered student. Confirm `/requests` has a first-request empty state; then open `/profile` and check the empty academic form, read-only email and profile-incomplete explanation.
2. Open `/catalogue`, select an active document type and follow its Request action. Confirm `/requests/new?documentTypeId=<id>` asks for profile completion before showing the submission form. Without a selected type, the page asks you to choose one first.
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

## Issue 8 document catalogue

All authenticated users can now open `/catalogue` and see only **active** document types, sorted by name, including their supporting-document instructions, delivery methods and collection information when physical delivery is offered. STUDENT users can choose **Request this document**, which navigates to `/requests/new?documentTypeId=...`. The request submission API is Issue 11 and the complete Issue 12 frontend provides a live request form with authoritative profile and catalogue checks.

An authenticated **ADMIN** opens `/admin/document-types` to create, edit, filter, paginate, activate and deactivate entries. Only admins can use the management APIs. There is no hard deletion. The first five proposal document types are created by the deterministic seed in **Issue 19**, so an empty catalogue before manual creation is expected.

| Endpoint | Role | Response / behavior |
| --- | --- | --- |
| `GET /api/document-types` | STUDENT, STAFF, ADMIN | 200 array of active types ordered by name |
| `GET /api/document-types/{id}` | STUDENT, STAFF, ADMIN | 200 active detail, otherwise 404 `DOCUMENT_TYPE_NOT_FOUND` |
| `GET /api/admin/document-types?q=&active=&page=&size=&sort=&direction=` | ADMIN | 200 `PageResponse<AdminDocumentTypeResponse>` including inactive |
| `POST /api/admin/document-types` | ADMIN | 201 new admin document type |
| `GET /api/admin/document-types/{id}` | ADMIN | 200 editable details including inactive |
| `PUT /api/admin/document-types/{id}` | ADMIN | 200 full replacement of editable details |
| `PATCH /api/admin/document-types/{id}/status` | ADMIN | 200, body `{ "active": false }` or `true` |

The creation/PUT JSON body contains `code`, `name`, `description`, `requirements`, `supportingDocumentsRequired`, `electronicAvailable`, `physicalAvailable`, optional `collectionLocation`, optional `collectionInstructions`, and `active`. All Boolean flags must be supplied. A document type must have at least one delivery method; physical delivery requires a collection location. Code/name duplicates return 409 `DOCUMENT_TYPE_CODE_EXISTS`/`DOCUMENT_TYPE_NAME_EXISTS`; invalid fields return 400 `VALIDATION_ERROR`. Codes normalize to uppercase and names compare case-insensitively. Admin `q` searches code/name only, has a 100-character maximum, and only `name`, `code`, `createdAt` and `active` are allowed sort properties. Page starts at 0 and size is 1–100.

**Manual Issue 8 acceptance:**

1. Run `backend\mvnw.cmd -f backend\pom.xml clean test`, `clean package`, `npm --prefix frontend ci`, `npm --prefix frontend run lint`, and `npm --prefix frontend run build`. Start local MySQL, the API and Vite. Sign in as an existing ADMIN.
2. Open `/admin/document-types`. Confirm the unfiltered list can be empty, filters and pagination render correctly, and only ADMIN can see management controls.
3. Create an electronic-only **active** document type, omitting collection location. Confirm it appears on `/catalogue` with *Electronic* only and no collection guidance.
4. Create a physical-only type with a required nonblank collection location, optional instructions, and supporting documents required. Confirm it displays *Physical* only, the location and instructions, and a statement that supporting files are required.
5. Create a third type supporting both methods and set it **inactive**. Confirm admin listing includes it but `/catalogue` and the shared detail API do not show it. Activate it with confirmation, then verify it appears. Deactivate again and verify shared detail returns 404. Confirm no existing request record is deleted or rewritten.
6. Edit an existing type. Change the code casing, name, description, requirements and collection guidance; confirm the new saved values are shown, the original creation timestamp is preserved and the audit actor is stored internally. Editing an entry with unchanged code/name must succeed.
7. Attempt the same code and name with different letter casing (409 with the respective duplicate code), invalid code characters, a too-short description, both methods disabled, missing physical collection location, and overlong instructions (400 field errors). Confirm the form preserves input and focuses an invalid field.
8. Search by code/name, apply active and inactive filters, change sort/direction, clear filters and navigate pages. Try `size=101` or `sort=passwordHash` directly against the API: expect 400 `INVALID_PARAMETER`.
9. Sign in separately as STAFF and STUDENT. Confirm both can browse `/catalogue`, both receive 403 on admin endpoints and cannot enter `/admin/document-types`. Confirm unauthenticated catalogue reads return 401. Only STUDENT should see *Request this document*; no role should see a delete action.
10. Verify at approximately 360, 768 and 1366 px and with keyboard-only navigation. Check the modal scrolls and traps focus, Escape/cancel behavior, required-field feedback, long description wrapping, status confirmation and error/retry states.

See `docs/issue-08-implementation-checklist.md` for the exact tests and the later request-snapshot boundary.

## Repository boundaries

- `backend/` contains the only server application.
- `frontend/` contains the only browser application.
- `data/uploads/` is private runtime storage and is never served as a public static directory.
- `docs/` contains the approved project documents and implementation plan.
- `scripts/` is reserved for the documented operational PowerShell scripts.

See `docs/09-deployment-strategy.md` for the complete local deployment design and `docs/12-implementation-plan.md` for the remaining implementation sequence.


## Issue 9 - In-app notifications and optional Gmail email

The authenticated `/notifications` page supports read-state/event-type filters, newest-first pagination, marking one or all notifications read, and secure internal navigation links. The application header/sidebar shows the unread count, refreshed after read actions and approximately once per minute. Each authenticated role may access only its own notifications.

| Endpoint | Method | Result |
| --- | --- | --- |
| `/api/notifications?read=false&type=REQUEST_SUBMITTED&page=0&size=20` | GET | Owned `PageResponse<NotificationResponse>` |
| `/api/notifications/unread-count` | GET | `{ "count": 0 }` when empty |
| `/api/notifications/{id}/read` | POST | Updated notification; no-op when already read |
| `/api/notifications/read-all` | POST | 204; reads only current recipient's unread rows |

`NotificationService.create(NotificationCommand)` is an internal workflow-only operation that must be called inside the existing database transaction. It persists the notification with the workflow and emits a small event; `EmailNotificationListener` handles the event asynchronously only after commit. There is deliberately **no public endpoint for creating notifications**. Live request-transition notifications will be produced by Issues 11 and 14-16, and demo notifications by Issue 19.

Email stays disabled by default (`UNIDOC_EMAIL_ENABLED=false`); core notifications work without SMTP credentials. To optionally demonstrate Gmail, supply a sender address in `UNIDOC_EMAIL_FROM`, Gmail credentials in `UNIDOC_SMTP_USERNAME` and `UNIDOC_SMTP_APP_PASSWORD`, retain the configured `smtp.gmail.com:587`, SMTP AUTH and STARTTLS settings, then set `UNIDOC_EMAIL_ENABLED=true` and restart. The sender validates enabled configuration at startup. Do not share or commit the App Password. Mail is plain text, includes an authenticated frontend link, and carries neither files nor credentials. A delivery failure is a warning; the committed request and notification persist. No retries or delivery-status records are stored.

See `docs/issue-09-implementation-checklist.md` for focused tests, rollback/SMTP verification, and current feature boundaries.

## Issue 10 - Protected local file storage

Issue 10 provides the server-side storage building blocks for attachment submission (Issue 11), information-response attachments (Issue 14), and issued PDF delivery (Issue 15). **No file upload or download HTTP endpoint is introduced in Issue 10**: access control and request lifecycle checks must be performed by the later request services before invoking the storage service. The existing document-request and file-metadata entities are reused, without altering the eight-table data model.

- `StoragePaths`: one initialized, canonical, writable private root plus `.tmp`; request-specific `supporting`, `additional-information`, and `issued` directories; restricted relative-path resolution with traversal and symlink rejection.
- `FileStorageService.validateSupportingFiles(files, required)`: validates zero-to-three optional/required PDF/JPEG/PNG files, each at most 5 MiB by default, including size, extension, declared MIME type, and signature.
- `FileStorageService.validateIssuedPdf(file)`: validates one nonempty PDF, at most 10 MiB by default, without accepting image files.
- `FileStorageService.stage(validated, StorageCategory)`: copies bounded, rechecked bytes into private `.tmp` under generated UUID names with create-new semantics. A failed batch removes its own successfully staged items.
- `FileStorageService.finalizeFiles(operation, requestId)`: safely promotes files into `requests/{id}/{category}` without overwriting existing content. It returns `StoredFile` values with sanitized original display name, server-generated name, forward-slash relative path, validated MIME, and size for the caller's database transaction.
- `FileStorageService.cleanup(operation)`: best-effort compensation for only that operation's newly staged/finalized files, including an attempt at every tracked file when a deletion fails. **The calling service must invoke this on transaction/commit failure.** Do not run it after a successful committed request.
- `FileStorageService.readAuthorized(relativePath, contentType, sizeBytes)` and `FileDownloadHeaders.forDownload(originalFilename, contentType, sizeBytes)`: call **only after** authenticating the role, checking ownership/authorization of the database record, and matching attachment/request IDs. The read is fully buffered within the configured bound for later first-download completion, and the headers enforce an attachment, `nosniff`, and private/no-store caching.

Configuration retains `UNIDOC_STORAGE_ROOT=./data/uploads`, `UNIDOC_SUPPORTING_MAX_BYTES=5242880`, and `UNIDOC_ISSUED_MAX_BYTES=10485760`; the server's coarse multipart limits remain **10 MB/file and 20 MB/request**. Storage size configuration must be positive and no more than 10 MiB, ensuring it cannot exceed the server's outer per-file bound. The root and `.tmp` must be writable protected directories, never `frontend/public` or backend static resources. The documented default `./data/uploads` resolves under the repository root even if Maven is launched from `backend/`; custom relative roots remain relative to the process working directory. Existing files previously created in `backend/data/uploads` are **not** automatically moved or deleted; verify the configured root before using real uploads. The application does not scan or delete already-finalized files on startup and does not provide malware scanning.

**Integration outline for Issues 11, 14 and 15:** validate all multipart files first; stage them; perform the database transaction, using `finalizeFiles` only after the request ID and workflow prerequisites have been established; persist every `StoredFile` in that same transaction. Wrap the transactional service invocation in an outer `try/catch` that calls `cleanup(operation)` on *all* failure outcomes, including database commit failures that occur after the transactional method returns; then rethrow the original exception. On success, leave finalized files untouched. A failed email must not undo a committed file and record.

**Windows verification:** from the project root with the usual `.env` loaded, run `./backend/mvnw.cmd -f backend/pom.xml "-Dtest=FileStorageServiceTests,StoragePathsTest,StorageConfigurationTests,SanitizedFileNamesTests,FileDownloadHeadersTests,FileDownloadHttpTests" test` (PowerShell supports `./`; use `.\backend\mvnw.cmd` if preferred), then the full `clean test` / `clean package` and frontend `lint` / `build` commands shown earlier. All file tests use temporary directories rather than the configured real upload root. Confirm that placing a marker file under `data/uploads/` cannot be retrieved by a direct `/data/uploads/...` or `/uploads/...` URL. Do not test by posting secrets or confidential personal documents. For details see `docs/issue-10-implementation-checklist.md`.


## Issue 11 — Student request submission and tracking backend

The following STUDENT-only endpoints are implemented; every call requires an active student Bearer token and **all record/file access is owner scoped**:

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/student/requests` | Create request with multipart JSON `request` and zero to three `files` (201) |
| `GET` | `/api/student/requests` | Own requests, optional `status`, `deliveryMethod`, `sort`, `direction`, `page`, `size` |
| `GET` | `/api/student/requests/{requestId}` | Own request, attachment metadata and ordered status history |
| `GET` | `/api/student/requests/{requestId}/attachments/{attachmentId}` | Authorized PDF/JPEG/PNG attachment download |

**Submission validation:** Complete student profile; active document type; delivery method offered by that type; purpose 5–1000 characters; required date today or later on the local host calendar; zero-to-three PDF/JPEG/PNG files at 5 MiB or less each, with at least one file if the type requires supporting evidence. Use `request` as an `application/json` multipart part. Upload files as parts all named `files`.

**Successful submission:** Returns 201 with `RequestDetailResponse` (including `id`, `requestNumber`, `version`, immutable catalogue snapshot, student summary, attachments, initial history and available actions); the request starts at `SUBMITTED`. A student confirmation and one notification per active STAFF user are stored in the same transaction; optional email is sent after commit. Files receive UUID storage names and protected request-relative paths in the database, but their paths are never included in responses. A failed transaction triggers best-effort cleanup of newly staged/finalized bytes. There is no student edit/cancel endpoint.

**Postman manual smoke (not a UI test):**

1. Sign in as a STUDENT using `POST http://localhost:8080/api/auth/login`, save the returned JWT, and finish `PUT /api/student/profile` if it is incomplete.
2. Obtain an active document-type ID from `GET /api/document-types` using the same Bearer token. Check its available delivery methods and `supportingDocumentsRequired` flag.
3. In Postman, create `POST http://localhost:8080/api/student/requests`. Choose **Body → form-data**. Add key `request` as a text JSON part with media type `application/json`, containing e.g. `{"documentTypeId":1,"purpose":"Required for university administration.","requiredDate":"2026-10-20","deliveryMethod":"ELECTRONIC"}` (adjust the ID/date/method); add up to three keys named `files` of type **File**. Postman must send a proper `application/json` content type for the JSON part. Do not manually set the multipart boundary.
4. Verify HTTP 201, `UDR-` request number, `SUBMITTED`, the initial history entry and attachment metadata (no private paths). Repeat with an optional-attachment type and a required-attachment type.
5. Use the created ID with `GET /api/student/requests` and `GET /api/student/requests/{id}`. Verify filters/page/sort, and download one returned attachment using `GET /api/student/requests/{id}/attachments/{attachmentId}` with the same Bearer token.
6. Test a second STUDENT: the first student's request detail and attachment must return 404. Anonymous returns 401; STAFF/ADMIN returns 403. Invalid fields/files must fail without creating a request. Student PUT/PATCH/DELETE endpoints are intentionally absent.
7. Use the notifications endpoint to verify a student confirmation and an active-staff notification. Leave `UNIDOC_EMAIL_ENABLED=false` for normal local testing; if true, emails are best effort and occur after commit.

Run the Issue 11 focused suite and full backend regression on Windows:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml "-Dtest=RequestTransitionValidatorTests,DocumentRequestServiceTests,StudentRequestHttpTests,RequestStorageCompensationTests,RequestRollbackIntegrationTests" test
.\backend\mvnw.cmd -f backend\pom.xml clean test
.\backend\mvnw.cmd -f backend\pom.xml clean package
npm --prefix frontend run lint
npm --prefix frontend run build
```

**Scope boundary:** Issue 11 added the backend; Issue 12 has now implemented the form/list/detail React screens. The RESPOND action and staff/issued-document delivery mutations are represented in the state machine but their HTTP commands belong to Issues 13–16.

## Issue 12 - Student catalogue, submission, and tracking UI

An authenticated **STUDENT** can browse `/catalogue`, choose **Request this document**, and continue to `/requests/new?documentTypeId=<id>`. The page retrieves the selected active type and the student's saved profile again from the API. It never relies on the query string or login state to prove an active document type or a complete profile. An unavailable type links back to the catalogue; an incomplete profile links to `/profile`. STAFF and ADMIN cannot enter student pages.

The new request form shows the catalogue requirements, supporting-file condition, delivery options, and any physical collection instructions. It validates purpose (5-1000 characters), required date (today or later in local time), enabled delivery method and PDF/JPEG/PNG files (maximum three, maximum 5 MiB each). These are *usability checks*; the backend still validates content, signatures, storage, role, and ownership. Submission sends exactly one `request` application/json Blob and repeated `files` entries with a browser-generated multipart boundary. A successful HTTP 201 opens `/requests/{id}` with a success message and canonical UDR request number. While a submission is pending, a synchronous guard prevents double posts. If the connection fails without confirming whether the POST committed, the form pauses further submits and directs the student to check My requests before explicitly choosing to try again.

`/requests` lists only the authenticated student's own requests, newest first, with status/delivery filters, a clear action, and server-side zero-based pagination (10 per page). Filters remain in the URL while paging and reset the page number when changed. The unfiltered empty state goes to the catalogue. `/requests/:requestId` displays catalogue-name snapshot, immutable request details, status explanation, delivery/collection information, additional-information and rejection notes, issued metadata if returned, supporting files, and backend-ordered status history. A missing or other student's ID produces the same not-found view. The frontend exposes no request edit or cancellation command.

Attachment downloads use the **owner-scoped Bearer-authenticated** `/api/student/requests/{id}/attachments/{attachmentId}` endpoint and save a Blob with a sanitized response/metadata filename. The browser revokes the temporary Blob URL. Each row has independent pending and error states. No protected file path or public upload URL is exposed. The existing Spring Security CORS configuration exposes `Content-Disposition`; the existing file handler returns `nosniff` and private/no-store headers. Responding to staff information requests and downloading the issued final document are *not yet implemented* (Issues 14 and 15); the detail screen displays their state but does not create dead or unauthorized buttons.

### Verification (Windows PowerShell, from repository root)

```powershell
.\backend\mvnw.cmd -f backend\pom.xml "-Dtest=RequestTransitionValidatorTests,DocumentRequestServiceTests,StudentRequestHttpTests,RequestStorageCompensationTests,RequestRollbackIntegrationTests" test
.\backend\mvnw.cmd -f backend\pom.xml clean test
.\backend\mvnw.cmd -f backend\pom.xml clean package
npm --prefix frontend ci
npm --prefix frontend run test:requests
npm --prefix frontend run lint
npm --prefix frontend run build
```

The new `test:requests` script runs pure TypeScript validation and download-filename tests via Node.js 22+ type stripping. The standard Vite/oxlint commands continue to run on Windows with npm's locally installed native binaries.

For manual end-to-end verification, start MySQL, load the normal private `.env` into the shell, run the backend (`.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run`) and run Vite (`npm --prefix frontend run dev`) in another shell. Sign in as a STUDENT, complete `/profile`, create an active document type as ADMIN if none exists, then perform both an optional-file electronic request and a required-file physical request (use a small **test** PDF/JPEG/PNG). Confirm the UDR number, owner-scoped list/detail/history, required and optional files, downloading the exact file, and refresh persistence. Use a second student login and an owned-by-another-student direct ID to verify identical 404 behavior. Check error/retry/empty states, invalid/past date, inactive catalogue type, file count/type/size, and duplicate-submission prevention. Repeat the screens at **360 px, 768 px and 1366 px** and with keyboard-only navigation. Do not use sensitive real student files for testing.

See `docs/issue-12-implementation-checklist.md` for the full acceptance matrix and the remaining manual UAT steps. The eleven original architecture documents and Issue 11 request backend are unchanged.

## Issue 13 - Staff work list, request details, and start review

All **ACTIVE STAFF** accounts share one work list at `/staff/requests`. Staff can search by request number, student name, email, or registration number; filter by exact status, document type ID, delivery, submitted date range (both dates inclusive, UTC), and required date; sort by the documented fields; and paginate. The list uses database-side filtering and ordering, with deterministic ID tie-breaking. The document type shown in results and details is the *submission-time snapshot*, not a later catalogue edit. Opening the detail does **not** change the status. Missing student profile data is shown as unavailable rather than breaking the view.

| Endpoint | Access | Result |
| --- | --- | --- |
| `GET /api/staff/requests?q=&status=&documentTypeId=&deliveryMethod=&submittedFrom=&submittedTo=&requiredDate=&sort=&direction=&page=&size=` | ACTIVE STAFF | `PageResponse<RequestSummary>` for all matching requests |
| `GET /api/staff/requests/{requestId}` | ACTIVE STAFF | Complete staff detail including academic profile, request snapshots, supporting files, issued-document metadata (if present), history, available actions, and version |
| `POST /api/staff/requests/{requestId}/start-review` | ACTIVE STAFF | `{ "expectedVersion": 1 }` -> new staff detail after SUBMITTED → UNDER_REVIEW |
| `GET /api/staff/requests/{requestId}/attachments/{attachmentId}` | ACTIVE STAFF | Private attachment download after validating both IDs, using safe filename/content headers |

The work-list API defaults to `createdAt desc`, page 0, size 20 (frontend uses size 10), with allowed sort keys `createdAt`, `updatedAt`, `requiredDate`, `status`, and `requestNumber`. Size must be 1–100; `q` at most 120 characters; documentTypeId positive; `submittedFrom` must not be after `submittedTo`. Invalid filters receive 400 `INVALID_PARAMETER`. Unknown request is 404 `REQUEST_NOT_FOUND`, unknown or request-mismatched attachment is 404 `ATTACHMENT_NOT_FOUND`. STUDENT and ADMIN receive 403; unauthenticated requests receive 401. Inactive accounts cannot access staff endpoints.

Start Review is the **only** workflow mutation introduced in Issue 13. It requires a freshly fetched `version` in `expectedVersion`, compares it before modifying the request, checks the current role/status/delivery action and the allowed state edge, and relies on the existing JPA `@Version` check to protect concurrent database transactions. Successful review updates the status to UNDER_REVIEW and appends a single `Review started.` history event with the staff actor in the same transaction; no notification/email is created for this transition. Explicit or database-level stale writes map to 409 `REQUEST_VERSION_CONFLICT`; an unsupported transition maps to 409 `INVALID_REQUEST_TRANSITION`. The frontend shows the conflict, reloads, and **never retries a mutation automatically**. An unconfirmed network result is treated as unknown; refresh and inspect the latest status/history before choosing another action. If the recovery fetch fails, the frontend blocks further mutation until a subsequent explicit refresh succeeds. Issue 14 now enables the four review commands; the remaining delivery-specific actions remain informational until Issues 15-16.

Staff downloads require authentication for every request and never serve files from static storage. The browser saves protected blobs with safe response filenames and revokes temporary object URLs. The student download path was refactored to use the same common browser save helper, retaining owner-specific endpoint authorization. Four previous React hook warnings were addressed by stable pagination callbacks, derived invalid-route states, and a keyed student request form.

### Issue 13 verification (Windows PowerShell, from the project root)

```powershell
.\backend\mvnw.cmd -f backend\pom.xml "-Dtest=StaffRequestServiceTests,StaffRequestHttpTests,StaffRequestOptimisticLockTests,RequestTransitionValidatorTests" test
.\backend\mvnw.cmd -f backend\pom.xml clean test
.\backend\mvnw.cmd -f backend\pom.xml clean package
npm --prefix frontend ci
npm --prefix frontend run test:requests
npm --prefix frontend run lint
npm --prefix frontend run build
```

For manual acceptance, start local MySQL and load the private `.env` using the existing documented workflow, then start Spring Boot and Vite. Submit requests as a STUDENT. Open two distinct ACTIVE STAFF sessions at `/staff/requests` and verify identical records, text/date/type/status/delivery filters, clearing, and pagination. Both staff should open the same SUBMITTED request and note its version. Let the first staff member select **Start review**, confirm the status/history/version changed, then have the second staff attempt their still-open Start Review: the API must reject their stale version and the browser must show a conflict and refreshed UNDER_REVIEW state without creating a duplicate history row. Check supporting file download and request/attachment mismatch, role denial for STUDENT/ADMIN, inactive staff denial, long file/name wrapping, and 360/768/1366-pixel layouts. Do not use real personal records for test data.

See `docs/issue-13-implementation-checklist.md` for the implementation coverage and verification status. The 11 architecture documents remain unchanged.

## Issue 14 - Review decisions and the additional-information cycle

From the staff detail `/staff/requests/:id`, ACTIVE STAFF can request additional information, approve, reject (with a mandatory reason), and start processing an approved request. Review decisions are only available when the backend advertises the corresponding action. Each mutation checks `expectedVersion` and the request status again on the server. Success updates the request, append-only history, student in-app notification and best-effort after-commit email. Approval and starting processing are **separate** operations. REJECTED is terminal.

| Endpoint | Caller | Command/body | Effect |
| --- | --- | --- | --- |
| `POST /api/staff/requests/{id}/request-information` | STAFF | `{"message":"Please attach your student ID.","expectedVersion":1}` | UNDER_REVIEW -> ADDITIONAL_INFORMATION_REQUIRED |
| `POST /api/staff/requests/{id}/approve` | STAFF | `{"expectedVersion":1}` | UNDER_REVIEW -> APPROVED |
| `POST /api/staff/requests/{id}/reject` | STAFF | `{"reason":"The requirements were not met.","expectedVersion":1}` | UNDER_REVIEW -> REJECTED |
| `POST /api/staff/requests/{id}/start-processing` | STAFF | `{"expectedVersion":2}` | APPROVED -> PROCESSING |
| `POST /api/student/requests/{id}/information-response` | Owning STUDENT | multipart `response` JSON part with `{"message":"I attached the requested file.","expectedVersion":2}` and optional 0-3 `files` | ADDITIONAL_INFORMATION_REQUIRED -> SUBMITTED |

Staff message/rejection reason must contain 5-2000 characters after trimming; the student's written response must contain 2-2000. `expectedVersion` is mandatory on staff commands and optional on student response for compatibility with the documented message-only payload; the browser supplies the version it last observed. Additional response files use the existing 5 MiB/signature-checked PDF/JPEG/PNG storage and are immutable after submission. The current staff question is cleared after response; both question and response persist in request history. The student gets a confirmation and all ACTIVE STAFF accounts receive a new-information notification.

Workflows maintain atomic SQL updates for status/history/notifications and best-effort compensating cleanup for new files if the transaction or commit fails. `REQUEST_VERSION_CONFLICT` and `INVALID_REQUEST_TRANSITION` return HTTP 409; neither client automatically resends an uncertain mutation. In-app notifications are authoritative; SMTP errors do not roll back successful actions.

Run the Windows tests and manual scenarios documented in `docs/issue-14-implementation-checklist.md`, including both delivery methods, a repeated information cycle, valid/invalid PDF/JPEG/PNG, rejection as a terminal state, simultaneous staff decisions, and 360/768/1366-pixel screen checks. **Linux sandbox verification:** Java production source compilation, TypeScript compilation, and all 28 Node frontend tests passed; new backend JUnit tests, full Maven package, native oxlint/Vite and real MySQL/browser UAT still require execution on the Windows development laptop.
