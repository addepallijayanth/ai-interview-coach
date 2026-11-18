# Deploying to Azure

The deploy workflow (`.github/workflows/deploy.yml`) builds the backend image, pushes it to GitHub Container
Registry and rolls it out to **Azure Container Apps**, then uploads the frontend to **Azure Static Web Apps**.
It stays switched off until you complete the steps below.

| Piece | Azure service | Cost for a portfolio demo |
|---|---|---|
| API (Spring Boot container) | Container Apps, consumption plan, scale to zero | Usually inside the monthly free grant |
| Frontend (static files) | Static Web Apps, Free plan | Free |
| Database | PostgreSQL Flexible Server, Burstable B1ms | Free for 12 months on a new Azure account, then roughly $13/month. A free [Neon](https://neon.tech) database also works |
| AI | Claude API | Pay per use, capped by `AI_DAILY_SPEND_CAP_USD` |

Commands use the [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli). Replace everything in `<angle brackets>`.

## 1. Resource group and database

```bash
az login
az group create --name coach-rg --location eastus

az postgres flexible-server create \
  --resource-group coach-rg --name <unique-db-name> --location eastus \
  --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 17 \
  --admin-user coach --admin-password '<strong-db-password>' \
  --public-access 0.0.0.0          # allows connections from Azure services only

az postgres flexible-server db create --resource-group coach-rg --server-name <unique-db-name> --database-name coach
```

## 2. Container Apps (the API)

The first revision uses a placeholder image; the workflow replaces it on every push to `main`.

```bash
az containerapp env create --resource-group coach-rg --name coach-env --location eastus

az containerapp create --resource-group coach-rg --name coach-api --environment coach-env \
  --image mcr.microsoft.com/k8se/quickstart:latest \
  --target-port 8080 --ingress external \
  --min-replicas 0 --max-replicas 1 \
  --secrets anthropic-key='<your-anthropic-api-key>' jwt-secret='<output of: openssl rand -base64 48>' db-password='<strong-db-password>' \
  --env-vars \
    ANTHROPIC_API_KEY=secretref:anthropic-key \
    JWT_SECRET=secretref:jwt-secret \
    DB_PASSWORD=secretref:db-password \
    DB_USERNAME=coach \
    DB_URL='jdbc:postgresql://<unique-db-name>.postgres.database.azure.com:5432/coach?sslmode=require' \
    AI_DAILY_SPEND_CAP_USD=5.00

az containerapp show --resource-group coach-rg --name coach-api --query properties.configuration.ingress.fqdn -o tsv
```

The last command prints the API host name, e.g. `coach-api.<hash>.eastus.azurecontainerapps.io`.

`--max-replicas 1` matters: interview runs and rate limits are coordinated in memory (see *Design decisions* in the
README), so the app is meant to run as a single instance.

**Container image access:** the workflow pushes to `ghcr.io/<owner>/<repo>/backend`. After the first successful run,
open the package on GitHub (*Your profile → Packages*) and set its visibility to **Public** so Azure can pull it.
To keep it private, add registry credentials instead: `az containerapp registry set --server ghcr.io --username <github-user> --password <PAT with read:packages>`.

## 3. Static Web Apps (the frontend)

```bash
az staticwebapp create --resource-group coach-rg --name coach-web --location eastus2 --sku Free
az staticwebapp show --resource-group coach-rg --name coach-web --query defaultHostname -o tsv
az staticwebapp secrets list --resource-group coach-rg --name coach-web --query properties.apiKey -o tsv
```

Allow the frontend origin to call the API, and point the links in account emails at it:

```bash
az containerapp update --resource-group coach-rg --name coach-api \
  --set-env-vars CORS_ALLOWED_ORIGINS=https://<static-web-app-hostname> APP_BASE_URL=https://<static-web-app-hostname>
```

## 3b. Email (sign-up confirmation and password reset)

New accounts must confirm their email before they can sign in, so production needs an SMTP provider. Any provider
works; two easy options:

- **SendGrid** (free tier): create an API key, verify a sender address, then use host `smtp.sendgrid.net`, port
  `587`, username `apikey` and the API key as the password.
- **Azure Communication Services Email**: create an Email Communication Service with an Azure-managed domain,
  connect it to a Communication Services resource, create an SMTP username for an Entra app, and use host
  `smtp.azurecomm.net`, port `587`.

```bash
az containerapp secret set --resource-group coach-rg --name coach-api --secrets smtp-password='<smtp-password-or-api-key>'
az containerapp update --resource-group coach-rg --name coach-api --set-env-vars \
  MAIL_HOST=smtp.sendgrid.net MAIL_PORT=587 MAIL_USERNAME=apikey MAIL_PASSWORD=secretref:smtp-password \
  MAIL_STARTTLS=true "MAIL_FROM=AI Interview Coach <no-reply@yourdomain.com>"
```

`MAIL_FROM` must be a sender the provider has verified, or messages are rejected. Without `MAIL_HOST` the API still
runs but only writes the links to its log, so nobody could finish signing up.

## 4. Let GitHub Actions deploy (OpenID Connect, no stored cloud password)

```bash
az ad app create --display-name coach-github-deploy --query appId -o tsv          # -> <app-id>
az ad sp create --id <app-id>
az role assignment create --assignee <app-id> --role Contributor \
  --scope $(az group show --name coach-rg --query id -o tsv)
az ad app federated-credential create --id <app-id> --parameters '{
  "name": "github-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:<github-user>/<repo>:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'
az account show --query "{tenant:tenantId, subscription:id}" -o json
```

In the GitHub repository, open **Settings → Secrets and variables → Actions** and add:

| Type | Name | Value |
|---|---|---|
| Secret | `AZURE_CLIENT_ID` | `<app-id>` |
| Secret | `AZURE_TENANT_ID` | tenant from the last command |
| Secret | `AZURE_SUBSCRIPTION_ID` | subscription from the last command |
| Secret | `AZURE_STATIC_WEB_APPS_API_TOKEN` | the Static Web Apps API key |
| Variable | `AZURE_RESOURCE_GROUP` | `coach-rg` |
| Variable | `AZURE_CONTAINER_APP` | `coach-api` |
| Variable | `API_BASE_URL` | `https://<container-app-fqdn>` |
| Variable | `AZURE_DEPLOY_ENABLED` | `true` |

Optionally add the secret `ANTHROPIC_API_KEY` to run the AI eval workflow (*Actions → AI eval → Run workflow*).

Push to `main` (or run the workflow manually). The site is then live at `https://<static-web-app-hostname>` and the
API docs at `https://<container-app-fqdn>/swagger-ui.html`.

## 5. After going live

- Check `https://<container-app-fqdn>/actuator/health` returns `{"status":"UP"}`.
- Sign up with your own address and confirm the email arrives (check spam the first time) and its link opens the site.
- Watch spending in the Anthropic console; tune `AI_DAILY_SPEND_CAP_USD` and `AI_REQUESTS_PER_USER_PER_DAY`.
- With scale to zero the first request after a quiet period takes a few seconds while the container starts.

## Other hosts

The backend is a plain container listening on `PORT` (default 8080) and configured entirely through environment
variables, so AWS App Runner, Google Cloud Run, Fly.io or Render work the same way. The frontend is static files;
`frontend/vercel.json` and `frontend/public/staticwebapp.config.json` handle client-side routing on Vercel and Azure.
