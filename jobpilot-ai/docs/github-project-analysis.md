# GitHub project analysis preview

This feature turns a public GitHub repository into an evidence-first preview for a later PPT workflow.

## Endpoint

POST /api/v1/project-analysis/github

~~~json
{
  "repositoryUrl": "https://github.com/owner/repository"
}
~~~

The response includes repository metadata, detected technology, ranked core files, feature candidates, and a summarySource field.

~~~mermaid
flowchart LR
  A["Repository URL"] --> B["GitHub REST API"]
  B --> C["Tree + languages"]
  C --> D["Manifest and high-signal source selection"]
  D --> E["Deterministic evidence analysis"]
  E --> F["Preview + feature selection"]
  E --> G{"Gemini enabled?"}
  G -->|Yes| H["Bounded semantic code reading"]
  G -->|No or failed| F
  H --> F
~~~

## What is inspected

- Repository metadata, default branch, language statistics, and the recursive file tree.
- Primary files such as README.md, package.json, pom.xml, Gradle files, Python manifests, and Docker configuration.
- At most 10 source files, ranked to favor controllers, routers, services, repositories, entities, application entry points, pages, and components.

Files over 120 KB and generated, dependency, build, test, environment, vendor, and minified files are excluded. The service reads code; it does not execute, build, test, or deploy the target repository.

## Analysis behavior

The deterministic stage is always the source of facts. It detects stack evidence from manifests and source patterns, ranks core files by architectural responsibility, and creates candidate features with their supporting paths.

Gemini is optional. When enabled, it first receives a bounded repository map and selects up to eight exact source paths needed to understand the architecture. The server validates those paths against the GitHub tree and fetches only those extra files. A second request receives the selected files, additional core files, and up to two small context files (for example `pom.xml` or `package.json`), not the entire repository. Values that look like keys, secrets, tokens, or passwords are redacted before sending. Both requests set `store=false` and `thinking_level=minimal`; the final explanation is capped at 1,200 output tokens.

The semantic result explains the project in Korean and creates one to five fact-only implementation stories. Each story states what the supplied code implements, how the relevant files participate, detected technologies, and exact file/symbol evidence; the preview places a short code excerpt next to that explanation. It must not include code-review feedback, recommendations, risks, or unsupported API/database facts. Static technology and integration facts remain deterministic. If the key is absent or Gemini fails, the endpoint still returns a `STATIC` result with a conservative implementation-story fallback.

## Configuration

Set environment variables on the Spring Boot process:

~~~text
GITHUB_TOKEN=                 # optional for public repositories, recommended for higher rate limits
GEMINI_ENABLED=false          # set true only when AI wording is wanted
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.5-flash-lite
~~~

`GEMINI_ENABLED=false` is the kill switch: with that value there is no Gemini API request or Gemini token usage.

For the React dev server, copy frontend/.env.example to frontend/.env and set:

~~~text
VITE_API_BASE_URL=http://localhost:8080
~~~

Private-repository support should use a GitHub App installation token with repository-scoped read-only Contents permission. A personal token must never be accepted from the browser or stored in the application database.
