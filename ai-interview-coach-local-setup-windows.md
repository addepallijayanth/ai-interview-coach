# AI Interview Coach: Local Setup Guide (Windows)

How to run the project on a Windows laptop after downloading the code, step by step.
Works with the **free mock AI**, so no paid API key is needed.

---

## 0. What you need to install (one time)

Open **PowerShell** (press the Windows key, type `PowerShell`, press Enter) and run:

```powershell
winget install Git.Git
winget install Docker.DockerDesktop
```

Optional, for developers:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install OpenJS.NodeJS.LTS
winget install JetBrains.IntelliJIDEA.Community
winget install Postman.Postman
winget install GitHub.cli
winget install ngrok.ngrok
```

| Tool | Needed? | Why |
|---|---|---|
| Git | Yes | Download the code |
| Docker Desktop | Yes | Runs everything: database, backend, frontend, email inbox, mock AI |
| Java 21 (Temurin) | Optional | Run the backend or tests without Docker, and in IntelliJ |
| Node.js | Optional | Run the frontend without Docker |
| IntelliJ IDEA | Optional | Read and edit the code |
| Postman | Optional | Test the API by hand |
| GitHub CLI | Optional | Push your own copy to GitHub |
| ngrok | Optional | Share a temporary public link to your running app |

After installing Docker Desktop, **restart the laptop**. The first time Docker opens, accept its terms; if it asks to install or update **WSL**, let it.

Check the installs:

```powershell
git --version
docker --version
java -version
```

---

## 1. Get the code

### Option A: with Git (recommended)

```powershell
mkdir C:\Projects
cd C:\Projects
git clone https://github.com/addepallijayanth/ai-interview-coach
cd ai-interview-coach
```

### Option B: from a ZIP file

1. On GitHub, click **Code → Download ZIP** (or use a zip someone sent you).
2. Create `C:\Projects`: in File Explorer go to **This PC → Local Disk (C:)**, right-click, **New → Folder**, and name it `Projects`.
3. Right-click the zip, choose **Extract All…**, and extract into `C:\Projects`.
4. Rename the folder to `ai-interview-coach` if needed.

Or move an already-unzipped folder from Downloads with PowerShell:

```powershell
Move-Item C:\Users\<your-user>\Downloads\ai-interview-coach C:\Projects\ai-interview-coach
```

> **Watch the backslashes.** `C:\Projectsai-interview-coach` (missing `\`) creates a wrongly named folder.
> If that happens, fix it with:
> `Move-Item C:\Projectsai-interview-coach C:\Projects\ai-interview-coach`

### Check you are in the right folder

```powershell
cd C:\Projects\ai-interview-coach
dir
```

You should see `backend`, `frontend`, `docs`, `dev`, `docker-compose.yml`, `README.md` and `.env.example`.
If you see **only another `ai-interview-coach` folder** (zip double folder), run `cd ai-interview-coach` once more.

> Avoid running it from OneDrive or Downloads: syncing slows down the build and can lock files.

---

## 2. Create the settings file (one time)

```powershell
cd C:\Projects\ai-interview-coach
Copy-Item .env.example .env
```

Optionally open `.env` in Notepad (`notepad .env`) and set:

```
JWT_SECRET=any-random-text-at-least-32-characters-long-1234
```

Without it you are signed out every time the app restarts. **Never upload `.env` to GitHub.** It is already listed in `.gitignore`.

For real AI later, put a key after `ANTHROPIC_API_KEY=`. For the free mock, leave it empty.

---

## 3. Start Docker Desktop

1. Open **Docker Desktop** from the Start menu.
2. Wait until the bottom-left corner shows **Engine running** (green).
   "Running in Resource Saver mode" is also fine: it wakes up automatically.

You can type the commands below in normal PowerShell or in Docker Desktop's built-in **Terminal** tab.

---

## 4. Start the app

### Free, with the mock AI (no key needed)

```powershell
cd C:\Projects\ai-interview-coach
$env:ANTHROPIC_BASE_URL="http://mock-claude:8089"; $env:ANTHROPIC_API_KEY="mock"; docker compose --profile mock up --build
```

### With real AI (only if you have an Anthropic key in `.env`)

```powershell
docker compose up --build
```

### What to expect

- The **first run takes 5–15 minutes**: it downloads and builds everything, and lots of text scrolls by.
- It is **ready** when the log shows `Started CoachApplication` and the scrolling stops.
- The summary shows **5 containers Running**: `db`, `mailpit`, `backend`, `frontend`, `mock-claude`.
- The terminal then looks "stuck". **That is normal**: the app is running. **Do not close this window.**
- The options at the bottom (`v` View in Docker Desktop, `o` View Config, `w` Enable Watch, `d` Detach) are optional. You don't need to press any.
  `d` keeps the app running in the background and gives you the terminal back.

---

## 5. Open the app in the browser

Always type the full address **with `http://`**:

