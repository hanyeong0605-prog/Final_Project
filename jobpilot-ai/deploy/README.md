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

## Administrator phone face verification

The administrator screen shows a short-lived QR code instead of requiring a PC
webcam. Before deploying, create the private directory configured by
`ADMIN_FACE_PHOTOS_DIR` in `/etc/jobpilot/jobpilot.env` and place one reference
photo per administrator in it. The file name must be the member's `loginId`, for
example `admin01.jpg` or `admin01.png`.

The directory is mounted read-only into the internal face service; it is not
included in the Docker image, Git repository, or Nginx public paths. Keep
`INTERNAL_API_KEY` set: the backend uses it when calling the internal face
endpoint. The public site must use HTTPS for phone camera permission to work.

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
