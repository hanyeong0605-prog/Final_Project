# EC2 deployment

1. Create `/etc/jobpilot/jobpilot.env` from `jobpilot.env.example` and set permissions to `600`.
2. From the project directory, run:

   ```bash
   sudo ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
     --env-file /etc/jobpilot/jobpilot.env \
     -f docker-compose.prod.yml up -d --build
   ```

3. Verify:

   ```bash
   sudo ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
     --env-file /etc/jobpilot/jobpilot.env \
     -f docker-compose.prod.yml ps
   curl http://localhost/api/v1/health
   ```

## GitHub Actions CI/CD

`main` branch pushes run frontend and backend checks, then deploy only services whose
source changed. Runtime secrets stay on EC2 in `/etc/jobpilot/jobpilot.env`; the
workflow never writes or prints that file.

Add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `EC2_HOST` | EC2 Elastic IP or domain |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_PRIVATE_KEY` | Full contents of the EC2 `.pem` private key |
| `REPO_READ_TOKEN` | Fine-grained GitHub token with **Contents: Read-only** access to this repository |

The deployment workflow passes `REPO_READ_TOKEN` to the EC2 only for `git fetch` and
does not save it in the EC2 git remote configuration. `VITE_KAKAO_MAP_KEY` remains in
the EC2 environment file because the frontend is built on EC2.

The server working tree must remain clean. The workflow deliberately uses
`git merge --ff-only`; it stops rather than overwriting manual changes on the server.