| What | Address |
|---|---|
| The app | **http://localhost:3000** |
| Email inbox (Mailpit) | **http://localhost:8025** |
| API docs (Swagger) | **http://localhost:8080/swagger-ui.html** |
| API base (for Postman) | `http://localhost:8080` |

> **"This site can't provide a secure connection" / `ERR_SSL_PROTOCOL_ERROR`:** Chrome switched to `https://`.
> Type `http://localhost:3000` exactly. If Chrome keeps switching, open `chrome://net-internals/#hsts`,
> type `localhost` under **Delete domain security policies**, click **Delete**, or use an Incognito window (`Ctrl+Shift+N`).
> This also produces a harmless `Invalid character found in method name` error in the backend log.

---

## 6. Try the full flow

1. **Create an account:** open http://localhost:3000, click **Create account**, and fill in first name, last name, email, country, mobile number and password (8+ characters with a letter and a number). Submit.
2. **Confirm the email:** open **http://localhost:8025**, click the email "Confirm your email for AI Interview Coach", and click **Confirm email**. You are signed in.
   - "Already confirmed" means the link was used once already. Just click **Sign in**.
   - You can't sign in before confirming; the sign-in page offers **Resend link**.
3. **Analyse:** upload your resume (PDF or DOCX, max 2 MB), paste a job description (at least 50 characters), and click **Analyse my match**.
4. **Interview:** choose **Easy (10)**, **Standard (20)** or **Professional (30)** and click **Start … interview**. Answer the questions; each one is labelled Technical, Problem solving, System design, Project deep-dive, Behavioural, Situational or HR.
5. **Report:** click **End interview** at any time, then **See your report**, then **Download PDF**.
6. **Forgot password:** on the sign-in page click **Forgot password?**; the reset email appears in Mailpit.

> With the mock AI, questions are canned and scores depend mostly on answer length. That's fine for demos.

---

## 7. Stop and start again

**Stop:** click in the terminal and press **Ctrl + C**. Wait for the containers to show *Stopped*.
Your accounts and interviews are kept for next time.

**Start again later** (fast, no `--build`):

```powershell
cd C:\Projects\ai-interview-coach
$env:ANTHROPIC_BASE_URL="http://mock-claude:8089"; $env:ANTHROPIC_API_KEY="mock"; docker compose --profile mock up
```

**After changing code**, add `--build` at the end of the command.

**Stopped from somewhere else / pressed `d`:**

```powershell
docker compose down
```

**Wipe all test data and start fresh** (deletes accounts, interviews and emails):

```powershell
docker compose down -v
```

You can close the laptop after stopping. Optionally quit Docker Desktop: right-click the whale icon in the taskbar and choose **Quit Docker Desktop**.

---

## 8. Test the API with Postman

Postman calls the backend on **port 8080**, not 8025 (Mailpit) or 3000 (website).

### 8.1 Sign in and get a token

- Method: **POST**
- URL: `http://localhost:8080/api/auth/login`
- **Body → raw → JSON** (change the dropdown from *Text* to *JSON*):

```json
{ "email": "you@example.com", "password": "YourPassword1" }
```

Click **Send** and you get **200 OK**:

```json
{ "token": "eyJhbGciOi...long text...", "expiresAt": "...", "user": { "id": 1, "firstName": "...", ... } }
```

Copy the whole token value: from `eyJ…` up to the closing quote, without the quotes. It is valid for 24 hours.

### 8.2 Use the token

- New request, method **GET**, URL `http://localhost:8080/api/auth/me`
- **Authorization** tab → **Auth Type: Bearer Token** → paste the token → **Send**

### 8.3 Other requests (all with the Bearer token)

| What | Method | URL | Body |
|---|---|---|---|
| Register | POST | `/api/auth/register` | `{"firstName","lastName","email","countryCode":"IN","mobileNumber","password"}` |
| Confirm email | POST | `/api/auth/verify-email` | `{"token": "token from the email link"}` |
| My profile | GET | `/api/auth/me` | none |
| Upload resume | POST | `/api/resumes` | **form-data**, key `file`, type **File** |
| Save job description | POST | `/api/job-descriptions` | `{"text": "at least 50 characters..."}` |
| Run analysis | POST | `/api/analyses` | `{"resumeId": 1, "jdId": 1}` |
| My analyses | GET | `/api/analyses` | none |
| Start interview | POST | `/api/interviews` | `{"analysisId": 1, "level": "EASY"}` |
| My interviews | GET | `/api/interviews` | none |
| Report | GET | `/api/reports/1` | none |
| AI usage today | GET | `/api/usage/me` | none |
| App settings | GET | `/api/meta` | none (no token needed) |

Put `http://localhost:8080` in front of every path. Use the website for the interview chat itself: it is a live stream, which Postman shows poorly.

### 8.4 Common Postman errors

