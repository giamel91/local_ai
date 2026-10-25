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
| `deploy` | `sample-ai-deploy` | **no `ssm:*` rights**, and cannot grant them to itself (`iam:PutUserPolicy` denied) | SSM must be driven from the console as root, until `SampleAiDeploySsm` is attached |

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
      world (nginx), all egress. No public 8080 rule. **No inbound SSH rule at all** -
      port 22 is closed, host access is via SSM (see below).
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
- [x] **Reboot test passed** (`aws ec2 reboot-instances`, whole OS restart): back in
      **40 s** with no manual intervention. `docker` is `systemctl is-enabled`, every
      service has `restart: unless-stopped`, the `app_chroma-data` volume reattaches and
      the acme.sh cron entry survives.
- [x] **Stop/start test passed** (`stop-instances` -> `start-instances`, full
      deallocation): public IP stayed **`18.102.200.77`**, status checks OK in 158 s, the
      stack came back by itself and the API answered in 2.5 s. This is what confirms the
      Elastic IP earns its keep — without it the address would have changed.
- [x] **nginx hardened:** `server_tokens off` (no version in `Server`), HSTS
      `max-age=15552000` (no `includeSubDomains`, no `preload`), `X-Content-Type-Options:
      nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`; `/api/` rate limited to
      `10r/s` with `burst=20 nodelay`, answered as **429**. Verified: a 40-request burst
      produced 22x200 + 18x429, and a single request after 3 s returned 200 again.

- [x] **SSM Session Manager works** - shell access through the AWS console, no port 22 and
      no key file. Inline policy `SampleAiSsmSessionPolicy` on
      `sample-ai-ec2-bedrock-role` grants `ssm:UpdateInstanceInformation`,
      `ssm:ListInstanceAssociations`, the four `ssmmessages` channel actions and the four
      `ec2messages` message actions. Proven by sending `docker compose ps` **through AWS**
      (the instance was temporarily allowed to command itself, scoped to its own ARN, then
      that permission was deleted again).
- [x] **Port 22 closed** (`admin-ssh` rule removed from `sg-01918d764450132ea`). The host
      now accepts only 80 and 443. Site, API and health stayed `200` after the change.

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

Port 22 is closed, so the old `docker save | ssh | docker load` path no longer works as
written. Two ways forward; pick per situation.

### Immediate: temporary SSH window (works today, no new AWS resources)

Open 22 only to your current IP, deploy, close it again. The port is exposed for minutes,
not permanently.

```powershell
$aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe'
$pem = "$env:USERPROFILE\.ssh\sample-ai-key.pem"
$myip = (Invoke-RestMethod 'https://checkip.amazonaws.com').Trim()

# 1. open the window
& $aws ec2 authorize-security-group-ingress --group-id sg-01918d764450132ea `
  --region eu-south-1 --profile deploy `
  --ip-permissions "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=$myip/32,Description=admin-ssh}]"

# 2. deploy
docker build -t sample-ai-api .
cmd /c "docker save sample-ai-api:latest | ssh -i $pem ec2-user@18.102.200.77 `"docker load`""
ssh -i $pem ec2-user@18.102.200.77 "cd /home/ec2-user/app && docker compose up -d --pull never api"

# 3. close the window - do not skip this
& $aws ec2 revoke-security-group-ingress --group-id sg-01918d764450132ea `
  --region eu-south-1 --profile deploy `
  --ip-permissions "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=$myip/32}]"
```

To edit `docker-compose.yml`, `nginx/default.conf` or `.env` (all in `/home/ec2-user/app`
on the instance) use the same window with `scp`, or change them in place from a Session
Manager terminal:

```powershell
scp -i $pem docker-compose.yml ec2-user@18.102.200.77:/home/ec2-user/app/
scp -i $pem nginx/default.conf ec2-user@18.102.200.77:/home/ec2-user/app/nginx/
ssh -i $pem ec2-user@18.102.200.77 "cd /home/ec2-user/app && docker compose up -d --pull never"
```

### Target: registry-based, no SSH at all

Once `sample-ai-api` exists in ECR (see above), deploying needs neither SSH nor a temporary
port: push the image, then ask the agent to pull it.

```powershell
$reg = '401905377384.dkr.ecr.eu-south-1.amazonaws.com'
& $aws ecr get-login-password --region eu-south-1 --profile deploy |
    docker login --username AWS --password-stdin $reg
docker tag sample-ai-api:latest "$reg/sample-ai-api:latest"
docker push "$reg/sample-ai-api:latest"

& $aws ssm send-command --instance-ids i-0a904020481f92b78 --region eu-south-1 --profile deploy `
  --document-name AWS-RunShellScript `
  --parameters commands='cd /home/ec2-user/app && docker compose pull api && docker compose up -d api'
