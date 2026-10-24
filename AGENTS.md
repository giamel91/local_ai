# sample_ai — agent instructions

Spring Boot **3.5.16** + Spring AI **1.0.7** RAG service. Takes a question, does a
vector search, asks an LLM, returns structured JSON.

## Build & run

```powershell
# build
.\mvnw.cmd -B clean package -DskipTests

# run against local Ollama
$env:SPRING_PROFILES_ACTIVE='local'; .\mvnw.cmd spring-boot:run

# run against Amazon Bedrock
$env:SPRING_PROFILES_ACTIVE='aws'; .\mvnw.cmd spring-boot:run
```

## Architecture

```
Angular frontend (separate repo: sample_ai_frontend)
        |
        v
this API (Spring Boot)  --->  Amazon Bedrock (Qwen3 32B + Titan v2 embeddings)
        |
        +---> ChromaDB   (REQUIRED at startup - the app crashes without it)
        +---> Kafka      (optional; not deployed yet)
```

Spring profiles decide where the model runs:

| profile | chat | embeddings | needs AWS? |
|---|---|---|---|
| `local` | Ollama on this machine | Ollama | no |
| `aws` | Bedrock `qwen.qwen3-32b-v1:0` | Bedrock Titan v2 | yes |

## Hard-won gotchas — do not regress these

1. **Two Bedrock starters are required.** `spring-ai-starter-model-bedrock` gives
   embeddings; `spring-ai-starter-model-bedrock-converse` gives the chat model.
   Without the second one Spring Boot fails with *"No qualifying bean of type ChatModel"*.
2. **Bedrock models are disabled by default.** Both
   `spring.ai.bedrock.converse.chat.enabled=true` and
   `spring.ai.bedrock.titan.embedding.enabled=true` must be set.
3. **`spring.ai.bedrock.titan.embedding.input-type` defaults to `image`.** Spring AI then
   sends a 1x1 PNG to a text model and Bedrock answers *"Malformed input request: 2 schema
   violations found"*. It **must** be `text`.
4. **Chroma 1.0 cannot create tenants/databases.** Spring AI defaults to
   `SpringAiTenant`/`SpringAiDatabase`, which don't exist. Use `default_tenant` /
   `default_database`, and `initialize-schema=true`.
5. **Kafka will hang every request for 60s** if no broker is reachable. Keep the fast-fail
   settings (`max.block.ms=2000`) and `spring.kafka.listener.auto-startup=false` until a
   real broker exists.
6. Spring AI needs both providers on the classpath to be disambiguated via
   `spring.ai.model.chat` / `spring.ai.model.embedding`.

## Conventions

- **Never hard-code AWS credentials.** On AWS the app uses the EC2 IAM role via the SDK
  default credential chain; locally it uses the `default` CLI profile.
- Environment-specific values belong in `application-<profile>.properties`, never
  hard-coded in Java.
- Chroma settings are shared and live in `application.properties`.

## Current task

AWS deployment (EC2 + Docker Compose). **Read `DEPLOYMENT-STATUS.md` for exact state,
remaining steps and open blockers.**

## Working style — permanent instruction from the owner

The owner is learning while this gets built. Every future change follows this:

1. **Explain before acting.** Say what will change, why that layer, and what breaks if it
   goes wrong. Name the trade-off, do not hide it.
2. **Teach the concept, not just the command.** Define every unfamiliar term once
   (challenge, reverse proxy, origin, prefix list).
3. **Show the change.** Diff or the changed lines, plus the file path.
4. **Ask before anything irreversible or costly**: security-group edits, DNS records,
   certificate issuance, deletions, anything that bills money.
5. **Verify and state the evidence.** Command run, output observed, exact result.
6. Explain mistakes plainly when one happens; do not paper over them.