| Error | Cause | Fix |
|---|---|---|
| `You need a browser with JavaScript enabled to use Mailpit` | URL uses port **8025** | Use **8080** |
| `404 File not found` | Wrong port, or wrong method | `POST http://localhost:8080/api/auth/login` |
| `400 Failed to read request` | Body is empty or set to *Text* | **Body → raw → JSON**, straight quotes `"` |
| `405 Method 'GET' is not supported` on `/login` | `/login` only accepts POST | For profile data use `GET /api/auth/me` |
| `403 email_not_verified` | Email not confirmed | Click **Confirm email** in Mailpit |
| `401` | Token missing, expired or partly copied | Log in again and copy the whole token |

> Ignore Postman's suggestions like "Change method to GET": they are often wrong for this API.
> Easier alternative: open http://localhost:8080/swagger-ui.html and use **Try it out → Execute**.

---

## 9. Look at the database directly (optional)

With the app running, open a **second** PowerShell window:

```powershell
cd C:\Projects\ai-interview-coach
docker compose exec db psql -U coach -d coach
```

At the `coach=#` prompt:

```sql
\dt
select id, first_name, last_name, email, country_code, phone_number, email_verified_at from users;
select id, user_id, level, status, question_target, overall_score from interview_session;
\q
```

Passwords are stored only as BCrypt hashes, never as plain text.

---

## 10. Share a public link for a demo (optional, ngrok)

`localhost` links only work on your own laptop. For a temporary public link:

```powershell
ngrok config add-authtoken YOUR_TOKEN      # one time, token from https://dashboard.ngrok.com
ngrok http 3000
```

Share the `https://….ngrok-free.app` address it prints. It works only while your laptop, the app and ngrok are running.
New users can't confirm their email (emails go to your local Mailpit), so demo with an already-confirmed account.

For a permanent website, see `docs/deployment.md` (Azure, plus a real email provider such as SendGrid).

---

## 11. Open the code in IntelliJ (optional)

1. **File → Open**, choose `C:\Projects\ai-interview-coach`, click **OK**, then **Trust Project**.
2. The backend is in `backend/` (Java 21, Spring Boot) and the frontend in `frontend/` (React, TypeScript).
3. Run the backend tests from PowerShell:

```powershell
cd C:\Projects\ai-interview-coach\backend
.\mvnw test
```

---

## 12. Push your own copy to GitHub (owner only)

### From IntelliJ

1. **File → Settings → Version Control → GitHub**: click **+**, choose **Log In via GitHub**, approve in the browser, click **OK**.
2. **Git → GitHub → Share Project on GitHub**: name `ai-interview-coach`, untick **Private**, click **Share**.
3. In the file list, make sure **`.env` is not included**, then click **Add**.

### From PowerShell

```powershell
cd C:\Projects\ai-interview-coach
gh auth login --web
git init
git config user.name "Your Name"
git config user.email "yourusername@users.noreply.github.com"
git add .
git commit -m "Initial commit: AI Interview Coach"
gh repo create ai-interview-coach --public --source . --push
```

Later changes: in IntelliJ press **Ctrl + K** to commit and **Ctrl + Shift + K** to push.
Make an existing repository public: on GitHub, **Settings → Danger Zone → Change visibility → Make public**.

---

## 13. Quick troubleshooting

| Problem | Fix |
|---|---|
| `Cannot find path ... because it does not exist` | Check the folder path and backslashes; run `dir` to see where you are |
| `docker` not recognised / cannot connect | Start Docker Desktop and wait for **Engine running** |
| Port 3000 / 8080 / 5432 already in use | Stop the other program, or run `docker compose down` and try again |
| Page shows `ERR_SSL_PROTOCOL_ERROR` | Type `http://`, not `https://` (see section 5) |
| No confirmation email | Open http://localhost:8025 (Mailpit); check you typed the right email |
| Signed out after restart | Set `JWT_SECRET` in `.env` (section 2) |
| "Folder in use" when deleting the project | Stop the app (Ctrl+C or `docker compose down`), close IntelliJ, terminals and editors in that folder, pause OneDrive, or restart the laptop |
| Something else red in the terminal | Copy the error text and search for it, or ask for help with a screenshot |

---

## Cheat sheet

```powershell
# start (first time or after code changes)
cd C:\Projects\ai-interview-coach
$env:ANTHROPIC_BASE_URL="http://mock-claude:8089"; $env:ANTHROPIC_API_KEY="mock"; docker compose --profile mock up --build

# start (normal)
$env:ANTHROPIC_BASE_URL="http://mock-claude:8089"; $env:ANTHROPIC_API_KEY="mock"; docker compose --profile mock up

# stop: Ctrl + C   (or)   docker compose down
# wipe data:              docker compose down -v
```

App: **http://localhost:3000** · Emails: **http://localhost:8025** · API docs: **http://localhost:8080/swagger-ui.html**
