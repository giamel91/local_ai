# Deployment status & handoff

Goal: run this API on AWS so the LLM runs in the cloud (no local GPU needed).

**Target architecture chosen:** single EC2 instance running Docker Compose with
**two containers** — the API and ChromaDB — plus an **IAM role** for Bedrock access.

---

## AWS facts

| | |
|---|---|
| Account ID | `401905377384` |
| Region | `eu-south-1` (Milan) — hosts Qwen3 32B |
| Bedrock chat model | `qwen.qwen3-32b-v1:0` |
| Bedrock embedding model | `amazon.titan-embed-text-v2:0` |

### IAM users and CLI profiles

| CLI profile | IAM user | Permissions | Purpose |
|---|---|---|---|
| `default` | `sample-ai-dev` | `bedrock:InvokeModel` only | app credentials for local testing |
| `deploy` | `sample-ai-deploy` | `AmazonEC2ContainerRegistryPowerUser`, `AmazonEC2FullAccess`, `SampleAiDeployIamPolicy` (scoped IAM) | infrastructure work |

aws.exe lives at `C:\Program Files\Amazon\AWSCLIV2\aws.exe` (may not be on PATH).

Verify with:

```powershell
aws sts get-caller-identity --profile deploy
aws sts get-caller-identity
```

---

## Done so far

- [x] Upgraded Spring Boot 3.3.0 -> 3.5.16, Spring AI 1.0.0-M1 -> 1.0.7
- [x] Added Bedrock: Qwen3 32B chat (Converse API) + Titan v2 embeddings
- [x] Split config into `local` / `aws` Spring profiles
- [x] Externalised all config to environment variables
- [x] Global CORS config (`app.cors.allowed-origins`)
- [x] `Dockerfile` (multi-stage, runs as non-root) + `.dockerignore`
- [x] Verified end-to-end against real Bedrock locally (4s response)
- [x] Made Kafka fail-fast instead of hanging 60s per request
- [x] `docker-compose.yml` (API + Chroma, named volume `chroma-data:/data`)
- [x] IAM role `sample-ai-ec2-bedrock-role` + instance profile
      `sample-ai-ec2-bedrock-profile` (Bedrock invoke + `ecr:GetAuthorizationToken`
      and pull-only access to `repository/sample-ai-api`)
- [x] Security group `sample-ai-api-sg` (`sg-01918d764450132ea`): 443 and 80 open to the
      world (nginx), SSH 22 restricted to the admin IP, all egress. No public 8080 rule.
- [x] Elastic IP `18.102.200.77` (`eipalloc-0fb3d98e77dfb9934`,
      assoc `eipassoc-0270f0c126ce5ce5e`) — IP now survives stop/start
- [x] Free DNS name `arlingtonqwen.duckdns.org` (DuckDNS A record -> Elastic IP)
- [x] TLS: Let's Encrypt certificate via **acme.sh + DuckDNS DNS-01**, auto-renewed
- [x] `nginx` container terminates TLS on 80/443 and proxies to `api:8080`
- [x] **Verified:** `GET https://arlingtonqwen.duckdns.org/api/ai/generate?message=ciao`
      returns `200` with `argomento` / `spiegazioneTecnica` / `riassuntoSemplice`
      (Bedrock Qwen3 answered through the instance role — no credentials on disk)
- [x] SSH key pair `sample-ai-key` (private key at `C:\Users\giame\.ssh\sample-ai-key.pem`)
- [x] EC2 instance **`i-0a904020481f92b78`** — `t3.small`, Amazon Linux 2023
      (`ami-0d955a2b5ba6e444a`), 20 GB gp3, 2 GB swap file, Docker 25 + Compose v2
- [x] Stack running: `chroma` + `sample-ai-api` + `nginx` via `docker compose up -d`
      (`api` publishes 8080 on loopback only, for debugging)