```

Until then the image on the host keeps the ECR-shaped tag, so `docker-compose.yml` needs no
edit when the switch happens.

---

## Access without SSH (SSM Session Manager)

Plain Docker Compose on EC2 is **not** ECS or EKS, so AWS has no container-level view in
the console. The containers do not appear anywhere. What SSM gives you is a shell *inside*
the instance from the browser, which is enough to run `docker compose ps` and friends.

Port 22 is closed. There is no inbound SSH rule on `sg-01918d764450132ea` any more, so
`ssh` and `scp` both fail by design.

### Open a shell

Console as root, region **Europe (Milan) `eu-south-1`** ->
**Systems Manager** -> **Session Manager** -> **Start session** ->
select `i-0a904020481f92b78` -> **Start session**.

You land as `ssm-user`; `sudo -i` for root. No key file, no Session Manager plugin - the
console terminal is built in.

```bash
cd /home/ec2-user/app
docker compose ps
docker compose logs --tail 50 api
```

### Run a one-off command without a session

**Systems Manager** -> **Run Command** -> document `AWS-RunShellScript` -> target the
instance -> paste commands -> **Run**. Output appears under **Output**.

### The IAM policy, and why each line exists

`SampleAiSsmSessionPolicy` is inline on `sample-ai-ec2-bedrock-role`:

| action | why |
|---|---|
| `ssm:UpdateInstanceInformation` | the agent's "hello, I exist" call; without it the instance never registers |
| `ssm:ListInstanceAssociations` | State Manager bookkeeping; not needed for a shell, but its denial spams the log |
| `ssmmessages:CreateControlChannel` / `OpenControlChannel` | the always-on websocket to the service |
| `ssmmessages:CreateDataChannel` / `OpenDataChannel` | the data path a session or command uses |
| `ec2messages:GetMessages` + `AcknowledgeMessage` / `DeleteMessage` / `SendReply` | the queue the service hands work to the agent through |

This is deliberately narrower than the AWS-managed `AmazonSSMManagedInstanceCore`.

### Traps, in the order they bit

1. The agent reports **one** denied action at a time, so permissions have to be added in
   rounds. `UpdateInstanceInformation` passed first, then `ec2messages:GetMessages`, then
   `ListInstanceAssociations` - three restarts before the log was clean.
2. The action is **`ssm:ListInstanceAssociations`**. `ssm:ListAssociations` does not exist;
   only the untruncated log line reveals the real name (`cut -c200-620`, not `cut -c1-140`).
3. `RequestManagedInstanceRoleToken: instance management role is not configured` is not a
   separate problem. It means the role grants no SSM rights, and it clears by itself.
4. **"MDS Polling job stopped" is normal.** The poll loop stops when idle and wakes on
   demand; twice it looked like the cause of the failure, and twice a real command ran
   fine afterwards. Do not chase it.
5. Credentials are cached by the agent and rotated roughly every 27 minutes
   (`Next credential rotation will be in ... minutes` in the log). A freshly attached
   policy can look ignored for a few minutes; `sudo systemctl restart amazon-ssm-agent`
   forces a re-read.
6. The `deploy` CLI user cannot verify any of this: it has no `ssm:*` rights and cannot
   grant them to itself (`iam:PutUserPolicy` denied). The console as root can.

### Getting the CLI working too

Attach the policy saved at `..\ssm-deploy-policy.json` (also in this conversation) to the
IAM user `sample-ai-deploy`: IAM -> Users -> `sample-ai-deploy` -> Permissions ->
Add permissions -> Create inline policy -> JSON -> paste -> name it `SampleAiDeploySsm`.

`Resource: "*"` is unavoidable for most of it, because `StartSession` authorises against a
session ARN that does not exist until the session starts. Then:

```powershell
$env:AWS_PROFILE='deploy'
aws ssm start-session --target i-0a904020481f92b78 --region eu-south-1   # needs the Session Manager plugin
aws ssm send-command --instance-ids i-0a904020481f92b78 --region eu-south-1 `
  --document-name AWS-RunShellScript --parameters commands='docker ps'   # no plugin needed
```

### If you ever need SSH back

The `deploy` user still holds `ec2:AuthorizeSecurityGroupIngress`, so re-opening is one
command, and it is worth adding it as a `/32` only, never `0.0.0.0/0`:

```powershell
aws ec2 authorize-security-group-ingress --group-id sg-01918d764450132ea `
  --region eu-south-1 --profile deploy `
  --ip-permissions 'IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=YOUR.IP.HERE/32,Description=admin-ssh}]'
```

Why SSH was closed at all: the admin rule was pinned to the home IP, which rotated four
times in one session (`94.33.194.162` -> `.165` -> `94.33.255.117` -> ...), locking the
owner out each time. Closing 22 removes both the lockout and the exposed port.

## Remaining steps

1. **ECR repo** `sample-ai-api` — no longer merely optional. With port 22 closed it is the
   only way to deploy without opening a temporary SSH window; see the workaround notes and
   the registry-based deploy path above.
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
- **Cache headers:** hashed assets get `max-age=31536000`; `index.html` gets `no-cache`.
  Without the second half a deploy stays invisible in a browser that cached the shell.
- **`add_header` does not inherit.** A `location` that defines its own `add_header`
  discards every header set at `server` level, so HSTS would silently vanish from exactly
  the `.js`/`.css` responses a downgrade attack targets. Fixed by keeping the security
  headers only at server level and using the `expires` directive for caching, since
  `expires` does not break inheritance. Cost: assets lost the `immutable` token
  (they are now `max-age=31536000`, not `public, max-age=31536000, immutable`).

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

# host access: port 22 is closed, use SSM instead
aws ssm start-session --target i-0a904020481f92b78 --region eu-south-1 --profile deploy
aws ssm send-command --instance-ids i-0a904020481f92b78 --region eu-south-1 --profile deploy `
  --document-name AWS-RunShellScript --parameters commands='cd /home/ec2-user/app && docker compose ps'

# what SSH rules exist right now (should be none)
aws ec2 describe-security-groups --group-ids sg-01918d764450132ea --region eu-south-1 `
  --profile deploy --query 'SecurityGroups[0].IpPermissions[?FromPort==`22`].IpRanges' --output text

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