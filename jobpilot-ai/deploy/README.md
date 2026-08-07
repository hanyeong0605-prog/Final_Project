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