- [x] **Angular frontend deployed and served by the same nginx** (same origin as the API,
      so no CORS and no second certificate). Source repo is `giamel91/local_ai_fe`,
      cloned to `..\sample_ai_frontend`. Build output lives in
      `/home/ec2-user/app/frontend` on the instance and is mounted read-only at
      `/usr/share/nginx/html`.
- [x] Frontend API base is configuration, not a constant: `src/environments/environment.ts`
      (`http://localhost:8080`, used by `ng serve`) is swapped for
      `environment.production.ts` (`''` -> relative `/api/...`) via `fileReplacements`
      in `angular.json`.

Public IP is the Elastic IP `18.102.200.77`; it no longer changes on stop/start.
Re-read it with:

```powershell
aws ec2 describe-instances --instance-ids i-0a904020481f92b78 --region eu-south-1 `
  --query "Reservations[0].Instances[0].PublicIpAddress" --output text
```

---

## ⚠️ ECR still unresolved (workaround in use)

The `sample-ai-deploy` user cannot create ECR repositories —
`AmazonEC2ContainerRegistryPowerUser` grants push/pull but not `ecr:CreateRepository` —
and it also lacks `iam:AttachUserPolicy`, so it cannot grant itself the missing right.

A private repo `sample-ai-api` created from the Console was **not visible** to this CLI
(no repositories returned in eu-south-1 or any other region), so the console session is
probably signed into a different account.

**Workaround used:** the image was built locally and streamed to the host, then tagged
with the ECR URI so `docker-compose.yml` works unchanged:

```powershell
cmd /c "docker save sample-ai-api:latest | ssh -i C:\Users\giame\.ssh\sample-ai-key.pem ec2-user@<ip> `"docker load`""
```

To switch to registry-based deploys later, create `sample-ai-api` in account
`401905377384` / region `eu-south-1` (Console or a user that may call
`ecr:CreateRepository`), then:

```powershell
$aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe'
$reg = '401905377384.dkr.ecr.eu-south-1.amazonaws.com'
& $aws ecr get-login-password --region eu-south-1 --profile deploy |
    docker login --username AWS --password-stdin $reg
docker tag sample-ai-api:latest "$reg/sample-ai-api:latest"
docker push "$reg/sample-ai-api:latest"
```

---

## Redeploying after a code change

```powershell
$pem = "$env:USERPROFILE\.ssh\sample-ai-key.pem"
docker build -t sample-ai-api .
cmd /c "docker save sample-ai-api:latest | ssh -i $pem ec2-user@18.102.200.77 `"docker load`""
ssh -i $pem ec2-user@18.102.200.77 "cd /home/ec2-user/app && docker compose up -d --pull never api"
```

`docker-compose.yml`, `nginx/default.conf` and `.env` (holding `ECR_IMAGE`) live in
`/home/ec2-user/app` on the instance. After editing either file locally, copy it:

```powershell
scp -i $pem docker-compose.yml ec2-user@18.102.200.77:/home/ec2-user/app/
scp -i $pem nginx/default.conf ec2-user@18.102.200.77:/home/ec2-user/app/nginx/
ssh -i $pem ec2-user@18.102.200.77 "cd /home/ec2-user/app && docker compose up -d --pull never"
```

The ECR-shaped tag is applied on the host, so once ECR works the switch is just a
`docker login` + `docker push` — no compose edit.

---

## Remaining steps

1. **ECR repo** `sample-ai-api` — optional; see the workaround notes above.
2. Optional later: move the frontend to **S3 + CloudFront** for CDN caching and atomic
   deploys. Needs `sample-ai-deploy` to gain S3 + CloudFront permissions first (it has
   neither today: `s3:ListAllMyBuckets` and `cloudfront:ListDistributions` both return
   `AccessDenied`). The build artifact is reusable as-is; only `apiBaseUrl` in
   `environment.production.ts` has to become absolute, and the CloudFront domain has to
   be added to `CORS_ALLOWED_ORIGINS`.

Done: **8080 is no longer reachable from the internet** (loopback publish only, no SG rule),
the API is served over TLS at `https://arlingtonqwen.duckdns.org`, and the same host
serves the Angular app at `https://arlingtonqwen.duckdns.org/`.

