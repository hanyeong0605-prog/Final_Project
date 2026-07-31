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
  G -->|Yes| H["Korean presentation wording"]
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

Gemini is optional. When enabled, it receives capped, redacted excerpts only and produces Korean overview text and short feature explanations in JSON. It is instructed not to claim that code was run, secure, complete, or production-ready. If the key is absent or Gemini fails, the endpoint still returns a STATIC result.

## Configuration

Set environment variables on the Spring Boot process:

~~~text
GITHUB_TOKEN=                 # optional for public repositories, recommended for higher rate limits
GEMINI_ENABLED=false          # set true only when AI wording is wanted
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.5-flash-lite
~~~

For the React dev server, copy frontend/.env.example to frontend/.env and set:

~~~text
VITE_API_BASE_URL=http://localhost:8080
~~~

Private-repository support should use a GitHub App installation token with repository-scoped read-only Contents permission. A personal token must never be accepted from the browser or stored in the application database.