## Frontend

| | |
|---|---|
| Repo | `giamel91/local_ai_fe`, local clone `..\sample_ai_frontend` |
| Stack | Angular 22, SSR code present but **not used** (static deploy) |
| Build | `npm run build` -> `dist/sample_ai_frontend/browser` |
| Deployed files | `/home/ec2-user/app/frontend` (from `browser/`), mounted read-only |
| Served by | the existing `nginx` container, `root /usr/share/nginx/html` |
| API base | `''` in production -> relative `/api/...`, same origin |

Deploy loop:

```powershell
cd ..\sample_ai_frontend
npm run build
$pem = "$env:USERPROFILE\.ssh\sample-ai-key.pem"
ssh -i $pem ec2-user@18.102.200.77 "rm -rf /home/ec2-user/app/frontend_upload"
scp -i $pem -r dist\sample_ai_frontend\browser ec2-user@18.102.200.77:/home/ec2-user/app/frontend_upload
ssh -i $pem ec2-user@18.102.200.77 "rm -rf /home/ec2-user/app/frontend && mv /home/ec2-user/app/frontend_upload /home/ec2-user/app/frontend && chmod -R a+rX /home/ec2-user/app/frontend"
```

Three traps that bit during setup:

- **`chmod -R a+rX` is mandatory.** `scp` creates the folder `0700`; the nginx worker runs
  as uid 101 inside the container and cannot traverse it, so every request returns
  **403** with no useful error in `nginx -t`. Symptom to remember: 403 on files that
  plainly exist.
- **`prerender` runs your app in Node at build time.** With `outputMode: "server"`,
  `npm run build` prerendered `/` and `ngOnInit` fired a relative `/api/ai/history`
  request with no server to answer it -> `Unable to handle request` and a failed build.
  Fixed properly by skipping the call outside a browser:
  `if (!isPlatformBrowser(this.platformId)) return;` (and not by the `prerender: false`
  flag, which the CLI ignored).
- **Cache headers:** hashed assets get `public, max-age=31536000, immutable`;
  `index.html` gets `no-cache`. Without the second half a deploy stays invisible in a
  browser that cached the shell.

## TLS and DNS

| | |
|---|---|
| Public name | `arlingtonqwen.duckdns.org` (DuckDNS, A record -> Elastic IP) |
| Certificate | Let's Encrypt, DNS-01 via `acme.sh` `dns_duckdns` |
| Cert files | `/home/ec2-user/app/certs/{fullchain.pem,privkey.pem}`, mounted read-only |
| Renewal | `acme.sh` crontab entry, 4 times a day; renews at ~2/3 of the 90-day life |
| Reload hook | `docker compose exec -T nginx nginx -s reload` |
| DuckDNS token | `~/.acme.sh/account.conf` and `~/.acme.sh/dnsapi/dns_duckdns.sh` env; never in git |

Two traps that bit during setup:

- Current `acme.sh` defaults to **ZeroSSL**, which refuses accounts without an email.
  Pass `--server letsencrypt` on every command.
- Let's Encrypt rejects `example.com` as a contact and rejects `.local` domains outright,
  so the account is registered **without** a contact email (`ACCOUNT_EMAIL=` empty).

Force a renewal test:

```powershell
ssh -i $pem ec2-user@18.102.200.77 "export DuckDNS_Token=<token>; ~/.acme.sh/acme.sh --renew -d arlingtonqwen.duckdns.org --force --server letsencrypt"
```


## After that

- Optional: replace Kafka with **Amazon MSK**, or replace Chroma with a managed vector
  store (Aurora pgvector / OpenSearch Serverless).

---

## What actually runs where

Nothing model-related is in the container. The API image is just the Spring Boot jar.

| Piece | Where |
|---|---|
| nginx (TLS endpoint + static frontend) | container `nginx` on the EC2 instance, ports 80/443 |
| Angular app (static files) | `/home/ec2-user/app/frontend` on the instance, mounted into nginx |
| Spring Boot API | container `sample-ai-api` on the EC2 instance, 8080 on loopback |
| ChromaDB | container `chroma` on the EC2 instance (volume `chroma-data`) |
| Qwen3 32B chat + Titan v2 embeddings | **Amazon Bedrock**, managed — called over HTTPS, auth via the EC2 instance role |

Request path: browser -> `https://arlingtonqwen.duckdns.org` -> nginx (TLS ends here) ->
static files for `/`, or `http://api:8080` over the compose network for `/api/` ->
Bedrock over HTTPS.

So a 2 GB `t3.small` is enough: no model weights are loaded locally.

---

## Costs & monitoring

Billed assets: **EC2** (per second while running), **EBS** 20 GB gp3 (always, even stopped),
**Bedrock** (per token, only when called). IAM, security groups and the SSH key are free.
The Elastic IP is free while attached to the running instance; it bills hourly only when
the instance is stopped or the address is unattached.

| Asset | Rough cost |
|---|---|
| `t3.small` running 24/7 | ~$17/month |
| 20 GB gp3 while stopped | ~$1.60/month |
| One question to Qwen3 32B | fractions of a cent |

The `sample-ai-deploy` user **cannot read billing data** (`ce:GetCostAndUsage` and
`budgets:ViewBudget` are denied), so use the Console signed in as **root**:

- Bills (cost per service): <https://us-east-1.console.aws.amazon.com/billing/home#/bills>
- Cost Explorer: <https://us-east-1.console.aws.amazon.com/costmanagement/home#/cost-explorer>
- Budgets: <https://us-east-1.console.aws.amazon.com/billing/home#/budgets>

**Do this once:** Budgets -> Create budget -> Cost budget -> **$10/month**, alerts at 80%
(actual) and 100% (forecasted) to your email. That is the safety net against forgetting a
running instance.

To filter this deployment's spend, enable the cost allocation tag `Name=sample-ai-api`
under Billing -> Cost allocation tags, then group Cost Explorer by that tag. Services to
expect: `EC2-Instances`, `EC2-Other`, `Bedrock`.

If CLI-based cost checks are wanted later, an admin must grant the deploy user
`ce:GetCostAndUsage` and `budgets:ViewBudget`, then:

```powershell
aws ce get-cost-and-usage --time-period Start=2026-10-01,End=2026-10-18 `
  --granularity DAILY --metrics UnblendedCost --region us-east-1 --profile deploy
```

---

## Useful commands

```powershell
# stop the instance when done studying (keeps EBS + Chroma data, saves ~$17/month)
aws ec2 stop-instances --instance-ids i-0a904020481f92b78 --region eu-south-1 --profile deploy
# ...and start it again; the Elastic IP keeps the address, so the DNS name still resolves
aws ec2 start-instances --instance-ids i-0a904020481f92b78 --region eu-south-1 --profile deploy

# public HTTPS smoke test
curl.exe -sS "https://arlingtonqwen.duckdns.org/api/ai/generate?message=ciao"
curl.exe -sS https://arlingtonqwen.duckdns.org/health

# is Chroma up locally?
docker ps --filter name=chroma
# Chroma API smoke test
(Invoke-WebRequest 'http://localhost:8000/api/v2/heartbeat' -UseBasicParsing).Content

# local run against Bedrock
$env:SPRING_PROFILES_ACTIVE='aws'; .\mvnw.cmd spring-boot:run

# confirm a Bedrock model works, without the app in the way
aws bedrock-runtime converse --model-id qwen.qwen3-32b-v1:0 `
  --messages '[{"role":"user","content":[{"text":"ciao"}]}]' --region eu-south-1
```
